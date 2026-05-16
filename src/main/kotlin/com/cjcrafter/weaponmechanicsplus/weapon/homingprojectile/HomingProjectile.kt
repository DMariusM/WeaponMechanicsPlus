/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.homingprojectile

import com.cjcrafter.weaponmechanicsplus.weapon.modifiers.util.Whitelist
import me.deecaad.core.file.SerializeData
import me.deecaad.core.file.Serializer
import me.deecaad.weaponmechanics.WeaponMechanics
import org.bukkit.entity.EntityType
import java.util.Locale

class HomingProjectile(
    val enabled: Boolean,
    val lockRadius: Double,
    val coneAngle: Double,
    val maxTurnDegrees: Double,
    val maxChaseDistance: Double,
    val maxHomingTicks: Int,
    val maxLostTicks: Int,
    val targetTypeFilter: Whitelist<EntityType>?
) : Serializer<HomingProjectile> {

    constructor() : this(
        enabled = true,
        lockRadius = 50.0,
        coneAngle = 30.0,
        maxTurnDegrees = 10.0,
        maxChaseDistance = 50.0,
        maxHomingTicks = 100,
        maxLostTicks = 200,
        targetTypeFilter = null
    )

    override fun getKeyword(): String = "Homing_Projectiles"

    override fun getParentKeywords(): List<String> = listOf("Projectile")

    override fun serialize(data: SerializeData): HomingProjectile {
        val enabled = data.of("Enabled").getBool().orElse(true)

        val lockRadius = data.of("Lock_Radius")
            .assertRange(0.0, null)
            .getDouble().orElse(50.0)

        val coneAngle = data.of("Cone_Angle")
            .assertRange(0.0, 180.0)
            .getDouble().orElse(30.0)

        val maxTurnDegrees = data.of("Max_Turn_Degrees")
            .assertRange(0.0, 180.0)
            .getDouble().orElse(10.0)

        val maxChaseDistance = data.of("Max_Chase_Distance")
            .assertRange(0.0, null)
            .getDouble().orElse(lockRadius)

        val maxHomingTicks = data.of("Max_Homing_Ticks")
            .assertRange(0, null)
            .getInt().orElse(100)

        val maxLostTicks = data.of("Max_Lost_Ticks")
            .assertRange(0, null)
            .getInt().orElse(200)

        val targetTypeFilter = readTargetTypeFilter(data)

        return HomingProjectile(
            enabled = enabled,
            lockRadius = lockRadius,
            coneAngle = coneAngle,
            maxTurnDegrees = maxTurnDegrees,
            maxChaseDistance = maxChaseDistance,
            maxHomingTicks = maxHomingTicks,
            maxLostTicks = maxLostTicks,
            targetTypeFilter = targetTypeFilter
        )
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

            val set = java.util.EnumSet.noneOf(EntityType::class.java)
            for (o in values) {
                val s = o?.toString()?.trim()?.uppercase(Locale.ROOT) ?: continue
                try {
                    set.add(EntityType.valueOf(s))
                } catch (_: IllegalArgumentException) {
                    WeaponMechanics.getInstance().debugger.warning(
                        "[Homing_Projectiles] Unknown EntityType '$s' at ${data.of(root).location}"
                    )
                }
            }

            return if (set.isEmpty()) null else Whitelist(isWhitelist, set)
        }

        return null
    }
}