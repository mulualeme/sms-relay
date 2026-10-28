package com.example.smsrelay

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.telephony.SmsManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

@Serializable enum class EmailMode { APP, GMAIL, SMTP }
@Serializable data class Smtp(
    val host: String = "", val user: String = "", val pass: String = "", val port: String = "",
    val auth: Boolean = true, val ssl: Boolean = true, // ssl=false -> TLS (STARTTLS)
)
@Serializable data class Settings(
    val emailMode: EmailMode = EmailMode.SMTP,
    val smtp: Smtp = Smtp(),
    val gmailUser: String = "", val gmailPass: String = "",
    val telegramToken: String = "",
    val saveHistory: Boolean = true,
    val historyDays: Int = 14,
    val sticky: Boolean = true,
    val acceptedWarning: Boolean = false,
)
@Serializable data class PhoneAlerts(val target: Target = Target(), val alerts: Set<Alert> = emptySet())
@Serializable data class RemoteReply(val allowAll: Boolean = false, val numbers: List<String> = listOf(""))
@Serializable data class Result(val type: TargetType, val value: String, val ok: Boolean, val error: String? = null)
@Serializable data class History(val time: Long, val rule: String, val sender: String, val body: String, val sent: String, val results: List<Result>, val kind: String = "Forward")
@Serializable data class RulesFile(val app: String = APP_NAME, val version: Int = 1, val rules: List<Rule> = emptyList())
@Serializable data class Pending(val ruleId: String, val event: Event, val targets: List<Target>, val tries: Int = 0)
@Serializable data class ReplyState(val count: Int = 0, val last: Long = 0, val day: String = "")

// ponytail: JSON in SharedPreferences instead of Room; fine for hundreds of rows, move to Room if history grows large.
// ponytail: passwords stored in app-private prefs, unencrypted; add androidx.security EncryptedSharedPreferences if needed.
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("relay", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private inline fun <reified T> get(k: String, d: T): T = p.getString(k, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() } ?: d
    private inline fun <reified T> put(k: String, v: T) = p.edit().putString(k, json.encodeToString(v)).apply()

    var rules: List<Rule> get() = get("rules2", emptyList()); set(v) = put("rules2", v)

    fun exportRules(): String = json.encodeToString(RulesFile(rules = rules))

    /** Rules with an id we already have are replaced, the rest are added. Returns how many were imported. */
    fun importRules(text: String): Int {
        val file = runCatching { json.decodeFromString<RulesFile>(text) }.getOrNull()
        require(file != null && file.app == APP_NAME) { "This is not a $APP_NAME rules file." }
        rules = mergeRules(rules, file.rules)
        return file.rules.size
    }
    var settings: Settings get() = get("settings2", Settings()); set(v) = put("settings2", v)
    var history: List<History> get() = get("history2", emptyList()); set(v) = put("history2", v)
    var pending: List<Pending> get() = get("pending2", emptyList()); set(v) = put("pending2", v)
    var alerts: PhoneAlerts get() = get("alerts", PhoneAlerts()); set(v) = put("alerts", v)
    var remote: RemoteReply get() = get("remote", RemoteReply()); set(v) = put("remote", v)
    var replyStates: Map<String, ReplyState> get() = get("replyStates", emptyMap()); set(v) = put("replyStates", v)
    var total: Int get() = p.getInt("total", 0); set(v) = p.edit().putInt("total", v).apply()
    val today: Int get() = if (p.getString("todayDate", "") == LocalDate.now().toString()) p.getInt("today", 0) else 0

    fun record(h: History) = synchronized(Store::class) {
        if (h.results.any { it.ok }) {
            p.edit().putString("todayDate", LocalDate.now().toString()).putInt("today", today + 1).apply()
            total += 1
        }
        val s = settings
        if (!s.saveHistory) return@synchronized
        val cutoff = System.currentTimeMillis() - s.historyDays * 86_400_000L
        history = (listOf(h) + history).filter { it.time >= cutoff }.take(1000)
    }
}

private val NETWORK_ERRORS = listOf("Error in calling api", "Not Connected", "Error in connecting")

object Forwarder {
    // several at once: one by one, a single slow or failing target held up every message queued behind it
    private val pool = Executors.newFixedThreadPool(4)

    fun onEvent(ctx: Context, e: Event, done: () -> Unit = {}) = pool.execute {
        watchNetwork(ctx)
        try { handle(ctx, e) } catch (t: Throwable) { t.printStackTrace() } finally { done() }
    }

    private val watching = AtomicBoolean()

    /** Retries queued forwards the moment the network is back, instead of waiting for JobScheduler's backoff. */
    private fun watchNetwork(ctx: Context) {
        if (!watching.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        runCatching {
            app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(n: Network) { thread { retryPending(app) } }
                override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                    if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) thread { retryPending(app) }
                }
            })
        }
    }

    private val retrying = AtomicBoolean()

    /** Re-sends queued network failures in parallel; returns true if some are still waiting. Never call from [pool]. */
    fun retryPending(ctx: Context): Boolean {
        val st = Store(ctx)
        if (!online(ctx)) return st.pending.isNotEmpty() // offline: don't spend tries
        if (!retrying.compareAndSet(false, true)) return true
        try {
            val queued = synchronized(Store::class) { st.pending.also { if (it.isNotEmpty()) st.pending = emptyList() } }
            if (queued.isEmpty()) return false
            val rules = st.rules.associateBy { it.id }
            val wifi = onWifi(ctx)
            val left = queued.map { p ->
                pool.submit(Callable {
                    val r = rules[p.ruleId] ?: return@Callable null
                    if (r.wifiOnly && !wifi) return@Callable p // waits for Wi-Fi without using up its tries
                    val failed = deliver(ctx, st, r, p.event, p.targets, queue = false).zip(p.targets).filter { !it.first.ok }.map { it.second }
                    if (failed.isEmpty() || p.tries >= 5) null else p.copy(targets = failed, tries = p.tries + 1)
                })
            }.mapNotNull { it.get() }
            if (left.isNotEmpty()) synchronized(Store::class) { st.pending = st.pending + left }
            return left.isNotEmpty()
        } finally { retrying.set(false) }
    }

    private fun online(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun handle(ctx: Context, e: Event) {
        val st = Store(ctx)
        if (e.type == EventType.SMS && remoteReply(ctx, st, e)) return
        if (e.type == EventType.ALERT) {
            val a = st.alerts
            if (e.alert in a.alerts && a.target.valid()) {
                val r = Rule(name = "Phone Alert", targets = listOf(a.target))
                deliver(ctx, st, r, e, r.targets, text = e.body, subject = "$APP_NAME Phone Alert")
            }
        }
        for (r in st.rules) {
            if (r.matches(e)) {
                deliver(ctx, st, r, e, r.targets)
                if (r.autoReply && r.replyMessage.isNotBlank()) sendReply(ctx, st, r, e, r.replyMessage)
            }
            if (r.kind == RuleKind.AUTO_REPLY && claimReply(ctx, st, r, e)) {
                val delay = r.delay * 1000L
                // ponytail: in-process delay; a very long delay is lost if Android kills the process. Use AlarmManager if that matters.
                if (delay > 0) Thread { Thread.sleep(delay); sendReply(ctx, Store(ctx), r, e, r.replyMessage) }.start()
                else sendReply(ctx, st, r, e, r.replyMessage)
            }
        }
    }

    /** Sends to every target, records history, queues network failures. */
    fun deliver(ctx: Context, st: Store, r: Rule, e: Event, targets: List<Target>, queue: Boolean = true,
                text: String = render(r.message, e, r.name).trimEnd(), subject: String = render(r.subject, e, r.name).trim()): List<Result> {
        val res = targets.map { t ->
            val x = runCatching { send(ctx, st.settings, r, e, t, text, subject) }
            Result(t.type, t.value, x.isSuccess, x.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName })
        }
        st.record(History(System.currentTimeMillis(), r.name, e.name?.let { "${e.sender} ($it)" } ?: e.sender, e.body, text, res))
        // retry only network-level failures (no connection / timeout / not on Wi-Fi), not rejected requests
        val retry = res.zip(targets).filter { (x, t) -> !x.ok && t.type != TargetType.SMS &&
            NETWORK_ERRORS.any { x.error?.startsWith(it) == true } }.map { it.second }
        if (queue && r.retry && retry.isNotEmpty()) {
            synchronized(Store::class) { st.pending = st.pending + Pending(r.id, e, retry) }
            RetryJob.schedule(ctx, r.wifiOnly)
        }
        return res
    }

    private fun send(ctx: Context, s: Settings, r: Rule, e: Event, t: Target, text: String, subject: String) {
        if (t.type != TargetType.SMS && r.wifiOnly && !onWifi(ctx)) throw IOException("Not Connected To Wifi")
        // fail at once with no connection instead of waiting out the timeouts; it is queued for retry
        if (t.type != TargetType.SMS && !online(ctx)) throw IOException("Not Connected To Internet")
        when (t.type) {
            TargetType.SMS -> sendSms(ctx, t.value, text, if (r.sendSim == -2) e.subId else r.sendSim)
            TargetType.EMAIL -> sendMail(s, t.value, subject, text)
            TargetType.TELEGRAM -> {
                require(s.telegramToken.isNotBlank()) { "Telegram bot token is not set. Add it in Settings > Telegram." }
                http("POST", "https://api.telegram.org/bot${s.telegramToken}/sendMessage",
                    listOf(Header("Content-Type", "application/x-www-form-urlencoded")), "chat_id=${enc(t.value)}&text=${enc(text)}")
            }
            TargetType.URL -> {
                val jsonBody = t.body.trimStart().let { it.startsWith("{") || it.startsWith("[") }
                val body = urlFormat(t.body, e, r.name, text, if (jsonBody) ::jsonEscape else { v -> v })
                val headers = t.headers.filter { it.key.isNotBlank() }.let { h ->
                    if (jsonBody && h.none { it.key.equals("Content-Type", true) }) h + Header("Content-Type", "application/json") else h
                }
                val url = urlFormat(t.value, e, r.name, text, ::enc)
                http(t.method, url, headers, if (t.method == "GET") null else body)
            }
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Suppress("DEPRECATION")
    fun sendSms(ctx: Context, to: String, text: String, subId: Int = -1) {
        val sm = when {
            subId >= 0 && Build.VERSION.SDK_INT >= 31 -> ctx.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            subId >= 0 -> SmsManager.getSmsManagerForSubscriptionId(subId)
            Build.VERSION.SDK_INT >= 31 -> ctx.getSystemService(SmsManager::class.java)
            else -> SmsManager.getDefault()
        }
        sm.sendMultipartTextMessage(to, null, sm.divideMessage(text), null, null)
    }

    private fun http(method: String, url: String, headers: List<Header>, body: String?) {
        val c = try { URL(url).openConnection() as HttpURLConnection } catch (x: Exception) { error("Invalid URL: $url") }
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            headers.forEach { c.setRequestProperty(it.key, it.value) }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            if (code !in 200..299) error("Api returned HTTP $code " + (c.errorStream?.bufferedReader()?.readText()?.take(200) ?: ""))
        } catch (x: IOException) {
            throw IOException("Error in calling api ${x.message}", x)
        } finally { c.disconnect() }
    }

    private fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun sendMail(s: Settings, to: String, subject: String, body: String) = when (s.emailMode) {
        EmailMode.APP -> error("App email account is not available in $APP_NAME. Select Gmail or SMTP in Settings > Email Configuration.")
        EmailMode.GMAIL -> smtp(Smtp("smtp.gmail.com", s.gmailUser, s.gmailPass, "465"), to, subject, body)
        EmailMode.SMTP -> smtp(s.smtp, to, subject, body)
    }

    /** Minimal SMTP client: SSL (implicit TLS) or TLS (STARTTLS), optional AUTH LOGIN, UTF-8 plain text. */
    private fun smtp(c: Smtp, to: String, subject: String, body: String) {
        require(c.host.isNotBlank()) { "Please enter SMTP host to continue" }
        val port = c.port.toIntOrNull() ?: if (c.ssl) 465 else 587
        val tls = SSLSocketFactory.getDefault() as SSLSocketFactory
        var sock: Socket = try {
            // plain Socket(host, port) has no connect timeout and can hang for minutes on a bad network
            Socket().apply { connect(InetSocketAddress(c.host, port), 10_000) }.let { if (c.ssl) tls.createSocket(it, c.host, port, true) else it }
        } catch (x: IOException) { throw IOException("Error in connecting to smtp server. ${x.message}", x) }
        sock.soTimeout = 15_000
        try {
            var r = BufferedReader(InputStreamReader(sock.getInputStream()))
            var w = OutputStreamWriter(sock.getOutputStream())
            fun cmd(line: String?, expect: Int) {
                if (line != null) { w.write(line + "\r\n"); w.flush() }
                var resp: String
                do resp = r.readLine() ?: throw IOException("SMTP connection closed") while (resp.length > 3 && resp[3] == '-')
                if (resp.take(3).toIntOrNull() != expect) error("Error in forwarding message using smtp. $resp")
            }
            fun verify(s: Socket) {
                (s as SSLSocket).startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(c.host, s.session)) throw IOException("SMTP certificate does not match ${c.host}")
            }
            if (c.ssl) verify(sock)
            cmd(null, 220); cmd("EHLO localhost", 250)
            if (!c.ssl) {
                cmd("STARTTLS", 220)
                sock = tls.createSocket(sock, c.host, port, true).also(::verify)
                r = BufferedReader(InputStreamReader(sock.getInputStream())); w = OutputStreamWriter(sock.getOutputStream())
                cmd("EHLO localhost", 250)
            }
            val b64 = { s: String -> Base64.getEncoder().encodeToString(s.toByteArray()) }
            if (c.auth) { cmd("AUTH LOGIN", 334); cmd(b64(c.user), 334); cmd(b64(c.pass), 235) }
            val from = c.user
            val rcpts = to.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
            cmd("MAIL FROM:<$from>", 250)
            rcpts.forEach { cmd("RCPT TO:<$it>", 250) }
            cmd("DATA", 354)
            val msg = "From: $APP_NAME <$from>\r\nTo: ${rcpts.joinToString { "<$it>" }}\r\nSubject: =?UTF-8?B?${b64(subject)}?=\r\n" +
                "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" +
                Base64.getMimeEncoder().encodeToString(body.toByteArray()) + "\r\n."
            cmd(msg, 250)
            runCatching { cmd("QUIT", 221) }
        } finally { sock.close() }
    }

    // ---------- Remote reply ----------

    private fun remoteReply(ctx: Context, st: Store, e: Event): Boolean {
        val rr = st.remote
        val digits = e.sender.filter(Char::isDigit)
        val allowed = rr.allowAll || rr.numbers.any { n -> n.filter(Char::isDigit).let { it.length >= 6 && (digits.endsWith(it) || it.endsWith(digits)) } }
        if (!allowed || digits.isEmpty()) return false
        val (who, text) = parseRemoteReply(e.body) ?: return false
        val number = if (who.any(Char::isLetter)) numberForName(ctx, who) else who
        val res = if (number == null) Result(TargetType.SMS, who, false, "Contact not found: $who")
        else runCatching { sendSms(ctx, number, text, e.subId) }.let { Result(TargetType.SMS, number, it.isSuccess, it.exceptionOrNull()?.message) }
        st.record(History(System.currentTimeMillis(), "Remote Reply", e.name?.let { "${e.sender} ($it)" } ?: e.sender, e.body, text, listOf(res), "Remote Reply"))
        return true
    }

    // ---------- Auto reply ----------

    private fun autoReplyApplies(ctx: Context, st: Store, r: Rule, e: Event): Boolean {
        if (r.paused || r.replyMessage.isBlank() || e.sender.none(Char::isDigit)) return false
        if (e.replySource == null && e.type == EventType.SMS) { if (ReplySource.SMS !in r.sources) return false }
        else if (e.replySource !in r.sources) return false
        if (r.receiveSim >= 0 && e.subId != r.receiveSim) return false
        if (r.inSchedule(java.time.LocalDateTime.now()) != r.workWhen) return false
        for (c in r.conditions) if (!condition(ctx, c)) return false
        val key = r.id + "|" + e.sender.filter(Char::isDigit)
        val s = st.replyStates[key] ?: ReplyState()
        val now = System.currentTimeMillis()
        return when (r.multi) {
            MultiMode.ALL -> true
            MultiMode.ONCE_A_DAY -> s.day != LocalDate.now().toString()
            MultiMode.STOP_AFTER -> s.count < r.multiCount
            MultiMode.PAUSE_FOR -> now - s.last >= r.multiCount * r.pauseUnit.ms
        }
    }

    /** Checks and records the reply in one step, so two messages handled at once can't both pass "once a day". */
    private fun claimReply(ctx: Context, st: Store, r: Rule, e: Event): Boolean = synchronized(Store::class) {
        if (!autoReplyApplies(ctx, st, r, e)) return false
        val key = r.id + "|" + e.sender.filter(Char::isDigit)
        val s = st.replyStates[key] ?: ReplyState()
        st.replyStates = st.replyStates + (key to ReplyState(s.count + 1, System.currentTimeMillis(), LocalDate.now().toString()))
        true
    }

    private fun condition(ctx: Context, c: ReplyCond): Boolean = when (c) {
        ReplyCond.Silent -> ctx.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL
        ReplyCond.Dnd -> ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        ReplyCond.ScreenOff -> !ctx.getSystemService(PowerManager::class.java).isInteractive || ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        ReplyCond.Charging -> ctx.getSystemService(BatteryManager::class.java).isCharging
    }

    private fun sendReply(ctx: Context, st: Store, r: Rule, e: Event, template: String) {
        val text = replyFormat(template, e)
        val res = runCatching { sendSms(ctx, e.sender, text, if (r.sendSim == -2 || r.sendSim == -1 && e.subId >= 0) e.subId else r.sendSim) }
        st.record(History(System.currentTimeMillis(), r.name, e.name?.let { "${e.sender} ($it)" } ?: e.sender, e.body, text,
            listOf(Result(TargetType.SMS, e.sender, res.isSuccess, res.exceptionOrNull()?.message)), "Auto Reply"))
    }
}

/** Retries queued deliveries once the network is back (original: NetworkConnectivityJobService). */
class RetryJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread { jobFinished(params, Forwarder.retryPending(this)) }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        fun schedule(ctx: Context, wifiOnly: Boolean = false) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            // re-scheduling the same id cancels a retry that is already waiting or running
            js.getPendingJob(1)?.let { @Suppress("DEPRECATION") if (wifiOnly || it.networkType == JobInfo.NETWORK_TYPE_ANY) return }
            // fallback for when the app process is not running; while it is, the network callback retries at once
            val job = JobInfo.Builder(1, ComponentName(ctx, RetryJob::class.java))
                .setRequiredNetworkType(if (wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_LINEAR)
                .setPersisted(true)
                .build()
            js.schedule(job)
        }
    }
}
