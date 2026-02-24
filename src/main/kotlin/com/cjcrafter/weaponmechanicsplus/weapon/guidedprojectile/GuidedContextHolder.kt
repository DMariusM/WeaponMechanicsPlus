/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.guidedprojectile

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

object GuidedContextHolder {

    private val map = ConcurrentHashMap<UUID, ConcurrentLinkedDeque<GuidedContext>>()

    fun register(plugin: Plugin, shooterId: UUID, ctx: GuidedContext) {
        map.computeIfAbsent(shooterId) { ConcurrentLinkedDeque() }.addLast(ctx)

        // Small TTL so configs/attachments can be captured at shoot-time while still allowing
        // a little breathing room for burst/shotgun scheduling or minor lag
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val q = map[shooterId] ?: return@Runnable
            q.remove(ctx)
            if (q.isEmpty()) map.remove(shooterId, q)
        }, 10L) // 10 ticks should be enough
    }

    fun consumeOne(shooterId: UUID): GuidedContext? {
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