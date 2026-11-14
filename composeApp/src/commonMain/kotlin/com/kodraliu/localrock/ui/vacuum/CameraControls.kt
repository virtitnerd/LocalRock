package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/** The map in the corner with the quick actions that matter while watching. */
@Composable
fun CameraMiniMap(viewModel: VacuumViewModel, onPinGo: () -> Unit, modifier: Modifier = Modifier) {
    val map by viewModel.repository.parsedMap.collectAsState()
    val parsed = map ?: return
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
        Text(
            "Pin n Go",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(Color.White.copy(alpha = 0.18f))
                .clickable(onClick = onPinGo)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
