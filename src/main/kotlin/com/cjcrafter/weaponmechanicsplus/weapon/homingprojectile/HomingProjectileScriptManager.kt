/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.homingprojectile

import me.deecaad.weaponmechanics.weapon.projectile.AProjectile
import me.deecaad.weaponmechanics.weapon.projectile.ProjectileScriptManager
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile
import org.bukkit.plugin.Plugin

class HomingProjectileScriptManager(plugin: Plugin) : ProjectileScriptManager(plugin) {

    override fun attach(projectile: AProjectile) {
        if (projectile !is WeaponProjectile) return
        if (projectile.getTag("wmp_homing") != null) return

        val shooter = projectile.shooter ?: return

        val ctx = HomingTargetHolder.consumeOne(shooter.uniqueId) ?: return
        projectile.setTag("wmp_homing", "1")
        projectile.addProjectileScript(HomingProjectileScript(plugin, projectile, ctx))
    }
}