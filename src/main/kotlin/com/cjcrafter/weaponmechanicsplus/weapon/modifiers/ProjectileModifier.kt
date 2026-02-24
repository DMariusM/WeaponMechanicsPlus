/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.modifiers

import com.cjcrafter.weaponmechanicsplus.weapon.guidedprojectile.GuidedProjectile
import com.cjcrafter.weaponmechanicsplus.weapon.homingprojectile.HomingProjectile
import me.deecaad.core.file.*
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Bouncy
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.ProjectileSettings
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Sticky
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Through
import com.cjcrafter.weaponmechanicsplus.weapon.modifiers.util.DoubleModifier
import com.cjcrafter.weaponmechanicsplus.weapon.modifiers.util.IntegerModifier
import kotlin.jvm.optionals.getOrNull

class ProjectileModifier : Serializer<ProjectileModifier> {

    // If overrideProjectileSettings is used (not null), none of the variables
    // in the group below are used.
    var overrideProjectileSettings: ProjectileSettings? = null
    var gravity: DoubleModifier? = null
    var minimumSpeed: DoubleModifier? = null
    var maximumSpeed: DoubleModifier? = null
    var decrease: DoubleModifier? = null
    var decreaseInWater: DoubleModifier? = null
    var decreaseWhenRainingOrSnowing: DoubleModifier? = null
    var maxAliveTicks: IntegerModifier? = null

    var overrideSticky: Sticky? = null

    // If overrideThrough is used (not null), none of the variables in the group
    // below are used.
    var overrideThrough: Through? = null
    var maximumThroughAmount: DoubleModifier? = null

    // If overrideBouncy is used (not null), none of the variables in the group
    // below are used.
    var overrideBouncy: Bouncy? = null
    var maximumBounceAmount: IntegerModifier? = null

    // The new features requested by CJ (he said it would be fun so blame him if something goes haywire! :) )
    var homingProjectile : HomingProjectile? = null
    var guidedProjectile : GuidedProjectile? = null

    /**
     * Default constructor for serializer
     */
    constructor()

    constructor(
        overrideProjectileSettings: ProjectileSettings?,
        gravity: DoubleModifier?,
        minimumSpeed: DoubleModifier?,
        maximumSpeed: DoubleModifier?,
        decrease: DoubleModifier?,
        decreaseInWater: DoubleModifier?,
        decreaseWhenRainingOrSnowing: DoubleModifier?,
        maxAliveTicks: IntegerModifier?,
        overrideSticky: Sticky?,
        overrideThrough: Through?,
        maximumThroughAmount: DoubleModifier?,
        overrideBouncy: Bouncy?,
        maximumBounceAmount: IntegerModifier?
    ) {
        this.overrideProjectileSettings = overrideProjectileSettings
        this.gravity = gravity
        this.minimumSpeed = minimumSpeed
        this.maximumSpeed = maximumSpeed
        this.decrease = decrease
        this.decreaseInWater = decreaseInWater
        this.decreaseWhenRainingOrSnowing = decreaseWhenRainingOrSnowing
        this.maxAliveTicks = maxAliveTicks
        this.overrideSticky = overrideSticky
        this.overrideThrough = overrideThrough
        this.maximumThroughAmount = maximumThroughAmount
        this.overrideBouncy = overrideBouncy
        this.maximumBounceAmount = maximumBounceAmount
    }


    @Throws(SerializerException::class)
    override fun serialize(data: SerializeData): ProjectileModifier {

        val overrideProjectileSettings: ProjectileSettings? = data.of("Override_Projectile_Settings").serialize(ProjectileSettings::class.java).getOrNull()
        val gravity = data.of("Gravity").serialize(DoubleModifier::class.java).getOrNull()
        val minimumSpeed = data.of("Minimum_Speed").serialize(DoubleModifier::class.java).getOrNull()
        val maximumSpeed = data.of("Maximum_Speed").serialize(DoubleModifier::class.java).getOrNull()
        val decrease = data.of("Drag.Base").serialize(DoubleModifier::class.java).getOrNull()
        val decreaseInWater = data.of("Drag.In_Water").serialize(DoubleModifier::class.java).getOrNull()
        val decreaseWhenRainingOrSnowing = data.of("Drag.When_Raining_Or_Snowing").serialize(DoubleModifier::class.java).getOrNull()
        val maxAliveTicks = data.of("Maximum_Alive_Ticks").serialize(IntegerModifier::class.java).getOrNull()

        val overrideSticky: Sticky? = data.of("Override_Sticky").serialize(Sticky::class.java).getOrNull()

        val overrideThrough: Through? = data.of("Override_Through").serialize(Through::class.java).getOrNull()
        val maximumThroughAmount = data.of("Maximum_Through_Amount").serialize(DoubleModifier::class.java).getOrNull()

        val overrideBouncy: Bouncy? = data.of("Override_Bouncy").serialize(Bouncy::class.java).getOrNull()
        val maximumBounceAmount = data.of("Maximum_Bounce_Amount").serialize(IntegerModifier::class.java).getOrNull()

        // These aren't "physics" modifiers, but projectile-behavior scripts
        val guided = if (data.has("Guided_Projectile")) {
            data.of("Guided_Projectile").serialize(GuidedProjectile::class.java).getOrNull()
        } else null

        val homing = if (data.has("Homing_Projectiles")) {
            data.of("Homing_Projectiles").serialize(HomingProjectile::class.java).getOrNull()
        } else null

        return ProjectileModifier(
            overrideProjectileSettings,
            gravity,
            minimumSpeed,
            maximumSpeed,
            decrease,
            decreaseInWater,
            decreaseWhenRainingOrSnowing,
            maxAliveTicks,
            overrideSticky,
            overrideThrough,
            maximumThroughAmount,
            overrideBouncy,
            maximumBounceAmount
        ).also {
            it.guidedProjectile = guided
            it.homingProjectile = homing
        }
    }
}