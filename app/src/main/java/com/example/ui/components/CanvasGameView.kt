package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import com.example.physics.LineType
import com.example.physics.TrackLine
import com.example.physics.Vector2D
import com.example.tools.LineSmoother
import com.example.tools.ToolMode
import com.example.ui.LineRiderUiState
import com.example.ui.LineRiderViewModel
import kotlin.math.*

@Composable
fun CanvasGameView(
    viewModel: LineRiderViewModel,
    uiState: LineRiderUiState,
    modifier: Modifier = Modifier
) {
    val cameraX by viewModel.cameraOffsetX.collectAsState()
    val cameraY by viewModel.cameraOffsetY.collectAsState()
    val zoom by viewModel.cameraZoom.collectAsState()

    // Transient drawing state
    var currentTouchWorld by remember { mutableStateOf<Vector2D?>(null) }
    val freehandPoints = remember { mutableStateListOf<Vector2D>() }
    var straightStart by remember { mutableStateOf<Vector2D?>(null) }
    var straightEnd by remember { mutableStateOf<Vector2D?>(null) }
    var isDraggingStartFlag by remember { mutableStateOf(false) }

    fun screenToWorld(screenOffset: Offset, canvasWidth: Float, canvasHeight: Float): Vector2D {
        val cx = canvasWidth * 0.5f
        val cy = canvasHeight * 0.5f
        val wx = (screenOffset.x - cx) / zoom + cameraX
        val wy = (screenOffset.y - cy) / zoom + cameraY
        return if (uiState.snapToGrid) {
            val gridStep = 25f
            Vector2D(
                (wx / gridStep).roundToInt() * gridStep,
                (wy / gridStep).roundToInt() * gridStep
            )
        } else {
            Vector2D(wx, wy)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // Transform gesture for 2-finger zoom and pan
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    if (pan != Offset.Zero || gestureZoom != 1f) {
                        viewModel.cameraOffsetX.value -= pan.x / viewModel.cameraZoom.value
                        viewModel.cameraOffsetY.value -= pan.y / viewModel.cameraZoom.value
                        val newZoom = (viewModel.cameraZoom.value * gestureZoom).coerceIn(0.25f, 3.5f)
                        viewModel.cameraZoom.value = newZoom
                    }
                }
            }
            // Drag gesture for 1-finger drawing / erasing / start flag drag
            .pointerInput(uiState.toolMode, uiState.isPlaying) {
                if (uiState.isPlaying) return@pointerInput

                detectDragGestures(
                    onDragStart = { offset ->
                        val worldPos = screenToWorld(offset, size.width.toFloat(), size.height.toFloat())
                        currentTouchWorld = worldPos

                        // Check if touching start flag (within 35 units)
                        val distToStart = hypot(worldPos.x - uiState.startX, worldPos.y - uiState.startY)
                        if (distToStart < 35f) {
                            isDraggingStartFlag = true
                            return@detectDragGestures
                        }

                        when (uiState.toolMode) {
                            ToolMode.FREEHAND -> {
                                freehandPoints.clear()
                                freehandPoints.add(worldPos)
                            }
                            ToolMode.STRAIGHT -> {
                                straightStart = worldPos
                                straightEnd = worldPos
                            }
                            ToolMode.CURVE -> {
                                straightStart = worldPos
                                straightEnd = worldPos
                            }
                            ToolMode.ERASER -> {
                                viewModel.eraseLinesAt(worldPos, uiState.eraserRadius)
                            }
                            ToolMode.PAN -> {
                                // Pan handled by drag updates
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val worldPos = screenToWorld(change.position, size.width.toFloat(), size.height.toFloat())
                        currentTouchWorld = worldPos

                        if (isDraggingStartFlag) {
                            viewModel.setStartPosition(worldPos.x, worldPos.y)
                            return@detectDragGestures
                        }

                        when (uiState.toolMode) {
                            ToolMode.FREEHAND -> {
                                if (freehandPoints.isEmpty() || freehandPoints.last().distanceTo(worldPos) > 6f) {
                                    freehandPoints.add(worldPos)
                                }
                            }
                            ToolMode.STRAIGHT -> {
                                straightEnd = worldPos
                            }
                            ToolMode.CURVE -> {
                                straightEnd = worldPos
                            }
                            ToolMode.ERASER -> {
                                viewModel.eraseLinesAt(worldPos, uiState.eraserRadius)
                            }
                            ToolMode.PAN -> {
                                viewModel.cameraOffsetX.value -= dragAmount.x / zoom
                                viewModel.cameraOffsetY.value -= dragAmount.y / zoom
                            }
                        }
                    },
                    onDragEnd = {
                        if (isDraggingStartFlag) {
                            isDraggingStartFlag = false
                            currentTouchWorld = null
                            return@detectDragGestures
                        }

                        when (uiState.toolMode) {
                            ToolMode.FREEHAND -> {
                                if (freehandPoints.size >= 2) {
                                    val smoothed = LineSmoother.smoothPoints(freehandPoints.toList(), iterations = 2)
                                    val newLines = LineSmoother.pointsToLines(smoothed, uiState.lineType)
                                    viewModel.onAddLines(newLines)
                                }
                                freehandPoints.clear()
                            }
                            ToolMode.STRAIGHT -> {
                                val s = straightStart
                                val e = straightEnd
                                if (s != null && e != null && s.distanceTo(e) > 8f) {
                                    val line = TrackLine(x1 = s.x, y1 = s.y, x2 = e.x, y2 = e.y, type = uiState.lineType)
                                    viewModel.onAddLines(listOf(line))
                                }
                                straightStart = null
                                straightEnd = null
                            }
                            ToolMode.CURVE -> {
                                val s = straightStart
                                val e = straightEnd
                                if (s != null && e != null && s.distanceTo(e) > 15f) {
                                    // Default arc bowing downward slightly or based on drag
                                    val mid = (s + e) * 0.5f
                                    val normal = (e - s).perpendicular().normalized() * (s.distanceTo(e) * 0.35f)
                                    val control = mid + normal
                                    val curveLines = LineSmoother.createBezierArc(s, control, e, 14, uiState.lineType)
                                    viewModel.onAddLines(curveLines)
                                }
                                straightStart = null
                                straightEnd = null
                            }
                            else -> {}
                        }
                        currentTouchWorld = null
                    },
                    onDragCancel = {
                        freehandPoints.clear()
                        straightStart = null
                        straightEnd = null
                        isDraggingStartFlag = false
                        currentTouchWorld = null
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        val cx = width * 0.5f
        val cy = height * 0.5f

        // 1. Draw Crisp Dark Canvas Background
        drawRect(Color(0xFF0B132B))

        // 2. Camera Transformations (translate & scale)
        val matrix = Matrix()
        matrix.translate(cx, cy)
        matrix.scale(zoom, zoom)
        matrix.translate(-cameraX, -cameraY)

        // Calculate visible world bounds
        val leftWorld = (0f - cx) / zoom + cameraX
        val rightWorld = (width - cx) / zoom + cameraX
        val topWorld = (0f - cy) / zoom + cameraY
        val bottomWorld = (height - cy) / zoom + cameraY

        // 3. Optional Precision Blueprint Grid
        if (uiState.showGrid) {
            drawBlueprintGrid(leftWorld, rightWorld, topWorld, bottomWorld, cx, cy, cameraX, cameraY, zoom)
        }

        // Draw World Elements
        // Helper to convert world point to screen point
        fun worldToScreen(wx: Float, wy: Float): Offset {
            return Offset(
                cx + (wx - cameraX) * zoom,
                cy + (wy - cameraY) * zoom
            )
        }

        // 4. Draw Saved Track Lines
        for (line in uiState.lines) {
            // Frustum culling
            if (line.maxX() < leftWorld || line.minX() > rightWorld ||
                line.maxY() < topWorld || line.minY() > bottomWorld
            ) {
                continue
            }

            val p1Screen = worldToScreen(line.x1, line.y1)
            val p2Screen = worldToScreen(line.x2, line.y2)

            val lineColor = line.type.displayColor
            val strokeW = line.width * zoom

            drawLine(
                color = lineColor,
                start = p1Screen,
                end = p2Screen,
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // Accent indicators for special line types
            if (line.type == LineType.BOOST && line.length > 20f) {
                // Draw small directional arrow/notch along boost line
                val midX = (line.x1 + line.x2) * 0.5f
                val midY = (line.y1 + line.y2) * 0.5f
                val norm = line.normal * 6f
                val nScreen = worldToScreen(midX + norm.x, midY + norm.y)
                val mScreen = worldToScreen(midX, midY)
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = mScreen,
                    end = nScreen,
                    strokeWidth = 2f * zoom,
                    cap = StrokeCap.Round
                )
            } else if (line.type == LineType.BOUNCE && line.length > 18f) {
                // Springy dots along bounce line
                val midScreen = worldToScreen((line.x1 + line.x2) * 0.5f, (line.y1 + line.y2) * 0.5f)
                drawCircle(Color.White.copy(alpha = 0.8f), radius = 2.5f * zoom, center = midScreen)
            }
        }

        // 5. Draw In-Progress Freehand Trail
        if (freehandPoints.size >= 2) {
            val path = Path()
            val firstScreen = worldToScreen(freehandPoints[0].x, freehandPoints[0].y)
            path.moveTo(firstScreen.x, firstScreen.y)
            for (i in 1 until freehandPoints.size) {
                val s = worldToScreen(freehandPoints[i].x, freehandPoints[i].y)
                path.lineTo(s.x, s.y)
            }
            drawPath(
                path = path,
                color = uiState.lineType.displayColor.copy(alpha = 0.85f),
                style = Stroke(width = 3.5f * zoom, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // 6. Draw In-Progress Straight Ruler Line
        val rStart = straightStart
        val rEnd = straightEnd
        if (rStart != null && rEnd != null) {
            val s1 = worldToScreen(rStart.x, rStart.y)
            val s2 = worldToScreen(rEnd.x, rEnd.y)

            drawLine(
                color = uiState.lineType.displayColor,
                start = s1,
                end = s2,
                strokeWidth = 3.5f * zoom,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                cap = StrokeCap.Round
            )

            // Endpoint markers
            drawCircle(Color.White, radius = 5f * zoom, center = s1)
            drawCircle(uiState.lineType.displayColor, radius = 6f * zoom, center = s2)
        }

        // 7. Draw Eraser Cursor
        if (uiState.toolMode == ToolMode.ERASER && currentTouchWorld != null) {
            val cur = currentTouchWorld!!
            val curScreen = worldToScreen(cur.x, cur.y)
            drawCircle(
                color = Color(0x33FF1744),
                radius = uiState.eraserRadius * zoom,
                center = curScreen
            )
            drawCircle(
                color = Color(0xFFFF1744),
                radius = uiState.eraserRadius * zoom,
                center = curScreen,
                style = Stroke(width = 1.5f)
            )
        }

        // 8. Draw Start Flag & Draggable Spawn Marker
        val startScreen = worldToScreen(uiState.startX, uiState.startY)
        drawStartGate(startScreen, uiState.startAngle, zoom)

        // 9. Draw Line Rider Sledder
        drawSledder(viewModel.sledder, ::worldToScreen, zoom, uiState.isCrashed)
    }
}

/**
 * Draws the blueprint grid with axes and coordinates for precision track design.
 */
private fun DrawScope.drawBlueprintGrid(
    left: Float, right: Float, top: Float, bottom: Float,
    cx: Float, cy: Float, camX: Float, camY: Float, zoom: Float
) {
    val step = 50f
    val startX = (floor(left / step) * step)
    val endX = (ceil(right / step) * step)
    val startY = (floor(top / step) * step)
    val endY = (ceil(bottom / step) * step)

    // Minor grid lines
    var x = startX
    while (x <= endX) {
        val sx = cx + (x - camX) * zoom
        val isMajor = (x.toInt() % 200 == 0)
        drawLine(
            color = if (isMajor) Color(0x2238BDF8) else Color(0x1038BDF8),
            start = Offset(sx, 0f),
            end = Offset(sx, size.height),
            strokeWidth = if (isMajor) 1.2f else 0.8f
        )
        x += step
    }

    var y = startY
    while (y <= endY) {
        val sy = cy + (y - camY) * zoom
        val isMajor = (y.toInt() % 200 == 0)
        drawLine(
            color = if (isMajor) Color(0x2238BDF8) else Color(0x1038BDF8),
            start = Offset(0f, sy),
            end = Offset(size.width, sy),
            strokeWidth = if (isMajor) 1.2f else 0.8f
        )
        y += step
    }
}

/**
 * Renders the Line Rider Start Gate & Checkered Flag.
 */
private fun DrawScope.drawStartGate(pos: Offset, angle: Float, zoom: Float) {
    val r = 18f * zoom

    // Glow ring
    drawCircle(
        color = Color(0x3300E676),
        radius = r * 1.5f,
        center = pos
    )

    // Outer circle
    drawCircle(
        color = Color(0xFF00E676),
        radius = r,
        center = pos,
        style = Stroke(width = 2.5f * zoom)
    )

    // Start flag pole & checkered banner
    val flagPoleBottom = pos
    val flagPoleTop = Offset(pos.x, pos.y - 32f * zoom)
    drawLine(
        color = Color.White,
        start = flagPoleBottom,
        end = flagPoleTop,
        strokeWidth = 2.5f * zoom
    )

    // Triangle flag
    val flagPath = Path().apply {
        moveTo(flagPoleTop.x, flagPoleTop.y)
        lineTo(flagPoleTop.x + 22f * zoom, flagPoleTop.y + 9f * zoom)
        lineTo(flagPoleTop.x, flagPoleTop.y + 18f * zoom)
        close()
    }
    drawPath(flagPath, Color(0xFF00E676))

    // Sled launch arrow indicator
    val dirX = cos(angle) * 26f * zoom
    val dirY = sin(angle) * 26f * zoom
    drawLine(
        color = Color.Yellow,
        start = pos,
        end = Offset(pos.x + dirX, pos.y + dirY),
        strokeWidth = 2.5f * zoom,
        cap = StrokeCap.Round
    )
}

/**
 * Renders the sledder character: sled runners, chassis, rider body, helmet, and dynamic scarf cloth!
 */
private fun DrawScope.drawSledder(
    sledder: com.example.physics.Sledder,
    toScreen: (Float, Float) -> Offset,
    zoom: Float,
    isCrashed: Boolean
) {
    val p0 = toScreen(sledder.p0.x, sledder.p0.y)
    val p1 = toScreen(sledder.p1.x, sledder.p1.y)
    val p2 = toScreen(sledder.p2.x, sledder.p2.y)
    val p3 = toScreen(sledder.p3.x, sledder.p3.y)

    // 1. Sled Runner Blade (Metal ski connecting P0 to P1 with upturned tip)
    val dx = p1.x - p0.x
    val dy = p1.y - p0.y
    val len = hypot(dx, dy)
    val nx = if (len > 0.001f) -dy / len else 0f
    val ny = if (len > 0.001f) dx / len else -1f

    // Tip curl
    val tipCurl = Offset(p1.x + (dx / len) * 4f * zoom + nx * 5f * zoom, p1.y + (dy / len) * 4f * zoom + ny * 5f * zoom)

    val runnerPath = Path().apply {
        moveTo(p0.x, p0.y)
        lineTo(p1.x, p1.y)
        lineTo(tipCurl.x, tipCurl.y)
    }
    drawLine(
        color = Color(0xFFE2E8F0),
        start = p0,
        end = p1,
        strokeWidth = 3.2f * zoom,
        cap = StrokeCap.Round
    )
    drawPath(
        path = runnerPath,
        color = Color(0xFFCBD5E1),
        style = Stroke(width = 3f * zoom, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // 2. Wooden Sled Top Board
    val boardP0 = Offset(p0.x + nx * 5f * zoom, p0.y + ny * 5f * zoom)
    val boardP1 = Offset(p1.x + nx * 5f * zoom, p1.y + ny * 5f * zoom)
    drawLine(
        color = Color(0xFFF59E0B), // Warm wood sled
        start = boardP0,
        end = boardP1,
        strokeWidth = 2.8f * zoom,
        cap = StrokeCap.Round
    )

    // Sled vertical struts
    val strut1B = Offset(p0.x + dx * 0.25f, p0.y + dy * 0.25f)
    val strut1T = Offset(boardP0.x + dx * 0.25f, boardP0.y + dy * 0.25f)
    val strut2B = Offset(p0.x + dx * 0.75f, p0.y + dy * 0.75f)
    val strut2T = Offset(boardP0.x + dx * 0.75f, boardP0.y + dy * 0.75f)
    drawLine(Color(0xFF94A3B8), strut1B, strut1T, strokeWidth = 2f * zoom)
    drawLine(Color(0xFF94A3B8), strut2B, strut2T, strokeWidth = 2f * zoom)

    // 3. Dynamic Red Fluttering Scarf
    if (sledder.scarfNodes.isNotEmpty()) {
        val scarfPath = Path()
        val s0 = toScreen(sledder.scarfNodes[0].x, sledder.scarfNodes[0].y)
        scarfPath.moveTo(s0.x, s0.y)
        for (i in 1 until sledder.scarfCount) {
            val sn = toScreen(sledder.scarfNodes[i].x, sledder.scarfNodes[i].y)
            scarfPath.lineTo(sn.x, sn.y)
        }
        drawPath(
            path = scarfPath,
            color = Color(0xFFFF1744), // Classic Line Rider red scarf!
            style = Stroke(width = 3.2f * zoom, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // 4. Rider Body & Limbs
    // Torso (P2 to P3)
    drawLine(
        color = Color.White,
        start = p2,
        end = p3,
        strokeWidth = 3.8f * zoom,
        cap = StrokeCap.Round
    )

    if (!isCrashed) {
        // Arms holding front of sled
        val armMid = Offset((p3.x + p1.x) * 0.5f, (p3.y + p1.y) * 0.5f)
        drawLine(Color.White, p3, boardP1, strokeWidth = 2.4f * zoom, cap = StrokeCap.Round)

        // Legs on sled board
        drawLine(Color.White, p2, boardP0, strokeWidth = 2.8f * zoom, cap = StrokeCap.Round)
    } else {
        // Ragdoll arms and legs flailing
        val arm1 = Offset(p2.x - 12f * zoom, p2.y - 8f * zoom)
        val leg1 = Offset(p2.x + 10f * zoom, p2.y + 14f * zoom)
        drawLine(Color.White, p2, arm1, strokeWidth = 2.5f * zoom, cap = StrokeCap.Round)
        drawLine(Color.White, p2, leg1, strokeWidth = 2.8f * zoom, cap = StrokeCap.Round)
    }

    // 5. Rider Helmet / Head (P3)
    drawCircle(
        color = Color.White,
        radius = sledder.headRadius * zoom,
        center = p3
    )

    // Goggles / visor
    val headAngle = sledder.getSledAngle()
    val eyeOffsetX = cos(headAngle) * 3f * zoom
    val eyeOffsetY = sin(headAngle) * 3f * zoom
    drawCircle(
        color = if (isCrashed) Color(0xFFFF1744) else Color(0xFF0F172A),
        radius = 2.2f * zoom,
        center = Offset(p3.x + eyeOffsetX, p3.y + eyeOffsetY)
    )

    // Snow spray particles if going fast on track
    if (sledder.currentSpeed > 35f && sledder.contactCount > 0) {
        val sprayCount = 4
        for (i in 0 until sprayCount) {
            val spX = p0.x - dx * (0.1f * i) + (Math.random() * 8 - 4).toFloat() * zoom
            val spY = p0.y - dy * (0.1f * i) - (Math.random() * 8 + 2).toFloat() * zoom
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = (Math.random() * 2 + 1).toFloat() * zoom,
                center = Offset(spX, spY)
            )
        }
    }
}
