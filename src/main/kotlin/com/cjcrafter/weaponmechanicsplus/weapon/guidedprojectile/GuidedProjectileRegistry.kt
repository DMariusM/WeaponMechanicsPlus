/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.guidedprojectile

import com.cjcrafter.weaponmechanicsplus.WeaponMechanicsPlusAPI
import me.deecaad.weaponmechanics.WeaponMechanics
import me.deecaad.weaponmechanics.utils.CustomTag
import me.deecaad.weaponmechanics.weapon.weaponevents.PrepareWeaponShootEvent
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

class GuidedProjectileRegistry(private val plugin: Plugin) : Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onPrepareWeaponShoot(event: PrepareWeaponShootEvent) {
        val shooter = event.shooter
        val weaponTitle = event.weaponTitle
        val stack = event.weaponStack

        val config = WeaponMechanics.getInstance().weaponConfigurations
        val actualTitle = safeTagTitle(stack) ?: weaponTitle

        // Base weapon config
        var guided: GuidedProjectile? = when {
            config.contains("$weaponTitle.Projectile.Guided_Projectile") -> config.getObject("$weaponTitle.Projectile.Guided_Projectile", GuidedProjectile::class.java)
            config.contains("$weaponTitle.Guided_Projectile") -> config.getObject("$weaponTitle.Guided_Projectile", GuidedProjectile::class.java)
            else -> null
        }

        // Attachment overrides (higher priority attachments win)
        if (stack != null) {
            WeaponMechanicsPlusAPI.forEachModifier(shooter, stack) { mod ->
                val wm = mod.getWeaponModifier(actualTitle) ?: return@forEachModifier
                val override = wm.projectile?.guidedProjectile ?: return@forEachModifier
                guided = override
            }
        }

        val effective = guided ?: return
        if (!effective.enabled) return

        GuidedContextHolder.register(
            plugin,
            shooter.uniqueId,
            GuidedContext(effective, event.projectileAmount)
        )
    }

    private fun safeTagTitle(stack: ItemStack?): String? {
        if (stack == null || stack.type == Material.AIR) return null
        return try { CustomTag.WEAPON_TITLE.getString(stack) } catch (_: Throwable) { null }
    }
}