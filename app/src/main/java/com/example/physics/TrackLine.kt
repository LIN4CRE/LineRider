package com.example.physics

import androidx.compose.ui.graphics.Color
import kotlin.math.*

enum class LineType(
    val title: String,
    val description: String,
    val displayColor: Color,
    val isSolid: Boolean
) {
    RIDE("Ride", "Solid smooth line for sled to glide on", Color(0xFF00B4D8), true),
    BOOST("Boost", "Accelerates sledder forward in line direction", Color(0xFFFF3366), true),
    BOUNCE("Bounce", "Springy trampoline line with high bounce", Color(0xFFFF9E00), true),
    SCENERY("Scenery", "Decorative art line (no collisions)", Color(0xFF38B000), false),
    SLOW("Grip / Slow", "High-friction rough surface to brake", Color(0xFF9D4EDD), true)
}

data class TrackLine(
    val id: Long = System.nanoTime(),
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val type: LineType = LineType.RIDE,
    val width: Float = 3.5f
) {
    val length: Float by lazy {
        val dx = x2 - x1
        val dy = y2 - y1
        sqrt(dx * dx + dy * dy)
    }

    val tangent: Vector2D by lazy {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = max(length, 0.001f)
        Vector2D(dx / len, dy / len)
    }

    // Normal pointing perpendicular to tangent (points upwards in screen coordinates when line is drawn left-to-right)
    val normal: Vector2D by lazy {
        Vector2D(tangent.y, -tangent.x)
    }

    fun minX(): Float = min(x1, x2)
    fun maxX(): Float = max(x1, x2)
    fun minY(): Float = min(y1, y2)
    fun maxY(): Float = max(y1, y2)

    /**
     * Calculates distance from point (px, py) to this line segment.
     */
    fun distanceToPoint(px: Float, py: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val l2 = dx * dx + dy * dy
        if (l2 < 0.0001f) {
            val ex = px - x1
            val ey = py - y1
            return sqrt(ex * ex + ey * ey)
        }
        var t = ((px - x1) * dx + (py - y1) * dy) / l2
        t = t.coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        val rx = px - projX
        val ry = py - projY
        return sqrt(rx * rx + ry * ry)
    }

    /**
     * Finds projection of (px, py) onto this segment clamped to endpoints.
     */
    fun closestPointOnSegment(px: Float, py: Float): Vector2D {
        val dx = x2 - x1
        val dy = y2 - y1
        val l2 = dx * dx + dy * dy
        if (l2 < 0.0001f) return Vector2D(x1, y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / l2).coerceIn(0f, 1f)
        return Vector2D(x1 + t * dx, y1 + t * dy)
    }
}
