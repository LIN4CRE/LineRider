package com.example.physics

import kotlin.math.*

class PhysicsEngine {
    // Gravity pointing downwards
    var gravity = Vector2D(0f, 0.42f)
    var drag = 0.998f
    var substeps = 6 // Sub-stepping for ultra smooth collision without tunneling

    /**
     * Advances simulation by one frame.
     */
    fun update(sledder: Sledder, lines: List<TrackLine>) {
        sledder.contactCount = 0

        // Substep integration
        val subGravity = gravity * (1f / substeps)

        for (step in 0 until substeps) {
            sledder.stepVerlet(subGravity, drag)

            // Resolve collisions for sled points
            resolvePointCollisions(sledder.p0, sledder.p0Prev, lines, sledder, isRunner = true)
            resolvePointCollisions(sledder.p1, sledder.p1Prev, lines, sledder, isRunner = true)

            // Rider collisions
            if (!sledder.isCrashed) {
                // If head hits ground at high speed, trigger crash
                val headContact = resolvePointCollisions(sledder.p3, sledder.p3Prev, lines, sledder, isRunner = false, radius = sledder.headRadius)
                if (headContact) {
                    val vel = sledder.p3 - sledder.p3Prev
                    if (vel.length() > 3.0f) {
                        sledder.triggerCrash()
                    }
                }
            } else {
                resolvePointCollisions(sledder.p2, sledder.p2Prev, lines, sledder, isRunner = false, radius = 6f)
                resolvePointCollisions(sledder.p3, sledder.p3Prev, lines, sledder, isRunner = false, radius = sledder.headRadius)
            }

            // Enforce constraints
            sledder.satisfyConstraints()
        }

        if (sledder.isCrashed) {
            sledder.crashTime += 1f / 60f
        }
    }

    private fun resolvePointCollisions(
        p: Vector2D,
        pPrev: Vector2D,
        lines: List<TrackLine>,
        sledder: Sledder,
        isRunner: Boolean,
        radius: Float = 3.5f
    ): Boolean {
        var hadCollision = false
        val px = p.x
        val py = p.y
        val searchRadius = radius + 12f

        for (line in lines) {
            if (line.type == LineType.SCENERY) continue

            // Fast bounding box rejection
            if (px < line.minX() - searchRadius || px > line.maxX() + searchRadius ||
                py < line.minY() - searchRadius || py > line.maxY() + searchRadius
            ) {
                continue
            }

            val dist = line.distanceToPoint(px, py)
            if (dist < radius) {
                hadCollision = true
                sledder.contactCount++

                // Normal vector of the line
                val normal = line.normal
                val tangent = line.tangent

                // Project point onto line surface along normal
                val closest = line.closestPointOnSegment(px, py)
                p.x = closest.x + normal.x * radius
                p.y = closest.y + normal.y * radius

                // Adjust previous position to reflect friction, boost, bounce
                val vel = p - pPrev
                val velNormal = vel.dot(normal)
                val velTangent = vel.dot(tangent)

                when (line.type) {
                    LineType.BOOST -> {
                        // Accelerate in tangent direction
                        val boostForce = 1.15f
                        val newTangentVel = (velTangent + boostForce).coerceAtLeast(velTangent + 0.5f)
                        val newVel = tangent * newTangentVel
                        pPrev.x = p.x - newVel.x
                        pPrev.y = p.y - newVel.y
                    }

                    LineType.BOUNCE -> {
                        // High restitution trampoline bounce
                        val bounceRestitution = 1.25f
                        val bounceNormal = if (velNormal < 0) -velNormal * bounceRestitution else velNormal
                        val newVel = tangent * (velTangent * 0.99f) + normal * bounceNormal
                        pPrev.x = p.x - newVel.x
                        pPrev.y = p.y - newVel.y
                    }

                    LineType.SLOW -> {
                        // High friction braking line
                        val frictionVel = velTangent * 0.85f
                        val newVel = tangent * frictionVel
                        pPrev.x = p.x - newVel.x
                        pPrev.y = p.y - newVel.y
                    }

                    LineType.RIDE -> {
                        // Standard ice/snow glide with slight friction
                        val friction = 0.997f
                        val newVel = tangent * (velTangent * friction)
                        pPrev.x = p.x - newVel.x
                        pPrev.y = p.y - newVel.y
                    }

                    LineType.SCENERY -> { /* No collision */ }
                }
            }
        }
        return hadCollision
    }
}
