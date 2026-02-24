/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.modifiers

import me.deecaad.core.file.*
import com.cjcrafter.weaponmechanicsplus.weapon.modifiers.util.*
import com.cjcrafter.weaponmechanicsplus.weapon.modifiers.util.MechanicsModifier.Companion.serializeMechanicsModifier
import com.cjcrafter.weaponmechanicsplus.weapon.thermal.ThermalScopeSettings
import me.deecaad.core.file.simple.DoubleSerializer
import me.deecaad.core.file.simple.EnumValueSerializer
import java.util.*
import kotlin.jvm.optionals.getOrNull

class ScopeModifier : Serializer<ScopeModifier> {

    var zoomAmount: DoubleModifier? = null
    var isNightVision: Boolean? = null
    var isPumpkinOverlay: Boolean? = null
    var zoomStacking: List<DoubleModifier?> = listOf()
    var mechanicsModifier: MechanicsModifier? = null
    var thermalScope: ThermalScopeSettings? = null

    /**
     * Default constructor for serializer
     */
    constructor()

    constructor(
        zoomAmount: DoubleModifier?,
        isNightVision: Boolean?,
        isPumpkinOverlay: Boolean?,
        zoomStacking: List<DoubleModifier?>,
        mechanicsModifier: MechanicsModifier?,
    ) {
        this.zoomAmount = zoomAmount
        this.isNightVision = isNightVision
        this.isPumpkinOverlay = isPumpkinOverlay
        this.zoomStacking = zoomStacking
        this.mechanicsModifier = mechanicsModifier
    }

    @Throws(SerializerException::class)
    override fun serialize(data: SerializeData): ScopeModifier {
        val zoomAmount = data.of("Zoom_Amount").serialize(DoubleModifier::class.java).getOrNull()
        val isNightVision = data.of("Night_Vision").getBool().getOrNull()
        val isPumpkinOverlay = data.of("Pumpkin_Overlay").getBool().getOrNull()

        val splits = data.ofList("Zoom_Stacking")
            .addArgument(EnumValueSerializer(Operation::class.java, false))
            .addArgument(DoubleSerializer())
            .requireAllPreviousArgs()
            .assertList()

        val thermal: ThermalScopeSettings? =
            if (data.has("Thermal_Scope")) {
                val thermalNode = data.move("Thermal_Scope")
                val enabled = !thermalNode.has("Enabled") || thermalNode.of("Enabled").getBool().orElse(true)
                if (enabled) ThermalScopeSettings().serialize(thermalNode) else null
            } else null

        val zoomStacking: MutableList<DoubleModifier> = ArrayList()
        for (split in splits) {
            val operation = (split[0].get() as List<Operation>).first()
            val number = split[1].get() as Double
            zoomStacking.add(DoubleModifier(operation, number))
        }

        val mechanicsModifier = data.serializeMechanicsModifier()

        return ScopeModifier(zoomAmount, isNightVision, isPumpkinOverlay, zoomStacking, mechanicsModifier).also {it.thermalScope = thermal }
    }
}