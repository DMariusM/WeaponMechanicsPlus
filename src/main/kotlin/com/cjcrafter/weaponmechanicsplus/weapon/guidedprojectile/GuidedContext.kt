/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.guidedprojectile

import org.bukkit.inventory.EquipmentSlot

class GuidedContext(
    val weaponTitle: String,
    val hand: EquipmentSlot,
    val heldSlot: Int,
    val settings: GuidedProjectile,
    var remainingProjectiles: Int,
)