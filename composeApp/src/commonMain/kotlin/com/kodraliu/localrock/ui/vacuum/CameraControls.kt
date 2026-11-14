package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MAX_SPEED = 0.3
private const val MAX_TURN = 0.4
private const val DEAD_ZONE = 0.15f
private const val STEP_MS = 300L
private const val MOVE_MS = 700

/** A thumb stick: push up to drive, sideways to turn, harder for faster. Letting go stops the robot. */
@Composable
fun CameraJoystick(viewModel: VacuumViewModel, modifier: Modifier = Modifier) {
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()
    // Remote control stops a clean in progress, so it starts only when the stick is first used.
    val rc = remember { object { var started = false } }
    DisposableEffect(repository) {
        onDispose { if (rc.started) scope.launch { runCatching { repository.stopRemoteControl() } } }
    }

    var stick by remember { mutableStateOf(Offset.Zero) }
    val current by rememberUpdatedState(stick)
    LaunchedEffect(repository) {
        var moving = false
        while (true) {
            val s = current
            val vx = if (abs(s.x) < DEAD_ZONE) 0f else s.x
            val vy = if (abs(s.y) < DEAD_ZONE) 0f else s.y
            if (vx != 0f || vy != 0f) {
                moving = true
                if (!rc.started) {
                    rc.started = true
                    runCatching { repository.startRemoteControl() }
                }
                runCatching { repository.moveRemote(-vy * MAX_SPEED, -vx * MAX_TURN, MOVE_MS) }
            } else if (moving) {
                moving = false
                runCatching { repository.moveRemote(0.0, 0.0, 200) }
            }
            delay(STEP_MS)
        }
    }

    val size = 150.dp
    val thumb = 62.dp
    val travelPx = with(LocalDensity.current) { ((size - thumb) / 2).toPx() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .pointerInput(Unit) {
                val half = this.size.width / 2f
                fun toStick(p: Offset): Offset {
                    val v = Offset((p.x - half) / half, (p.y - half) / half)
                    val len = sqrt(v.x * v.x + v.y * v.y)
                    return if (len > 1f) Offset(v.x / len, v.y / len) else v
                }
                detectDragGestures(
                    onDragStart = { stick = toStick(it) },
                    onDragEnd = { stick = Offset.Zero },
                    onDragCancel = { stick = Offset.Zero },
                ) { change, _ ->
                    change.consume()
                    stick = toStick(change.position)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset { IntOffset((stick.x * travelPx).roundToInt(), (stick.y * travelPx).roundToInt()) }
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (stick == Offset.Zero) 0.55f else 0.85f)),
        )
    }
}

/** The map in the corner with what you can do from here: Pin n Go and the pet search, or pause, end and dock while the robot is busy. */
@Composable
fun CameraSidePanel(
    viewModel: VacuumViewModel,
    pinGoOpen: Boolean,
    onPinGoChange: (Boolean) -> Unit,
    onPet: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val map by viewModel.repository.parsedMap.collectAsState()
    val status by viewModel.repository.status.collectAsState()
    val parsed = map ?: return
    if (pinGoOpen) {
        PinGoPanel(viewModel, parsed, { onPinGoChange(false) }, modifier)
        return
    }
    val state = status.state
    val busy = state != null && state !in setOf(
        VacuumStateCodes.IDLE, VacuumStateCodes.NOT_CHARGING, VacuumStateCodes.CHARGING,
        VacuumStateCodes.CHARGING_COMPLETE, VacuumStateCodes.CHARGER_DISCONNECTED, VacuumStateCodes.SHUTTING_DOWN,
        // Driving by hand or a paused clean is not a running task: you can still send the robot somewhere.
        VacuumStateCodes.MANUAL_MODE, VacuumStateCodes.REMOTE_CONTROL_ACTIVE, VacuumStateCodes.PAUSED,
    )
    Column(
        modifier
            .width(168.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = rememberMapBitmap(parsed),
            contentDescription = "Map",
            modifier = Modifier.heightIn(max = 150.dp),
            contentScale = ContentScale.Fit,
        )
        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelButton("Pause", Modifier.weight(1f)) { viewModel.run { it.pause() } }
                PanelButton("End", Modifier.weight(1f)) { viewModel.run { it.stopCleaning() } }
            }
            PanelButton("Dock", Modifier.fillMaxWidth()) { viewModel.run { it.dock() } }
        } else {
            if (state == VacuumStateCodes.PAUSED) PanelButton("Resume", Modifier.fillMaxWidth()) { viewModel.run { it.resumeCleaning() } }
            PanelButton("Pin n Go", Modifier.fillMaxWidth()) { onPinGoChange(true) }
            if (onPet != null) PanelButton("Search for the Pet", Modifier.fillMaxWidth(), onPet)
        }
    }
}

@Composable
private fun PanelButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

/** Pick a spot in a room on a bigger map and send the robot there without leaving the camera. */
@Composable
private fun PinGoPanel(viewModel: VacuumViewModel, parsed: ParsedMap, onClose: () -> Unit, modifier: Modifier) {
    var tap by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val bitmap = rememberMapBitmap(parsed)
    Column(
        modifier
            .width(300.dp)
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = "Close",
            tint = Color.White,
            modifier = Modifier.align(Alignment.End).size(32.dp).clip(CircleShape).clickable(onClick = onClose).padding(6.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .aspectRatio(parsed.width.toFloat() / parsed.height.toFloat())
                    .onSizeChanged { size = it }
                    .pointerInput(parsed) {
                        detectTapGestures { offset ->
                            if (parsed.isRoomFloorAt(offset, size.width.toFloat(), size.height.toFloat())) tap = offset
                        }
                    },
            ) {
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
                    filterQuality = FilterQuality.None,
                )
                tap?.let { tp ->
                    drawCircle(color = Color(0xFFE53935).copy(alpha = 0.9f), radius = 20f, center = tp)
                    drawCircle(color = Color.White, radius = 8f, center = tp)
                }
            }
        }
        Text(
            "Go",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (tap != null) Color(0xFF1E6FFF) else Color.White.copy(alpha = 0.18f))
                .clickable(enabled = tap != null) {
                    val tp = tap ?: return@clickable
                    val (x, y) = parsed.canvasToMm(tp, size.width.toFloat(), size.height.toFloat())
                    viewModel.run { it.gotoTarget(x, y) }
                    onClose()
                }
                .padding(horizontal = 44.dp, vertical = 10.dp),
        )
    }
}
