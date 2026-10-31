package com.kodraliu.localrock.ui.vacuum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DEFAULT_FIX_INTERVAL_MS = 3_000L
private const val MIN_GLIDE_MS = 600L
private const val MAX_GLIDE_MS = 6_000L

/** Further than this between two fixes (mm) is a new map or a lifted robot, not driving, so it is not glided. */
private const val MAX_GLIDE_MM = 2_500

/** The point [t] (0 to 1) of the way from [from] to [to], turning the short way round for the angle. */
internal fun glide(from: ParsedMapPoint, to: ParsedMapPoint, t: Float): ParsedMapPoint {
    var turn = (to.angle - from.angle) % 360
    if (turn > 180) turn -= 360
    if (turn < -180) turn += 360
    return ParsedMapPoint(
        x = from.x + ((to.x - from.x) * t).roundToInt(),
        y = from.y + ((to.y - from.y) * t).roundToInt(),
        angle = from.angle + (turn * t).roundToInt(),
    )
}

internal fun glideDurationMs(sinceLastFixMs: Long?): Long =
    (sinceLastFixMs ?: DEFAULT_FIX_INTERVAL_MS).coerceIn(MIN_GLIDE_MS, MAX_GLIDE_MS)

internal fun isJump(from: ParsedMapPoint, to: ParsedMapPoint): Boolean =
    abs(to.x - from.x) > MAX_GLIDE_MM || abs(to.y - from.y) > MAX_GLIDE_MM

/**
 * The robot's position is only known when a map arrives, a few seconds apart. This glides the drawn
 * robot from one fix to the next, over about the time the fixes were apart, so it drives across the map
 * instead of jumping. Only a changing position costs frames; a parked robot costs none.
 */
@Composable
internal fun rememberGlidingRobot(fix: ParsedMapPoint?): MutableState<ParsedMapPoint?> {
    val shown = remember { mutableStateOf(fix) }
    val clock = remember { LongArray(1) }
    LaunchedEffect(fix) {
        val to = fix
        val from = shown.value
        val arrived = withFrameNanos { it } / 1_000_000L
        val since = clock[0].takeIf { it > 0L }?.let { arrived - it }
        clock[0] = arrived
        if (to == null || from == null || from == to || isJump(from, to)) {
            shown.value = to
            return@LaunchedEffect
        }
        val duration = glideDurationMs(since)
        while (true) {
            val t = ((withFrameNanos { it } / 1_000_000L - arrived).toFloat() / duration).coerceIn(0f, 1f)
            shown.value = glide(from, to, t)
            if (t >= 1f) break
        }
    }
    return shown
}
