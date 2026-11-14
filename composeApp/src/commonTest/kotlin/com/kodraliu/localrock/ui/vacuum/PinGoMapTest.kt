package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinGoMapTest {

    // Same offsets as MapGeometryTest's captured sample: 439 x 516 cells, left 315, top 142.
    private val map = ParsedMap(
        width = 439, height = 516, grid = ByteArray(439 * 516), rooms = emptyList(), resolution = 0.05f,
        pixelOffsetLeft = 315, pixelOffsetTop = 142, format = MapFormat.LEGACY,
    )

    // Floor plan in the middle half of the grid, as contentBoundsNorm would report it.
    private val bounds = MapContentBounds(0.25f, 0.25f, 0.75f, 0.75f)

    private fun close(a: Offset, b: Offset) = abs(a.x - b.x) < 0.01f && abs(a.y - b.y) < 0.01f

    @Test
    fun the_screen_centre_is_the_centre_of_the_floor_plan() {
        val view = pinGoView(map, bounds, scale = 3, canvasW = 1080f, canvasH = 1800f)
        assertTrue(close(Offset(439 / 2f, 516 / 2f), pinCellAt(view, Offset(540f, 900f), 3)))
    }

    @Test
    fun the_floor_plan_fits_inside_the_canvas() {
        val view = pinGoView(map, bounds, scale = 2, canvasW = 1080f, canvasH = 1800f)
        val topLeft = pinScreenAt(view, Offset(439 * 0.25f, 516 * 0.25f), 2)
        val bottomRight = pinScreenAt(view, Offset(439 * 0.75f, 516 * 0.75f), 2)
        assertTrue(topLeft.x >= 0f && topLeft.y >= 0f)
        assertTrue(bottomRight.x <= 1080f && bottomRight.y <= 1800f)
    }

    @Test
    fun a_tap_reaches_the_robot_mm_of_the_cell_drawn_under_it() {
        val view = pinGoView(map, bounds, scale = 3, canvasW = 1080f, canvasH = 2000f)
        // The charger of the captured sample, drawn on screen, then tapped.
        val charger = map.mmToCell(25552, 24972)
        val tap = pinScreenAt(view, charger, 3)
        assertEquals(25552 to 24972, map.cellToMm(pinCellAt(view, tap, 3)))
    }
}
