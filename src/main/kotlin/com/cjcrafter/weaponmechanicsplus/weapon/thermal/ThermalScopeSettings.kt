/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.thermal

import com.cjcrafter.weaponmechanicsplus.weapon.modifiers.util.Whitelist
import me.deecaad.core.file.SerializeData
import me.deecaad.core.file.Serializer
import me.deecaad.weaponmechanics.WeaponMechanics
import org.bukkit.Material
import org.bukkit.entity.EntityType
import java.util.EnumSet
import java.util.Locale

class ThermalScopeSettings(
    val enabled: Boolean,
    val mode: DetectMode,
    val distance: Int,
    val raySize: Double,
    val tickInterval: Int,
    val typeFilter: Whitelist<EntityType>?,
    val blockFilter: Whitelist<Material>?
) : Serializer<ThermalScopeSettings> {

    constructor() : this(
        enabled = true,
        mode = DetectMode.RAY,
        distance = 20,
        raySize = 0.2,
        tickInterval = 1,
        typeFilter = null,
        blockFilter = null
    )

    enum class DetectMode { RAY, SPHERE }

    override fun getKeyword(): String = "Thermal_Scope"

    override fun serialize(data: SerializeData): ThermalScopeSettings {
        val enabled = data.of("Enabled").getBool().orElse(true)

        val mode = data.of("Mode").getEnum(DetectMode::class.java).orElse(DetectMode.RAY)
        val distance = data.of("Distance").assertRange(1, null).getInt().orElse(20)
        val raySize = data.of("Ray_Size").assertRange(0.0, null).getDouble().orElse(0.2)
        val tickInterval = data.of("Tick_Interval").assertRange(1, null).getInt().orElse(1)

        val typeFilter = readTargetTypeFilter(data)
        val blockFilter = readBlockFilter(data)

        return ThermalScopeSettings(enabled, mode, distance, raySize, tickInterval, typeFilter, blockFilter)
    }

    private fun readTargetTypeFilter(data: SerializeData): Whitelist<EntityType>? {
        val roots = listOf("Target_Filter", "Target_Type_Filter")

        for (root in roots) {
            if (!data.has(root)) continue

            val modeStr = data.of("$root.Mode").get(String::class.java).orElse(null)
            val isWhitelist = when {
                modeStr != null -> modeStr.equals("WHITELIST", ignoreCase = true)
                data.has("$root.Use_Whitelist") -> data.of("$root.Use_Whitelist").getBool().orElse(true)
                else -> true // safer default (same spirit as thermal)
            }

            val rawAny =
                data.of("$root.Entity_Types").get(Any::class.java).orElse(null)
                    ?: data.of("$root.Targets").get(Any::class.java).orElse(null)
                    ?: data.of("$root.List").get(Any::class.java).orElse(null)
                    ?: return null

            val values: Collection<*> = when (rawAny) {
                is Collection<*> -> rawAny
                is Array<*> -> rawAny.asList()
                else -> listOf(rawAny)
            }

            if (values.isEmpty()) return null

            val set = EnumSet.noneOf(EntityType::class.java)
            for (o in values) {
                val s = o?.toString()?.trim()?.uppercase(Locale.ROOT) ?: continue
                try {
                    set.add(EntityType.valueOf(s))
                } catch (_: IllegalArgumentException) {
                    WeaponMechanics.getInstance().debugger.warning(
                        "[Thermal_Scope] Unknown EntityType '$s' at ${data.of(root).location}"
                    )
                }
            }

            return if (set.isEmpty()) null else Whitelist(isWhitelist, set)
        }

        return null
    }

    private fun readBlockFilter(data: SerializeData): Whitelist<Material>? {
        val roots = listOf("Block_Filter", "Block_Type_Filter")

        for (root in roots) {
            if (!data.has(root)) continue

            val modeStr = data.of("$root.Mode").get(String::class.java).orElse(null)
            val isWhitelist = when {
                modeStr != null -> modeStr.equals("WHITELIST", ignoreCase = true)
                data.has("$root.Use_Whitelist") -> data.of("$root.Use_Whitelist").getBool().orElse(true)
                else -> true
            }

            val rawAny =
                data.of("$root.Block_Types").get(Any::class.java).orElse(null)
                    ?: data.of("$root.Blocks").get(Any::class.java).orElse(null)
                    ?: data.of("$root.List").get(Any::class.java).orElse(null)
                    ?: return null

            val values: Collection<*> = when (rawAny) {
                is Collection<*> -> rawAny
                is Array<*> -> rawAny.asList()
                else -> listOf(rawAny)
            }

            if (values.isEmpty()) return null

            val set = EnumSet.noneOf(Material::class.java)
            for (o in values) {
                val s = o?.toString()?.trim()?.uppercase(Locale.ROOT) ?: continue
                try {
                    set.add(Material.valueOf(s))
                } catch (_: IllegalArgumentException) {
                    WeaponMechanics.getInstance().debugger.warning(
                        "[Thermal_Scope] Unknown Material '$s' at ${data.of(root).location}"
                    )
                }
            }

            return if (set.isEmpty()) null else Whitelist(isWhitelist, set)
        }

        return null
    }

    fun merge(other: ThermalScopeSettings): ThermalScopeSettings {
        return ThermalScopeSettings(
            enabled = this.enabled && other.enabled,
            mode = other.mode,
            distance = maxOf(this.distance, other.distance),
            raySize = maxOf(this.raySize, other.raySize),
            tickInterval = minOf(this.tickInterval, other.tickInterval),
            typeFilter = other.typeFilter ?: this.typeFilter,
            blockFilter = other.blockFilter ?: this.blockFilter
        )
    }
}