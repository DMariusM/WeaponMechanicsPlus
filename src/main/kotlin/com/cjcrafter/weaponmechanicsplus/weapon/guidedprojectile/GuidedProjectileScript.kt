/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.guidedprojectile

import me.deecaad.weaponmechanics.weapon.projectile.ProjectileScript
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile
import org.bukkit.FluidCollisionMode
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector

class GuidedProjectileScript(
    owner: Plugin,
    projectile: WeaponProjectile,
    private val guided: GuidedProjectile
) : ProjectileScript<WeaponProjectile>(owner, projectile) {

    private var tick = 0

    override fun onTickStart() {
        val shooter = projectile.shooter as? Player ?: return
        if (!shooter.isValid || shooter.isDead) { removeScript = true; return }

        tick++
        if (guided.updateInterval > 1 && tick % guided.updateInterval != 0) return

        val eye = shooter.eyeLocation
        val dir = eye.direction.normalize()

        val aimPoint: Vector = if (guided.useRayTrace) {
            val result = shooter.world.rayTrace(
                eye,
                dir,
                guided.maxRange,
                FluidCollisionMode.NEVER,
                guided.ignorePassableBlocks,
                guided.raySize
            ) { e -> e.entityId != shooter.entityId && e.isValid }

            result?.hitPosition ?: eye.toVector().add(dir.multiply(guided.maxRange))
        } else {
            eye.toVector().add(dir.multiply(guided.maxRange))
        }

        val speed = projectile.motionLength
        val newDir = guided.rotateVector(projectile.normalizedMotion, projectile.location, aimPoint)
        projectile.motion = newDir.multiply(speed)
    }
}