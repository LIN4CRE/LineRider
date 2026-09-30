package com.example.physics

import kotlin.math.*

/**
 * 2D Vector with high-performance vector math for Line Rider physics simulation.
 */
data class Vector2D(
    var x: Float = 0f,
    var y: Float = 0f
) {
    operator fun plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vector2D(x * scalar, y * scalar)
    operator fun div(scalar: Float) = if (scalar != 0f) Vector2D(x / scalar, y / scalar) else Vector2D()

    fun plusAssign(other: Vector2D) {
        x += other.x
        y += other.y
    }

    fun minusAssign(other: Vector2D) {
        x -= other.x
        y -= other.y
    }

    fun timesAssign(scalar: Float) {
        x *= scalar
        y *= scalar
    }

    fun length(): Float = sqrt(x * x + y * y)
    fun lengthSquared(): Float = x * x + y * y

    fun distanceTo(other: Vector2D): Float = sqrt((x - other.x) * (x - other.x) + (y - other.y) * (y - other.y))
    fun distanceSquaredTo(other: Vector2D): Float = (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y)

    fun normalized(): Vector2D {
        val len = length()
        return if (len > 0.0001f) Vector2D(x / len, y / len) else Vector2D(0f, 0f)
    }

    fun dot(other: Vector2D): Float = x * other.x + y * other.y
    fun cross(other: Vector2D): Float = x * other.y - y * other.x

    fun perpendicular(): Vector2D = Vector2D(-y, x)

    fun rotate(angleRad: Float): Vector2D {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        return Vector2D(x * cosA - y * sinA, x * sinA + y * cosA)
    }

    fun copy(): Vector2D = Vector2D(x, y)
}
