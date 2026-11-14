package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.startVoiceChat
import com.kodraliu.localrock.shared.vacuum.stopVoiceChat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.ui.graphics.vector.ImageVector
import com.kodraliu.localrock.ui.rememberMicrophonePermissionRequest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewModelScope
import com.kodraliu.localrock.shared.webrtc.RtcStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.camera.CameraLiveSession
import com.kodraliu.localrock.shared.webrtc.RtcVideoView
import com.kodraliu.localrock.shared.webrtc.liveViewSupported
import com.kodraliu.localrock.ui.LandscapeWhile
import com.kodraliu.localrock.ui.LocalAppContainer


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraLiveViewScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val duid = viewModel.repository.duid
    val liveSession = remember { viewModel.cameraSession() }
    val state by liveSession.state.collectAsState()
    val peer by liveSession.peer.collectAsState()
    val pinLockSeconds by liveSession.pinLockSeconds.collectAsState()

    var pin by remember { mutableStateOf(container.appSettings.cameraPin(duid) ?: "") }
    var autoStarted by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(liveSession.quality) }
    var micOn by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<CameraPanel?>(null) }
    var gearOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    val robotStatus by viewModel.repository.status.collectAsState()
    val petSearch by viewModel.repository.petSearchActive.collectAsState()
    val scope = rememberCoroutineScope()
    val askMic = rememberMicrophonePermissionRequest { granted ->
        if (granted) {
            micOn = true
            peer?.setMicEnabled(true)
            scope.launch { runCatching { viewModel.repository.session.startVoiceChat(play = true, record = true) } }
        }
    }

    LandscapeWhile(liveViewSupported)

    DisposableEffect(Unit) {
        viewModel.repository.setLiveMapRate(true)
        onDispose { viewModel.repository.setLiveMapRate(false) }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (micOn) viewModel.viewModelScope.launch { runCatching { viewModel.repository.session.stopVoiceChat() } }
            viewModel.closeCamera()
        }
    }

    // If a PIN is already saved, connect immediately.
    LaunchedEffect(Unit) {
        if (liveViewSupported && pin.isNotBlank() && !autoStarted && liveSession.state.value is CameraLiveSession.State.Idle) {
            autoStarted = true
            liveSession.start(pin)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0),
    ) { _ ->
        Box(
            // Black runs under the navigation bar too; only the video area is inset.
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (!liveViewSupported) {
                CameraMessage(
                    icon = { Icon(Icons.Default.VideocamOff, null, Modifier.size(48.dp), tint = Color.White) },
                    text = "Camera live view isn't available on this platform yet.",
                )
                return@Box
            }

            if (state !is CameraLiveSession.State.Streaming) Text(
                "BETA: work in progress, may not work on every robot",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 8.dp)
                    .background(Color(0xFFB26A00), MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )

            when (val s = state) {
                is CameraLiveSession.State.Idle -> {
                    LaunchedEffect(pinLockSeconds > 0) { if (pinLockSeconds > 0) pin = "" }
                    PinEntry(
                        pin = pin,
                        lockSeconds = pinLockSeconds,
                        onPinChange = { pin = it },
                        onConnect = {
                            container.appSettings.setCameraPin(duid, pin)
                            liveSession.start(pin)
                        },
                        onSetPinOnRobot = {
                            container.appSettings.setCameraPin(duid, pin)
                            liveSession.setPinAndStart(pin)
                        },
                    )
                }

                is CameraLiveSession.State.Connecting -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text(s.stage, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is CameraLiveSession.State.Activating -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (s.twoKey) "Press the Power button on the robot now."
                            else "Press and hold the Dock, Power and Mop Only buttons on the robot together (Spot Cleaning instead of Mop Only on older models).",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Remote viewing turns on when the robot speaks. ${s.secondsLeft}s left.",
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                is CameraLiveSession.State.Streaming -> {
                    peer?.let { RtcVideoView(it, Modifier.fillMaxSize()) }
                    Box(
                        Modifier.fillMaxSize().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { controlsVisible = !controlsVisible },
                    )
                    if (controlsVisible) CameraJoystick(
                        viewModel,
                        Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 24.dp, bottom = 24.dp),
                    )
                    if (controlsVisible) CameraSidePanel(
                        viewModel,
                        panel,
                        { panel = it },
                        if (viewModel.supportsPetSearch) {
                            { viewModel.run("Looking for your pet") { it.startPetSearch() } }
                        } else null,
                        viewModel.supportsPatrol,
                        Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 12.dp, end = 12.dp, bottom = 12.dp),
                    )
                    val stats by (peer?.stats ?: noStats).collectAsState()
                    if (controlsVisible) Column(
                        Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    Text(
                        when {
                            robotStatus.state == VacuumStateCodes.PATROLLING && (robotStatus.isPetPatrol || petSearch) -> "Searching for pets"
                            else -> robotStatus.state?.let { VacuumStateCodes.describe(it) } ?: ""
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    StatsChip(
                        stats,
                        quality,
                        onQuality = { q ->
                            quality = q
                            liveSession.restartWithQuality(pin, q)
                        },
                        modifier = Modifier,
                    )
                    }
                }

                is CameraLiveSession.State.Failed -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(Icons.Default.VideocamOff, null, Modifier.size(48.dp), tint = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            s.message,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (s.canActivate) {
                            Button(onClick = { liveSession.activate(pin, viewModel.twoKeyVideo) }) { Text("Activate remote viewing") }
                        } else {
                            Button(onClick = { liveSession.start(pin) }) { Text("Retry") }
                        }
                        TextButton(onClick = {
                            container.appSettings.setCameraPin(duid, null)
                            pin = ""
                            liveSession.stop()
                        }) { Text("Change PIN", color = Color.White) }
                    }
                }
            }

            if (gearOpen) CallSettingsDialog(viewModel) { gearOpen = false }

            if (controlsVisible || state !is CameraLiveSession.State.Streaming) Row(
                Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 8.dp, start = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "Go back", Color.Black.copy(alpha = 0.45f), onBack)
                if (state is CameraLiveSession.State.Streaming) {
                    RoundButton(Icons.Default.Settings, "Call settings", Color.Black.copy(alpha = 0.45f)) { gearOpen = true }
                    RoundButton(
                        if (micOn) Icons.Default.CallEnd else Icons.Default.Call,
                        if (micOn) "Stop talking" else "Talk to the robot",
                        if (micOn) Color(0xFFD32F2F) else Color(0xFF2E9E4F),
                    ) {
                        if (micOn) {
                            micOn = false
                            peer?.setMicEnabled(false)
                            scope.launch { runCatching { viewModel.repository.session.stopVoiceChat() } }
                        } else {
                            askMic()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallSettingsDialog(viewModel: VacuumViewModel, onClose: () -> Unit) {
    var volume by remember { mutableStateOf(50f) }
    val scope = rememberCoroutineScope()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Call volume") },
        text = {
            Column {
                Text("How loud the robot's speaker is during a call.", style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..100f,
                    onValueChangeFinished = {
                        scope.launch { runCatching { viewModel.repository.setCallVolume(volume.toInt()) } }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, background: Color, onClick: () -> Unit) {
    Icon(
        icon,
        contentDescription = description,
        tint = Color.White,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(10.dp),
    )
}

@Composable
private fun PinEntry(
    pin: String,
    lockSeconds: Int,
    onPinChange: (String) -> Unit,
    onConnect: () -> Unit,
    onSetPinOnRobot: () -> Unit,
) {
    // Newer models draw the camera PIN as a pattern; older ones type it. Default to drawing and
    // keep the keypad as a fallback so the S8-era flow still works.
    var typed by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            if (typed) {
                "The live view is protected by a camera PIN stored on the robot. " +
                    "Enter your existing PIN, or pick a new one and register it on the robot."
            } else {
                "The live view is protected by a camera PIN stored on the robot. " +
                    "Draw your existing pattern, or draw a new one and register it on the robot."
            },
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (lockSeconds > 0) {
            Text(
                "Too many wrong attempts. Retry after ${lockSeconds}s",
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
            )
        } else if (typed) {
            OutlinedTextField(
                value = pin,
                onValueChange = onPinChange,
                label = { Text("Camera PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PatternPinInput(onPattern = onPinChange)
            Text(
                if (pin.isBlank()) "Draw a pattern of at least 4 dots" else "Pattern captured",
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = onConnect, enabled = pin.isNotBlank() && lockSeconds == 0) { Text("Start live view") }
        TextButton(onClick = onSetPinOnRobot, enabled = pin.isNotBlank() && lockSeconds == 0) {
            Text("Set as new PIN on robot", color = Color.White)
        }
        TextButton(
            onClick = {
                typed = !typed
                onPinChange("")
            },
        ) {
            Text(
                if (typed) "Draw a pattern instead" else "Type a PIN instead",
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun CameraMessage(icon: @Composable () -> Unit, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        icon()
        Spacer(Modifier.height(12.dp))
        Text(text, color = Color.White, textAlign = TextAlign.Center)
    }
}

private val noStats = MutableStateFlow<RtcStats?>(null)

@Composable
private fun StatsChip(stats: RtcStats?, quality: String, onQuality: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val info = stats?.let {
        listOfNotNull(
            if (it.relayed) "Relay" else "Direct",
            it.height?.takeIf { h -> h > 0 }?.let { h -> "${h}p" },
            it.fps?.takeIf { f -> f > 0 }?.let { f -> "${f}fps" },
            it.bufferMs?.let { b -> "buf ${b}ms" },
            it.rttMs?.let { r -> "rtt ${r}ms" },
        ).joinToString(" | ")
    }
    Box(modifier) {
        Text(
            listOfNotNull(quality, info).joinToString(" | "),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf("SD", "HD", "FHD", "AUTO").forEach { q ->
                DropdownMenuItem(text = { Text(q) }, onClick = { open = false; onQuality(q) })
            }
        }
    }
}
