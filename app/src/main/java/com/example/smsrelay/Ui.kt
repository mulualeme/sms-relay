package com.example.smsrelay

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Palette sampled from the original app's screenshots.
val Green = Color(0xFF37715A)
val GreenLight = Color(0xFF9BB8AC)
val GreenSelected = Color(0xFFE1E8E5)
val Bg = Color(0xFFF8F8F8)
val Grey = Color(0xFF888888)
val Label = Color(0xFF666666)
val Divider = Color(0xFFDADADA)
val Border = Color(0xFFCCCCCC)
val HelpBlue = Color(0xFF0D6EFD)
val Orange = Color(0xFFEE9B00)
val Blue = Color(0xFF0077B6)
val Warn = Color(0xFFD19600)
val ErrRed = Color(0xFF960000)
val ErrBg = Color(0xFFFFD3D3)
val OkGreen = Color(0xFF1D873B)
val OkBg = Color(0xFFD6F0DD)
val DialogBg = Color(0xFFEBEBEB)
val ChipBg = Color(0xFFDADADA)

val Montserrat = FontFamily(
    Font(R.font.montserrat_light, FontWeight.Light), Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium), Font(R.font.montserrat_semibold, FontWeight.SemiBold),
)

@Composable
fun RelayTheme(content: @Composable () -> Unit) {
    val base = TextStyle(fontFamily = Montserrat, color = Color.Black)
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Green, onPrimary = Color.White, background = Bg, surface = Color.White, onSurface = Color.Black, surfaceVariant = DialogBg),
        typography = Typography(
            bodyLarge = base.copy(fontSize = 17.sp), bodyMedium = base.copy(fontSize = 15.sp), bodySmall = base.copy(fontSize = 13.sp),
            titleLarge = base.copy(fontSize = 22.sp), titleMedium = base.copy(fontSize = 17.sp), labelLarge = base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        ),
    ) {
        Scaled { ProvideTextStyle(base.copy(fontSize = 17.sp), content) }
    }
}

// Everything was ~1.15x the original on device, so all dp/sp are scaled down uniformly.
const val UI_SCALE = 0.88f // ponytail: one knob for overall size; tune if it still feels off

/** Applies UI_SCALE from the display's real density, so wrapping twice is harmless. */
@Composable
fun Scaled(content: @Composable () -> Unit) {
    val res = LocalContext.current.resources
    CompositionLocalProvider(LocalDensity provides Density(res.displayMetrics.density * UI_SCALE, res.configuration.fontScale), content = content)
}

/** Dialogs and dropdowns open in their own window, which resets density, so they must re-apply the scale. */
@Composable
fun ScaledDialog(onDismiss: () -> Unit, properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit) =
    Dialog(onDismiss, properties) { Scaled(content) }

@Composable
fun ScaledMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    DropdownMenu(expanded, onDismiss, containerColor = Color.White, shape = RoundedCornerShape(4.dp)) { val scope = this; Scaled { scope.content() } }

@Composable
fun T(text: String, size: TextUnit = 17.sp, color: Color = Color.Black, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, color = color, fontSize = size, fontWeight = weight, fontFamily = Montserrat, maxLines = maxLines, lineHeight = size * 1.25f)

@Composable
fun AppBar(title: String, centered: Boolean = false, icon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack, desc: String = "Navigate Back", onNav: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(64.dp).background(Green).shadow(2.dp)) { // 64dp x UI_SCALE = the original 56dp toolbar
        Box(Modifier.fillMaxSize().background(Green))
        IconButton(onNav, Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(28.dp)) }
        T(title, 23.sp, Color.White, modifier = if (centered) Modifier.align(Alignment.Center) else Modifier.align(Alignment.CenterStart).padding(start = 80.dp))
    }
}

@Composable
fun HelpIcon(onClick: () -> Unit) = Icon(Icons.Outlined.HelpOutline, "help", tint = HelpBlue, modifier = Modifier.padding(start = 8.dp).size(24.dp).clip(CircleShape).clickable(onClick = onClick))

@Composable
fun Heading(text: String, size: TextUnit = 20.sp, weight: FontWeight = FontWeight.Normal, help: (() -> Unit)? = null) =
    Row(verticalAlignment = Alignment.CenterVertically) { T(text, size, weight = weight, modifier = Modifier.weight(1f, false)); help?.let { HelpIcon(it) } }

/** Info / help popup: title, message, Ok. */
@Composable
fun InfoDialog(title: String, text: String, bg: Color = Bg, onDismiss: () -> Unit) = PlainDialog(title, text, onDismiss, bg) { GreenButton("Ok", onDismiss) }

@Composable
fun PlainDialog(title: String, text: String, onDismiss: () -> Unit, bg: Color = Bg, buttons: @Composable RowScope.() -> Unit) = ScaledDialog(onDismiss) {
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Column(Modifier.padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)) {
            T(title, 20.sp, weight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            T(text, 16.sp, Color(0xFF555555), modifier = Modifier.padding(end = 8.dp))
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End), content = buttons)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, no: String, yes: String, onNo: () -> Unit, onYes: () -> Unit) =
    PlainDialog(title, text, onNo) { LightButton(no, onNo); GreenButton(yes, onYes) }

/** Dialog with the green title bar (Edit URL Config, Send Test Email, Test Rule). */
@Composable
fun GreenDialog(title: String, onDismiss: () -> Unit, full: Boolean = false, bg: Color = Color(0xFFF7F7F7), footer: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) =
    ScaledDialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(horizontal = 11.dp, vertical = if (full) 12.dp else 0.dp).fillMaxWidth().then(if (full) Modifier.fillMaxHeight() else Modifier),
            shape = RoundedCornerShape(4.dp), color = bg) {
            Column {
                Box(Modifier.fillMaxWidth().background(Green).padding(horizontal = 14.dp, vertical = 9.dp)) { T(title, 22.sp, Color.White) }
                Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(12.dp), content = content)
                if (full) HorizontalDivider(color = Grey)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), content = footer)
            }
        }
    }

@Composable
fun GreenButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, weight: FontWeight = FontWeight.SemiBold) =
    Button(onClick, modifier.heightIn(min = 40.dp), enabled = enabled, shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(Green, Color.White, Divider, Grey), elevation = ButtonDefaults.buttonElevation(3.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)) {
        icon?.let { Icon(it, null, Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) }
        T(text, 16.sp, if (enabled) Color.White else Grey, weight, maxLines = 1)
    }

@Composable
fun LightButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, bg: Color = Bg) =
    OutlinedButton(onClick, modifier.heightIn(min = 40.dp), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Divider),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = bg), contentPadding = PaddingValues(horizontal = 20.dp)) {
        T(text, 16.sp, Green, FontWeight.SemiBold, maxLines = 1)
    }

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = true, minHeight: Dp = 42.dp,
          error: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    BasicTextField(value, onChange, modifier.onFocusChanged { focused = it.isFocused }, singleLine = singleLine,
        textStyle = TextStyle(fontFamily = Montserrat, fontSize = 18.sp), keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().heightIn(min = minHeight).border(if (focused || error) 1.5.dp else 1.dp, if (error) Color.Red else if (focused) Green else Border, RoundedCornerShape(3.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top) {
                Box(Modifier.weight(1f)) { if (value.isEmpty()) T(placeholder, 18.sp, Color(0xFFAAAAAA)); inner() }
                if (password) Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show password", Modifier.size(28.dp).clickable { show = !show })
                if (error) Icon(Icons.Default.Error, "error", tint = Color.Red, modifier = Modifier.size(24.dp))
                trailing?.invoke()
            }
        })
}


/** Grey box with green label and ▼, opening a dropdown (the original's spinner look). */
@Composable
fun <T> DropBox(value: T, options: List<T>, label: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier, desc: String = "Show Options", bg: Color = Bg) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(Modifier.height(46.dp).background(bg, RoundedCornerShape(3.dp)).border(1.dp, Divider, RoundedCornerShape(3.dp)).clickable { open = true }
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            T(label(value), 17.sp, Green, FontWeight.SemiBold)
            Icon(Icons.Default.ArrowDropDown, desc, tint = Green, modifier = Modifier.padding(start = 8.dp))
        }
        Menu(open, { open = false }, options.map { label(it) to { onPick(it) } })
    }
}

@Composable
fun Menu(open: Boolean, onClose: () -> Unit, items: List<Pair<String, () -> Unit>>, icons: List<ImageVector?>? = null) =
    ScaledMenu(open, onClose) {
        items.forEachIndexed { i, (t, f) ->
            DropdownMenuItem({ T(t, if (icons != null) 18.sp else 17.sp, if (icons != null) Color(0xFF444444) else Color.Black) }, { onClose(); f() },
                leadingIcon = icons?.get(i)?.let { { Icon(it, null, tint = Color(0xFF555555), modifier = Modifier.size(28.dp)) } },
                contentPadding = PaddingValues(horizontal = if (icons != null) 16.dp else 24.dp, vertical = if (icons != null) 14.dp else 12.dp))
        }
    }

@Composable
fun Check(checked: Boolean, onChange: (Boolean) -> Unit) = Checkbox(checked, onChange,
    colors = CheckboxDefaults.colors(checkedColor = Green, uncheckedColor = Color(0xFF555555), checkmarkColor = Color.White))

@Composable
fun CheckRow(text: String, checked: Boolean, help: (() -> Unit)? = null, size: TextUnit = 17.sp, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { T(text, size); help?.let { HelpIcon(it) } }
        Check(checked, onChange)
    }

/** Old Material switch look: thin track, overhanging thumb. */
@Composable
fun LegacySwitch(on: Boolean, onChange: (Boolean) -> Unit) =
    Box(Modifier.size(52.dp, 36.dp).clickable { onChange(!on) }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(16.dp).background(if (on) GreenLight else Color(0xFF9E9E9E), CircleShape))
        Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).size(24.dp).shadow(2.dp, CircleShape).background(if (on) Green else Color(0xFFF1F1F1), CircleShape))
    }

@Composable
fun SwitchRow(text: String, on: Boolean, help: (() -> Unit)? = null, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { T(text, 18.sp); help?.let { HelpIcon(it) } }
        LegacySwitch(on, onChange)
    }

@Composable
fun Section(label: String) = Column(Modifier.padding(top = 14.dp, bottom = 8.dp)) {
    HorizontalDivider(color = Divider, thickness = 1.dp)
    T(label, 13.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun Badge(text: String, color: Color) = Box(Modifier.padding(start = 12.dp).background(color, CircleShape).padding(horizontal = 18.dp, vertical = 4.dp)) { T(text, 14.sp, Color.White) }

fun TargetType.icon(): ImageVector = when (this) {
    TargetType.SMS -> Icons.Default.Call
    TargetType.EMAIL -> Icons.Default.Email
    TargetType.TELEGRAM -> Icons.AutoMirrored.Filled.Send
    TargetType.URL -> Icons.Default.Api
}

@Composable
fun TargetIcon(t: TargetType, tint: Color, size: Dp = 30.dp) =
    if (t == TargetType.TELEGRAM) Box(Modifier.size(size).background(tint, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    } else Icon(t.icon(), null, tint = tint, modifier = Modifier.size(size))

/** Receiver type dropdown + contact field (or URL config button) + optional delete. */
@Composable
fun ReceiverRow(t: Target, onChange: (Target) -> Unit, error: Boolean = false, onDelete: (() -> Unit)? = null, kind: UrlKind = UrlKind.SMS) {
    var open by remember { mutableStateOf(false) }
    var editUrl by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { uri ->
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { if (it.moveToFirst()) onChange(t.copy(value = it.getString(0))) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            Row(Modifier.size(width = 56.dp, height = 50.dp).background(Bg, RoundedCornerShape(3.dp)).border(1.dp, Border, RoundedCornerShape(3.dp)).clickable { open = true }.padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TargetIcon(t.type, Green, 26.dp)
                Icon(Icons.Default.ArrowDropDown, "Show Receiver Options", tint = Green)
            }
            ScaledMenu(open, { open = false }) {
                TargetType.entries.forEach { ty ->
                    DropdownMenuItem({ T(ty.label, 18.sp) }, { open = false; if (ty != t.type) onChange(Target(ty)) }, leadingIcon = { TargetIcon(ty, Color.Black) },
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp))
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        if (t.type == TargetType.URL) {
            Row(Modifier.weight(1f).height(48.dp).background(Bg, RoundedCornerShape(3.dp)).border(1.dp, Border, RoundedCornerShape(3.dp)).clickable { editUrl = true },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, "Edit URL", tint = Green, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                T(if (t.value.isBlank()) "Edit URL Config" else t.display(), 17.sp, Green, FontWeight.SemiBold, maxLines = 1)
            }
        } else {
            Field(t.value, { onChange(t.copy(value = it)) }, t.type.hint, Modifier.weight(1f), error = error,
                keyboard = when (t.type) { TargetType.SMS -> KeyboardType.Phone; TargetType.EMAIL -> KeyboardType.Email; else -> KeyboardType.Text },
                trailing = if (t.type == TargetType.SMS) { {
                    Icon(Icons.Default.AccountBox, "Contact Launch Action", tint = Green, modifier = Modifier.size(32.dp).clickable {
                        pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                    })
                } } else null)
        }
        onDelete?.let { IconButton(it) { Icon(Icons.Default.Delete, "Delete receiver", Modifier.size(30.dp)) } }
    }
    if (editUrl) UrlConfigDialog(t, kind, { editUrl = false }) { onChange(it); editUrl = false }
}

enum class UrlKind(val props: List<Pair<String, String>>, val templates: List<Pair<String, String>>) {
    SMS(listOf("{msg}" to "Content of SMS", "{local-time}" to "Local Time", "{time}" to "UTC Time in ISO Format", "{sender}" to "Sender Detail",
        "{sim-detail}" to "Sim Detail", "{rule-name}" to "Rule Name", "{app-name}" to "App Name", "{formatted-msg}" to "Contains Formatted SMS with other details"),
        listOf("General Template" to "{\n\"message\": \"From: {sender}\\n{msg}\"\n}", "Teams Template" to "{\n\"text\": \"From: {sender}<br>{msg}\"\n}",
            "Slack Template" to "{\n\"text\": \"{formatted-msg}\"\n}")),
    NOTIFICATION(listOf("{msg}" to "Notification Content", "{time}" to "UTC Time in ISO Format", "{local-time}" to "Local Time", "{app-name}" to "App Name",
        "{formatted-msg}" to "Contains Formatted Notification with other details"),
        listOf("General Template" to "{\n\"message\": \"{msg}\"\n}", "Teams Template" to "{\n\"text\": \"{msg}\"\n}", "Slack Template" to "{\n\"text\": \"{msg}\"\n}")),
    ALERT(listOf("{msg}" to "Notification Content", "{time}" to "UTC Time in ISO Format", "{local-time}" to "Local Time"),
        listOf("General Template" to "{\n\"message\": \"{msg}\"\n}", "Teams Template" to "{\n\"text\": \"{msg}\"\n}", "Slack Template" to "{\n\"text\": \"{msg}\"\n}")),
}

@Composable
fun Properties(props: List<Pair<String, String>>, intro: String? = null, footer: String? = "View detailed description here", bg: Color = Color(0xFFF1F1F1)) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(3.dp)).border(1.dp, Border, RoundedCornerShape(3.dp)).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Available Properties", 18.sp, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Default.Remove else Icons.Default.Add, "Show Available Properties", tint = Green, modifier = Modifier.size(36.dp))
        }
        if (open) {
            intro?.let { T(it, 15.sp, Color(0xFF444444), modifier = Modifier.padding(vertical = 6.dp)) }
            props.forEach { (k, v) ->
                Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(k) }; append(": $v") },
                    fontFamily = Montserrat, fontSize = 16.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
            footer?.let { T(it, 15.sp, Label, modifier = Modifier.padding(vertical = 10.dp)) }
        }
    }
}

@Composable
fun UrlConfigDialog(initial: Target, kind: UrlKind, onCancel: () -> Unit, onSave: (Target) -> Unit) {
    var t by remember { mutableStateOf(initial.copy(headers = initial.headers.ifEmpty { listOf(Header()) })) }
    var tplOpen by remember { mutableStateOf(false) }
    GreenDialog("Edit URL Config", onCancel, full = true, bg = DialogBg, footer = {
        LightButton("Cancel", onCancel)
        GreenButton("Save", { onSave(t.copy(headers = t.headers.filter { it.key.isNotBlank() })) }, enabled = t.value.isNotBlank())
    }) {
        T("Method", 18.sp)
        Spacer(Modifier.height(8.dp))
        DropBox(t.method, listOf("POST", "PUT", "GET"), { it }, { t = t.copy(method = it) }, Modifier.width(112.dp), "Show Receiver Options", Color.Transparent)
        Spacer(Modifier.height(16.dp))
        Properties(kind.props)
        Spacer(Modifier.height(12.dp))
        T("URL", 18.sp)
        Field(t.value, { t = t.copy(value = it.trim()) }, "", Modifier.fillMaxWidth().padding(top = 4.dp), singleLine = false, minHeight = 155.dp, keyboard = KeyboardType.Uri)
        Spacer(Modifier.height(16.dp))
        T("Headers", 18.sp)
        t.headers.forEachIndexed { i, h ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(h.key, { v -> t = t.copy(headers = t.headers.toMutableList().also { it[i] = h.copy(key = v) }) }, "Key", Modifier.weight(0.35f))
                Spacer(Modifier.width(8.dp))
                Field(h.value, { v -> t = t.copy(headers = t.headers.toMutableList().also { it[i] = h.copy(value = v) }) }, "Value", Modifier.weight(0.65f))
                IconButton({ t = t.copy(headers = t.headers.toMutableList().also { it.removeAt(i) }) }) { Icon(Icons.Outlined.Delete, "Delete Header", Modifier.size(30.dp), tint = Color.Black) }
            }
        }
        GreenButton("Add Header", { t = t.copy(headers = t.headers + Header()) }, Modifier.padding(top = 18.dp))
        if (t.method != "GET") {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.Bottom) {
                T("Body", 18.sp, modifier = Modifier.weight(1f))
                Box {
                    Row(Modifier.border(1.dp, Border, RoundedCornerShape(3.dp)).clickable { tplOpen = true }.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("Templates", 18.sp, Green); Icon(Icons.Default.ArrowDropDown, "Show Receiver Options", tint = Green)
                    }
                    Menu(tplOpen, { tplOpen = false }, kind.templates.map { (n, b) -> n to { t = t.copy(body = b) } })
                }
            }
            Field(t.body, { t = t.copy(body = it) }, "", Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = false, minHeight = 220.dp)
        }
    }
}

/** Chip-based template editor: Preview, Added Details (with inline custom text), Available Details. */
@Composable
fun TemplateEditor(title: String, help: String, chips: List<Chip>, defaults: List<Chip>, available: List<ChipType>, sample: Event, ruleName: String, onChange: (List<Chip>) -> Unit) {
    var showHelp by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(true) }
    var custom by remember { mutableStateOf("") }
    fun commit() { if (custom.isNotBlank()) { onChange(chips + Chip(ChipType.Text, custom.trim())); custom = "" } }
    Heading(title, 22.5.sp, FontWeight.SemiBold) { showHelp = true }
    if (showHelp) InfoDialog(title, help) { showHelp = false }
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().border(1.5.dp, GreenLight, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().clickable { preview = !preview }, verticalAlignment = Alignment.CenterVertically) {
            T("Preview", 18.sp, modifier = Modifier.weight(1f))
            Icon(if (preview) Icons.Default.Remove else Icons.Default.Add, "Toggle Preview", tint = Green, modifier = Modifier.size(34.dp))
        }
        if (preview) T(render(chips + listOfNotNull(custom.takeIf { it.isNotBlank() }?.let { Chip(ChipType.Text, it) }), sample, ruleName, preview = true).trimEnd(), 16.sp, modifier = Modifier.padding(top = 6.dp))
    }
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        T("Added Details", 18.sp, modifier = Modifier.weight(1f))
        T("Reset", 17.sp, Green, FontWeight.SemiBold, Modifier.clickable { onChange(defaults); custom = "" }.padding(horizontal = 16.dp))
    }
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        chips.forEachIndexed { i, c -> TemplateChip(if (c.type == ChipType.Text) c.text else c.type.label, false) { onChange(chips.toMutableList().also { it.removeAt(i) }) } }
        BasicTextField(custom, { v -> if (v.endsWith("\n")) { custom = v.trimEnd(); commit() } else custom = v }, Modifier.widthIn(min = 120.dp).height(36.dp).padding(top = 8.dp),
            textStyle = TextStyle(fontFamily = Montserrat, fontSize = 15.5.sp), singleLine = false)
    }
    T("Available Details", 18.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        available.filter { it == ChipType.NewLine || chips.none { c -> c.type == it } }.forEach { ty -> TemplateChip(ty.label, true) { commit(); onChange(chips + Chip(ty)) } }
    }
}

@Composable
fun TemplateChip(text: String, add: Boolean, onClick: () -> Unit) =
    Row(Modifier.height(36.dp).background(ChipBg, CircleShape).clickable(onClick = onClick).padding(start = 5.dp, end = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (add) Icons.Default.Add else Icons.Default.Remove, if (add) "Add" else "Remove Chip", tint = Color(0xFF646464), modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(11.dp))
        T(text, 15.5.sp, Color(0xFF1C1C1C))
    }

/** "Forward message if [Any|All] condition(s) are met." editor for one condition group. */
@Composable
fun GroupDialog(initial: Group, noun: String, onDelete: () -> Unit, onCancel: () -> Unit, onSave: (Group) -> Unit) {
    var g by remember { mutableStateOf(initial.copy(conditions = initial.conditions.ifEmpty { listOf(Condition()) })) }
    ScaledDialog(onCancel, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(6.dp), color = DialogBg) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(verticalArrangement = Arrangement.Center) {
                    T("Forward $noun if ", 19.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    DropBox(g.all, listOf(false, true), { if (it) "All" else "Any" }, { g = g.copy(all = it) }, desc = "Show Condition Options")
                    T(" condition(s) are met.", 19.sp, modifier = Modifier.align(Alignment.CenterVertically))
                }
                g.conditions.forEachIndexed { i, c ->
                    if (i > 0) T(if (g.all) "AND" else "OR", 18.sp, Color(0xFF444444), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).wrapContentWidth())
                    DropBox(c.filter, Filter.entries, { it.label }, { f -> g = g.copy(conditions = g.conditions.toMutableList().also { it[i] = c.copy(filter = f) }) },
                        Modifier.padding(top = 12.dp), "Show Content Filter Options")
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Field(c.value, { v -> g = g.copy(conditions = g.conditions.toMutableList().also { it[i] = c.copy(value = v) }) }, "Enter text", Modifier.weight(1f))
                        if (i > 0) IconButton({ g = g.copy(conditions = g.conditions.toMutableList().also { it.removeAt(i) }) }) { Icon(Icons.Default.Delete, "Delete receiver", Modifier.size(30.dp)) }
                        else Spacer(Modifier.width(48.dp))
                    }
                }
                LightButton("Add condition", { g = g.copy(conditions = g.conditions + Condition()) }, Modifier.padding(vertical = 20.dp).align(Alignment.CenterHorizontally))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LightButton("Delete", onDelete)
                    Spacer(Modifier.weight(1f))
                    LightButton("Cancel", onCancel)
                    Spacer(Modifier.width(20.dp))
                    GreenButton("Save", { onSave(g.copy(conditions = g.conditions.filter { it.value.isNotBlank() })) })
                }
            }
        }
    }
}

@Composable
fun DaysRow(days: Set<Int>, onChange: (Set<Int>) -> Unit) =
    Row(Modifier.widthIn(max = 387.dp).fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { // original: 45dp circles, 12dp apart
        // Sunday first, like the original (DayOfWeek values: Mon=1..Sun=7)
        listOf(7 to "S", 1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S").forEach { (d, l) ->
            val on = d in days
            Box(Modifier.weight(1f).aspectRatio(1f).shadow(if (on) 3.dp else 0.dp, CircleShape).background(if (on) Green else ChipBg, CircleShape)
                .clickable { onChange(if (on) days - d else days + d) }, contentAlignment = Alignment.Center) {
                T(l, 17.sp, if (on) Color.White else Color(0xFF444444), FontWeight.SemiBold)
            }
        }
    }

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
fun minutes(m: Int): String = LocalTime.of(m / 60, m % 60).format(CLOCK)

@Composable
fun TimeRow(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val ctx = LocalContext.current
    val c = if (enabled) Color.Black else Color(0xFFAAAAAA)
    Row(Modifier.fillMaxWidth().clickable(enabled) { TimePickerDialog(ctx, { _, h, m -> onChange(h * 60 + m) }, value / 60, value % 60, false).show() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        T(label, 18.sp, c, modifier = Modifier.weight(1f))
        T(minutes(value), 18.sp, c)
        Icon(Icons.Outlined.Schedule, label, tint = c, modifier = Modifier.padding(start = 10.dp).size(28.dp))
    }
}

@Composable
fun ScheduleSection(r: Rule, noun: String, dayHelp: String = "$noun will be forwarded only during the days selected. By default, all days are selected.",
                    timeHelp: String = "Time range during which $noun will be forwarded. Unselect All Day option to set a custom time range.", onChange: (Rule) -> Unit) {
    var help by remember { mutableStateOf<Pair<String, String>?>(null) }
    help?.let { (a, b) -> InfoDialog(a, b) { help = null } }
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { T("Days", 18.sp); HelpIcon { help = "Days" to dayHelp } }
    DaysRow(r.days) { onChange(r.copy(days = it)) }
    Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) { T("Time", 18.sp); HelpIcon { help = "Time" to timeHelp } }
    SwitchRow("All Day", r.allDay) { onChange(r.copy(allDay = it)) }
    TimeRow("Start Time", r.start, !r.allDay) { onChange(r.copy(start = it)) }
    TimeRow("End Time", r.end, !r.allDay) { onChange(r.copy(end = it)) }
}

/** Previous ● ● ● Next footer of the rule wizards. */
@Composable
fun WizardBar(step: Int, steps: Int, onPrev: () -> Unit, onNext: () -> Unit) =
    Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        GreenButton("Previous", onPrev, Modifier.width(112.dp), enabled = step > 0, weight = FontWeight.Normal)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(steps) { i -> Box(Modifier.padding(horizontal = 6.dp).size(if (i == step) 16.dp else 10.dp).background(if (i == step) Green else Grey, CircleShape)) }
        }
        GreenButton(if (step == steps - 1) "Save" else "Next", onNext, Modifier.width(112.dp))
    }
