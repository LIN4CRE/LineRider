package com.example.tools

import com.example.physics.LineType
import com.example.physics.TrackLine
import com.example.physics.Vector2D
import kotlin.math.*

enum class ToolMode(val label: String, val iconName: String) {
    FREEHAND("Freehand", "brush"),
    STRAIGHT("Ruler", "straighten"),
    CURVE("Curve Arc", "gesture"),
    ERASER("Eraser", "auto_fix_high"),
    PAN("Pan & Zoom", "pan_tool")
}

sealed class HistoryAction {
    data class AddLines(val lines: List<TrackLine>) : HistoryAction()
    data class RemoveLines(val lines: List<TrackLine>) : HistoryAction()
    data class Batch(val removed: List<TrackLine>, val added: List<TrackLine>) : HistoryAction()
}

class TrackHistory {
    private val undoStack = mutableListOf<HistoryAction>()
    private val redoStack = mutableListOf<HistoryAction>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun record(action: HistoryAction) {
        undoStack.add(action)
        redoStack.clear()
        if (undoStack.size > 150) {
            undoStack.removeAt(0)
        }
    }

    fun popUndo(): HistoryAction? {
        if (undoStack.isEmpty()) return null
        val action = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(action)
        return action
    }

    fun popRedo(): HistoryAction? {
        if (redoStack.isEmpty()) return null
        val action = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(action)
        return action
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

/**
 * Smoothing utility implementing Chaikin's corner-cutting algorithm
 * and Catmull-Rom spline interpolation for silky smooth physics tracks.
 */
object LineSmoother {
    fun smoothPoints(rawPoints: List<Vector2D>, iterations: Int = 1): List<Vector2D> {
        if (rawPoints.size < 3) return rawPoints

        var current = rawPoints
        for (it in 0 until iterations) {
            val smoothed = mutableListOf<Vector2D>()
            smoothed.add(current.first())

            for (i in 0 until current.size - 1) {
                val p0 = current[i]
                val p1 = current[i + 1]

                val q = Vector2D(0.75f * p0.x + 0.25f * p1.x, 0.75f * p0.y + 0.25f * p1.y)
                val r = Vector2D(0.25f * p0.x + 0.75f * p1.x, 0.25f * p0.y + 0.75f * p1.y)

                smoothed.add(q)
                smoothed.add(r)
            }

            smoothed.add(current.last())
            current = smoothed
        }
        return current
    }

    fun pointsToLines(points: List<Vector2D>, lineType: LineType, width: Float = 3.5f): List<TrackLine> {
        if (points.size < 2) return emptyList()
        val lines = mutableListOf<TrackLine>()
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            // Avoid zero-length lines
            if (p1.distanceTo(p2) > 1.5f) {
                lines.add(TrackLine(
                    x1 = p1.x,
                    y1 = p1.y,
                    x2 = p2.x,
                    y2 = p2.y,
                    type = lineType,
                    width = width
                ))
            }
        }
        return lines
    }

    /**
     * Generates a quadratic Bézier curve arc subdivided into line segments.
     */
    fun createBezierArc(
        start: Vector2D,
        control: Vector2D,
        end: Vector2D,
        segments: Int = 18,
        lineType: LineType
    ): List<TrackLine> {
        val points = mutableListOf<Vector2D>()
        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val u = 1f - t
            val x = u * u * start.x + 2f * u * t * control.x + t * t * end.x
            val y = u * u * start.y + 2f * u * t * control.y + t * t * end.y
            points.add(Vector2D(x, y))
        }
        return pointsToLines(points, lineType)
    }
}
