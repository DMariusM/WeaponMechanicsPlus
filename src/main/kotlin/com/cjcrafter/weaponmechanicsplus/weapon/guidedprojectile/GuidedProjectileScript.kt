/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.guidedprojectile

import me.deecaad.weaponmechanics.utils.CustomTag
import me.deecaad.weaponmechanics.weapon.projectile.ProjectileScript
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile
import org.bukkit.FluidCollisionMode
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector

class GuidedProjectileScript(
    owner: Plugin,
    projectile: WeaponProjectile,
    private val guided: GuidedProjectile,
    private val ctx: GuidedContext
) : ProjectileScript<WeaponProjectile>(owner, projectile) {

    private var tick = 0
    private var guidedTicks = 0

    override fun onTickStart() {
        val shooter = projectile.shooter as? Player ?: return
        if (!shooter.isValid || shooter.isDead) { removeScript = true; return }

        guidedTicks++
        if (guided.maximumGuidedTicks > 0 && guidedTicks >= guided.maximumGuidedTicks) {
            removeScript = true // projectile continues, just no more guidance
            return
        }

        if (guided.requireHoldingWeapon) {
            val holding = isHoldingSameWeapon(shooter, ctx)
            GuidedProjectileRegistry.GuidedControlGate.allowed[shooter.uniqueId] = holding

            if (!holding) {
                when (guided.controlLossMode) {
                    ControlLossMode.PAUSE -> return
                    ControlLossMode.STOP -> { removeScript = true; return }
                }
            }
        }

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

    private fun isHoldingSameWeapon(player: Player, ctx: GuidedContext): Boolean {
        val stack = when (ctx.hand) {
            EquipmentSlot.HAND -> player.inventory.itemInMainHand
            EquipmentSlot.OFF_HAND -> player.inventory.itemInOffHand
            else -> player.inventory.itemInMainHand
        }

        val title = try { CustomTag.WEAPON_TITLE.getString(stack) } catch (_: Throwable) { null }
        if (title == null) return false

        if (ctx.hand == EquipmentSlot.HAND && player.inventory.heldItemSlot != ctx.heldSlot) return false

        return title.equals(ctx.weaponTitle, ignoreCase = true)
    }
}