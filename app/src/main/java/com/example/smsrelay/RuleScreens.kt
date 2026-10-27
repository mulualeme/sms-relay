package com.example.smsrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RCS_HELP = "Rich Communication Services (RCS) is an upgraded version of SMS and is used in latest generation messaging apps. \nRCS messages cannot be forwarded as SMS and require notification permissions to forward them accurately."
private const val TEMPLATE_HELP = "Customize message to be sent while forwarding SMS. Use preview feature to see message content created.\n\nCustom text can also be added by typing in the 'Added Details' section"
private const val NOTIF_TEMPLATE_HELP = "Customize message to be sent while forwarding Notification. Use preview feature to see message content created.\n\nCustom text can also be added by typing in the 'Added Details' section"
val REPLY_PROPS = listOf("{msg}" to "Message Received", "{sender_full_name}" to "Sender Full Name", "{sender_first_name}" to "Sender First Name",
    "{sender_last_name}" to "Sender Last Name", "{time_12}" to "Time in 12 hour format", "{time_24}" to "Time in 24 hour format", "{date}" to "Date",
    "{day_of_week}" to "Day of the week", "{month}" to "Month", "{year}" to "Year")
private const val REPLY_INTRO = "Following properties can be used in auto reply message and will be replaced with actual value while creating message."
private const val REPLY_FOOTER = "You can configure date, time properties to show other day's date or time. E.g. use {date+1} to show tomorrow's date, {time_12 + 1} for one hour later."

/** App bar + unsaved-changes guard shared by every editor. */
@Composable
fun EditorScaffold(title: String, dirty: Boolean, onBack: () -> Unit, bottom: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    var ask by remember { mutableStateOf(false) }
    val back = { if (dirty) ask = true else onBack() }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().background(Color.White)) {
        AppBar(title, onNav = back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp), content = content)
        bottom()
    }
    if (ask) ConfirmDialog("Unsaved Changes", "You have unsaved changes. Do you want to discard them?", "No", "Discard", { ask = false }) { ask = false; onBack() }
}

private fun nameError(r: Rule) = when {
    r.name.isBlank() -> "Please enter rule name"
    r.name.length > 20 -> "Rule Name cannot be greater than 20 characters"
    else -> null
}

private fun targetsError(ts: List<Target>) = when {
    ts.isEmpty() || ts.all { it.value.isBlank() } -> "Please enter at least one contact"
    ts.any { it.value.isBlank() } -> "Please enter contact detail"
    ts.any { !it.valid() } -> "Please enter a valid contact."
    else -> null
}

private fun scheduleError(r: Rule) = when {
    r.days.isEmpty() -> "Please select at least one day"
    !r.allDay && r.start > r.end -> "Start Time cannot be after End Time"
    else -> null
}

@Composable
private fun ColumnScope.Receivers2(r: Rule, kind: UrlKind, showErrors: Boolean, onChange: (Rule) -> Unit) {
    r.targets.forEachIndexed { i, t ->
        Box(Modifier.padding(bottom = 10.dp)) {
            ReceiverRow(t, { nt -> onChange(r.copy(targets = r.targets.toMutableList().also { it[i] = nt })) }, showErrors && t.value.isNotBlank() && !t.valid(),
                onDelete = if (i > 0) ({ onChange(r.copy(targets = r.targets.toMutableList().also { it.removeAt(i) })) }) else null, kind = kind)
        }
    }
    GreenButton("Add Receiver", { onChange(r.copy(targets = r.targets + Target())) }, Modifier.padding(top = 14.dp).align(Alignment.CenterHorizontally), icon = Icons.Default.Add)
}

// ---------------- Basic Rule ----------------

@Composable
fun BasicRuleScreen(initial: Rule, isNew: Boolean, onSave: (Rule) -> Unit, onBack: () -> Unit) {
    var r by remember { mutableStateOf(initial) }
    var help by remember { mutableStateOf<Pair<String, String>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var showErrors by remember { mutableStateOf(false) }
    val content = r.groups.firstOrNull()?.conditions?.firstOrNull() ?: Condition()
    val sender = r.senders.firstOrNull() ?: Condition()
    help?.let { (a, b) -> InfoDialog(a, b) { help = null } }
    err?.let { InfoDialog(APP_NAME, it) { err = null } }
    EditorScaffold("Basic Rule", r != initial, onBack) {
        Heading("Where to forward SMS") { help = "Where to forward SMS" to "Enter the contact detail where the SMS should be forwarded. Use Advance rule to enter multiple recipients." }
        T("Enter the contact detail where the SMS should be forwarded. You can select phone number, email, telegram ID or URL.", 14.sp, Grey, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        ReceiverRow(r.targets.first(), { r = r.copy(targets = listOf(it)) }, showErrors && !r.targets.first().valid())
        Spacer(Modifier.height(16.dp))
        CheckRow("Forward RCS", r.forwardRcs, { help = "RCS Messages" to RCS_HELP }, 18.sp) { r = r.copy(forwardRcs = it) }
        SwitchRow("Forward all messages", r.allSenders && r.allContent) { r = r.copy(allSenders = it, allContent = it) }
        if (!(r.allSenders && r.allContent)) {
            Column(Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(3.dp)).padding(16.dp)) {
                Heading("Filter based on message content", 18.sp) { help = "Filter SMS based on message content" to "Optional filter to forward SMS based on message content. Use Advance rule to enter multiple filters.\n\nKeep it blank to forward all messages without filtering." }
                DropBox(content.filter, Filter.entries, { it.label }, { r = r.copy(groups = listOf(Group(conditions = listOf(content.copy(filter = it))))) }, Modifier.padding(vertical = 10.dp), "Show Content Filter Options")
                Field(content.value, { r = r.copy(groups = listOf(Group(conditions = listOf(content.copy(value = it))))) }, "Content to filter", Modifier.fillMaxWidth())
                Spacer(Modifier.height(28.dp))
                Heading("Filter based on sender of SMS", 18.sp) { help = "Filter SMS based on sender of SMS" to "Optional filter to forward SMS based on sender's contact name or phone number. Use Advance rule to enter multiple filters.\n\nKeep it blank to forward all messages without filtering." }
                DropBox(sender.filter, Filter.entries, { it.label }, { r = r.copy(senders = listOf(sender.copy(filter = it))) }, Modifier.padding(vertical = 10.dp), "Show Receiver Options")
                Field(sender.value, { r = r.copy(senders = listOf(sender.copy(value = it))) }, "Contact name or Phone Number", Modifier.fillMaxWidth())
            }
        }
        T("Rule Name", 18.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Field(r.name, { r = r.copy(name = it) }, "", Modifier.fillMaxWidth())
        GreenButton(if (isNew) "Save" else "Update", {
            showErrors = true
            err = targetsError(r.targets) ?: nameError(r)
            if (err == null) onSave(r)
        }, Modifier.padding(top = 40.dp).align(Alignment.End))
    }
}

// ---------------- Wizard pages shared by Advanced / Notification ----------------

@Composable
private fun SenderFilters(r: Rule, title: String, helpTitle: String, helpText: String, allLabel: String, placeholder: String, button: String, onChange: (Rule) -> Unit) {
    var help by remember { mutableStateOf(false) }
    if (help) InfoDialog(helpTitle, helpText) { help = false }
    Heading(title, 24.sp, FontWeight.SemiBold) { help = true }
    Spacer(Modifier.height(12.dp))
    SwitchRow(allLabel, r.allSenders) { onChange(r.copy(allSenders = it)) }
    if (!r.allSenders) {
        r.senders.forEachIndexed { i, c ->
            DropBox(c.filter, Filter.entries, { it.label }, { f -> onChange(r.copy(senders = r.senders.toMutableList().also { it[i] = c.copy(filter = f) })) },
                Modifier.padding(top = 14.dp, bottom = 10.dp), "Show Sender Options")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(c.value, { v -> onChange(r.copy(senders = r.senders.toMutableList().also { it[i] = c.copy(value = v) })) }, placeholder, Modifier.weight(1f))
                if (i > 0) IconButton({ onChange(r.copy(senders = r.senders.toMutableList().also { it.removeAt(i) })) }) { Icon(Icons.Default.Delete, "Delete receiver", Modifier.size(30.dp)) }
                else Spacer(Modifier.width(48.dp))
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
            GreenButton(button, { onChange(r.copy(senders = r.senders + Condition())) }, icon = Icons.Default.Add)
        }
    }
}

@Composable
private fun ContentGroups(r: Rule, title: String, helpTitle: String, helpText: String, allLabel: String, noun: String, onChange: (Rule) -> Unit) {
    var help by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    if (help) InfoDialog(helpTitle, helpText) { help = false }
    Heading(title, 24.sp, FontWeight.SemiBold) { help = true }
    Spacer(Modifier.height(12.dp))
    SwitchRow(allLabel, r.allContent) { onChange(r.copy(allContent = it)) }
    if (!r.allContent) {
        r.groups.forEachIndexed { i, g ->
            if (i > 0) T("OR", 18.sp, Color(0xFF444444), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).wrapContentWidth())
            Column(Modifier.padding(top = if (i == 0) 12.dp else 0.dp).fillMaxWidth().border(1.dp, Border, RoundedCornerShape(4.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                val summary = g.summary("Forward $noun if")
                T(summary, 18.sp, if (g.active().isEmpty()) Grey else Color.Black, modifier = Modifier.fillMaxWidth().wrapContentWidth(if (g.active().isEmpty()) Alignment.CenterHorizontally else Alignment.Start))
                Row(Modifier.padding(top = 14.dp).clickable { editing = i }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, "Show Content Filter Options", tint = Green, modifier = Modifier.size(26.dp))
                    T(" Edit conditions", 17.sp, Green, FontWeight.SemiBold)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) { LightButton("Add Group", { onChange(r.copy(groups = r.groups + Group())) }) }
    }
    editing?.let { i ->
        GroupDialog(r.groups[i], noun, onDelete = {
            editing = null
            onChange(r.copy(groups = r.groups.toMutableList().also { it.removeAt(i) }.ifEmpty { listOf(Group()) }))
        }, onCancel = { editing = null }) { g -> editing = null; onChange(r.copy(groups = r.groups.toMutableList().also { it[i] = g })) }
    }
}

@Composable
private fun SimSection(r: Rule, onChange: (Rule) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) }
    val req = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = it[Manifest.permission.READ_PHONE_STATE] == true }
    Section("SIM")
    if (!granted) {
        T("SIM Permissions are required to read SIM Details", 18.sp, Warn, modifier = Modifier.padding(vertical = 12.dp))
        LightButton("Grant Permission", { req.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)) })
    } else {
        val list = remember { sims(ctx) }
        if (list.isEmpty()) T("No SIM Found", 18.sp, Warn, modifier = Modifier.padding(vertical = 12.dp))
        else {
            T("Receive SMS From", 17.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            DropBox(r.receiveSim, listOf(-1) + list.map { it.id }, { id -> if (id == -1) "All" else list.first { it.id == id }.label }, { onChange(r.copy(receiveSim = it)) })
            T("Send SMS Using", 17.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            DropBox(r.sendSim, listOf(-1, -2) + list.map { it.id }, { id -> when (id) { -1 -> "Default Number"; -2 -> "Same SIM"; else -> list.first { it.id == id }.label } },
                { onChange(r.copy(sendSim = it)) })
        }
    }
}

private fun checkStep(r: Rule, step: Int, kind: RuleKind): String? = when {
    step == 0 -> targetsError(r.targets)
    kind == RuleKind.NOTIFICATION && step == 1 && !r.allApps && r.apps.isEmpty() -> "Please select at least one app"
    else -> null
}

// ---------------- Advanced Rule ----------------

@Composable
fun AdvancedRuleScreen(initial: Rule, isNew: Boolean, onSave: (Rule) -> Unit, onBack: () -> Unit) {
    var r by remember { mutableStateOf(initial) }
    var step by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf<Pair<String, String>?>(null) }
    help?.let { (a, b) -> InfoDialog(a, b) { help = null } }
    err?.let { InfoDialog(APP_NAME, it) { err = null } }
    EditorScaffold("Advanced Rule", r != initial, onBack, bottom = {
        WizardBar(step, 5, { step-- }) {
            err = checkStep(r, step, RuleKind.ADVANCED)
            if (err == null) {
                if (step < 4) step++
                else {
                    err = nameError(r) ?: scheduleError(r) ?: if (r.autoReply && r.replyMessage.isBlank()) "Message for auto reply is required as it is enabled" else null
                    if (err == null) onSave(r)
                }
            }
        }
    }) {
        when (step) {
            0 -> {
                Heading("Where to forward SMS", 24.sp, FontWeight.SemiBold) { help = "Where to forward SMS" to "Enter the contact detail where the SMS should be forwarded. You can enter multiple recipients." }
                T("Enter the contact detail where the SMS should be forwarded. You can select phone number, email, telegram ID or URL.", 14.sp, Grey, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                Receivers2(r, UrlKind.SMS, true) { r = it }
            }
            1 -> {
                TemplateEditor("Message Template", TEMPLATE_HELP, r.message, SMS_MESSAGE, SMS_MESSAGE_CHIPS, SAMPLE, r.name) { r = r.copy(message = it) }
                Spacer(Modifier.height(48.dp))
                TemplateEditor("Subject Template", TEMPLATE_HELP, r.subject, SMS_SUBJECT, SMS_SUBJECT_CHIPS, SAMPLE, r.name) { r = r.copy(subject = it) }
            }
            2 -> SenderFilters(r, "Filter based on sender of message", "Filter based on sender of message",
                "Optional filter to forward SMS based on sender's contact name or phone number. In case of multiple sender filter added, SMS will be forwarded if any of the sender filter matches. \n\nKeep it empty to forward all SMS.",
                "Forward all messages", "Enter sender name or number", "Add Sender filter") { r = it }
            3 -> ContentGroups(r, "Filter based on message content", "How to filter based on message content",
                "By default, all the messages are forwarded without any filtering applied. You can filter the messages based on its content. You can add multiple conditions to filter the messages.\n\nFor example, you can filter messages that contain a specific word or phrase, or it starts, ends with a word or messages that match a specific pattern using regular expressions.\n\nWithin each condition group, you can create multiple conditions and you can choose to filter messages that match all conditions or any of the conditions.\n\nDo not set any condition to forward all messages.",
                "Forward all messages", "message") { r = it }
            else -> {
                T("Rule Name", 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                Field(r.name, { r = r.copy(name = it) }, "", Modifier.fillMaxWidth())
                Section("General")
                CheckRow("Forward RCS", r.forwardRcs, { help = "RCS Messages" to RCS_HELP }, 18.sp) { r = r.copy(forwardRcs = it) }
                Section("Auto Reply")
                SwitchRow("Enable Auto Reply", r.autoReply, { help = "Auto Reply" to "Automatic Reply to all SMS that are filtered by the rule.\n\nUse Remote Reply feature to reply to a SMS from another phone number." }) { r = r.copy(autoReply = it) }
                if (r.autoReply) {
                    Spacer(Modifier.height(8.dp))
                    Properties(REPLY_PROPS, REPLY_INTRO, REPLY_FOOTER)
                    T("Auto Reply Message", 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    Field(r.replyMessage, { r = r.copy(replyMessage = it) }, "", Modifier.fillMaxWidth(), singleLine = false, minHeight = 120.dp)
                }
                Section("Network")
                CheckRow("Send delayed message", r.retry, { help = "Send delayed message" to "If this option is selected, SMS which were not forwarded due to Internet unavailability will be retried again once Internet is available." }, 18.sp) { r = r.copy(retry = it) }
                CheckRow("Use Wifi Data only", r.wifiOnly, size = 18.sp) { r = r.copy(wifiOnly = it) }
                SimSection(r) { r = it }
                Section("Schedule")
                ScheduleSection(r, "SMS") { r = it }
            }
        }
    }
}

// ---------------- Notification Rule ----------------

class AppEntry(val pkg: String, val label: String, val icon: Drawable)

@Composable
fun NotificationRuleScreen(initial: Rule, isNew: Boolean, onSave: (Rule) -> Unit, onBack: () -> Unit) {
    var r by remember { mutableStateOf(initial) }
    var step by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf<Pair<String, String>?>(null) }
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }.filter { it.packageName != ctx.packageName }
                .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }.sortedBy { it.label }
        }
    }
    help?.let { (a, b) -> InfoDialog(a, b) { help = null } }
    err?.let { InfoDialog(APP_NAME, it) { err = null } }
    EditorScaffold("Notification Rule", r != initial, onBack, bottom = {
        WizardBar(step, 6, { step-- }) {
            err = checkStep(r, step, RuleKind.NOTIFICATION)
            if (err == null) {
                if (step < 5) step++
                else { err = nameError(r) ?: scheduleError(r); if (err == null) onSave(r) }
            }
        }
    }) {
        when (step) {
            0 -> {
                Heading("Where to forward Notification", 24.sp, FontWeight.SemiBold) { help = "Where to forward Notification?" to "Enter the contact detail where the notification should be forwarded. You can enter multiple recipients." }
                T("Enter the contact detail where the notification should be forwarded. You can select phone number, email, telegram ID or URL.", 14.sp, Grey, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                Receivers2(r, UrlKind.NOTIFICATION, true) { r = it }
            }
            1 -> {
                T("Select apps from which notifications should be forwarded", 20.sp)
                CheckRow("Forward notifications from all apps", r.allApps) { r = r.copy(allApps = it) }
                val list = apps
                if (list == null) T("Fetching apps list", 17.sp, Grey, modifier = Modifier.fillMaxWidth().padding(top = 200.dp).wrapContentWidth())
                else list.forEach { a ->
                    val on = a.pkg in r.apps
                    Row(Modifier.fillMaxWidth().alpha(if (r.allApps) 0.5f else 1f).clickable(!r.allApps) { r = r.copy(apps = if (on) r.apps - a.pkg else r.apps + a.pkg) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Image(remember(a.pkg) { a.icon.toBitmap(96, 96).asImageBitmap() }, null, Modifier.size(42.dp))
                        T(a.label, 18.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
                        if (!r.allApps) Check(on) { r = r.copy(apps = if (on) r.apps - a.pkg else r.apps + a.pkg) }
                    }
                    HorizontalDivider(color = Divider)
                }
            }
            2 -> {
                val sample = SAMPLE.copy(type = EventType.NOTIFICATION)
                TemplateEditor("Message Template", NOTIF_TEMPLATE_HELP, r.message, NOTIF_MESSAGE, NOTIF_MESSAGE_CHIPS, sample, r.name) { r = r.copy(message = it) }
                Spacer(Modifier.height(48.dp))
                TemplateEditor("Subject Template", NOTIF_TEMPLATE_HELP, r.subject, NOTIF_SUBJECT, NOTIF_SUBJECT_CHIPS, sample, r.name) { r = r.copy(subject = it) }
            }
            3 -> ContentGroups(r, "Filter based on notification content", "How to filter based on notification content",
                "By default, all the notifications are forwarded without any filtering applied. You can filter the notifications based on its content. You can add multiple conditions to filter the notifications.\n\nFor example, you can filter notifications that contain a specific word or phrase, or it starts, ends with a word or notifications that match a specific pattern using regular expressions.\n\nWithin each condition group, you can create multiple conditions and you can choose to filter notifications that match all conditions or any of the conditions.\n\nDo not set any condition to forward all notifications.",
                "Forward all notifications", "notification") { r = it }
            4 -> SenderFilters(r, "Filter based on title of notification", "Filter based on title of notification",
                "Optional filter to forward notifications based on its title. In case of multiple title filters added, notification will be forwarded if any of the filter matches. \n\nKeep it empty to forward all notifications.",
                "Forward all notifications", "Enter title of notification", "Add Title filter") { r = it }
            else -> {
                T("Rule Name", 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                Field(r.name, { r = r.copy(name = it) }, "", Modifier.fillMaxWidth())
                Section("Network")
                CheckRow("Send delayed notification", r.retry, { help = "Send delayed notification" to "If this option is selected, Notifications which were not forwarded due to Internet unavailability will be retried again once Internet is available." }, 18.sp) { r = r.copy(retry = it) }
                CheckRow("Use Wifi Data only", r.wifiOnly, size = 18.sp) { r = r.copy(wifiOnly = it) }
                Section("Schedule")
                ScheduleSection(r, "Notifications") { r = it }
            }
        }
    }
}

// ---------------- Auto Reply Rule ----------------

@Composable
fun AutoReplyRuleScreen(initial: Rule, isNew: Boolean, onSave: (Rule) -> Unit, onBack: () -> Unit) {
    var r by remember { mutableStateOf(initial) }
    var err by remember { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf<Pair<String, String>?>(null) }
    help?.let { (a, b) -> InfoDialog(a, b) { help = null } }
    err?.let { InfoDialog(APP_NAME, it) { err = null } }
    EditorScaffold("Auto Reply Rule", r != initial, onBack, bottom = {
        Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), contentAlignment = Alignment.CenterEnd) {
            GreenButton(if (isNew) "Save" else "Update", {
                err = nameError(r) ?: when {
                    r.sources.isEmpty() -> "At least one source should be selected"
                    r.replyMessage.isBlank() -> "Auto Reply message is required"
                    r.multi == MultiMode.STOP_AFTER && r.multiCount < 1 -> "Stop after value should be a number"
                    r.multi == MultiMode.PAUSE_FOR && r.multiCount < 1 -> "Pause value should be a number"
                    else -> null
                } ?: scheduleError(r)
                if (err == null) onSave(r)
            })
        }
    }) {
        T("Rule Name", 18.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Field(r.name, { r = r.copy(name = it) }, "", Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Heading("Sources", 19.sp, FontWeight.SemiBold) { help = "Auto Reply Sources" to "Select the sources from which you want to auto reply." }
        val src = ReplySource.entries
        src.chunked(2).forEach { row ->
            Row {
                row.forEach { s ->
                    Row(Modifier.weight(1f).clickable { r = r.copy(sources = if (s in r.sources) r.sources - s else r.sources + s) }, verticalAlignment = Alignment.CenterVertically) {
                        Check(s in r.sources) { r = r.copy(sources = if (it) r.sources + s else r.sources - s) }
                        T(s.label, 15.sp)
                        if (s == ReplySource.RCS) HelpIcon { help = "RCS Messages" to RCS_HELP }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        T("Message", 19.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        Properties(REPLY_PROPS, REPLY_INTRO, REPLY_FOOTER)
        Field(r.replyMessage, { r = r.copy(replyMessage = it) }, "", Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = false, minHeight = 130.dp)
        Spacer(Modifier.height(24.dp))
        Heading("Conditions", 19.sp, FontWeight.SemiBold) { help = "Auto Reply Conditions" to "Select the conditions under which you want to auto reply.\n\nIn case when multiple conditions are selected, auto reply will be sent only when all conditions are met." }
        ReplyCond.entries.forEach { c -> CheckRow(c.label, c in r.conditions) { r = r.copy(conditions = if (it) r.conditions + c else r.conditions - c) } }
        Spacer(Modifier.height(24.dp))
        Heading("Multiple Replies to sender", 19.sp, FontWeight.SemiBold) { help = "Multiple Replies to sender" to "Manage how replies will be sent in case of multiple messages or calls from the same sender.\n\nBy default, auto reply will be sent for each SMS and call." }
        DropBox(r.multi, MultiMode.entries, { it.label }, { r = r.copy(multi = it) }, Modifier.padding(top = 8.dp), "Show Content Filter Options")
        if (r.multi == MultiMode.STOP_AFTER || r.multi == MultiMode.PAUSE_FOR) {
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(if (r.multiCount == 0) "" else r.multiCount.toString(), { v -> r = r.copy(multiCount = v.filter(Char::isDigit).take(4).toIntOrNull() ?: 0) }, "",
                    Modifier.width(56.dp), keyboard = KeyboardType.Number, minHeight = 52.dp)
                Spacer(Modifier.width(16.dp))
                if (r.multi == MultiMode.STOP_AFTER) T("replies", 18.sp)
                else DropBox(r.pauseUnit, PauseUnit.entries, { it.label }, { r = r.copy(pauseUnit = it) }, desc = "Show Content Filter Options")
            }
        }
        Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Reply with delay (seconds)", 17.sp, modifier = Modifier.weight(1f))
            Field(if (r.delay == 0) "" else r.delay.toString(), { v -> r = r.copy(delay = v.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) }, "0", Modifier.width(76.dp), keyboard = KeyboardType.Number)
        }
        T("Schedule", 19.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        DropBox(r.workWhen, listOf(true, false), { if (it) "Work when" else "Do not work when" }, { r = r.copy(workWhen = it) }, desc = "Show Content Filter Options")
        ScheduleSection(r, "SMS", dayHelp = "Days during which the rule will work. By default, all days are selected.",
            timeHelp = "Time range during which rule will work. Unselect All Day option to set a custom time range.") { r = it }
    }
}

// ---------------- Test Rule dialog ----------------

@Composable
fun TestRuleDialog(rule: Rule, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sender by remember { mutableStateOf("1234567890") }
    var msg by remember { mutableStateOf("This is a sample SMS") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    GreenDialog("Test Rule", onClose, footer = {
        LightButton("Cancel", onClose)
        GreenButton("Test", {
            busy = true
            scope.launch {
                result = withContext(Dispatchers.IO) {
                    if (rule.kind == RuleKind.AUTO_REPLY) return@withContext "Reply: " + replyFormat(rule.replyMessage, Event(EventType.SMS, sender, contactName(ctx, sender), msg))
                    val type = if (rule.kind == RuleKind.NOTIFICATION) EventType.NOTIFICATION else EventType.SMS
                    val sim = sims(ctx).let { l -> l.firstOrNull { it.id == rule.receiveSim } ?: l.firstOrNull() }
                    val e = Event(type, sender, contactName(ctx, sender), msg, app = rule.apps.firstOrNull(), appLabel = APP_NAME,
                        subId = sim?.id ?: -1, sim = sim?.detail.orEmpty())
                    // like the original: a test message that the rule's filters reject is not sent
                    rule.copy(paused = false).rejectReason(e)?.let { return@withContext "Following error occurred while sending Test SMS:\n$it" }
                    val res = Forwarder.deliver(ctx, Store(ctx), rule, e, rule.targets, queue = false)
                    val errs = res.filter { !it.ok }
                    if (errs.isEmpty()) "Test SMS sent successfully. If not received, please check spam or junk folder for email receiver."
                    else "Following error occurred while sending Test SMS:\n" + errs.joinToString("\n") { it.error ?: "Unknown error" }
                }
                busy = false
            }
        }, enabled = !busy)
    }) {
        T("You can override the message and its sender to test the rule.", 18.sp)
        T("Sender", 14.sp, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
        Field(sender, { sender = it }, "", Modifier.fillMaxWidth(), keyboard = KeyboardType.Phone)
        T("Message", 14.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Field(msg, { msg = it }, "", Modifier.fillMaxWidth(), singleLine = false, minHeight = 120.dp)
        if (busy) T("Loading", 16.sp, Grey, modifier = Modifier.padding(top = 20.dp))
        result?.let { T(it, 16.sp, modifier = Modifier.padding(top = 20.dp)) }
    }
}
