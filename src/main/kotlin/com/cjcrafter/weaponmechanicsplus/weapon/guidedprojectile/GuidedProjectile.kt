/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.guidedprojectile

import me.deecaad.core.file.SerializeData
import me.deecaad.core.file.Serializer
import me.deecaad.core.file.SerializerException
import org.bukkit.util.Vector
import kotlin.math.abs

class GuidedProjectile(
    val enabled: Boolean,
    val maximumCurvePerTick: Double,
    val maxRange: Double,
    val updateInterval: Int,
    val useRayTrace: Boolean,
    val ignorePassableBlocks: Boolean,
    val raySize: Double,
) : Serializer<GuidedProjectile> {

    constructor() : this(
        enabled = true,
        maximumCurvePerTick = 0.12,
        maxRange = 96.0,
        updateInterval = 1,
        useRayTrace = true,
        ignorePassableBlocks = true,
        raySize = 0.0
    )

    override fun getKeyword(): String = "Guided_Projectile"

    @Throws(SerializerException::class)
    override fun serialize(data: SerializeData): GuidedProjectile {
        val enabled = data.of("Enabled").getBool().orElse(true)

        val maxCurve = data.of("Maximum_Curve_Per_Tick")
            .assertRange(0.0, Math.PI)
            .getDouble().orElse(0.12)

        val maxRange = data.of("Max_Range")
            .assertRange(1.0, 10000.0)
            .getDouble().orElse(96.0)

        val interval = data.of("Update_Interval")
            .assertRange(1, 20)
            .getInt().orElse(1)

        val useRay = data.of("Ray_Trace").getBool().orElse(true)
        val ignorePassable = data.of("Ignore_Passable_Blocks").getBool().orElse(true)

        val raySize = data.of("Ray_Size")
            .assertRange(0.0, 5.0)
            .getDouble().orElse(0.0)

        return GuidedProjectile(
            enabled = enabled,
            maximumCurvePerTick = maxCurve,
            maxRange = maxRange,
            updateInterval = interval,
            useRayTrace = useRay,
            ignorePassableBlocks = ignorePassable,
            raySize = raySize
        )
    }

    fun rotateVector(currentDirection: Vector, aPoint: Vector, bPoint: Vector): Vector {
        val targetDir = bPoint.clone().subtract(aPoint).normalize()
        return rotateVector(currentDirection, targetDir)
    }

    fun rotateVector(currentDirection: Vector, otherDirection: Vector): Vector {
        val angle = currentDirection.angle(otherDirection)

        if (angle < 1.0E-9) return otherDirection
        if (abs(angle) <= maximumCurvePerTick) return otherDirection

        return currentDirection.clone().multiply((angle - maximumCurvePerTick) / angle)
            .add(otherDirection.clone().multiply(maximumCurvePerTick / angle))
            .normalize()
    }
}