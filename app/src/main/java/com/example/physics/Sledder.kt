package com.example.physics

import kotlin.math.*

/**
 * Multi-body Sledder with Verlet integration, distance constraints,
 * dynamic scarf cloth physics, and crash ragdoll mechanics.
 */
class Sledder(
    startX: Float = 0f,
    startY: Float = 0f,
    startAngleRad: Float = 0f
) {
    // Sled runners dimensions
    val sledLength = 30f
    val riderHeight = 16f
    val headRadius = 6f

    // Points in Verlet space: pos & prevPos
    // P0: Back runner (tail)
    // P1: Front runner (tip)
    // P2: Rider body / hips
    // P3: Rider head
    var p0 = Vector2D()
    var p0Prev = Vector2D()

    var p1 = Vector2D()
    var p1Prev = Vector2D()

    var p2 = Vector2D()
    var p2Prev = Vector2D()

    var p3 = Vector2D()
    var p3Prev = Vector2D()

    // Scarf nodes (fluttering cloth in the wind)
    val scarfCount = 5
    val scarfLength = 7f
    val scarfNodes = Array(scarfCount) { Vector2D() }
    val scarfPrevNodes = Array(scarfCount) { Vector2D() }

    // State
    var isCrashed = false
    var crashTime = 0f
    var airTime = 0f
    var totalDistance = 0f
    var maxSpeed = 0f
    var currentSpeed = 0f
    var contactCount = 0
    var totalRotations = 0f
    private var lastAngle = 0f

    init {
        resetTo(startX, startY, startAngleRad)
    }

    fun resetTo(startX: Float, startY: Float, angleRad: Float = 0f) {
        isCrashed = false
        crashTime = 0f
        airTime = 0f
        totalDistance = 0f
        maxSpeed = 0f
        currentSpeed = 0f
        contactCount = 0
        totalRotations = 0f
        lastAngle = angleRad

        val dir = Vector2D(cos(angleRad), sin(angleRad))
        val up = Vector2D(-dir.y, dir.x)

        // Sled runners
        p0 = Vector2D(startX - dir.x * (sledLength * 0.5f), startY - dir.y * (sledLength * 0.5f))
        p0Prev = p0.copy()

        p1 = Vector2D(startX + dir.x * (sledLength * 0.5f), startY + dir.y * (sledLength * 0.5f))
        p1Prev = p1.copy()

        // Rider seated on sled
        p2 = Vector2D(startX - dir.x * 2f + up.x * 12f, startY - dir.y * 2f + up.y * 12f)
        p2Prev = p2.copy()

        // Rider head
        p3 = Vector2D(p2.x + up.x * riderHeight, p2.y + up.y * riderHeight)
        p3Prev = p3.copy()

        // Initialize scarf trailing behind
        for (i in 0 until scarfCount) {
            val offset = i * scarfLength
            scarfNodes[i] = Vector2D(p3.x - dir.x * offset, p3.y - dir.y * offset)
            scarfPrevNodes[i] = scarfNodes[i].copy()
        }
    }

    /**
     * Center position of the sledder for camera tracking.
     */
    fun getCenter(): Vector2D {
        return Vector2D((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
    }

    /**
     * Current average velocity of the sled.
     */
    fun getVelocity(): Vector2D {
        val v0 = p0 - p0Prev
        val v1 = p1 - p1Prev
        return Vector2D((v0.x + v1.x) * 0.5f, (v0.y + v1.y) * 0.5f)
    }

    /**
     * Sled angle in radians.
     */
    fun getSledAngle(): Float {
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        return atan2(dy, dx)
    }

    /**
     * Advances physics simulation by one substep dt.
     */
    fun stepVerlet(gravity: Vector2D, drag: Float = 0.999f) {
        // P0 (tail)
        val v0 = (p0 - p0Prev) * drag
        p0Prev = p0.copy()
        p0 += v0 + gravity

        // P1 (tip)
        val v1 = (p1 - p1Prev) * drag
        p1Prev = p1.copy()
        p1 += v1 + gravity

        // P2 (body)
        val v2 = (p2 - p2Prev) * drag
        p2Prev = p2.copy()
        p2 += v2 + gravity

        // P3 (head)
        val v3 = (p3 - p3Prev) * drag
        p3Prev = p3.copy()
        p3 += v3 + gravity

        // Scarf verlet
        scarfNodes[0] = p3.copy()
        for (i in 1 until scarfCount) {
            val vs = (scarfNodes[i] - scarfPrevNodes[i]) * 0.95f
            scarfPrevNodes[i] = scarfNodes[i].copy()
            // Add flutter turbulence
            val flutterX = (sin(System.nanoTime() * 0.00000002 + i) * 0.3).toFloat()
            val flutterY = (cos(System.nanoTime() * 0.00000003 + i) * 0.2).toFloat()
            scarfNodes[i] += vs + gravity * 0.6f + Vector2D(flutterX, flutterY)
        }

        // Update stats
        val vel = getVelocity()
        currentSpeed = vel.length() * 60f * 0.05f // km/h approx
        if (currentSpeed > maxSpeed) {
            maxSpeed = currentSpeed
        }
        totalDistance += vel.length() * 0.05f

        // Track airtime
        if (contactCount == 0) {
            airTime += (1f / 60f) / 6f
        }

        // Rotation tracking (for flips!)
        val currentAngle = getSledAngle()
        var dAngle = currentAngle - lastAngle
        while (dAngle > PI) dAngle -= (2 * PI).toFloat()
        while (dAngle < -PI) dAngle += (2 * PI).toFloat()
        totalRotations += dAngle / (2 * PI).toFloat()
        lastAngle = currentAngle
    }

    /**
     * Enforces distance constraints between structural points.
     */
    fun satisfyConstraints() {
        if (!isCrashed) {
            // Sled chassis rigid distance constraint between P0 and P1
            satisfyDistance(p0, p1, sledLength)

            // Rider connection to sled
            val sledCenter = Vector2D((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
            val sledDir = (p1 - p0).normalized()
            val sledUp = Vector2D(-sledDir.y, sledDir.x)

            // Desired body pos
            val targetP2 = sledCenter + sledDir * (-2f) + sledUp * 12f
            p2.x += (targetP2.x - p2.x) * 0.6f
            p2.y += (targetP2.y - p2.y) * 0.6f

            // Desired head pos
            val targetP3 = p2 + sledUp * riderHeight
            p3.x += (targetP3.x - p3.x) * 0.7f
            p3.y += (targetP3.y - p3.y) * 0.7f
        } else {
            // Ragdoll mode: loose limbs tumble freely
            satisfyDistance(p0, p1, sledLength)
            satisfyDistance(p2, p3, riderHeight)
            // Loose hip-tail distance
            val dist = p2.distanceTo(p0)
            if (dist > 60f) {
                satisfyDistance(p2, p0, 60f)
            }
        }

        // Scarf distance constraints
        for (i in 1 until scarfCount) {
            satisfyDistance(scarfNodes[i - 1], scarfNodes[i], scarfLength)
        }
    }

    private fun satisfyDistance(a: Vector2D, b: Vector2D, targetDist: Float) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val currentDist = sqrt(dx * dx + dy * dy)
        if (currentDist > 0.0001f) {
            val diff = (currentDist - targetDist) / currentDist
            val offsetX = dx * 0.5f * diff
            val offsetY = dy * 0.5f * diff
            a.x += offsetX
            a.y += offsetY
            b.x -= offsetX
            b.y -= offsetY
        }
    }

    fun triggerCrash() {
        if (!isCrashed) {
            isCrashed = true
            crashTime = 0f
            // Add tumbling impulse
            val tumble = Vector2D((Math.random() * 4 - 2).toFloat(), (Math.random() * -3 - 1).toFloat())
            p3Prev = p3 - tumble * 1.5f
            p2Prev = p2 - tumble
        }
    }
}
