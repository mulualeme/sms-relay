package com.example.smsrelay

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

const val APP_NAME = "SMS Relay"

// Mirrors the original app's models: Rule / ContentConditionGroup / RuleSchedule / receivers / chips.

@Serializable enum class RuleKind(val title: String, val badge: String, val desc: String) {
    BASIC("Basic Rule", "SMS", "Forward your messages with minimal setup"),
    ADVANCED("Advanced Rule", "SMS", "Provides more control and flexibility over how your messages are forwarded"),
    NOTIFICATION("Notification Rule", "Notification", "Forward other application notifications"),
    AUTO_REPLY("Auto Reply Rule", "SMS", "Automatic Reply to all SMS that are filtered by the rule."),
}

@Serializable enum class Filter(val label: String, val phrase: String) {
    Contains("Contains", "contains"),
    StartsWith("Starts with", "starts with"),
    EndsWith("Ends with", "ends with"),
    ExactlyMatches("Exactly matches with", "exactly matches with"),
    DoesNotContain("Does not contains", "does not contain"),
    Regex("Regex", "matches regex"),
}

@Serializable enum class TargetType(val label: String, val hint: String) {
    SMS("Phone Number", "Enter Phone Number"),
    EMAIL("Email", "Enter Email Address"),
    TELEGRAM("Telegram", "Enter Telegram Id"),
    URL("URL", "Edit URL"),
}

@Serializable enum class ChipType(val label: String) {
    Sender("Sender"), Message("Message"), Time("Time"), RuleName("Rule Name"), AppName("App Name"),
    NewLine("New Line"), SimDetail("Sim Detail"), NotificationAppName("Notification App Name"), Text("Text"),
}

@Serializable enum class Alert(val label: String) {
    MissedCall("Missed Call"), IncomingCall("Incoming Call"), OutgoingCall("Outgoing Call"),
    LowBattery("Low Battery"), ShutDown("Phone Shut Down"), BootUp("Phone Turned On"),
}

@Serializable enum class ReplySource(val label: String) { SMS("SMS"), RCS("RCS"), IncomingCallEnd("Incoming Call End"), OutgoingCallEnd("Outgoing Call end"), MissedCall("Missed Call") }
@Serializable enum class ReplyCond(val label: String) { Silent("Phone is Silent"), Dnd("Do Not Disturb is On"), ScreenOff("Screen is locked or Off"), Charging("Phone is charging") }
@Serializable enum class MultiMode(val label: String) { ALL("Reply to all SMS"), ONCE_A_DAY("One time a day"), STOP_AFTER("Stop After"), PAUSE_FOR("Pause for") }
@Serializable enum class PauseUnit(val label: String, val ms: Long) { SECONDS("second(s)", 1000), MINUTES("minute(s)", 60_000), HOURS("hour(s)", 3_600_000), DAYS("day(s)", 86_400_000) }

@Serializable data class Condition(val filter: Filter = Filter.Contains, val value: String = "")
@Serializable data class Group(val all: Boolean = false, val conditions: List<Condition> = emptyList())
@Serializable data class Header(val key: String = "", val value: String = "")
@Serializable data class Chip(val type: ChipType, val text: String = "")

@Serializable data class Target(
    val type: TargetType = TargetType.SMS,
    val value: String = "",                 // phone, email, telegram chat id, or URL
    val method: String = "POST",
    val headers: List<Header> = listOf(Header()),
    val body: String = "",
)

@Serializable data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Rule 1",
    val kind: RuleKind = RuleKind.BASIC,
    val paused: Boolean = false,
    val targets: List<Target> = listOf(Target()),
    val forwardRcs: Boolean = false,
    val allSenders: Boolean = true,
    val senders: List<Condition> = listOf(Condition()),   // number/contact name, or notification title
    val allContent: Boolean = true,
    val groups: List<Group> = listOf(Group()),              // groups are OR-ed
    val allApps: Boolean = true,
    val apps: List<String> = emptyList(),
    val message: List<Chip> = SMS_MESSAGE,
    val subject: List<Chip> = SMS_SUBJECT,
    val autoReply: Boolean = false,
    val replyMessage: String = "",
    val retry: Boolean = true,
    val wifiOnly: Boolean = false,
    val receiveSim: Int = -1,       // subscription id, -1 = all
    val sendSim: Int = -1,          // subscription id, -1 = default, -2 = same SIM
    val days: Set<Int> = (1..7).toSet(),   // DayOfWeek.value, Mon=1
    val allDay: Boolean = true,
    val start: Int = 0,             // minutes from midnight
    val end: Int = 0,
    // Auto Reply Rule
    val sources: Set<ReplySource> = setOf(ReplySource.SMS),
    val conditions: Set<ReplyCond> = emptySet(),
    val multi: MultiMode = MultiMode.ALL,
    val multiCount: Int = 1,
    val pauseUnit: PauseUnit = PauseUnit.HOURS,
    val delay: Int = 0,
    val workWhen: Boolean = true,
)

@Serializable enum class EventType { SMS, NOTIFICATION, ALERT }

@Serializable data class Event(
    val type: EventType,
    val sender: String,
    val name: String? = null,      // resolved contact name
    val body: String,
    val app: String? = null,       // notification package
    val appLabel: String? = null,
    val alert: Alert? = null,
    val replySource: ReplySource? = null,
    val subId: Int = -1,
    val sim: String = "",          // "SIM: Carrier: number"
    val rcs: Boolean = false,
    val time: Long = System.currentTimeMillis(),
)

val SMS_MESSAGE = listOf(Chip(ChipType.Sender), Chip(ChipType.NewLine), Chip(ChipType.Message), Chip(ChipType.NewLine), Chip(ChipType.Time))
val SMS_SUBJECT = listOf(Chip(ChipType.AppName), Chip(ChipType.Sender))
val NOTIF_MESSAGE = listOf(Chip(ChipType.NotificationAppName), Chip(ChipType.NewLine), Chip(ChipType.Message), Chip(ChipType.NewLine), Chip(ChipType.Time))
val NOTIF_SUBJECT = listOf(Chip(ChipType.AppName), Chip(ChipType.NotificationAppName))

// Order chips appear in "Available Details"; New Line can be added any number of times.
val SMS_MESSAGE_CHIPS = listOf(ChipType.Sender, ChipType.Message, ChipType.Time, ChipType.RuleName, ChipType.AppName, ChipType.NewLine, ChipType.SimDetail)
val SMS_SUBJECT_CHIPS = listOf(ChipType.Sender, ChipType.Time, ChipType.RuleName, ChipType.AppName, ChipType.SimDetail)
val NOTIF_MESSAGE_CHIPS = listOf(ChipType.NotificationAppName, ChipType.Message, ChipType.Time, ChipType.RuleName, ChipType.NewLine, ChipType.AppName)
val NOTIF_SUBJECT_CHIPS = listOf(ChipType.NotificationAppName, ChipType.Time, ChipType.RuleName, ChipType.AppName)

val SAMPLE = Event(EventType.SMS, "1234", "Contact Name", "This is a sample SMS", appLabel = "Telegram",
    sim = "SIM: Carrier Name: 7890", time = LocalDateTime.of(2022, 7, 17, 16, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

fun Condition.test(s: String) = when (filter) {
    Filter.Contains -> s.contains(value, true)
    Filter.DoesNotContain -> !s.contains(value, true)
    Filter.StartsWith -> s.startsWith(value, true)
    Filter.EndsWith -> s.endsWith(value, true)
    Filter.ExactlyMatches -> s.trim().equals(value.trim(), true)
    Filter.Regex -> runCatching { Regex(value).containsMatchIn(s) }.getOrDefault(false)
}

fun Group.active() = conditions.filter { it.value.isNotBlank() }
fun Group.test(s: String) = active().let { c -> if (all) c.all { it.test(s) } else c.any { it.test(s) } }

private fun phoneDigits(s: String) = s.filter(Char::isDigit).trimStart('0')

/** Same phone number written differently: "+251 94 528 7216" == "0945287216" (the trailing digits agree). */
fun samePhone(a: String, b: String): Boolean {
    if (a.any(Char::isLetter) || b.any(Char::isLetter)) return false
    val x = phoneDigits(a); val y = phoneDigits(b)
    return x.length >= 7 && y.length >= 7 && (x.endsWith(y) || y.endsWith(x))
}

/** Sender filters look at the number (in any format) and the saved contact name. */
fun Condition.testSender(sender: String, name: String?): Boolean {
    val fields = listOfNotNull(sender, name)
    val digits = value.filter(Char::isDigit)
    val numberHit = value.none(Char::isLetter) && digits.length >= 3 && sender.filter(Char::isDigit).contains(digits)
    return when (filter) {
        Filter.DoesNotContain -> fields.none { it.contains(value, true) } && !numberHit && !samePhone(sender, value)
        Filter.Contains -> fields.any { test(it) } || numberHit || samePhone(sender, value)
        Filter.ExactlyMatches, Filter.EndsWith -> fields.any { test(it) } || samePhone(sender, value)
        Filter.StartsWith, Filter.Regex -> fields.any { test(it) }
    }
}

fun Rule.inSchedule(now: LocalDateTime): Boolean {
    if (now.dayOfWeek.value !in days) return false
    if (allDay) return true
    val m = now.hour * 60 + now.minute
    return m in start..end
}

/** Wording of the original app's "why wasn't this forwarded" messages. */
private fun Condition.failure(noun: String) = "$noun " + when (filter) {
    Filter.Contains -> "does not contain"
    Filter.DoesNotContain -> "contains"
    Filter.StartsWith -> "does not start with"
    Filter.EndsWith -> "does not end with"
    Filter.ExactlyMatches -> "does not exactly match with"
    Filter.Regex -> "does not match regex"
} + " $value"

/** Null when the rule forwards [e]; otherwise the reason it doesn't (shown by Test Rule). */
fun Rule.rejectReason(e: Event, now: LocalDateTime = LocalDateTime.now()): String? {
    if (paused) return "Rule is currently in paused status."
    val notif = kind == RuleKind.NOTIFICATION
    if (notif) {
        if (!allApps && e.app !in apps) return "App is not selected"
    } else {
        if (e.rcs && !forwardRcs) return "RCS is not allowed"
        if (receiveSim >= 0 && e.subId != receiveSim) return "Sender SIM does not match"
    }
    if (!allSenders) {
        val f = senders.filter { it.value.isNotBlank() }
        if (f.isNotEmpty() && f.none { it.testSender(e.sender, e.name) })
            return if (f.size == 1) f[0].failure(if (notif) "Title" else "Sender") else "${if (notif) "Title" else "Sender"} does not match any of the conditions"
    }
    if (!allContent) {
        val g = groups.filter { it.active().isNotEmpty() }
        if (g.isNotEmpty() && g.none { it.test(e.body) }) return when {
            g.size > 1 -> "Message does not match any of the group conditions"
            g[0].active().size == 1 -> g[0].active()[0].failure("Message")
            else -> "Message does not match any of the conditions"
        }
    }
    if (now.dayOfWeek.value !in days) return "Weekday is not selected"
    if (!inSchedule(now)) return "Received Date out of range"
    return null
}

fun Rule.matches(e: Event, now: LocalDateTime = LocalDateTime.now()): Boolean {
    val typeOk = when (kind) {
        RuleKind.NOTIFICATION -> e.type == EventType.NOTIFICATION
        RuleKind.AUTO_REPLY -> false
        else -> e.type == EventType.SMS
    }
    return typeOk && rejectReason(e, now) == null
}

val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yyyy, h:mm a", Locale.US)
val PREVIEW_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.US)
fun Long.local(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

fun render(chips: List<Chip>, e: Event, ruleName: String, preview: Boolean = false): String = chips.joinToString("") { c ->
    when (c.type) {
        ChipType.Sender -> "From: ${e.sender}${e.name?.let { " ($it)" } ?: ""} "
        ChipType.Message -> e.body + " "
        ChipType.Time -> "Received At: ${e.time.local().format(if (preview) PREVIEW_TIME_FMT else TIME_FMT)} "
        ChipType.RuleName -> "$ruleName "
        ChipType.AppName -> "$APP_NAME "
        ChipType.NewLine -> "\n"
        ChipType.SimDetail -> e.sim
        ChipType.NotificationAppName -> "From: ${e.appLabel ?: e.app ?: ""} "
        ChipType.Text -> c.text + " "
    }
}

/** URL placeholders as listed in the original's "Available Properties". */
fun urlFormat(template: String, e: Event, ruleName: String, formatted: String, escape: (String) -> String): String {
    val vars = mapOf(
        "msg" to e.body, "time" to Instant.ofEpochMilli(e.time).toString(), "local-time" to e.time.local().format(TIME_FMT),
        "sender" to (e.name?.let { "${e.sender} ($it)" } ?: e.sender), "sim-detail" to e.sim, "rule-name" to ruleName,
        "app-name" to (e.appLabel ?: APP_NAME), "formatted-msg" to formatted,
    )
    return Regex("\\{([a-z-]+)\\}").replace(template) { m -> vars[m.groupValues[1]]?.let(escape) ?: m.value }
}

/** Auto reply properties; {date+1} adds days, {time_12 + 1} adds hours. */
fun replyFormat(template: String, e: Event): String {
    val name = e.name ?: ""
    return Regex("\\{(\\w+)(?:\\s*([+-])\\s*(\\d+))?\\}").replace(template) { m ->
        val n = (m.groupValues[3].toLongOrNull() ?: 0) * (if (m.groupValues[2] == "-") -1 else 1)
        val t = e.time.local()
        when (m.groupValues[1]) {
            "msg" -> e.body
            "sender_full_name" -> name
            "sender_first_name" -> name.substringBefore(' ')
            "sender_last_name" -> name.substringAfter(' ', "")
            "time_12" -> t.plusHours(n).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
            "time_24" -> t.plusHours(n).format(DateTimeFormatter.ofPattern("HH:mm"))
            "date" -> t.plusDays(n).format(DateTimeFormatter.ofPattern("M/d/yyyy"))
            "day_of_week" -> t.plusDays(n).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "month" -> t.plusMonths(n).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            "year" -> (t.year + n).toString()
            else -> m.value
        }
    }
}

/** "Forward message if it contains OTP, code" */
fun Group.summary(prefix: String = "Forward message if"): String {
    val c = active()
    if (c.isEmpty()) return "No conditions added yet."
    val parts = c.groupBy { it.filter }.map { (f, l) -> "it ${f.phrase} ${l.joinToString(", ") { it.value }}" }
    return "$prefix " + parts.joinToString(if (all) " and " else " or ")
}

fun jsonEscape(s: String) = buildString {
    for (c in s) when (c) {
        '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
        else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
    }
}

fun validPhone(s: String) = s.trim().matches(Regex("\\+?[0-9 ()-]{8,}")) && s.count(Char::isDigit) >= 8
fun validEmail(s: String) = s.split(',').all { it.trim().matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) }
fun Target.valid() = when (type) {
    TargetType.SMS -> validPhone(value)
    TargetType.EMAIL -> validEmail(value)
    TargetType.TELEGRAM -> value.isNotBlank()
    TargetType.URL -> value.startsWith("http://") || value.startsWith("https://")
}
fun Target.display() = if (type == TargetType.URL) "$method: " + value.substringAfter("://").substringBefore('/') else value

/** Remote reply: "@5550100 message" or "@(Jane Doe) message". */
fun parseRemoteReply(body: String): Pair<String, String>? {
    val m = Regex("^\\s*@(?:\\(([^)]+)\\)|(\\S+))\\s+([\\s\\S]+)$").find(body) ?: return null
    return (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim() to m.groupValues[3].trim()
}

/** Import merge: an incoming rule replaces the one with the same id, new ones are appended (re-importing is a no-op). */
fun mergeRules(existing: List<Rule>, incoming: List<Rule>): List<Rule> {
    val byId = incoming.associateBy { it.id }
    return existing.map { byId[it.id] ?: it } + incoming.filter { r -> existing.none { it.id == r.id } }
}
