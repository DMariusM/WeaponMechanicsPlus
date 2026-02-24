/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.listeners

import com.cjcrafter.weaponmechanicsplus.WeaponMechanicsPlusAPI
import com.cjcrafter.weaponmechanicsplus.weapon.thermal.ThermalScopeManager
import com.cjcrafter.weaponmechanicsplus.weapon.thermal.ThermalScopeSettings
import com.cjcrafter.weaponmechanicsplus.weapon.thermal.ThermalScopeWeaponConfig
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponScopeEvent
import me.deecaad.weaponmechanics.utils.CustomTag
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack

class ThermalScopeListener(
    private val thermal: ThermalScopeManager
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onThermalScope(event: WeaponScopeEvent) {
        val stack = event.weaponStack ?: return
        val player = event.shooter as? Player ?: return

        val actualTitle = safeTagTitle(stack) ?: event.weaponTitle

        var merged: ThermalScopeSettings? = ThermalScopeWeaponConfig.read(actualTitle)

        // Attachment modifiers
        WeaponMechanicsPlusAPI.forEachModifier(player, stack) { mod ->
            val wm = mod.getWeaponModifier(actualTitle)

            val scope = wm?.scope
            val thermal = scope?.thermalScope

            val t = thermal ?: return@forEachModifier
            merged = merged?.merge(t) ?: t
        }

        val settings = merged ?: return

        when (event.scopeType) {
            WeaponScopeEvent.ScopeType.IN,
            WeaponScopeEvent.ScopeType.STACK -> thermal.onScopeStartOrStack(player, settings)
            WeaponScopeEvent.ScopeType.OUT -> thermal.onScopeEnd(player)
        }
    }

    private fun safeTagTitle(stack: ItemStack?): String? {
        if (stack == null || stack.type == Material.AIR) return null
        return try { CustomTag.WEAPON_TITLE.getString(stack) } catch (_: Throwable) { null }
    }
}