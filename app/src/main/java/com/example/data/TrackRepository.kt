package com.example.data

import com.example.physics.LineType
import com.example.physics.TrackLine
import com.example.physics.Vector2D
import com.example.tools.LineSmoother
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

class TrackRepository(private val trackDao: TrackDao) {

    val allTracks: Flow<List<TrackEntity>> = trackDao.getAllTracks()

    suspend fun getTrackById(id: Long): TrackEntity? = withContext(Dispatchers.IO) {
        trackDao.getTrackById(id)
    }

    suspend fun saveTrack(
        title: String,
        description: String,
        lines: List<TrackLine>,
        startX: Float,
        startY: Float,
        startAngle: Float,
        existingId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        val linesJson = serializeLines(lines)
        val track = TrackEntity(
            id = existingId ?: 0L,
            title = title.ifBlank { "Untitled Track" },
            description = description,
            linesJson = linesJson,
            startX = startX,
            startY = startY,
            startAngle = startAngle,
            createdAt = System.currentTimeMillis()
        )
        trackDao.insertTrack(track)
    }

    suspend fun deleteTrack(id: Long) = withContext(Dispatchers.IO) {
        trackDao.deleteTrackById(id)
    }

    suspend fun ensurePresetsSeeded() = withContext(Dispatchers.IO) {
        val count = trackDao.getCount()
        if (count == 0) {
            seedPresets()
        }
    }

    private suspend fun seedPresets() {
        val presets = listOf(
            createLeapAndLoopPreset(),
            createAvalanchePreset(),
            createBouncyParkPreset(),
            createBlankCanvasPreset()
        )
        for (preset in presets) {
            trackDao.insertTrack(preset)
        }
    }

    fun serializeLines(lines: List<TrackLine>): String {
        val array = JSONArray()
        for (line in lines) {
            val obj = JSONObject()
            obj.put("x1", line.x1.toDouble())
            obj.put("y1", line.y1.toDouble())
            obj.put("x2", line.x2.toDouble())
            obj.put("y2", line.y2.toDouble())
            obj.put("t", line.type.name)
            obj.put("w", line.width.toDouble())
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeLines(json: String): List<TrackLine> {
        val list = mutableListOf<TrackLine>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val typeName = obj.optString("t", LineType.RIDE.name)
                val type = try {
                    LineType.valueOf(typeName)
                } catch (e: Exception) {
                    LineType.RIDE
                }
                list.add(
                    TrackLine(
                        id = System.nanoTime() + i,
                        x1 = obj.getDouble("x1").toFloat(),
                        y1 = obj.getDouble("y1").toFloat(),
                        x2 = obj.getDouble("x2").toFloat(),
                        y2 = obj.getDouble("y2").toFloat(),
                        type = type,
                        width = obj.optDouble("w", 3.5).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Standalone playable HTML5 Line Rider web app generator.
     */
    fun generateStandaloneWebPlayerHtml(
        title: String,
        lines: List<TrackLine>,
        startX: Float,
        startY: Float
    ): String {
        val linesJson = serializeLines(lines)
        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
  <title>$title - Line Rider Web</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; }
    body { background: #0b132b; color: #fff; font-family: system-ui, -apple-system, sans-serif; overflow: hidden; }
    #canvas { width: 100vw; height: 100vh; display: block; background: #0f172a; }
    .hud { position: absolute; top: 16px; left: 16px; display: flex; gap: 10px; z-index: 10; }
    .btn { background: #1e293b; border: 1px solid #334155; color: #38bdf8; padding: 8px 16px; border-radius: 8px; font-weight: bold; cursor: pointer; }
    .btn:hover { background: #334155; }
    .speed-hud { position: absolute; top: 16px; right: 16px; background: rgba(15, 23, 42, 0.85); padding: 8px 16px; border-radius: 8px; border: 1px solid #334155; font-size: 14px; }
  </style>
</head>
<body>
  <div class="hud">
    <button class="btn" id="playBtn">Play / Pause</button>
    <button class="btn" id="resetBtn">Reset</button>
  </div>
  <div class="speed-hud" id="stats">Speed: 0 km/h</div>
  <canvas id="canvas"></canvas>

  <script>
    const canvas = document.getElementById('canvas');
    const ctx = canvas.getContext('2d');
    function resize() { canvas.width = window.innerWidth; canvas.height = window.innerHeight; }
    window.addEventListener('resize', resize);
    resize();

    const trackLines = $linesJson;
    const startX = $startX, startY = $startY;

    let playing = true;
    let sled = {
      p0: { x: startX - 15, y: startY },
      p0Prev: { x: startX - 15, y: startY },
      p1: { x: startX + 15, y: startY },
      p1Prev: { x: startX + 15, y: startY }
    };

    function resetSled() {
      sled.p0 = { x: startX - 15, y: startY };
      sled.p0Prev = { x: startX - 15, y: startY };
      sled.p1 = { x: startX + 15, y: startY };
      sled.p1Prev = { x: startX + 15, y: startY };
    }

    document.getElementById('playBtn').onclick = () => { playing = !playing; };
    document.getElementById('resetBtn').onclick = resetSled;

    function loop() {
      if (playing) {
        for (let sub = 0; sub < 4; sub++) {
          const g = 0.45 / 4;
          // Step Verlet
          let v0x = (sled.p0.x - sled.p0Prev.x) * 0.999;
          let v0y = (sled.p0.y - sled.p0Prev.y) * 0.999 + g;
          sled.p0Prev = { ...sled.p0 };
          sled.p0.x += v0x; sled.p0.y += v0y;

          let v1x = (sled.p1.x - sled.p1Prev.x) * 0.999;
          let v1y = (sled.p1.y - sled.p1Prev.y) * 0.999 + g;
          sled.p1Prev = { ...sled.p1 };
          sled.p1.x += v1x; sled.p1.y += v1y;

          // Collisions
          [sled.p0, sled.p1].forEach((p, idx) => {
            const pPrev = idx === 0 ? sled.p0Prev : sled.p1Prev;
            trackLines.forEach(l => {
              if (l.t === 'SCENERY') return;
              const dx = l.x2 - l.x1, dy = l.y2 - l.y1;
              const len = Math.hypot(dx, dy);
              if (len < 0.1) return;
              const t = Math.max(0, Math.min(1, ((p.x - l.x1) * dx + (p.y - l.y1) * dy) / (len * len)));
              const cx = l.x1 + t * dx, cy = l.y1 + t * dy;
              const dist = Math.hypot(p.x - cx, p.y - cy);
              if (dist < 4) {
                const nx = -dy / len, ny = dx / len;
                p.x = cx + nx * 4; p.y = cy + ny * 4;
                const tx = dx / len, ty = dy / len;
                const vx = p.x - pPrev.x, vy = p.y - pPrev.y;
                let vt = vx * tx + vy * ty;
                if (l.t === 'BOOST') vt += 0.8;
                pPrev.x = p.x - tx * vt * 0.995;
                pPrev.y = p.y - ty * vt * 0.995;
              }
            });
          });

          // Chassis constraint (dist = 30)
          const cdx = sled.p1.x - sled.p0.x, cdy = sled.p1.y - sled.p0.y;
          const cdist = Math.hypot(cdx, cdy);
          if (cdist > 0.1) {
            const diff = (cdist - 30) / cdist * 0.5;
            sled.p0.x += cdx * diff; sled.p0.y += cdy * diff;
            sled.p1.x -= cdx * diff; sled.p1.y -= cdy * diff;
          }
        }
      }

      // Draw
      ctx.fillStyle = '#0b132b';
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      const midX = (sled.p0.x + sled.p1.x) * 0.5;
      const midY = (sled.p0.y + sled.p1.y) * 0.5;
      const camX = canvas.width / 2 - midX;
      const camY = canvas.height / 2 - midY;

      ctx.save();
      ctx.translate(camX, camY);

      // Draw lines
      trackLines.forEach(l => {
        ctx.beginPath();
        ctx.moveTo(l.x1, l.y1);
        ctx.lineTo(l.x2, l.y2);
        ctx.lineWidth = l.w || 3.5;
        ctx.lineCap = 'round';
        ctx.strokeStyle = l.t === 'BOOST' ? '#ef4444' : l.t === 'BOUNCE' ? '#f59e0b' : l.t === 'SCENERY' ? '#22c55e' : '#38bdf8';
        ctx.stroke();
      });

      // Draw sled & rider
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.moveTo(sled.p0.x, sled.p0.y);
      ctx.lineTo(sled.p1.x, sled.p1.y);
      ctx.stroke();

      // Rider head
      const sAngle = Math.atan2(sled.p1.y - sled.p0.y, sled.p1.x - sled.p0.x);
      const headX = midX - Math.sin(sAngle) * 16;
      const headY = midY + Math.cos(sAngle) * 16;
      ctx.fillStyle = '#fff';
      ctx.beginPath();
      ctx.arc(headX, headY, 5, 0, Math.PI * 2);
      ctx.fill();

      ctx.restore();

      const speed = Math.round(Math.hypot(sled.p1.x - sled.p1Prev.x, sled.p1.y - sled.p1Prev.y) * 60 * 0.05 * 4);
      document.getElementById('stats').innerText = 'Speed: ' + speed + ' km/h';

      requestAnimationFrame(loop);
    }
    requestAnimationFrame(loop);
  </script>
</body>
</html>
        """.trimIndent()
    }

    // --- PRESETS BUILDERS ---

    private fun createLeapAndLoopPreset(): TrackEntity {
        val lines = mutableListOf<TrackLine>()

        // Starting drop hill
        val dropPoints = mutableListOf<Vector2D>()
        for (i in 0..20) {
            val x = 100f + i * 25f
            val y = 150f + (i * 0.15f).pow(2.2f) * 150f
            dropPoints.add(Vector2D(x, y))
        }
        lines.addAll(LineSmoother.pointsToLines(LineSmoother.smoothPoints(dropPoints, 2), LineType.RIDE))

        // Red Boost pad leading into loop
        val lastDrop = dropPoints.last()
        lines.add(TrackLine(x1 = lastDrop.x, y1 = lastDrop.y, x2 = lastDrop.x + 80f, y2 = lastDrop.y + 10f, type = LineType.BOOST))
        lines.add(TrackLine(x1 = lastDrop.x + 80f, y1 = lastDrop.y + 10f, x2 = lastDrop.x + 160f, y2 = lastDrop.y + 15f, type = LineType.BOOST))

        // Giant 360 Loop-de-loop
        val loopCenterX = lastDrop.x + 300f
        val loopCenterY = lastDrop.y - 120f
        val radius = 135f
        val loopPoints = mutableListOf<Vector2D>()
        for (i in 0..36) {
            val angle = -PI.toFloat() * 0.5f + (i.toFloat() / 36f) * 2f * PI.toFloat()
            // Offset spiral so it exits smoothly
            val r = radius + (i * 0.6f)
            val x = loopCenterX + cos(angle) * r
            val y = loopCenterY + sin(angle) * r
            loopPoints.add(Vector2D(x, y))
        }
        lines.addAll(LineSmoother.pointsToLines(LineSmoother.smoothPoints(loopPoints, 2), LineType.RIDE))

        // Ski jump kicker
        val loopExit = loopPoints.last()
        val kickerPoints = listOf(
            loopExit,
            Vector2D(loopExit.x + 100f, loopExit.y + 10f),
            Vector2D(loopExit.x + 180f, loopExit.y - 15f),
            Vector2D(loopExit.x + 250f, loopExit.y - 50f) // JUMP!
        )
        lines.addAll(LineSmoother.pointsToLines(kickerPoints, LineType.BOOST))

        // Big air chasm with scenery flags
        lines.add(TrackLine(x1 = loopExit.x + 250f, y1 = loopExit.y - 50f, x2 = loopExit.x + 250f, y2 = loopExit.y - 100f, type = LineType.SCENERY))
        lines.add(TrackLine(x1 = loopExit.x + 250f, y1 = loopExit.y - 100f, x2 = loopExit.x + 280f, y2 = loopExit.y - 90f, type = LineType.SCENERY))

        // Landing ramp down
        val landingPoints = mutableListOf<Vector2D>()
        for (i in 0..25) {
            val x = loopExit.x + 500f + i * 28f
            val y = loopExit.y + 180f + (i * 0.12f).pow(1.8f) * 80f
            landingPoints.add(Vector2D(x, y))
        }
        lines.addAll(LineSmoother.pointsToLines(LineSmoother.smoothPoints(landingPoints, 2), LineType.RIDE))

        return TrackEntity(
            title = "The Mega Loop & Jump",
            description = "A massive 360-degree loop leading into an ultra-fast ski jump across the abyss.",
            linesJson = serializeLines(lines),
            startX = 120f,
            startY = 140f,
            startAngle = 0.25f,
            isPreset = true
        )
    }

    private fun createAvalanchePreset(): TrackEntity {
        val lines = mutableListOf<TrackLine>()
        var curX = 100f
        var curY = 100f

        val hillPoints = mutableListOf<Vector2D>()
        for (i in 0..40) {
            curX += 30f
            curY += 22f + sin(i * 0.5f) * 12f
            hillPoints.add(Vector2D(curX, curY))
        }
        lines.addAll(LineSmoother.pointsToLines(LineSmoother.smoothPoints(hillPoints, 2), LineType.RIDE))

        // Add speed strips
        val lastP = hillPoints.last()
        lines.add(TrackLine(x1 = lastP.x, y1 = lastP.y, x2 = lastP.x + 120f, y2 = lastP.y + 70f, type = LineType.BOOST))
        lines.add(TrackLine(x1 = lastP.x + 120f, y1 = lastP.y + 70f, x2 = lastP.x + 240f, y2 = lastP.y + 130f, type = LineType.BOOST))

        // Huge up-ramp
        val upRamp = listOf(
            Vector2D(lastP.x + 240f, lastP.y + 130f),
            Vector2D(lastP.x + 360f, lastP.y + 110f),
            Vector2D(lastP.x + 480f, lastP.y + 20f),
            Vector2D(lastP.x + 580f, lastP.y - 90f)
        )
        lines.addAll(LineSmoother.pointsToLines(upRamp, LineType.RIDE))

        return TrackEntity(
            title = "Avalanche Peaks",
            description = "High-velocity downhill slalom with turbocharged boost tracks and giant airs.",
            linesJson = serializeLines(lines),
            startX = 110f,
            startY = 90f,
            startAngle = 0.5f,
            isPreset = true
        )
    }

    private fun createBouncyParkPreset(): TrackEntity {
        val lines = mutableListOf<TrackLine>()

        // Drop slope
        val drop = listOf(
            Vector2D(100f, 100f),
            Vector2D(180f, 150f),
            Vector2D(260f, 240f),
            Vector2D(340f, 350f)
        )
        lines.addAll(LineSmoother.pointsToLines(drop, LineType.RIDE))

        // Trampoline bounce bowls (orange bouncy lines)
        val bounce1 = LineSmoother.createBezierArc(
            start = Vector2D(340f, 350f),
            control = Vector2D(460f, 480f),
            end = Vector2D(580f, 360f),
            segments = 14,
            lineType = LineType.BOUNCE
        )
        lines.addAll(bounce1)

        val bounce2 = LineSmoother.createBezierArc(
            start = Vector2D(650f, 370f),
            control = Vector2D(780f, 520f),
            end = Vector2D(900f, 360f),
            segments = 14,
            lineType = LineType.BOUNCE
        )
        lines.addAll(bounce2)

        val bounce3 = LineSmoother.createBezierArc(
            start = Vector2D(980f, 370f),
            control = Vector2D(1120f, 550f),
            end = Vector2D(1250f, 330f),
            segments = 14,
            lineType = LineType.BOUNCE
        )
        lines.addAll(bounce3)

        return TrackEntity(
            title = "Trampoline Circus",
            description = "Series of super-springy trampoline bowls! Sledder bounces high into the stratosphere.",
            linesJson = serializeLines(lines),
            startX = 120f,
            startY = 95f,
            startAngle = 0.4f,
            isPreset = true
        )
    }

    private fun createBlankCanvasPreset(): TrackEntity {
        val startHill = listOf(
            Vector2D(80f, 150f),
            Vector2D(140f, 170f),
            Vector2D(220f, 220f),
            Vector2D(320f, 290f),
            Vector2D(420f, 330f)
        )
        val lines = LineSmoother.pointsToLines(startHill, LineType.RIDE)
        return TrackEntity(
            title = "Free Draw (Blank Canvas)",
            description = "A gentle start hill with infinite open space to build your own masterpiece.",
            linesJson = serializeLines(lines),
            startX = 100f,
            startY = 135f,
            startAngle = 0.3f,
            isPreset = true
        )
    }
}
