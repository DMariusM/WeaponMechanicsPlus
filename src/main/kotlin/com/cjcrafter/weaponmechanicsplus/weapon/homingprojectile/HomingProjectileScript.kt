/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.homingprojectile

import me.deecaad.weaponmechanics.weapon.projectile.AProjectile
import me.deecaad.weaponmechanics.weapon.projectile.ProjectileScript
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector
import kotlin.math.abs
import kotlin.math.min

class HomingProjectileScript(
    plugin: Plugin,
    projectile: AProjectile,
    private val ctx: HomingContext
) : ProjectileScript<AProjectile>(plugin, projectile) {

    private val proj = projectile
    private var lostTicks = 0
    private var homingTicks = 0
    private var homingActive = true

    private val s = ctx.settings
    private val maxChaseSq = s.maxChaseDistance * s.maxChaseDistance

    override fun onTickEnd() {
        if (!homingActive) return

        val world: World = proj.world ?: run {
            proj.remove()
            return
        }

        if (s.maxHomingTicks <= 0 || homingTicks >= s.maxHomingTicks) {
            homingActive = false
            return
        }
        homingTicks++

        val target: Entity = ctx.target

        if (!target.isValid ||
            (target is LivingEntity && target.isDead) ||
            target.world != world
        ) {
            stopAfterLostTick()
            return
        }

        val projLoc = proj.bukkitLocation
        val projVec = projLoc.toVector()

        val targetVec = (target as? LivingEntity)?.eyeLocation?.toVector()
            ?: target.location.toVector()

        val distSq = projVec.distanceSquared(targetVec)
        if (distSq > maxChaseSq) {
            stopAfterLostTick()
            return
        }

        lostTicks = 0

        val current = proj.motion
        val speed = current.length()
        if (speed <= 1.0e-6) return

        val currDir = current.clone().multiply(1.0 / speed)
        val toTarget = targetVec.clone().subtract(projVec).normalize()

        val dist = Math.sqrt(distSq)

        // Base turn limit
        val baseMaxTurnRad = Math.toRadians(s.maxTurnDegrees)

        // Boost turning as we get closer. Uses lockRadius to avoid new config
        // Cap the boost so it doesn't go insane
        val boost = if (dist <= 1.0e-6) 8.0 else (s.lockRadius / dist).coerceIn(1.0, 8.0)
        val maxTurnRad = baseMaxTurnRad * boost

        val angleBetween = currDir.angle(toTarget).toDouble()
        val turn = min(angleBetween, maxTurnRad)

        val newDir =
            if (angleBetween < 1e-6) {
                toTarget
            } else {
                var axis = currDir.clone().crossProduct(toTarget)

                if (axis.lengthSquared() < 1.0e-12) {
                    val ref = if (abs(currDir.y) < 0.99) Vector(0.0, 1.0, 0.0) else Vector(1.0, 0.0, 0.0)
                    axis = currDir.clone().crossProduct(ref)
                }

                axis.normalize()
                currDir.clone().rotateAroundAxis(axis, turn)
            }

        proj.motion = newDir.multiply(speed)
    }

    private fun stopAfterLostTick() {
        if (s.maxLostTicks <= 0 || ++lostTicks >= s.maxLostTicks) {
            homingActive = false
        }
    }
}