/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.thermal

import me.deecaad.core.file.Configuration
import me.deecaad.weaponmechanics.WeaponMechanics
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object ThermalScopeWeaponConfig {

    private val resolvedRootKeyCache = ConcurrentHashMap<String, String>()

    fun read(weaponTitleRaw: String): ThermalScopeSettings? {
        val cfg = WeaponMechanics.getInstance().weaponConfigurations

        val requested = weaponTitleRaw.trim()
        val weaponKey = resolveWeaponKey(cfg, requested)

        val base = "$weaponKey.Scope.Thermal_Scope"

        val settings = cfg.getObject(base, ThermalScopeSettings::class.java) ?: return null

        return if (settings.enabled) settings else null
    }

    private fun resolveWeaponKey(cfg: Configuration, requested: String): String {
        val cacheKey = requested.lowercase(Locale.ROOT)
        return resolvedRootKeyCache[cacheKey] ?: run {
            val actual = cfg.keys(false).firstOrNull { it.equals(requested, ignoreCase = true) } ?: requested
            resolvedRootKeyCache[cacheKey] = actual
            actual
        }
    }
}