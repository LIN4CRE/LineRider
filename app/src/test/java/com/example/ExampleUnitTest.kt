package com.example

import com.example.physics.LineType
import com.example.physics.PhysicsEngine
import com.example.physics.Sledder
import com.example.physics.TrackLine
import com.example.physics.Vector2D
import com.example.tools.LineSmoother
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testTrackLineDistance() {
    val line = TrackLine(x1 = 0f, y1 = 0f, x2 = 100f, y2 = 0f, type = LineType.RIDE)
    val dist = line.distanceToPoint(50f, 10f)
    assertEquals(10f, dist, 0.001f)
  }

  @Test
  fun testSledderVerletAndCollision() {
    val sledder = Sledder(startX = 50f, startY = 10f, startAngleRad = 0f)
    val engine = PhysicsEngine()
    val lines = listOf(
      TrackLine(x1 = 0f, y1 = 20f, x2 = 100f, y2 = 20f, type = LineType.RIDE)
    )

    // Simulate 30 steps
    for (i in 0 until 30) {
      engine.update(sledder, lines)
    }

    // Sled should be supported by the horizontal line around y=20
    val center = sledder.getCenter()
    assertTrue("Sled should not fall below track", center.y <= 25f)
  }

  @Test
  fun testChaikinSmoothing() {
    val raw = listOf(
      Vector2D(0f, 0f),
      Vector2D(50f, 50f),
      Vector2D(100f, 0f)
    )
    val smoothed = LineSmoother.smoothPoints(raw, iterations = 1)
    assertTrue("Smoothed points count should increase", smoothed.size > raw.size)
  }
}

