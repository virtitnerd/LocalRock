package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.camera.AlertLevel
import com.kodraliu.localrock.shared.vacuum.camera.PatternCheck
import com.kodraliu.localrock.shared.vacuum.camera.PinLockPolicy
import com.kodraliu.localrock.ui.LocalAppContainer
import com.kodraliu.localrock.ui.components.BonusBadge
import com.kodraliu.localrock.ui.components.BonusKind
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.components.SettingIconTile
import com.kodraliu.localrock.ui.components.SettingRowClickable
import com.kodraliu.localrock.ui.components.SettingRowPadding
import com.kodraliu.localrock.ui.strings.RemoteStrings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RvPage { MAIN, ALERT, SECURITY, PRIVACY, PATTERN }

private sealed interface RvDialog {
    data object Deactivate : RvDialog
    data object PrivacyBeforeActivate : RvDialog
    data class Activating(val secondsLeft: Int, val twoKey: Boolean) : RvDialog
    data object Revoke : RvDialog
    data object RevokeEndsTask : RvDialog
    data object Forgot : RvDialog
    data object Tuning : RvDialog
}

// Remote Viewing settings (official CameraSettingDetail) with Alert Level, Security and privacy as in-page sub pages.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun RemoteViewingSettingsScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val duid = viewModel.repository.duid
    val caps by viewModel.capabilities.collectAsState()
    val controller = remember { RemoteViewingController(viewModel.repository.session) }
    val snapshot by controller.snapshot.collectAsState()
    val readFailed by controller.readFailed.collectAsState()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(RvPage.MAIN) }
    var dialog by remember { mutableStateOf<RvDialog?>(null) }
    var patternMode by remember { mutableStateOf(PatternMode.SET) }
    var working by remember { mutableStateOf(false) }
    var activationJob by remember { mutableStateOf<Job?>(null) }
    var experiments by remember { mutableStateOf(container.appSettings.videoExperiments()) }

    LaunchedEffect(caps.remoteViewing) { if (caps.remoteViewing) controller.refresh() }
    BackHandler(enabled = page != RvPage.MAIN) {
        page = if (page == RvPage.PATTERN) RvPage.SECURITY else RvPage.MAIN
    }

    fun busy(block: suspend () -> Unit) {
        if (working) return
        working = true
        scope.launch { try { block() } finally { working = false } }
    }

    fun startActivation() {
        val twoKey = caps.twoKeyVideo
        dialog = RvDialog.Activating(RemoteViewingController.ACTIVATION_SECONDS, twoKey)
        activationJob = scope.launch {
            val ok = controller.activate(twoKey) { left -> dialog = RvDialog.Activating(left, twoKey) }
            dialog = null
            if (!ok) container.messages.error(RemoteStrings.RV_TIMEOUT)
        }
    }

    fun revoke() = busy {
        if (controller.setPrivacy(false)) container.messages.success(RemoteStrings.REVOKED)
        else container.messages.error(RemoteStrings.REVOKE_TIMEOUT)
    }

    val title = when (page) {
        RvPage.MAIN -> RemoteStrings.RV_TITLE
        RvPage.ALERT -> RemoteStrings.ALERT_LEVEL
        RvPage.SECURITY -> RemoteStrings.SECURITY
        RvPage.PRIVACY -> RemoteStrings.PRIVACY_POLICY_TITLE
        RvPage.PATTERN -> patternMode.title
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        when (page) {
                            RvPage.MAIN -> onBack()
                            RvPage.PATTERN -> page = RvPage.SECURITY
                            else -> page = RvPage.MAIN
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, RemoteStrings.BACK) }
                },
            )
        },
    ) { padding ->
        val snap = snapshot
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !caps.remoteViewing -> CenteredNote(RemoteStrings.RV_NO_CAMERA)
                snap == null && readFailed -> CenteredNote(RemoteStrings.READ_FAILED)
                snap == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                page == RvPage.ALERT -> AlertLevelPage(AlertLevel.fromWire(snap.camera.alertLevel), working) { level ->
                    busy {
                        if (!controller.setAlertLevel(level.wire)) container.messages.error(RemoteStrings.ALERT_NOT_APPLIED)
                    }
                }
                page == RvPage.SECURITY -> SecurityPage(
                    passwordEnabled = snap.passwordEnabled,
                    onSetUp = { patternMode = PatternMode.SET; page = RvPage.PATTERN },
                    onChange = { patternMode = PatternMode.CHANGE; page = RvPage.PATTERN },
                )
                page == RvPage.PATTERN -> PatternPage(
                    controller = controller,
                    mode = patternMode,
                    onForgot = { dialog = RvDialog.Forgot },
                    onDone = { text ->
                        container.messages.success(text)
                        page = RvPage.SECURITY
                    },
                )
                page == RvPage.PRIVACY -> PrivacyPage(snap.camera.privacyAgreed, working) {
                    if (!snap.camera.privacyAgreed) busy {
                        if (!controller.setPrivacy(true)) container.messages.error(RemoteStrings.FAILED)
                    } else if (isTaskRunning(snap.robotState)) dialog = RvDialog.RevokeEndsTask
                    else dialog = RvDialog.Revoke
                }
                else -> MainPage(
                    snapshot = snap,
                    showAlertLevel = caps.remoteViewingFeatures.alertLevel,
                    showCallVolume = caps.remoteViewingFeatures.callVolume,
                    callVolume = container.appSettings.callVolume(duid),
                    working = working,
                    onActivateToggle = {
                        when {
                            snap.camera.activated -> dialog = RvDialog.Deactivate
                            snap.robotState == VacuumStateCodes.ERROR -> container.messages.error(RemoteStrings.RV_FIX_ERROR)
                            !snap.camera.privacyAgreed -> dialog = RvDialog.PrivacyBeforeActivate
                            else -> startActivation()
                        }
                    },
                    onAlertLevel = { page = RvPage.ALERT },
                    onCallVolume = { container.appSettings.setCallVolume(duid, it) },
                    onSecurity = { page = RvPage.SECURITY },
                    onTuning = { dialog = RvDialog.Tuning },
                    onPrivacy = { page = RvPage.PRIVACY },
                )
            }
        }
    }

    when (val d = dialog) {
        RvDialog.Deactivate -> ConfirmDialog(null, RemoteStrings.RV_DEACTIVATE_CONFIRM, RemoteStrings.OK, { dialog = null }) {
            dialog = null
            busy { if (!controller.deactivate()) container.messages.error(RemoteStrings.FAILED) }
        }
        RvDialog.PrivacyBeforeActivate -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(RemoteStrings.PRIVACY_POLICY_TITLE) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(RemoteStrings.PRIVACY_INTRO)
                    Text(
                        RemoteStrings.PRIVACY_POLICY,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { dialog = null; page = RvPage.PRIVACY },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    busy { if (controller.setPrivacy(true)) startActivation() else container.messages.error(RemoteStrings.FAILED) }
                }) { Text(RemoteStrings.AGREE) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(RemoteStrings.CANCEL) } },
        )
        is RvDialog.Activating -> AlertDialog(
            onDismissRequest = {},
            title = { Text(RemoteStrings.RV_ACTIVATION_PENDING) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (d.twoKey) RemoteStrings.RV_TWO_KEY_TOP else RemoteStrings.RV_THREE_KEY_TOP, style = MaterialTheme.typography.titleSmall)
                    Text(if (d.twoKey) RemoteStrings.RV_TWO_KEY_BODY else RemoteStrings.RV_THREE_KEY_BODY)
                    Text("${d.secondsLeft}${RemoteStrings.RV_SECONDS_LEFT}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activationJob?.cancel(); dialog = null }) { Text(RemoteStrings.CANCEL) }
            },
        )
        RvDialog.Revoke -> ConfirmDialog(RemoteStrings.REVOKE_TITLE, RemoteStrings.REVOKE_BODY, RemoteStrings.OK, { dialog = null }) {
            dialog = null
            revoke()
        }
        RvDialog.RevokeEndsTask -> ConfirmDialog(null, RemoteStrings.REVOKE_ENDS_TASK, RemoteStrings.END, { dialog = null }) {
            dialog = null
            busy {
                if (controller.stopTask()) {
                    if (controller.setPrivacy(false)) container.messages.success(RemoteStrings.REVOKED)
                    else container.messages.error(RemoteStrings.REVOKE_TIMEOUT)
                } else container.messages.error(RemoteStrings.RV_TIMEOUT)
            }
        }
        RvDialog.Forgot -> ConfirmDialog(null, RemoteStrings.FORGOT_CONFIRM, RemoteStrings.OK, { dialog = null }) {
            dialog = null
            busy {
                if (controller.resetPattern()) {
                    container.messages.success(RemoteStrings.PASSWORD_DISABLED)
                    page = RvPage.SECURITY
                } else container.messages.error(RemoteStrings.FAILED)
            }
        }
        RvDialog.Tuning -> VideoExperimentsDialog(
            experiments,
            { e -> experiments = e; container.appSettings.setVideoExperiments(e) },
            null,
        ) { dialog = null }
        null -> Unit
    }
}

@Composable
private fun MainPage(
    snapshot: RemoteViewingSnapshot,
    showAlertLevel: Boolean,
    showCallVolume: Boolean,
    callVolume: Int,
    working: Boolean,
    onActivateToggle: () -> Unit,
    onAlertLevel: () -> Unit,
    onCallVolume: (Int) -> Unit,
    onSecurity: () -> Unit,
    onTuning: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val active = snapshot.camera.activated
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Videocam, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                if (active) RemoteStrings.RV_ACTIVE_REMINDER else RemoteStrings.RV_INTRO,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (active) OutlinedButton(onClick = onActivateToggle, enabled = !working) { Text(RemoteStrings.RV_DEACTIVATE) }
            else Button(onClick = onActivateToggle, enabled = !working) { Text(RemoteStrings.RV_ACTIVATE) }
        }

        if (showAlertLevel) SettingGroup {
            Box {
                SettingRowClickable(
                    Icons.Default.Notifications, MaterialTheme.colorScheme.primary,
                    RemoteStrings.ALERT_LEVEL, AlertLevel.fromWire(snapshot.camera.alertLevel).label(),
                    enabled = !working, onClick = onAlertLevel,
                )
                BonusBadge(BonusKind.Beta, Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 16.dp))
            }
        }

        if (showCallVolume) SettingGroup {
            var volume by remember { mutableIntStateOf(callVolume) }
            Column(Modifier.padding(SettingRowPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingIconTile(Icons.AutoMirrored.Filled.VolumeUp, MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(RemoteStrings.CALL_VOLUME, style = MaterialTheme.typography.bodyLarge)
                        Text(RemoteStrings.CALL_VOLUME_DESC, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("$volume", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = volume.toFloat(),
                    onValueChange = { volume = CallVolume.snap(it) },
                    valueRange = 0f..CallVolume.MAX.toFloat(),
                    steps = CallVolume.MAX / CallVolume.STEP - 1,
                    onValueChangeFinished = { onCallVolume(volume) },
                )
            }
        }

        SettingGroup {
            SettingRowClickable(
                Icons.Default.Lock, MaterialTheme.colorScheme.primary, RemoteStrings.SECURITY,
                when (snapshot.passwordEnabled) {
                    true -> RemoteStrings.PASSWORD_ON
                    false -> RemoteStrings.PASSWORD_OFF
                    null -> ""
                },
                onClick = onSecurity,
            )
        }

        // LocalRock extra: the live view's offer and SDP switches, kept with the other camera settings.
        SettingGroup {
            Box {
                SettingRowClickable(
                    Icons.Default.Tune, MaterialTheme.colorScheme.tertiary,
                    RemoteStrings.CAMERA_TUNING, RemoteStrings.CAMERA_TUNING_DESC, onClick = onTuning,
                )
                BonusBadge(BonusKind.LocalRock, Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 16.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onPrivacy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(RemoteStrings.PRIVACY_POLICY, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AlertLevelPage(current: AlertLevel, working: Boolean, onSelect: (AlertLevel) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Text(
            RemoteStrings.ALERT_HEADER,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SettingGroup {
            AlertLevel.entries.forEach { level ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !working) { onSelect(level) }.padding(SettingRowPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(level.label(), style = MaterialTheme.typography.bodyLarge)
                        Text(level.description(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = level == current, enabled = !working, onClick = { onSelect(level) })
                }
            }
        }
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BonusBadge(BonusKind.Beta)
            Text(RemoteStrings.ALERT_CN_ONLY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SecurityPage(passwordEnabled: Boolean?, onSetUp: () -> Unit, onChange: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        SettingGroup {
            // Official: the set-up row only while no pattern is on; Change Password only works once one is.
            if (passwordEnabled == false) SettingRowClickable(
                Icons.Default.Lock, MaterialTheme.colorScheme.primary,
                RemoteStrings.SET_UP_PASSWORD, RemoteStrings.PASSWORD_EVERY_TIME, onClick = onSetUp,
            )
            SettingRowClickable(
                Icons.Default.Lock, MaterialTheme.colorScheme.primary,
                RemoteStrings.CHANGE_PASSWORD,
                if (passwordEnabled == true) RemoteStrings.PASSWORD_ON else RemoteStrings.PASSWORD_OFF,
                enabled = passwordEnabled == true, onClick = onChange,
            )
        }
    }
}

@Composable
private fun PrivacyPage(agreed: Boolean, working: Boolean, onButton: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(RemoteStrings.PRIVACY_INTRO, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (agreed) RemoteStrings.PRIVACY_AGREED else RemoteStrings.PRIVACY_NOT_AGREED,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onButton,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
            colors = if (agreed) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
        ) { Text(if (agreed) RemoteStrings.REVOKE else RemoteStrings.AGREE) }
    }
}

@Composable
private fun PatternPage(
    controller: RemoteViewingController,
    mode: PatternMode,
    onForgot: () -> Unit,
    onDone: (String) -> Unit,
) {
    val flow = remember(mode) { PatternFlow(mode) }
    var message by remember(mode) { mutableStateOf(PatternAction.Show(flow.initialMessage)) }
    var lockedSeconds by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var showsForgot by remember(mode) { mutableStateOf(flow.showsForgot) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lockedSeconds > 0) {
        while (lockedSeconds > 0) { delay(1_000); lockedSeconds-- }
    }

    fun handle(pin: String) {
        if (working || lockedSeconds > 0) return
        when (val action = flow.onDrawn(pin)) {
            is PatternAction.Show -> message = action
            is PatternAction.VerifyOld -> {
                working = true
                scope.launch {
                    when (controller.checkPattern(action.pin)) {
                        PatternCheck.OK -> { message = flow.oldAccepted(action.pin); showsForgot = flow.showsForgot }
                        PatternCheck.LOCKED -> lockedSeconds = (PinLockPolicy.LOCK_MS / 1000).toInt()
                        PatternCheck.TIMEOUT -> message = PatternAction.Show(RemoteStrings.PATTERN_TIMEOUT, wrong = true)
                        PatternCheck.WRONG -> message = PatternAction.Show(RemoteStrings.PATTERN_WRONG, wrong = true)
                    }
                    working = false
                }
            }
            is PatternAction.Submit -> {
                working = true
                scope.launch {
                    if (controller.submitPattern(action.oldPin, action.newPin)) {
                        onDone(if (mode == PatternMode.SET) RemoteStrings.PASSWORD_SET else RemoteStrings.PASSWORD_CHANGED)
                    } else message = PatternAction.Show(RemoteStrings.FAILED, wrong = true)
                    working = false
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            if (lockedSeconds > 0) "${RemoteStrings.PATTERN_LOCKED}. ${RemoteStrings.RETRY_AFTER.replace("%d", "$lockedSeconds")}" else message.message,
            style = MaterialTheme.typography.titleMedium,
            color = if (message.wrong || lockedSeconds > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        PatternPinInput(
            onPattern = ::handle,
            dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            activeColor = if (message.wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        if (working) CircularProgressIndicator()
        if (showsForgot) TextButton(onClick = onForgot) { Text(RemoteStrings.FORGOT_PASSWORD) }
    }
}

@Composable
private fun ConfirmDialog(title: String?, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(RemoteStrings.CANCEL) } },
    )
}

@Composable
private fun CenteredNote(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
