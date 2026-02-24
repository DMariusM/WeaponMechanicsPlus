/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.homingprojectile

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

object HomingTargetHolder {

    private val map = ConcurrentHashMap<UUID, ConcurrentLinkedDeque<HomingContext>>()

    fun register(plugin: Plugin, shooterId: UUID, ctx: HomingContext) {
        map.computeIfAbsent(shooterId) { ConcurrentLinkedDeque() }.addLast(ctx)

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val q = map[shooterId] ?: return@Runnable
            q.remove(ctx)
            if (q.isEmpty()) map.remove(shooterId, q)
        }, 10L) // 10 ticks should be sufficient so that it properly attaches (in case of lag...)
    }

    fun consumeOne(shooterId: UUID): HomingContext? {
        val q = map[shooterId] ?: return null
        val ctx = q.peekFirst() ?: return null

        ctx.remainingProjectiles -= 1
        if (ctx.remainingProjectiles <= 0) {
            q.pollFirst()
            if (q.isEmpty()) map.remove(shooterId, q)
        }
        return ctx
    }
}