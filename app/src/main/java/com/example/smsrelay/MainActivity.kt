package com.example.smsrelay

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings as SysSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        val st = Store(this)
        AlwaysOnService.sync(this)
        setContent { RelayTheme { App(st) } }
    }
}

sealed interface Screen {
    data class Top(val root: Root) : Screen
    data object SelectRule : Screen
    data object RulesList : Screen
    data object History : Screen
    data object EmailConfig : Screen
    data class Editor(val rule: Rule, val isNew: Boolean) : Screen
}

private fun newRule(kind: RuleKind, count: Int) = Rule(
    name = "Rule ${count + 1}", kind = kind,
    message = if (kind == RuleKind.NOTIFICATION) NOTIF_MESSAGE else SMS_MESSAGE,
    subject = if (kind == RuleKind.NOTIFICATION) NOTIF_SUBJECT else SMS_SUBJECT,
)

private fun neededPerms(r: Rule): List<String> = buildList {
    if (r.kind != RuleKind.NOTIFICATION) addAll(listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE))
    if (r.targets.any { it.type == TargetType.SMS } || r.autoReply || r.kind == RuleKind.AUTO_REPLY) add(Manifest.permission.SEND_SMS)
    if (r.kind == RuleKind.AUTO_REPLY && r.sources.any { it != ReplySource.SMS && it != ReplySource.RCS }) add(Manifest.permission.READ_CALL_LOG)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

private fun needsNotificationAccess(r: Rule) = r.kind == RuleKind.NOTIFICATION || r.forwardRcs || ReplySource.RCS in r.sources && r.kind == RuleKind.AUTO_REPLY

@Composable
fun App(st: Store) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Top(Root.Dashboard))) }
    var rules by remember { mutableStateOf(st.rules) }
    var accepted by remember { mutableStateOf(st.settings.acceptedWarning) }
    var testRule by remember { mutableStateOf<Rule?>(null) }
    var pendingSave by remember { mutableStateOf<Rule?>(null) }
    var askPerms by remember { mutableStateOf<Rule?>(null) }
    var askAccess by remember { mutableStateOf(false) }
    val top = stack.last()

    fun push(s: Screen) { stack = stack + s }
    fun pop() { stack = stack.dropLast(1).ifEmpty { listOf(Screen.Top(Root.Dashboard)) } }
    fun commit(r: Rule) {
        rules = if (rules.any { it.id == r.id }) rules.map { if (it.id == r.id) r else it } else rules + r
        st.rules = rules
        stack = listOf(Screen.Top(Root.Dashboard), Screen.RulesList)
        testRule = r
        if (needsNotificationAccess(r) && ctx.packageName !in NotificationManagerCompat.getEnabledListenerPackages(ctx)) askAccess = true
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { pendingSave?.let(::commit); pendingSave = null }
    fun save(r: Rule) {
        val missing = neededPerms(r).filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) commit(r) else askPerms = r
    }

    if (!accepted) PlainDialog("Beware of Scammers",
        "This application can synchronize messages to your registered email or phone number which could also include Bank OTPs and other confidential messages. This app is not provided by a bank, business or public organization.\n\nDO NOT PROCEED if you have been asked by someone to install this application for any other reason.",
        {}) {
        LightButton("Decline", { activity.finish() })
        GreenButton("I Accept", { st.settings = st.settings.copy(acceptedWarning = true); accepted = true })
    }
    askPerms?.let { r ->
        val text = if (r.kind == RuleKind.NOTIFICATION) "Please provide Notifications permission as it is required to post important notifications."
        else "Please provide SMS permission as it is required to read the SMS and forward to recipient."
        ConfirmDialog("Permissions Request", text, "Deny", "Allow", { askPerms = null; commit(r) }) {
            askPerms = null; pendingSave = r
            permLauncher.launch(neededPerms(r).toTypedArray())
        }
    }
    if (askAccess && testRule == null) ConfirmDialog("Notification Access", "Notification Access is required to forward notifications", "Cancel", "Go To Settings", { askAccess = false }) {
        askAccess = false; ctx.startActivity(Intent(SysSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    testRule?.let { TestRuleDialog(it) { testRule = null } }

    val root = (stack.firstOrNull { it is Screen.Top } as? Screen.Top)?.root ?: Root.Dashboard
    BackHandler(stack.size > 1 || root != Root.Dashboard || drawer.isOpen) {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            stack.size > 1 -> pop()
            else -> stack = listOf(Screen.Top(Root.Dashboard))
        }
    }
    val openMenu: () -> Unit = { scope.launch { drawer.open() } }

    ModalNavigationDrawer({ DrawerContent(root) { r -> stack = listOf(Screen.Top(r)); scope.launch { drawer.close() } } }, drawerState = drawer, gesturesEnabled = top is Screen.Top) {
        when (val s = top) {
            is Screen.Top -> when (s.root) {
                Root.Dashboard -> DashboardScreen(st, rules, openMenu, { push(Screen.History) }, { push(Screen.RulesList) }, { push(Screen.SelectRule) })
                Root.PhoneAlerts -> PhoneAlertsScreen(st, openMenu)
                Root.RemoteReply -> RemoteReplyScreen(st, openMenu)
                Root.Settings -> SettingsScreen(st, openMenu) { push(Screen.EmailConfig) }
                Root.Feedback -> FeedbackScreen(openMenu)
                Root.Help -> HelpScreen(openMenu)
                Root.Privacy -> PrivacyScreen(openMenu)
            }
            Screen.SelectRule -> SelectRuleScreen(::pop) { k -> stack = stack.dropLast(1) + Screen.SelectRule + Screen.Editor(newRule(k, rules.size), true) }
            Screen.RulesList -> RulesListScreen(rules, ::pop, onEdit = { push(Screen.Editor(it, false)) }, onTest = { testRule = it },
                onToggle = { r -> rules = rules.map { if (it.id == r.id) it.copy(paused = !it.paused) else it }; st.rules = rules },
                onDelete = { r -> rules = rules.filter { it.id != r.id }; st.rules = rules })
            Screen.History -> HistoryScreen(st, ::pop)
            Screen.EmailConfig -> EmailConfigScreen(st, ::pop)
            is Screen.Editor -> key(s.rule.id) {
                when (s.rule.kind) {
                    RuleKind.BASIC -> BasicRuleScreen(s.rule, s.isNew, ::save, ::pop)
                    RuleKind.ADVANCED -> AdvancedRuleScreen(s.rule, s.isNew, ::save, ::pop)
                    RuleKind.NOTIFICATION -> NotificationRuleScreen(s.rule, s.isNew, ::save, ::pop)
                    RuleKind.AUTO_REPLY -> AutoReplyRuleScreen(s.rule, s.isNew, ::save, ::pop)
                }
            }
        }
    }
}
