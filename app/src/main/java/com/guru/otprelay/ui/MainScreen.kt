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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.guru.otprelay.data.DurationOption
import com.guru.otprelay.data.ForwardStatus
import com.guru.otprelay.data.LogEntry
import com.guru.otprelay.data.Preset
import com.guru.otprelay.data.RequestLink
import com.guru.otprelay.data.Session
import com.guru.otprelay.data.Store
import com.guru.otprelay.data.Target
import com.guru.otprelay.forwarding.Forwarder
import com.guru.otprelay.forwarding.ForwardingService
import kotlinx.coroutines.delay

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

    var number by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf(DurationOption.M15) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pasted by remember { mutableStateOf<Preset?>(null) }
    var confirming by remember { mutableStateOf<Preset?>(null) }

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
                    number = cursor.getString(0).orEmpty().filter { it.isDigit() || it == '+' }
                    name = cursor.getString(1).orEmpty()
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
        if (Store.isKnownNumber(target.number)) {
            startConfirmed(target, millis)
        } else {
            confirming = Preset(target, millis)
        }
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

    fun copyLink(target: Target, millis: Long) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("OTP Relay request", RequestLink.build(target, millis))
        )
        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(top = 20.dp, bottom = 4.dp)) {
                    Text(
                        "OTP Relay",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Texts containing “OTP” go on to one person, for a set time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = activeSession != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    activeSession?.let {
                        ActiveCard(it, now) { ForwardingService.stop(context) }
                    }
                }
            }

            if (activeSession == null) {
                if (shortcuts.isNotEmpty()) {
                    item {
                        ShortcutsCard(
                            shortcuts = shortcuts,
                            onStart = { s -> startSession(s.target, s.durationMillis) },
                            onShare = { s -> shareLink(s.target, s.durationMillis) },
                            onDelete = { s -> Store.deleteShortcut(s) },
                        )
                    }
                }

                item {
                    StartCard(
                        number = number,
                        name = name,
                        duration = duration,
                        numbers = numbers,
                        onNumberChange = { number = it; name = "" },
                        onPickContact = {
                            contactLauncher.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))
                        },
                        onDurationChange = { duration = it },
                        onPickNumber = { number = it.number; name = it.name.orEmpty() },
                        onDeleteNumber = { Store.deleteNumber(it) },
                        onStart = { t, m -> startSession(t, m) },
                        onSave = { t, m ->
                            Store.saveShortcut(t, m)
                            Toast.makeText(context, "Shortcut saved", Toast.LENGTH_SHORT).show()
                        },
                        onShare = { t, m -> shareLink(t, m) },
                        onCopy = { t, m -> copyLink(t, m) },
                    )
                }

                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = myNumber,
                                onValueChange = { Store.setMyNumber(it) },
                                label = { Text("My number") },
                                supportingText = { Text("Named in the start and stop texts. Optional.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(onClick = { pasteLink() }) {
                                Text("Open a request link from the clipboard")
                            }
                        }
                    }
                }
            }

            item { BatteryHint(now) }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (sessions.isNotEmpty()) {
                        TextButton(onClick = { Store.clearHistory() }) { Text("Clear") }
                    }
                }
            }

            if (sessions.isEmpty()) {
                item {
                    Text(
                        "Nothing yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            sessions.forEach { session ->
                item(key = "s${session.id}") {
                    SessionHeader(session, session.id == activeSession?.id)
                }
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

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    val prompt = request ?: pasted
    if (prompt != null) {
        ApproveDialog(
            request = prompt,
            blocked = activeSession != null,
            onDismiss = { pasted = null; onRequestHandled() },
            onApprove = { target, millis ->
                pasted = null
                onRequestHandled()
                startSession(target, millis)
            },
        )
    }

    confirming?.let { first ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Send OTPs to this number?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    first.target.name?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(first.target.number, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "This is the first time you are using this number. Every text containing " +
                            "“OTP” for the next ${DurationOption.labelFor(first.durationMillis)} " +
                            "will be sent to it. Check the digits carefully.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    startConfirmed(first.target, first.durationMillis)
                }) { Text("Yes, forward to this number") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActiveCard(session: Session, now: Long, onStop: () -> Unit) {
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
            Text(session.target.display, style = MaterialTheme.typography.headlineSmall)
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
    onStart: (Preset) -> Unit,
    onShare: (Preset) -> Unit,
    onDelete: (Preset) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("Shortcuts")
            shortcuts.forEachIndexed { index, shortcut ->
                if (index > 0) HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f).clickable { onStart(shortcut) }.padding(vertical = 8.dp)
                    ) {
                        Text(
                            shortcut.target.display,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Start for ${DurationOption.labelFor(shortcut.durationMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onShare(shortcut) }) { Text("Link") }
                    TextButton(onClick = { onDelete(shortcut) }) { Text("Remove") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartCard(
    number: String,
    name: String,
    duration: DurationOption,
    numbers: List<Target>,
    onNumberChange: (String) -> Unit,
    onPickContact: () -> Unit,
    onDurationChange: (DurationOption) -> Unit,
    onPickNumber: (Target) -> Unit,
    onDeleteNumber: (Target) -> Unit,
    onStart: (Target, Long) -> Unit,
    onSave: (Target, Long) -> Unit,
    onShare: (Target, Long) -> Unit,
    onCopy: (Target, Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Row(verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = number,
                    onValueChange = onNumberChange,
                    label = { Text("Forward to") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onPickContact,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Contacts") }
            }

            if (name.isNotBlank()) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (numbers.isNotEmpty()) {
                Label("Saved contacts")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    numbers.forEach { target ->
                        InputChip(
                            selected = target.number == number,
                            onClick = { onPickNumber(target) },
                            label = { Text(target.display, maxLines = 1) },
                            trailingIcon = {
                                Text(
                                    "✕",
                                    modifier = Modifier.clickable { onDeleteNumber(target) },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                        )
                    }
                }
            }

            Label("For how long")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DurationOption.entries.forEach { option ->
                    FilterChip(
                        selected = option == duration,
                        onClick = { onDurationChange(option) },
                        label = { Text(option.label) },
                    )
                }
            }

            val target = Target(number.trim(), name.ifBlank { null })
            val enabled = isValid(number)

            Button(
                onClick = { onStart(target, duration.millis) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start forwarding") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSave(target, duration.millis) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
                OutlinedButton(
                    onClick = { onShare(target, duration.millis) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text("Share link") }
                OutlinedButton(
                    onClick = { onCopy(target, duration.millis) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) { Text("Copy") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApproveDialog(
    request: Preset,
    blocked: Boolean,
    onDismiss: () -> Unit,
    onApprove: (Target, Long) -> Unit,
) {
    var number by remember(request) { mutableStateOf(request.target.number) }
    var duration by remember(request) { mutableStateOf(DurationOption.nearest(request.durationMillis)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forwarding request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    request.target.name?.let { "$it is asking you to forward OTPs to them." }
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
            TextButton(
                onClick = { onApprove(Target(number.trim(), request.target.name), duration.millis) },
                enabled = isValid(number) && !blocked,
            ) { Text("Approve") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
private fun SessionHeader(session: Session, isActive: Boolean) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            (if (isActive) "● " else "") + session.target.display,
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
private fun BatteryHint(now: Long) {
    val context = LocalContext.current
    // Reading the state needs no permission; only asking for the exemption directly would, and
    // that permission is restricted on Google Play. So we read, and send the user to Settings.
    val exempt = remember(now / 5000) {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }
    if (exempt) return

    TextButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }) { Text("Allow background use (only matters for 6 hour and 1 day sessions)") }
}
