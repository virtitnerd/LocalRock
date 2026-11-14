package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.ui.AppColors

// Share of the canvas the floor plan fills, leaving a margin so the pin is never cut by the edge.
private const val PIN_GO_FILL = 0.94f

/** Flat, north-up view framing the floor plan inside the canvas, built the way the device page frames it. */
internal fun pinGoView(map: ParsedMap, bounds: MapContentBounds, scale: Int, canvasW: Float, canvasH: Float): MapView {
    val b = if (bounds.isUsable) bounds else MapContentBounds(0f, 0f, 1f, 1f)
    val bmpW = map.width * scale.toFloat()
    val bmpH = map.height * scale.toFloat()
    val fit = fitScale(canvasW, canvasH, b.width * bmpW, b.height * bmpH, 0f, squash = 1f, freeTurning = false) * PIN_GO_FILL
    return framedView(
        canvasW = canvasW, canvasH = canvasH,
        centerX = (b.left + b.right) / 2f * bmpW, centerY = (b.top + b.bottom) / 2f * bmpH,
        fit = fit, zoom = 1f, pan = Offset.Zero, yawDegrees = 0f, squash = 1f,
    )
}

/** The map cell under a screen tap; feed it to [cellToMm] for app_goto_target. */
internal fun pinCellAt(view: MapView, tap: Offset, scale: Int): Offset = view.toBitmap(tap.x, tap.y) / scale.toFloat()

/** Where a pinned cell sits on screen. */
internal fun pinScreenAt(view: MapView, cell: Offset, scale: Int): Offset = view.toScreen(cell.x * scale, cell.y * scale)

/** Pin & Go map: the device page's layers and route drawn sharp and fitted to the screen; taps report a map cell. */
@Composable
internal fun PinGoMap(map: ParsedMap, pinCell: Offset?, onTapCell: (Offset) -> Unit, modifier: Modifier = Modifier) {
    val scale = remember(map.width, map.height) { supersampleFor(map) }
    val (floor, overlay) = rememberMapLayers(map, scale)
    val floorShapes = remember(map.floorKey()) { map.floorPlanShapes() }
    val bounds = remember(map.floorKey()) { map.contentBoundsNorm() }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF14202B), fontWeight = FontWeight.SemiBold)
    val currentMap by rememberUpdatedState(map)
    val currentOnTap by rememberUpdatedState(onTapCell)

    Canvas(
        modifier = modifier.clipToBounds().pointerInput(bounds, scale) {
            detectTapGestures { tap ->
                val view = pinGoView(currentMap, bounds, scale, size.width.toFloat(), size.height.toFloat())
                currentOnTap(pinCellAt(view, tap, scale))
            }
        },
    ) {
        val view = pinGoView(map, bounds, scale, size.width, size.height)
        val quality = if (view.k >= 2f) FilterQuality.None else FilterQuality.Low
        drawLayer(floor, view, quality)
        drawFloorPlanStrokes(floorShapes, view, scale)
        drawLayer(overlay, view, quality)
        drawRouteAndMarkers(map, view, scale, dark)
        // Room names at the live map's label points, shortened to fit; rooms hidden with Remove are skipped.
        for (room in map.rooms) {
            if (room.name.isBlank() || room.id in map.erasedRoomIds) continue
            val nx = room.labelNormX ?: continue
            val ny = room.labelNormY ?: continue
            val at = view.toScreen(nx * floor.width, ny * floor.height)
            val roomWidth = ((room.labelSpanCells ?: 0) * view.k * scale).toInt()
            val text = textMeasurer.measure(
                room.name, labelStyle, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
                constraints = Constraints(maxWidth = roomWidth.coerceAtLeast(48)),
            )
            drawText(text, topLeft = Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f))
        }
        pinCell?.let { cell ->
            val at = pinScreenAt(view, cell, scale)
            drawCircle(color = AppColors.AccentRed.copy(alpha = 0.85f), radius = 22f, center = at)
            drawCircle(color = Color.White, radius = 9f, center = at)
        }
    }
}

private fun DrawScope.drawLayer(image: ImageBitmap, view: MapView, quality: FilterQuality) {
    withTransform({ transform(view.layerMatrix(0f)) }) {
        val size = IntSize(image.width, image.height)
        drawImage(image, IntOffset.Zero, size, IntOffset.Zero, size, filterQuality = quality)
    }
}
