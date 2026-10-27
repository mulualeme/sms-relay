package com.example.smsrelay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun Context.granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
private fun clock(ms: Long) = ms.local().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))

fun contactName(ctx: Context, number: String): String? {
    if (number.isBlank() || !ctx.granted(Manifest.permission.READ_CONTACTS)) return null
    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    return runCatching {
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
}

fun numberForName(ctx: Context, name: String): String? {
    if (!ctx.granted(Manifest.permission.READ_CONTACTS)) return null
    return runCatching {
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ? COLLATE NOCASE", arrayOf(name), null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
}

data class Sim(val id: Int, val slot: Int, val carrier: String, val number: String) {
    val label get() = "SIM ${slot + 1} - $carrier"
    val detail get() = "SIM: $carrier: $number"
}

@SuppressLint("MissingPermission")
fun sims(ctx: Context): List<Sim> {
    if (!ctx.granted(Manifest.permission.READ_PHONE_STATE)) return emptyList()
    val sm = ctx.getSystemService(SubscriptionManager::class.java)
    return runCatching {
        sm.activeSubscriptionInfoList.orEmpty().map { s ->
            @Suppress("DEPRECATION")
            val number = runCatching { if (Build.VERSION.SDK_INT >= 33) sm.getPhoneNumber(s.subscriptionId) else s.number }.getOrNull().orEmpty()
            Sim(s.subscriptionId, s.simSlotIndex, s.carrierName?.toString().orEmpty(), number)
        }
    }.getOrDefault(emptyList())
}

private fun BroadcastReceiver.dispatch(ctx: Context, e: Event) {
    val p = goAsync()
    Forwarder.onEvent(ctx.applicationContext, e) { p.finish() }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return
        val from = parts[0].displayOriginatingAddress ?: ""
        val body = parts.joinToString("") { it.displayMessageBody ?: "" }
        val subId = intent.getIntExtra("subscription", intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1))
        NotifListener.recentSms[body.trim()] = System.currentTimeMillis()
        dispatch(ctx, Event(EventType.SMS, from, contactName(ctx, from), body, subId = subId, sim = sims(ctx).firstOrNull { it.id == subId }?.detail.orEmpty()))
    }
}

/** On call end, reads the newest call-log row to tell missed / rejected / answered / outgoing calls apart and get the duration. */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_IDLE) return
        if (!ctx.granted(Manifest.permission.READ_CALL_LOG)) return
        val p = goAsync()
        val app = ctx.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            try { lastCall(app)?.let { Forwarder.onEvent(app, it) } } finally { p.finish() }
        }, 2000) // the call log row is written shortly after IDLE
    }

    private fun lastCall(ctx: Context): Event? {
        val prefs = ctx.getSharedPreferences("call", Context.MODE_PRIVATE)
        val cols = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DURATION, CallLog.Calls.DATE)
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, cols, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            if (!c.moveToFirst()) return null
            val id = c.getLong(0)
            if (id == prefs.getLong("lastId", -1) || System.currentTimeMillis() - c.getLong(4) > 30 * 60_000) return null
            prefs.edit().putLong("lastId", id).apply()
            val number = c.getString(1).orEmpty(); val dur = c.getLong(3); val at = clock(c.getLong(4))
            val name = contactName(ctx, number); val who = name ?: number
            val d = "%d:%02d".format(dur / 60, dur % 60)
            val (alert, src, text) = when (c.getInt(2)) {
                CallLog.Calls.INCOMING_TYPE -> Triple(Alert.IncomingCall, ReplySource.IncomingCallEnd, "Incoming Call from $who at $at.\nDuration: $d")
                CallLog.Calls.OUTGOING_TYPE -> Triple(Alert.OutgoingCall, ReplySource.OutgoingCallEnd, "Outgoing Call to $who at $at.\nDuration: $d")
                CallLog.Calls.REJECTED_TYPE -> Triple(Alert.IncomingCall, ReplySource.MissedCall, "Rejected Incoming Call from $who at $at.")
                CallLog.Calls.MISSED_TYPE -> Triple(Alert.MissedCall, ReplySource.MissedCall, "Missed Call from $who at $at")
                else -> return null
            }
            return Event(EventType.ALERT, number, name, text, alert = alert, replySource = src)
        }
        return null
    }
}

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val boot = intent.action == Intent.ACTION_BOOT_COMPLETED
        if (boot) {
            if (Store(ctx).pending.isNotEmpty()) RetryJob.schedule(ctx)
            AlwaysOnService.sync(ctx)
        }
        val now = clock(System.currentTimeMillis())
        val (alert, text) = if (boot) Alert.BootUp to "Phone turned on at $now" else Alert.ShutDown to "Phone shut down at $now"
        dispatch(ctx, Event(EventType.ALERT, APP_NAME, null, text, alert = alert))
    }
}

/** Sticky notification that keeps the app alive; also listens for BATTERY_LOW, which manifest receivers no longer get. */
class AlwaysOnService : Service() {
    private val battery = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val pct = ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            Forwarder.onEvent(ctx.applicationContext, Event(EventType.ALERT, APP_NAME, null, "battery remaining at $pct%", alert = Alert.LowBattery))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("persistent", "Notification To Forward SMS", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Channel for persistent notifications" })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "persistent").setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(APP_NAME).setContentText("This notification enables app to forward all messages even if the app is closed.")
            .setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING) else startForeground(1, n)
        registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_LOW))
        Mms.watch(this)
    }

    override fun onDestroy() { unregisterReceiver(battery); Mms.stop(this); super.onDestroy() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { Mms.watch(this); return START_STICKY } // retry once SMS permission is granted
    override fun onBind(intent: Intent?) = null

    companion object {
        fun sync(ctx: Context) {
            val i = Intent(ctx, AlwaysOnService::class.java)
            if (Store(ctx).settings.sticky) runCatching { ctx.startForegroundService(i) } else ctx.stopService(i)
        }
    }
}

class NotifListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.packageName == packageName || sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val x = n.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (x.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: x.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrNull()
        Forwarder.onEvent(applicationContext, Event(EventType.NOTIFICATION, title, null, text, app = sbn.packageName, appLabel = label))
        // RCS chats only surface as notifications of the default SMS app; skip ones that duplicate a just-received SMS.
        if (sbn.packageName == Telephony.Sms.getDefaultSmsPackage(this)) {
            val now = System.currentTimeMillis()
            recentSms.entries.removeAll { now - it.value > 60_000 }
            if (!recentSms.containsKey(text.trim())) {
                val number = if (title.any(Char::isDigit) && title.none(Char::isLetter)) title else numberForName(this, title) ?: title
                Forwarder.onEvent(applicationContext, Event(EventType.SMS, number, title.takeIf { it != number }, text, rcs = true, replySource = ReplySource.RCS))
            }
        }
    }

    companion object { val recentSms = java.util.concurrent.ConcurrentHashMap<String, Long>() }
}

/**
 * MMS (picture messages, and long texts some carriers send as MMS) never reach SMS_RECEIVED.
 * Watch the system message store and forward each newly downloaded inbox MMS as an SMS event.
 */
object Mms {
    private val INBOX = Uri.parse("content://mms/inbox")
    private var observer: android.database.ContentObserver? = null

    fun watch(ctx: Context) {
        if (observer != null || !ctx.granted(Manifest.permission.READ_SMS)) return
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences("mms", Context.MODE_PRIVATE)
        if (!prefs.contains("lastId")) prefs.edit().putLong("lastId", latestId(app)).apply() // don't forward old MMS
        observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = check(app)
        }.also { app.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, it) }
    }

    fun stop(ctx: Context) {
        observer?.let { ctx.applicationContext.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    private fun latestId(ctx: Context): Long = runCatching {
        ctx.contentResolver.query(INBOX, arrayOf("_id"), null, null, "_id DESC")?.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }.getOrNull() ?: 0L

    @Synchronized private fun check(ctx: Context) {
        val prefs = ctx.getSharedPreferences("mms", Context.MODE_PRIVATE)
        val last = prefs.getLong("lastId", 0)
        // m_type 132 = retrieved (downloaded) message; notification-only rows have no content yet
        val rows = runCatching {
            ctx.contentResolver.query(INBOX, arrayOf("_id", "sub_id", "date"), "_id > ? AND m_type = 132", arrayOf("$last"), "_id ASC")?.use { c ->
                buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getInt(1), c.getLong(2) * 1000)) }
            }
        }.getOrNull().orEmpty()
        for ((id, subId, date) in rows) {
            prefs.edit().putLong("lastId", id).apply()
            val from = address(ctx, id) ?: continue
            val (text, attachments) = parts(ctx, id)
            NotifListener.recentSms[text.trim()] = System.currentTimeMillis()
            val body = listOfNotNull(text.ifBlank { null }, if (attachments > 0) "[MMS: $attachments attachment(s)]" else null).joinToString("\n")
            Forwarder.onEvent(ctx, Event(EventType.SMS, from, contactName(ctx, from), body, subId = subId,
                sim = sims(ctx).firstOrNull { it.id == subId }?.detail.orEmpty(), time = date))
        }
    }

    // type 137 = the "from" address of an MMS
    private fun address(ctx: Context, id: Long): String? = runCatching {
        ctx.contentResolver.query(Uri.parse("content://mms/$id/addr"), arrayOf("address"), "type = 137", null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    private fun parts(ctx: Context, id: Long): Pair<String, Int> {
        var text = ""; var other = 0
        runCatching {
            ctx.contentResolver.query(Uri.parse("content://mms/part"), arrayOf("ct", "text"), "mid = ?", arrayOf("$id"), null)?.use { c ->
                while (c.moveToNext()) when (val ct = c.getString(0).orEmpty()) {
                    "text/plain" -> text += c.getString(1).orEmpty()
                    "application/smil" -> {}
                    else -> if (ct.isNotEmpty()) other++
                }
            }
        }
        return text to other
    }
}
