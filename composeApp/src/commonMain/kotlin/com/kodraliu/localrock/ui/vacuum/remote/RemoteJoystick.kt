package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

// Stick tuning shared by the camera and the Remote Control page; values unchanged from the camera joystick.
object JoystickDrive {
    const val MAX_SPEED = 0.3
    const val MAX_TURN = 0.4
    const val DEAD_ZONE = 0.15f
    // The stick is read every 25 ms and a changed command goes out at once; an unchanged one repeats every 200 ms,
    // well inside the 1500 ms the robot keeps moving, so one late command does not stop it.
    const val POLL_MS = 25L
    const val REPEAT_MS = 200L
    const val MOVE_MS = 1500
    const val CHANGE_STEP = 0.01

    /** Velocity and omega for a stick position (-1..1 per axis, y down), or null inside the dead zone. */
    fun command(x: Float, y: Float): Pair<Double, Double>? {
        val vx = if (abs(x) < DEAD_ZONE) 0f else x
        val vy = if (abs(y) < DEAD_ZONE) 0f else y
        if (vx == 0f && vy == 0f) return null
        return (-vy * MAX_SPEED) to (-vx * MAX_TURN)
    }

    fun changed(velocity: Double, omega: Double, lastVelocity: Double, lastOmega: Double): Boolean =
        abs(velocity - lastVelocity) >= CHANGE_STEP * MAX_SPEED || abs(omega - lastOmega) >= CHANGE_STEP * MAX_TURN
}

/** One remote control session: started lazily on first use (it stops a clean in progress), ended with app_rc_end. */
class RemoteControlLink(private val repository: VacuumRepository, private val endScope: CoroutineScope) {
    var started = false
        private set

    suspend fun ensureStarted() {
        if (started) return
        started = true
        runCatching { repository.startRemoteControl() }
    }

    /** False when the robot answered "action locked"; the caller then sends app_rc_start again. */
    suspend fun move(velocity: Double, omega: Double, durationMs: Int): Boolean =
        runCatching { repository.moveRemote(velocity, omega, durationMs) }.getOrDefault(true)

    suspend fun restart() { runCatching { repository.startRemoteControl() } }

    suspend fun stopMove() { runCatching { repository.stopRemoteMove() } }

    // Runs in a scope that outlives the screen, so leaving the page still sends app_rc_end.
    fun end() {
        if (!started) return
        started = false
        endScope.launch { runCatching { repository.stopRemoteControl() } }
    }
}

/** A link that ends remote control when the calling screen leaves the composition. */
@Composable
fun rememberRemoteControlLink(viewModel: VacuumViewModel): RemoteControlLink {
    val link = remember(viewModel) { RemoteControlLink(viewModel.repository, viewModel.viewModelScope) }
    DisposableEffect(link) { onDispose { link.end() } }
    return link
}

data class JoystickColors(val base: Color, val border: Color, val thumbIdle: Color, val thumbActive: Color) {
    companion object {
        val OverVideo = JoystickColors(
            base = Color.Black.copy(alpha = 0.35f),
            border = Color.White.copy(alpha = 0.35f),
            thumbIdle = Color.White.copy(alpha = 0.55f),
            thumbActive = Color.White.copy(alpha = 0.85f),
        )
    }
}

/** A thumb stick: push up to drive, sideways to turn, harder for faster. Letting go stops the robot. */
@Composable
fun RemoteJoystick(
    link: RemoteControlLink,
    modifier: Modifier = Modifier,
    colors: JoystickColors = JoystickColors.OverVideo,
    size: Dp = 150.dp,
    thumb: Dp = 62.dp,
) {
    val scope = rememberCoroutineScope()
    var stick by remember { mutableStateOf(Offset.Zero) }
    val current by rememberUpdatedState(stick)
    LaunchedEffect(link) {
        var moving = false
        var inFlight = false
        var lastVelocity = 0.0
        var lastOmega = 0.0
        var lastSent = TimeSource.Monotonic.markNow()
        while (true) {
            val s = current
            val command = JoystickDrive.command(s.x, s.y)
            if (command != null) {
                moving = true
                link.ensureStarted()
                val (velocity, omega) = command
                val changed = JoystickDrive.changed(velocity, omega, lastVelocity, lastOmega)
                val due = lastSent.elapsedNow() >= JoystickDrive.REPEAT_MS.milliseconds
                // Latest wins: while a send waits for its reply the next tick picks up the newest stick position.
                if (!inFlight && (changed || due)) {
                    inFlight = true
                    lastVelocity = velocity
                    lastOmega = omega
                    lastSent = TimeSource.Monotonic.markNow()
                    scope.launch {
                        try {
                            if (!link.move(velocity, omega, JoystickDrive.MOVE_MS)) link.restart()
                        } finally {
                            inFlight = false
                        }
                    }
                }
            } else if (moving) {
                // Letting go stops the robot on this tick, whatever is still waiting for a reply.
                moving = false
                lastVelocity = 0.0
                lastOmega = 0.0
                scope.launch { link.stopMove() }
            }
            delay(JoystickDrive.POLL_MS)
        }
    }

    val travelPx = with(LocalDensity.current) { ((size - thumb) / 2).toPx() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.base)
            .border(2.dp, colors.border, CircleShape)
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
                .background(if (stick == Offset.Zero) colors.thumbIdle else colors.thumbActive),
        )
    }
}
