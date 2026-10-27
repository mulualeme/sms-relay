package com.example.smsrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as SysSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(4.dp), content = content)

// ---------------- Drawer ----------------

enum class Root(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Outlined.Home), PhoneAlerts("Phone Alerts", Icons.Outlined.NotificationsActive),
    RemoteReply("Remote Reply", Icons.Outlined.Quickreply), Settings("Settings", Icons.Outlined.Settings),
    Feedback("Feedback", Icons.Outlined.SupportAgent), Help("Help", Icons.AutoMirrored.Outlined.HelpOutline), Privacy("Privacy Policy", Icons.Outlined.PrivacyTip),
}

@Composable
fun DrawerContent(current: Root, onPick: (Root) -> Unit) = ModalDrawerSheet(Modifier.width(330.dp), drawerContainerColor = Bg, drawerShape = RoundedCornerShape(0.dp)) {
    Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_logo), "App Logo", Modifier.size(40.dp))
        T(APP_NAME, 22.sp, modifier = Modifier.padding(start = 16.dp))
    }
    HorizontalDivider(color = Color(0xFFBDBDBD))
    Spacer(Modifier.height(8.dp))
    Root.entries.forEach { r ->
        val sel = r == current
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().height(56.dp).background(if (sel) GreenSelected else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable { onPick(r) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(r.icon, null, tint = if (sel) Green else Color(0xFF9F9F9F), modifier = Modifier.size(32.dp))
            T(r.label, 18.sp, if (sel) Green else Color(0xFF636363), modifier = Modifier.padding(start = 24.dp))
        }
    }
}

// ---------------- Dashboard ----------------

@Composable
fun DashboardScreen(st: Store, rules: List<Rule>, onMenu: () -> Unit, onHistory: () -> Unit, onAll: () -> Unit, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    var missing by remember { mutableStateOf(false) }
    val req = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { missing = !hasSmsPerms(ctx) }
    LaunchedEffect(rules) { missing = rules.any { it.kind != RuleKind.NOTIFICATION } && !hasSmsPerms(ctx) }
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column {
            AppBar(APP_NAME, centered = true, icon = Icons.Default.Tune, desc = "Dashboard Menu", onNav = onMenu)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                WhiteCard {
                    Column(Modifier.padding(horizontal = 26.dp, vertical = 20.dp)) {
                        Row {
                            Stat(Icons.Default.CalendarMonth, "Total Forwards", st.total, Modifier.weight(1f))
                            Stat(Icons.Default.Today, "Today's Forwards", st.today, Modifier.weight(0.7f))
                        }
                        OutlinedButton(onHistory, Modifier.fillMaxWidth().padding(top = 16.dp).height(44.dp), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.5.dp, Divider)) {
                            T("View History", 17.sp, Green, FontWeight.SemiBold)
                        }
                    }
                }
                if (missing) WhiteCard {
                    Column(Modifier.padding(20.dp)) {
                        T("Permissions Required", 20.sp, weight = FontWeight.SemiBold)
                        T("Please grant SMS and Contacts permission from settings to allow app to forward SMS.", 15.sp, Grey, modifier = Modifier.padding(vertical = 10.dp))
                        GreenButton("Grant Permission", { req.launch(SMS_PERMS) })
                    }
                }
                WhiteCard {
                    if (rules.isEmpty()) Text(buildAnnotatedString {
                        append("No rules exist. Tap on  "); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Add Rule") }; append("  Button to create one.")
                    }, Modifier.padding(horizontal = 24.dp, vertical = 36.dp), color = Grey, fontSize = 24.sp, fontFamily = Montserrat, lineHeight = 32.sp)
                    else Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                        T("Total Rules: ${rules.size}", 23.sp)
                        GreenButton("Show All Rules", onAll, Modifier.padding(top = 18.dp).align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
        ExtendedFloatingActionButton(onAdd, Modifier.align(Alignment.BottomEnd).padding(20.dp), shape = RoundedCornerShape(36.dp), containerColor = Green, contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(6.dp)) {
            Icon(Icons.Default.Add, "Add new rule", Modifier.size(30.dp)); Spacer(Modifier.width(14.dp)); T("Add Rule", 17.sp, Color.White, FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, n: Int, m: Modifier) = Column(m) {
    Icon(icon, label, tint = Color(0xFF555555), modifier = Modifier.size(28.dp))
    T(label, 14.sp, Label, modifier = Modifier.padding(top = 4.dp))
    T("$n", 42.sp, weight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
}

val SMS_PERMS = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE)
fun hasSmsPerms(ctx: android.content.Context) = ctx.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

// ---------------- Select Rule ----------------

@Composable
fun SelectRuleScreen(onBack: () -> Unit, onPick: (RuleKind) -> Unit) = Column(Modifier.fillMaxSize().background(Bg)) {
    AppBar("Select Rule", onNav = onBack)
    RuleKind.entries.forEach { k ->
        Row(Modifier.fillMaxWidth().clickable { onPick(k) }.padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { T(k.title, 24.sp); Badge(k.badge, if (k == RuleKind.NOTIFICATION) Blue else Orange) }
                T(k.desc, 16.sp, Grey, modifier = Modifier.padding(top = 10.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "create rule", tint = Green, modifier = Modifier.size(44.dp))
        }
        HorizontalDivider(color = Divider, thickness = 1.5.dp)
    }
}

// ---------------- Rules List ----------------

@Composable
fun RulesListScreen(rules: List<Rule>, onBack: () -> Unit, onEdit: (Rule) -> Unit, onTest: (Rule) -> Unit, onToggle: (Rule) -> Unit, onDelete: (Rule) -> Unit) {
    var del by remember { mutableStateOf<Rule?>(null) }
    del?.let { r -> ConfirmDialog("Delete Rule", "Please confirm to delete rule.", "Cancel", "Delete", { del = null }) { del = null; onDelete(r) } }
    Column(Modifier.fillMaxSize().background(Bg)) {
        AppBar("Rules List", onNav = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (rules.isEmpty()) T("No Rules", 18.sp, Grey, modifier = Modifier.fillMaxWidth().padding(top = 200.dp).wrapContentWidth())
            rules.forEach { r ->
                var menu by remember { mutableStateOf(false) }
                WhiteCard(Modifier.clickable { onEdit(r) }) {
                    Row(Modifier.padding(start = 20.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(r.name, 22.5.sp)
                        if (r.paused) T("Paused", 14.sp, Orange, modifier = Modifier.padding(start = 12.dp))
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Show Rule Options", Modifier.size(30.dp)) }
                            Menu(menu, { menu = false }, listOf("Edit" to { onEdit(r) }, "Test" to { onTest(r) },
                                (if (r.paused) "Activate" else "Pause") to { onToggle(r) }, "Delete" to { del = r }),
                                listOf(Icons.Default.Edit, Icons.Outlined.Science, if (r.paused) Icons.Default.PlayArrow else Icons.Default.Pause, Icons.Outlined.Delete))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- History ----------------

private val HIST_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.US)

@Composable
fun HistoryScreen(st: Store, onBack: () -> Unit) {
    val items = remember { st.history }
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    dialog?.let { (a, b) -> InfoDialog(a, b, Color(0xFFE6E6E6)) { dialog = null } } // History popups are grey in the original
    Column(Modifier.fillMaxSize().background(Color.White)) {
        AppBar("History", onNav = onBack)
        if (items.isEmpty()) T("No Message History Found", 18.sp, Grey, modifier = Modifier.fillMaxSize().wrapContentSize())
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { h ->
                val ok = h.results.count { it.ok }
                val (status, color) = when (ok) { h.results.size -> "Success" to OkGreen; 0 -> "Failed" to ErrRed; else -> "Partial Success" to Orange }
                WhiteCard {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp)) {
                        Row {
                            T(h.time.local().format(HIST_FMT), 15.sp, Grey, modifier = Modifier.weight(1f))
                            T(if (h.kind == "Forward") status else "${h.kind} - $status", 17.sp, color)
                        }
                        T("From: ${h.sender}", 16.sp, Color(0xFF444444), modifier = Modifier.padding(top = 10.dp))
                        T("Message: ${h.body}", 20.sp, Color(0xFF251607), modifier = Modifier.padding(top = 6.dp), maxLines = 6)
                        Row(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
                            T(h.rule, 18.sp, Color(0xFF737373), modifier = Modifier.weight(1f))
                            val replied = h.kind != "Forward"
                            Text(if (replied) "View Message Replied" else "View Message Sent", Modifier.clickable { dialog = (if (replied) "Message Replied" else "Message Sent") to h.sent },
                                color = Color(0xFF737373), fontSize = 16.sp, textDecoration = TextDecoration.Underline)
                        }
                        h.results.forEach { x ->
                            Row(Modifier.fillMaxWidth().background(if (x.ok) OkBg else ErrBg).padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                TargetIcon(x.type, if (x.ok) OkGreen else ErrRed, 28.dp)
                                T(x.value, 18.sp, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
                                if (!x.ok) Text("View Error", Modifier.clickable { dialog = "Error in forwarding SMS" to (x.error ?: "Unknown error") },
                                    fontSize = 16.sp, textDecoration = TextDecoration.Underline)
                            }
                            HorizontalDivider(color = if (x.ok) OkGreen.copy(alpha = 0.4f) else Color(0xFFE88A8A))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Phone Alerts ----------------

@Composable
fun PhoneAlertsScreen(st: Store, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    var a by remember { mutableStateOf(st.alerts) }
    var msg by remember { mutableStateOf<Pair<String, String>?>(null) }
    val req = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    msg?.let { (t, m) -> InfoDialog(t, m) { msg = null } }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        AppBar(APP_NAME, centered = true, icon = Icons.Default.Tune, desc = "Dashboard Menu", onNav = onMenu)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            T("Where to forward phone alerts", 21.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            T("Enter the contact detail where the phone alerts should be forwarded. You can select phone number, email, telegram ID or URL.", 14.sp, Grey, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            ReceiverRow(a.target, { a = a.copy(target = it) }, a.target.value.isNotBlank() && !a.target.valid(), kind = UrlKind.ALERT)
            T("Select Phone Alerts to forward", 21.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 36.dp, bottom = 8.dp))
            Alert.entries.forEach { al -> CheckRow(al.label, al in a.alerts, size = 19.sp) { a = a.copy(alerts = if (it) a.alerts + al else a.alerts - al) } }
            Row(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.End)) {
                LightButton("Reset", { a = PhoneAlerts() })
                GreenButton("Save", {
                    when {
                        a.alerts.isNotEmpty() && !a.target.valid() -> msg = APP_NAME to if (a.target.value.isBlank()) "Please enter contact detail" else "Please enter a valid contact."
                        a.target.value.isNotBlank() && a.alerts.isEmpty() -> msg = APP_NAME to "Please select at least one alert type."
                        else -> {
                            st.alerts = a
                            if (a.alerts.any { it in setOf(Alert.MissedCall, Alert.IncomingCall, Alert.OutgoingCall) })
                                req.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS))
                            if (Alert.LowBattery in a.alerts && !st.settings.sticky) st.settings = st.settings.copy(sticky = true)
                            AlwaysOnService.sync(ctx)
                            msg = "Settings Saved" to "Phone Alerts Settings Saved"
                        }
                    }
                })
            }
        }
    }
}

// ---------------- Remote Reply ----------------

@Composable
fun RemoteReplyScreen(st: Store, onMenu: () -> Unit) {
    var rr by remember { mutableStateOf(st.remote) }
    var msg by remember { mutableStateOf<Pair<String, String>?>(null) }
    val req = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    msg?.let { (t, m) -> InfoDialog(t, m) { msg = null } }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        AppBar(APP_NAME, centered = true, icon = Icons.Default.Tune, desc = "Dashboard Menu", onNav = onMenu)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Heading("Enter Phone Numbers from which reply is allowed", 19.sp) {
                msg = "Remote Reply Phone Numbers" to "Enter phone number(s) through which a remote reply can be send. \n\nAll numbers can also enabled but be cautious as it could lead to anyone sending remote messages on your behalf."
            }
            Spacer(Modifier.height(20.dp))
            CheckRow("Allow all phone numbers", rr.allowAll, size = 19.sp) { rr = rr.copy(allowAll = it) }
            if (!rr.allowAll) {
                rr.numbers.forEachIndexed { i, n ->
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            ReceiverRowPhoneOnly(n, { v -> rr = rr.copy(numbers = rr.numbers.toMutableList().also { it[i] = v }) })
                        }
                        if (i > 0) IconButton({ rr = rr.copy(numbers = rr.numbers.toMutableList().also { it.removeAt(i) }) }) { Icon(Icons.Default.Delete, "Delete receiver", Modifier.size(30.dp)) }
                        else Spacer(Modifier.width(64.dp))
                    }
                }
                GreenButton("Add Sender", { rr = rr.copy(numbers = rr.numbers + "") }, Modifier.padding(top = 24.dp).align(Alignment.CenterHorizontally), icon = Icons.Default.Add)
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text("How Remote Reply works?", Modifier.clickable {
                msg = "How Remote Reply works?" to "Send an SMS to this phone from one of the numbers above. Start it with @ followed by the phone number or saved contact name the SMS should be sent to, then the message.\n\nExamples:\n@5550100 I'll call you back\n@(Jane Doe) On my way\n\nWrap contact names that contain spaces in parentheses. $APP_NAME sends the message from this phone."
            }, color = Green, fontSize = 17.sp, fontFamily = Montserrat, textDecoration = TextDecoration.Underline)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.End)) {
                LightButton("Cancel", { rr = st.remote })
                GreenButton("Save", {
                    val nums = rr.numbers.filter { it.isNotBlank() }
                    if (!rr.allowAll && nums.any { !validPhone(it) }) msg = "Save Remote Reply Settings" to "Please enter valid phone number(s)"
                    else {
                        st.remote = rr.copy(numbers = nums.ifEmpty { listOf("") }); rr = st.remote
                        req.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS))
                        msg = "Save Remote Reply Settings" to "Settings Saved"
                    }
                })
            }
        }
    }
}

@Composable
private fun ReceiverRowPhoneOnly(value: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { uri ->
            ctx.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { if (it.moveToFirst()) onChange(it.getString(0)) }
        }
    }
    Field(value, onChange, "Enter Phone Number", Modifier.fillMaxWidth(), keyboard = KeyboardType.Phone, minHeight = 56.dp, trailing = {
        Icon(Icons.Default.AccountBox, "Contact Launch Action", tint = Green, modifier = Modifier.size(34.dp).clickable {
            pick.launch(Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        })
    })
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen(st: Store, onMenu: () -> Unit, onRulesChanged: () -> Unit, onEmail: () -> Unit) {
    val ctx = LocalContext.current
    var backupMsg by remember { mutableStateOf<String?>(null) }
    backupMsg?.let { InfoDialog(APP_NAME, it) { backupMsg = null } }
    val exportRules = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        backupMsg = runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(st.exportRules().toByteArray()) }
            "${st.rules.size} rule(s) exported."
        }.getOrElse { "Export failed: ${it.message}" }
    }
    val importRules = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        backupMsg = runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val n = st.importRules(text)
            onRulesChanged()
            "$n rule(s) imported."
        }.getOrElse { "Import failed: ${it.message}" }
    }
    var s by remember { mutableStateOf(st.settings) }
    var warn by remember { mutableStateOf(false) }
    fun save(n: Settings) { s = n; st.settings = n; AlwaysOnService.sync(ctx) }
    val pm = ctx.getSystemService(PowerManager::class.java)
    var batteryOk by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
    val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { batteryOk = pm.isIgnoringBatteryOptimizations(ctx.packageName) }
    if (warn) InfoDialog("Disable Sticky Notification", "Sticky notification is required to forward phone alerts (missed, incoming and outgoing calls) when app is in background. Please disable these alerts to continue.") { warn = false }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        AppBar(APP_NAME, centered = true, icon = Icons.Default.Tune, desc = "Dashboard Menu", onNav = onMenu)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Section("App Stability")
            CheckRow("Allow Battery Optimization", batteryOk) {
                if (!batteryOk) battery.launch(Intent(SysSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                else battery.launch(Intent(SysSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            CheckRow("Keep Sticky Notification", s.sticky) {
                if (!it && st.alerts.alerts.isNotEmpty()) warn = true else save(s.copy(sticky = it))
            }
            Section("History")
            CheckRow("Save messages history", s.saveHistory) { save(s.copy(saveHistory = it)) }
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Keep messages history for (days)", modifier = Modifier.weight(1f))
                Field(s.historyDays.toString(), { v -> v.filter(Char::isDigit).take(3).toIntOrNull()?.let { save(s.copy(historyDays = it)) } }, "",
                    Modifier.width(70.dp), keyboard = KeyboardType.Number, minHeight = 52.dp)
            }
            Section("Rules")
            Row(Modifier.fillMaxWidth().clickable { exportRules.launch("sms-relay-rules.json") }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Export Rules", modifier = Modifier.weight(1f))
                Icon(Icons.Default.Upload, "Export Rules", tint = Green, modifier = Modifier.size(30.dp))
            }
            Row(Modifier.fillMaxWidth().clickable { importRules.launch(arrayOf("application/json", "application/octet-stream", "text/*")) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                T("Import Rules", modifier = Modifier.weight(1f))
                Icon(Icons.Default.Download, "Import Rules", tint = Green, modifier = Modifier.size(30.dp))
            }
            T("Exported files can contain URL headers such as API keys. Keep them private.", 13.sp, Grey)
            Section("Email")
            Row(Modifier.fillMaxWidth().clickable(onClick = onEmail).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Email Configuration", modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Email Configuration", tint = Green, modifier = Modifier.size(40.dp))
            }
            Section("Telegram")
            T("Telegram messages are sent by your own bot. Create one with @BotFather, paste its token here, send your bot a message, then use your chat id as the Telegram Id.",
                14.sp, Grey, modifier = Modifier.padding(bottom = 10.dp))
            Field(s.telegramToken, { save(s.copy(telegramToken = it.trim())) }, "Enter Bot Token", Modifier.fillMaxWidth())
            Section("Notification Access")
            Row(Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(SysSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                T("Notification Access", modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Green, modifier = Modifier.size(40.dp))
            }
        }
    }
}

// ---------------- Email Config ----------------

@Composable
fun EmailConfigScreen(st: Store, onBack: () -> Unit) {
    var s by remember { mutableStateOf(st.settings) }
    var test by remember { mutableStateOf(false) }
    fun save(n: Settings) { s = n; st.settings = n }
    fun smtp(f: (Smtp) -> Smtp) = save(s.copy(smtp = f(s.smtp)))
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Color.White)) {
        AppBar("Email Config", onNav = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            T("Select Method to Send Email", 19.sp, Color(0xFF444444))
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(EmailMode.APP to "App", EmailMode.GMAIL to "Gmail", EmailMode.SMTP to "SMTP").forEach { (m, l) ->
                    RadioButton(s.emailMode == m, { save(s.copy(emailMode = m)) }, colors = RadioButtonDefaults.colors(Green, Color(0xFF555555)))
                    T(l, 19.sp, modifier = Modifier.clickable { save(s.copy(emailMode = m)) }.padding(end = 16.dp))
                }
            }
            when (s.emailMode) {
                EmailMode.APP -> Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("App's Email Account is not available", 20.sp, Color(0xFF444444))
                    T("$APP_NAME has no email server. Please select Gmail or SMTP.", 17.sp, Grey, modifier = Modifier.padding(top = 12.dp))
                }
                EmailMode.GMAIL -> {
                    Labeled("Gmail Address") { Field(s.gmailUser, { save(s.copy(gmailUser = it.trim())) }, "Enter Email Address", Modifier.fillMaxWidth(), keyboard = KeyboardType.Email) }
                    Labeled("App Password") { Field(s.gmailPass, { save(s.copy(gmailPass = it)) }, "Enter Password", Modifier.fillMaxWidth(), password = true) }
                    T("Gmail needs an App Password (Google Account > Security > 2-Step Verification > App passwords). Mail is sent through smtp.gmail.com.", 14.sp, Grey, modifier = Modifier.padding(top = 12.dp))
                }
                EmailMode.SMTP -> {
                    Labeled("Host") { Field(s.smtp.host, { v -> smtp { it.copy(host = v.trim()) } }, "Enter Host", Modifier.fillMaxWidth(), keyboard = KeyboardType.Uri) }
                    Labeled("Username") { Field(s.smtp.user, { v -> smtp { it.copy(user = v.trim()) } }, "Enter Username", Modifier.fillMaxWidth(), keyboard = KeyboardType.Email) }
                    Labeled("Password") { Field(s.smtp.pass, { v -> smtp { it.copy(pass = v) } }, "Enter Password", Modifier.fillMaxWidth(), password = true) }
                    Labeled("Port") { Field(s.smtp.port, { v -> smtp { it.copy(port = v.filter(Char::isDigit)) } }, "Enter Port", Modifier.fillMaxWidth(), keyboard = KeyboardType.Number) }
                    CheckRow("Authentication", s.smtp.auth, size = 19.sp) { v -> smtp { it.copy(auth = v) } }
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("Protocol", 19.sp, modifier = Modifier.weight(1f))
                        RadioButton(s.smtp.ssl, { smtp { it.copy(ssl = true) } }, colors = RadioButtonDefaults.colors(Green, Color(0xFF555555))); T("SSL", 19.sp)
                        RadioButton(!s.smtp.ssl, { smtp { it.copy(ssl = false) } }, colors = RadioButtonDefaults.colors(Green, Color(0xFF555555))); T("TLS", 19.sp)
                    }
                    LightButton("Reset", { smtp { Smtp() } }, Modifier.padding(top = 16.dp).align(Alignment.End))
                }
            }
        }
        GreenButton("Send Test Email", { test = true }, Modifier.align(Alignment.End).padding(16.dp))
    }
    if (test) TestEmailDialog(s) { test = false }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    T(label, 19.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)); content()
}

@Composable
fun TestEmailDialog(s: Settings, onClose: () -> Unit) {
    var to by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("Test Message") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    GreenDialog("Send Test Email", onClose, footer = {
        LightButton("Cancel", onClose)
        GreenButton("Send", {
            busy = true
            scope.launch {
                result = withContext(Dispatchers.IO) {
                    runCatching { Forwarder.sendMail(s, to, "Test email from $APP_NAME", msg) }
                        .fold({ "Test email sent successfully. If not received, please check the email address and spam folder." }, { it.message ?: "Error" })
                }
                busy = false
            }
        }, enabled = validEmail(to) && !busy)
    }) {
        T("You can override the message and email address.", 19.sp)
        T("Receiver", 15.sp, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
        Field(to, { to = it.trim() }, "Enter Email Address", Modifier.fillMaxWidth(), keyboard = KeyboardType.Email)
        T("Message", 15.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Field(msg, { msg = it }, "", Modifier.fillMaxWidth(), singleLine = false, minHeight = 120.dp)
        if (busy) T("Loading", 16.sp, Grey, modifier = Modifier.padding(top = 16.dp))
        result?.let { T(it, 16.sp, modifier = Modifier.padding(top = 16.dp)) }
    }
}

// ---------------- Feedback ----------------

@Composable
fun FeedbackScreen(onMenu: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        AppBar(APP_NAME, centered = true, icon = Icons.Default.Tune, desc = "Dashboard Menu", onNav = onMenu)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            T("Please write your feedback", 20.sp, modifier = Modifier.padding(vertical = 10.dp))
            Field(text, { text = it }, "", Modifier.fillMaxWidth(), singleLine = false, minHeight = 300.dp)
            T("Want to hear back from us? Please enter your email (optional)", 16.sp, modifier = Modifier.padding(top = 36.dp, bottom = 10.dp))
            Field(email, { email = it.trim() }, "Enter Email Address", Modifier.fillMaxWidth(), keyboard = KeyboardType.Email)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.End)) {
            LightButton("Clear", { text = ""; email = "" })
            GreenButton("Send", {
                val body = text + if (email.isNotBlank()) "\n\nReply to: $email" else ""
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_SUBJECT, "$APP_NAME Feedback").putExtra(Intent.EXTRA_TEXT, body))
                }
            }, enabled = text.isNotBlank())
        }
    }
}

// ---------------- Help / Privacy ----------------

private val GUIDES = listOf(
    "Set up the app" to "Grant SMS, Contacts and Notification permissions when asked. In Settings, allow Battery Optimization and keep the sticky notification so Android doesn't stop forwarding in the background.",
    "Set up a basic rule" to "Tap Add Rule > Basic Rule, choose where to forward (phone number, email, Telegram or URL) and tap Save. You can test the rule right after saving.",
    "Email" to "Settings > Email Configuration. Select Gmail (with an App Password) or SMTP and send a test email.",
    "Telegram" to "Create a bot with @BotFather and paste its token in Settings > Telegram. Send your bot any message, open https://api.telegram.org/bot<token>/getUpdates and use the chat id as the Telegram Id.",
    "Slack / Microsoft Teams / URL" to "Pick URL as the receiver, paste the incoming-webhook URL and select the Slack, Teams or General body template. Properties like {msg} and {sender} are replaced when forwarding.",
    "Advanced rule" to "Multiple receivers, custom message and subject templates, sender filters, content condition groups, schedule, SIM selection, Wi-Fi only and retry when back online.",
    "Notification rule" to "Forwards notifications of other apps. Requires Notification Access.",
    "Auto reply" to "Replies automatically to SMS and calls, with conditions (silent, DND, screen off, charging), repeat limits and a schedule.",
    "Remote reply" to "Send '@number message' or '@(Contact Name) message' from an allowed phone to send an SMS from this phone.",
    "Commonly faced issues" to "If messages stop arriving: check permissions, disable battery optimization for the app, enable Autostart on phones that have it, and keep the sticky notification on.",
)

@Composable
fun HelpScreen(onMenu: () -> Unit) = Column(Modifier.fillMaxSize().background(Bg)) {
    AppBar(APP_NAME, centered = true, icon = Icons.Default.Tune, desc = "Dashboard Menu", onNav = onMenu)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("Setup guides", 24.sp, weight = FontWeight.SemiBold)
        GUIDES.forEach { (t, b) ->
            var open by remember { mutableStateOf(false) }
            WhiteCard(Modifier.clickable { open = !open }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(t, 18.sp, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(if (open) Icons.Default.Remove else Icons.Default.Add, null, tint = Green)
                    }
                    if (open) T(b, 15.sp, Color(0xFF444444), modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

@Composable
fun PrivacyScreen(onMenu: () -> Unit) = Column(Modifier.fillMaxSize().background(Color.White)) {
    AppBar(APP_NAME, centered = true, icon = Icons.Default.Tune, desc = "Dashboard Menu", onNav = onMenu)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        T("Privacy Policy and Data Security", 22.sp, weight = FontWeight.SemiBold)
        listOf(
            "Permissions Requested" to "READ_SMS / RECEIVE_SMS - read incoming SMS to forward them\nSEND_SMS - forward to a phone number, auto reply and remote reply\nREAD_CONTACTS - show the sender's name\nREAD_CALL_LOG / READ_PHONE_STATE - call alerts, auto reply and SIM selection\nINTERNET - forward to email, Telegram or a URL\nRECEIVE_BOOT_COMPLETED - start again after a restart",
            "Managing Permissions" to "Permissions are requested at run time with the reason. They can be managed from the app details in the phone settings.",
            "Data handling procedures" to "$APP_NAME has no server. Messages are sent directly from this phone to the destinations you configure (your SMTP/Gmail account, your Telegram bot, your URL or a phone number).",
            "Data retention and deletion policy" to "Rules, settings and history are stored only on this device and are removed by clearing the app data or uninstalling the app.",
        ).forEach { (t, b) ->
            T(t, 18.sp, weight = FontWeight.Medium, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
            T(b, 15.sp, Color(0xFF444444))
        }
    }
}
