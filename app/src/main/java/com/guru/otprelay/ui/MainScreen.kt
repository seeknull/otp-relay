package com.guru.otprelay.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.guru.otprelay.BuildConfig
import com.guru.otprelay.R
import com.guru.otprelay.data.DurationOption
import com.guru.otprelay.data.ForwardStatus
import com.guru.otprelay.data.LogEntry
import com.guru.otprelay.data.Preset
import com.guru.otprelay.data.RequestLink
import com.guru.otprelay.data.Session
import com.guru.otprelay.data.sameNumber
import com.guru.otprelay.data.Store
import com.guru.otprelay.data.Target
import com.guru.otprelay.forwarding.Forwarder
import com.guru.otprelay.forwarding.ForwardingService
import kotlinx.coroutines.delay

private enum class Screen { HOME, HISTORY, SETTINGS }

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.RECEIVE_SMS)
    add(Manifest.permission.SEND_SMS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun isValid(number: String) = number.count { it.isDigit() } >= RequestLink.MIN_DIGITS

@Composable
fun MainScreen(request: Preset?, onRequestHandled: () -> Unit) {
    val context = LocalContext.current

    val active by Store.active.collectAsState()
    val sessions by Store.sessions.collectAsState()
    val logs by Store.logs.collectAsState()
    val shortcuts by Store.shortcuts.collectAsState()
    val numbers by Store.numbers.collectAsState()
    val myNumber by Store.myNumber.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    var selectedNumber by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf(DurationOption.M15) }
    val selected = numbers.firstOrNull { sameNumber(it.number, selectedNumber) }
    val marks = remember(numbers) { emojiAssignments(numbers.map { it.number }) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pasted by remember { mutableStateOf<Preset?>(null) }
    var unknown by remember { mutableStateOf<Preset?>(null) }
    var unmatched by remember { mutableStateOf<Preset?>(null) }
    var verifying by remember { mutableStateOf<Preset?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val block = pending
        pending = null
        if (granted.values.all { it }) {
            block?.invoke()
        } else {
            Toast.makeText(context, "SMS and notification access are required", Toast.LENGTH_LONG).show()
        }
    }

    val contactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // The picker grants read access to just this one row, so READ_CONTACTS is never needed.
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        context.contentResolver
            .query(uri, arrayOf(Phone.NUMBER, Phone.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val picked = Target(
                        cursor.getString(0).orEmpty().filter { it.isDigit() || it == '+' },
                        cursor.getString(1)?.takeIf { it.isNotBlank() },
                    )
                    // Picking proves the contact exists in the phone book, which is what makes
                    // this number allowed to receive OTPs.
                    Store.saveContact(picked)
                    selectedNumber = picked.number

                    // If we were verifying a request link, only continue when it is the same person.
                    verifying?.let { pendingRequest ->
                        verifying = null
                        if (sameNumber(picked.number, pendingRequest.target.number)) {
                            pasted = Preset(picked, pendingRequest.durationMillis)
                        } else {
                            unmatched = pendingRequest
                        }
                    }
                }
            }
    }

    fun startConfirmed(target: Target, millis: Long) {
        if (Store.currentSession() != null) {
            Toast.makeText(context, "Stop the current session first", Toast.LENGTH_SHORT).show()
            return
        }
        val block = {
            val session = Store.startSession(target, millis)
            ForwardingService.start(context)
            Forwarder.notifyStarted(context, session, formatClock(session.expiresAt))
        }
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            block()
        } else {
            pending = block
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /** A number that has never been used gets one confirmation before it is texted. */
    fun startSession(target: Target, millis: Long) {
        screen = Screen.HOME
        // Hard rule: OTPs only ever go to a number chosen from the phone book. A contact that
        // already cleared that bar starts straight away, with no extra prompt.
        if (Store.isKnownNumber(target.number)) {
            startConfirmed(target, millis)
        } else {
            unknown = Preset(target, millis)
        }
    }

    fun pickContact() {
        contactLauncher.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))
    }

    fun shareLink(target: Target, millis: Long) {
        val link = RequestLink.build(target, millis)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Send this back to me when you need an OTP:\n$link"),
                "Share request link",
            )
        )
    }

    fun pasteLink() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()
        val parsed = RequestLink.parse(text)
        if (parsed == null) {
            Toast.makeText(context, "No request link on the clipboard", Toast.LENGTH_SHORT).show()
        } else {
            pasted = parsed
        }
    }

    val activeSession = active?.takeIf { it.isActive(now) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                TopBar(
                    screen = screen,
                    onOpen = { screen = it },
                    onBack = { screen = Screen.HOME },
                )
            }

            when (screen) {
                Screen.HOME -> home(
                    activeSession = activeSession,
                    now = now,
                    shortcuts = shortcuts,
                    numbers = numbers,
                    marks = marks,
                    selected = selected,
                    duration = duration,
                    onStop = { ForwardingService.stop(context) },
                    onPickContact = ::pickContact,
                    onSelect = { selectedNumber = it.number },
                    onDurationChange = { duration = it },
                    onStart = ::startSession,
                    onSave = { t, m ->
                        Store.saveShortcut(t, m)
                        Toast.makeText(context, "Shortcut saved", Toast.LENGTH_SHORT).show()
                    },
                    onShare = ::shareLink,
                    onDeleteShortcut = { Store.deleteShortcut(it) },
                )

                Screen.HISTORY -> history(
                    sessions = sessions,
                    marks = marks,
                    logs = logs,
                    activeId = activeSession?.id,
                    onClear = { Store.clearHistory() },
                )

                Screen.SETTINGS -> settings(
                    myNumber = myNumber,
                    contacts = numbers,
                    now = now,
                    onMyNumberChange = { Store.setMyNumber(it) },
                    onDeleteContact = { Store.deleteNumber(it) },
                    onPasteLink = ::pasteLink,
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    val prompt = request ?: pasted
    if (prompt != null) {
        ApproveDialog(
            request = prompt,
            blocked = activeSession != null,
            onVerify = { p -> pasted = null; onRequestHandled(); verifying = p; pickContact() },
            onDismiss = { pasted = null; onRequestHandled() },
            onApprove = { target, millis ->
                pasted = null
                onRequestHandled()
                startSession(target, millis)
            },
        )
    }

    unknown?.let { blockedRequest ->
        AlertDialog(
            onDismissRequest = { unknown = null },
            title = { Text("Not a saved contact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(blockedRequest.target.number, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "OTPs are only ever sent to someone in your phone book. Choose this " +
                            "person from your contacts to allow it. If they are not in your phone " +
                            "book yet, add them there first.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    verifying = blockedRequest
                    unknown = null
                    pickContact()
                }) { Text("Choose from contacts") }
            },
            dismissButton = { TextButton(onClick = { unknown = null }) { Text("Cancel") } },
        )
    }

    unmatched?.let { mismatch ->
        AlertDialog(
            onDismissRequest = { unmatched = null },
            title = { Text("That is a different number") },
            text = {
                Text(
                    "The contact you chose does not have the number ${mismatch.target.number}. " +
                        "Save that number in your phone book against the right person, then try " +
                        "again. Nothing has been forwarded.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    verifying = mismatch
                    unmatched = null
                    pickContact()
                }) { Text("Try again") }
            },
            dismissButton = { TextButton(onClick = { unmatched = null }) { Text("Cancel") } },
        )
    }

}

@Composable
private fun TopBar(screen: Screen, onOpen: (Screen) -> Unit, onBack: () -> Unit) {
    if (screen != Screen.HOME) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(4.dp))
            Text(
                if (screen == Screen.HISTORY) "History" else "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_app_icon),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "OTP Relay",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    "Texts containing “OTP” go on to one person, for a set time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onOpen(Screen.HISTORY) }) { Text("History") }
            TextButton(onClick = { onOpen(Screen.SETTINGS) }) { Text("Settings") }
        }
    }
}

private fun LazyListScope.home(
    activeSession: Session?,
    now: Long,
    shortcuts: List<Preset>,
    numbers: List<Target>,
    marks: Map<String, String>,
    selected: Target?,
    duration: DurationOption,
    onStop: () -> Unit,
    onPickContact: () -> Unit,
    onSelect: (Target) -> Unit,
    onDurationChange: (DurationOption) -> Unit,
    onStart: (Target, Long) -> Unit,
    onSave: (Target, Long) -> Unit,
    onShare: (Target, Long) -> Unit,
    onDeleteShortcut: (Preset) -> Unit,
) {
    item {
        AnimatedVisibility(visible = activeSession != null, enter = fadeIn(), exit = fadeOut()) {
            activeSession?.let { ActiveCard(it, marks, now, onStop) }
        }
    }

    if (activeSession == null) {
        if (shortcuts.isNotEmpty()) {
            item {
                ShortcutsCard(
                    shortcuts = shortcuts,
                    marks = marks,
                    onStart = { onStart(it.target, it.durationMillis) },
                    onShare = { onShare(it.target, it.durationMillis) },
                    onDelete = onDeleteShortcut,
                )
            }
        }
        item {
            StartCard(
                contacts = numbers,
                marks = marks,
                selected = selected,
                duration = duration,
                onPickContact = onPickContact,
                onSelect = onSelect,
                onDurationChange = onDurationChange,
                onStart = onStart,
                onSave = onSave,
                onShare = onShare,
            )
        }
    }
}

private fun LazyListScope.history(
    sessions: List<Session>,
    marks: Map<String, String>,
    logs: List<LogEntry>,
    activeId: Long?,
    onClear: () -> Unit,
) {
    if (sessions.isEmpty()) {
        item {
            Text(
                "Nothing yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClear) { Text("Clear history") }
        }
    }

    sessions.forEach { session ->
        item(key = "s${session.id}") { SessionHeader(session, marks, session.id == activeId) }
        val entries = logs.filter { it.sessionId == session.id }
        if (entries.isEmpty()) {
            item(key = "e${session.id}") {
                Text(
                    "Nothing forwarded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(entries, key = { it.id }) { LogRow(it) }
        }
    }
}

private fun LazyListScope.settings(
    myNumber: String,
    contacts: List<Target>,
    now: Long,
    onMyNumberChange: (String) -> Unit,
    onDeleteContact: (Target) -> Unit,
    onPasteLink: () -> Unit,
) {
    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label("Contacts allowed to receive OTPs")
                Text(
                    "Only these can be forwarded to. Add one by choosing it from your phone book.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (contacts.isEmpty()) {
                    Text(
                        "None yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                contacts.forEach { contact ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            contact.name?.let { "$it (${contact.number})" } ?: contact.number,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { onDeleteContact(contact) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("Remove") }
                    }
                }
            }
        }
    }

    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = myNumber,
                    onValueChange = onMyNumberChange,
                    label = { Text("My number") },
                    supportingText = {
                        Text("Added to the start and stop texts so the other person knows it is you. Optional.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    item {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("Request link")
                Text(
                    "Request links normally open this app when tapped. If one arrives as plain " +
                        "text, copy it and use this.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onPasteLink) { Text("Open link from clipboard") }
            }
        }
    }

    item { BatteryCard(now) }
}

@Composable
private fun ActiveCard(session: Session, marks: Map<String, String>, now: Long, onStop: () -> Unit) {
    val total = (session.expiresAt - session.startedAt).coerceAtLeast(1)
    val remaining = (session.expiresAt - now).coerceAtLeast(0)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "FORWARDING IS ON",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${emojiFor(session.target.number, marks)}  ${session.target.display}",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (session.target.name != null) {
                Text(session.target.number, style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(
                progress = { remaining.toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Text(
                "${formatClock(session.startedAt)} → ${formatClock(session.expiresAt)}   ·   " +
                    formatRemaining(remaining),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Stop now") }
        }
    }
}

@Composable
private fun ShortcutsCard(
    shortcuts: List<Preset>,
    marks: Map<String, String>,
    onStart: (Preset) -> Unit,
    onShare: (Preset) -> Unit,
    onDelete: (Preset) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label("Quick Actions ⚡️")
            shortcuts.forEach { shortcut ->
                val tint = colorForMillis(shortcut.durationMillis)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(emojiFor(shortcut.target.number, marks), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f)) {
                        Text(
                            shortcut.target.name?.let { "$it (${shortcut.target.number})" }
                                ?: shortcut.target.number,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        DurationPill(shortcut.durationMillis)
                    }
                    Button(
                        onClick = { onStart(shortcut) },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp),
                    ) { Text("Start", maxLines = 1) }
                    TextButton(
                        onClick = { onShare(shortcut) },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.width(52.dp).height(36.dp),
                    ) { Text("Share", maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    TextButton(
                        onClick = { onDelete(shortcut) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.width(28.dp).height(36.dp),
                    ) { Text("✕", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartCard(
    contacts: List<Target>,
    marks: Map<String, String>,
    selected: Target?,
    duration: DurationOption,
    onPickContact: () -> Unit,
    onSelect: (Target) -> Unit,
    onDurationChange: (DurationOption) -> Unit,
    onStart: (Target, Long) -> Unit,
    onSave: (Target, Long) -> Unit,
    onShare: (Target, Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Only contacts chosen from the phone book can be picked, so there is no free text
            // field: the rule is enforced by what the UI offers rather than by a later error.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                contacts.forEach { contact ->
                    FilterChip(
                        selected = contact.number == selected?.number,
                        onClick = { onSelect(contact) },
                        label = { Text("${emojiFor(contact.number, marks)}  ${contact.display}", maxLines = 1) },
                    )
                }
                AssistChip(
                    onClick = onPickContact,
                    label = { Text(if (contacts.isEmpty()) "Choose a contact" else "+ Contact") },
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DurationOption.entries.forEach { option ->
                    DurationChip(option, option == duration) { onDurationChange(option) }
                }
            }

            Button(
                onClick = { selected?.let { onStart(it, duration.millis) } },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    selected?.let { "Start for ${duration.label}" } ?: "Choose a contact to begin",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selected != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSave(selected, duration.millis) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save shortcut", maxLines = 1) }
                    OutlinedButton(
                        onClick = { onShare(selected, duration.millis) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Share link", maxLines = 1) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApproveDialog(
    request: Preset,
    blocked: Boolean,
    onVerify: (Preset) -> Unit,
    onDismiss: () -> Unit,
    onApprove: (Target, Long) -> Unit,
) {
    var number by remember(request) { mutableStateOf(request.target.number) }
    var duration by remember(request) { mutableStateOf(DurationOption.nearest(request.durationMillis)) }
    val known = Store.isKnownNumber(number)
    val savedName = Store.nameFor(number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forwarding request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    savedName?.let { "$it is asking you to forward OTPs to them." }
                        ?: "Someone is asking you to forward OTPs to this number.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Forward to") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DurationOption.entries.forEach { option ->
                        FilterChip(
                            selected = option == duration,
                            onClick = { duration = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                if (!known) {
                    Text(
                        "This number is not one of your saved contacts. OTPs are only ever sent " +
                            "to a contact you have chosen from your phone book.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (blocked) {
                    Text(
                        "A session is already running. Stop it first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (!known) {
                TextButton(
                    onClick = { onVerify(Preset(Target(number.trim()), duration.millis)) },
                    enabled = isValid(number),
                ) { Text("Choose from contacts") }
            } else {
                TextButton(
                    onClick = { onApprove(Target(number.trim(), savedName), duration.millis) },
                    enabled = isValid(number) && !blocked,
                ) { Text("Approve") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DurationPill(millis: Long) {
    val color = colorForMillis(millis)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            DurationOption.labelFor(millis),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DurationChip(option: DurationOption, selected: Boolean, onClick: () -> Unit) {
    val color = colorFor(option)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(option.label) },
        leadingIcon = {
            if (!selected) Box(Modifier.size(10.dp).background(color, CircleShape))
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
        ),
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SessionHeader(session: Session, marks: Map<String, String>, isActive: Boolean) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            (if (isActive) "● " else "") + emojiFor(session.target.number, marks) + "  " + session.target.display,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "${formatDayClock(session.startedAt)} → ${formatClock(session.expiresAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val statusColor = when (entry.status) {
        ForwardStatus.SENT -> MaterialTheme.colorScheme.primary
        ForwardStatus.FAILED -> MaterialTheme.colorScheme.error
        ForwardStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${formatClock(entry.arrivedAt)}  ·  ${entry.from}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    entry.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
            }
            Text(
                entry.body,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "→ ${entry.to}  ·  ${formatLatency(entry.latencyMs)}" +
                    (entry.error?.let { "  ·  $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BatteryCard(now: Long) {
    val context = LocalContext.current
    // Reading the state needs no permission; only asking for the exemption directly would, and
    // that permission is restricted on Google Play. So we read, and send the user to Settings.
    val exempt = remember(now / 5000) {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("Background use")
            Text(
                if (exempt) {
                    "Allowed. Sessions will not be cut short by battery saving."
                } else {
                    "Android may cut a session short to save battery. This mostly affects the " +
                        "1 hour option."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!exempt) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }) { Text("Open battery settings") }
            }
        }
    }
}
