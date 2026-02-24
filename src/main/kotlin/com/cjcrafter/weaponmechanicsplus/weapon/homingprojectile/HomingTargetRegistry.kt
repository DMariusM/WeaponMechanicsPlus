/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.homingprojectile

import com.cjcrafter.weaponmechanicsplus.WeaponMechanicsPlusAPI
import com.cjcrafter.weaponmechanicsplus.weapon.modifiers.util.Whitelist
import me.deecaad.weaponmechanics.WeaponMechanics
import me.deecaad.weaponmechanics.utils.CustomTag
import me.deecaad.weaponmechanics.weapon.weaponevents.PrepareWeaponShootEvent
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import kotlin.math.cos

class HomingTargetRegistry(private val plugin: Plugin) : Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onPrepareWeaponShoot(event: PrepareWeaponShootEvent) {
        val shooter = event.shooter
        val weaponTitle = event.weaponTitle
        val stack = event.weaponStack

        val weaponConfig = WeaponMechanics.getInstance().weaponConfigurations
        val actualTitle = safeTagTitle(stack) ?: weaponTitle

        // Base weapon config
        var homing: HomingProjectile? =
            weaponConfig.getObject("$weaponTitle.Projectile.Homing_Projectiles", HomingProjectile::class.java)
                ?: weaponConfig.getObject("$weaponTitle.Homing_Projectiles", HomingProjectile::class.java)

        // Attachment overrides (higher priority attachments win)
        if (stack != null) {
            WeaponMechanicsPlusAPI.forEachModifier(shooter, stack) { mod ->
                val wm = mod.getWeaponModifier(actualTitle) ?: return@forEachModifier
                val override = wm.projectile?.homingProjectile ?: return@forEachModifier
                homing = override
            }
        }

        val effective = homing ?: return
        if (!effective.enabled) return

        val target = findBestInCone(shooter, effective.lockRadius, effective.coneAngle, effective.targetTypeFilter) ?: return

        val ctx = HomingContext(
            target = target,
            settings = effective,
            remainingProjectiles = event.projectileAmount
        )
        HomingTargetHolder.register(plugin, shooter.uniqueId, ctx)
    }

    private fun findBestInCone(shooter: LivingEntity, radius: Double, coneDeg: Double, typeFilter: Whitelist<EntityType>?): Entity? {
        val origin = shooter.eyeLocation
        val world = origin.world ?: return null

        val forward = origin.direction.normalize()
        val threshold = cos(Math.toRadians(coneDeg))

        var best: Entity? = null
        var bestDot = threshold
        var bestDistSq = Double.MAX_VALUE

        val originVec = origin.toVector()

        for (e in world.getNearbyEntities(origin, radius, radius, radius)) {
            if (e !is LivingEntity) continue
            if (e.uniqueId == shooter.uniqueId) continue
            if (e is ArmorStand && (e.isMarker || e.isInvisible)) continue
            if (typeFilter != null && !typeFilter.isWhitelisted(e.type)) continue

            val toE = e.eyeLocation.toVector().subtract(originVec).normalize()
            val dot = forward.dot(toE)
            if (dot < bestDot) continue
            if (!shooter.hasLineOfSight(e)) continue

            val distSq = e.location.toVector().distanceSquared(originVec)
            val better = dot > bestDot + 1.0e-6 || (kotlin.math.abs(dot - bestDot) <= 1.0e-6 && distSq < bestDistSq)
            if (!better) continue

            bestDot = dot
            bestDistSq = distSq
            best = e
        }
        return best
    }

    private fun safeTagTitle(stack: ItemStack?): String? {
        if (stack == null || stack.type == Material.AIR) return null
        return try { CustomTag.WEAPON_TITLE.getString(stack) } catch (_: Throwable) { null }
    }
}
