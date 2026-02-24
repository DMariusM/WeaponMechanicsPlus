/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.homingprojectile

import org.bukkit.entity.Entity

class HomingContext(
    val target: Entity,
    val settings: HomingProjectile,
    var remainingProjectiles: Int,
)