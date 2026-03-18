/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus.weapon.thermal

import com.cjcrafter.foliascheduler.TaskImplementation
import com.cjcrafter.weaponmechanicsplus.WeaponMechanicsPlus
import com.cjcrafter.weaponmechanicsplus.weapon.modifiers.util.Whitelist
import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.util.Vector
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

class ThermalScopeManager(private val plugin: WeaponMechanicsPlus) : Listener {

    private val tasks = ConcurrentHashMap<UUID, TaskImplementation<*>>() // generic doesn't matter for cancel()
    private val lastGlowing = ConcurrentHashMap<UUID, MutableSet<Int>>() // viewer -> entityIds
    private val settingsByViewer = ConcurrentHashMap<UUID, ThermalScopeSettings>()

    // Baseline flags byte (entity metadata index 0) per viewer, per entity
    private val baseFlagsPerViewer = ConcurrentHashMap<UUID, ConcurrentHashMap<Int, Byte>>()

    // Weak refs for fallback base recompute
    private val seenEntities = ConcurrentHashMap<UUID, ConcurrentHashMap<Int, WeakReference<Entity>>>()

    // Suppress baseline capture for our own packets: viewer -> set(entityId)
    private val suppressCapture = ConcurrentHashMap<UUID, MutableSet<Int>>()

    init {
        registerPacketTap()
    }

    fun onScopeStartOrStack(viewer: Player, settings: ThermalScopeSettings) {
        val id = viewer.uniqueId
        settingsByViewer[id] = settings

        // Don’t use computeIfAbsent because runAtFixedRate returns nullable
        if (tasks.containsKey(id)) return

        val period = settings.tickInterval.coerceAtLeast(1).toLong()
        val task = plugin.foliaScheduler.entity(viewer).runAtFixedRate(Runnable {
            if (!viewer.isOnline) {
                cleanup(viewer)
                return@Runnable
            }
            val s = settingsByViewer[id] ?: run {
                cleanup(viewer)
                return@Runnable
            }
            updateGlows(viewer, s)
        }, 1L, period) ?: return

        tasks[id] = task
    }

    fun onScopeEnd(viewer: Player) {
        cleanup(viewer)
    }

    private fun updateGlows(viewer: Player, settings: ThermalScopeSettings) {
        val viewerId = viewer.uniqueId
        val filter = buildTargetFilter(viewer, settings)
        val now = HashSet<Int>()

        when (settings.mode) {
            ThermalScopeSettings.DetectMode.SPHERE -> {
                val eye = viewer.eyeLocation
                val r = settings.distance.toDouble()

                val near = viewer.world.getNearbyEntities(viewer.location, r, r, r, filter)
                for (target in near) {
                    val to = when (target) {
                        is LivingEntity -> target.eyeLocation
                        else -> target.location.add(0.0, target.height * 0.5, 0.0)
                    }

                    val delta = to.toVector().subtract(eye.toVector())
                    val dist = delta.length()
                    if (dist <= 0.001) continue

                    val dir = delta.multiply(1.0 / dist)

                    if (isBlockedByBlocks(viewer.world, eye, dir, dist, settings.blockFilter)) continue

                    val eid = target.entityId
                    now += eid
                    seenEntities.computeIfAbsent(viewerId) { ConcurrentHashMap() }[eid] = WeakReference(target)
                    sendGlowPacket(viewer, eid, true)
                }
            }

            ThermalScopeSettings.DetectMode.RAY -> {
                val eye = viewer.eyeLocation
                val dir = eye.direction.normalize()

                val hit = viewer.world.rayTraceEntities(
                    eye, dir, settings.distance.toDouble(), settings.raySize, filter
                )

                val target = hit?.hitEntity

                if (target != null) {
                    val entDist = hit.hitPosition.distance(eye.toVector())

                    if (!isBlockedByBlocks(viewer.world, eye, dir, entDist, settings.blockFilter)) {
                        val eid = target.entityId
                        now += eid
                        seenEntities.computeIfAbsent(viewerId) { ConcurrentHashMap() }[eid] = WeakReference(target)
                        sendGlowPacket(viewer, eid, true)
                    }
                }
            }
        }

        val prev = lastGlowing[viewerId].orEmpty()
        for (oldId in prev) {
            if (!now.contains(oldId)) sendGlowPacket(viewer, oldId, false)
        }
        lastGlowing[viewerId] = now
    }

    private fun buildTargetFilter(viewer: Player, settings: ThermalScopeSettings): Predicate<Entity> {
        val filter = settings.typeFilter
        return Predicate { ent ->
            if (ent === viewer) return@Predicate false

            // If the filter is empty, we just allow any living entity
            if (filter == null) return@Predicate ent is LivingEntity

            filter.isWhitelisted(ent.type)
        }
    }

    private fun sendGlowPacket(receiver: Player, entityId: Int, glow: Boolean) {
        val viewerId = receiver.uniqueId

        val baseMap = baseFlagsPerViewer.computeIfAbsent(viewerId) { ConcurrentHashMap() }
        val base: Byte = baseMap[entityId] ?: run {
            val ent = seenEntities[viewerId]?.get(entityId)?.get()
            val computed = if (ent != null) computeBaseFlags(ent) else 0.toByte()
            baseMap.putIfAbsent(entityId, computed)
            computed
        }

        val flags: Byte = if (glow) (base.toInt() or 0x40).toByte() else base

        // Suppress capture of our own packet so baseline doesn't get overwritten by thermal
        val sup = suppressCapture.computeIfAbsent(viewerId) { ConcurrentHashMap.newKeySet() }
        sup.add(entityId)
        plugin.foliaScheduler.entity(receiver).runDelayed(Runnable { sup.remove(entityId) }, 1L)

        val flagEntry = EntityData(0, EntityDataTypes.BYTE, flags)
        val meta = WrapperPlayServerEntityMetadata(entityId, listOf(flagEntry))
        PacketEvents.getAPI().playerManager.sendPacket(receiver, meta)
    }

    private fun computeBaseFlags(e: Entity): Byte {
        var flags = 0

        // 0x01: on fire (visual)
        if (e.fireTicks > 0) flags = flags or 0x01

        if (e is Player) {
            if (e.isSneaking) flags = flags or 0x02 // 0x02: sneaking
            if (e.isSprinting) flags = flags or 0x08 // 0x08: sprinting
            if (e.isSwimming) flags = flags or 0x10 // 0x10: swimming
            if (e.isGliding) flags = flags or 0x80 // 0x80: fall-flying (elytra)
        }

        // 0x20: invisible (any living)
        if (e is LivingEntity && e.isInvisible) flags = flags or 0x20

        return flags.toByte()
    }

    private fun registerPacketTap() {
        PacketEvents.getAPI().eventManager.registerListener(object : PacketListener {
            override fun onPacketSend(event: PacketSendEvent) {
                if (event.packetType != PacketType.Play.Server.ENTITY_METADATA) return

                val viewerId: UUID = event.user.uuid ?: return
                val wrapper = WrapperPlayServerEntityMetadata(event)
                val eid = wrapper.entityId

                // Ignore metadata we just sent ourselves
                if (suppressCapture[viewerId]?.contains(eid) == true) return

                // Capture baseline
                for (data in wrapper.entityMetadata) {
                    if (data.index == 0 && data.type == EntityDataTypes.BYTE) {
                        val b = data.value as Byte
                        baseFlagsPerViewer.computeIfAbsent(viewerId) { ConcurrentHashMap() }[eid] = b
                        break
                    }
                }

                // If we currently want this entity glowing for this viewer, re-apply after vanilla updates
                if (lastGlowing[viewerId]?.contains(eid) == true) {
                    val viewer = Bukkit.getPlayer(viewerId) ?: return
                    sendGlowPacket(viewer, eid, true)
                }
            }
        }, PacketListenerPriority.NORMAL)
    }

    private fun cleanup(viewer: Player) {
        val id = viewer.uniqueId
        tasks.remove(id)?.cancel()

        // Clear glows with a couple retries
        val snapshot = HashSet(lastGlowing[id].orEmpty())
        for (eid in snapshot) sendGlowPacket(viewer, eid, false)
        plugin.foliaScheduler.entity(viewer).run(Runnable {
            for (eid in snapshot) sendGlowPacket(viewer, eid, false)
        })
        plugin.foliaScheduler.entity(viewer).runDelayed(Runnable {
            for (eid in snapshot) sendGlowPacket(viewer, eid, false)
        }, 10L)

        lastGlowing.remove(id)
        settingsByViewer.remove(id)
        seenEntities.remove(id)
        // Should we keep baseFlagsPerViewer or do we want it strictly per-session? not sure...
        // baseFlagsPerViewer.remove(id)
    }


    private fun isBlockedByBlocks(
        world: World,
        start: Location,
        dir: Vector,
        maxDistance: Double,
        blockFilter: Whitelist<Material>?
    ): Boolean {
        if (maxDistance <= 0.0) return false

        // No block filter configured -> thermal ignores blocks by default
        if (blockFilter == null) {
            return false
        }

        var cursor = start.clone()
        var remaining = maxDistance

        while (remaining > 0.0) {
            val hit = world.rayTraceBlocks(cursor, dir, remaining, FluidCollisionMode.NEVER, true) ?: return false
            val hitBlock = hit.hitBlock ?: return false

            val mat = hitBlock.type
            val hitPos = hit.hitPosition.toLocation(world)

            val travelled = hitPos.distance(cursor)
            if (travelled <= 0.0 || travelled.isNaN()) return true

            // If this block is considered "blocking thermal", we are occluded
            if (blockFilter.isWhitelisted(mat)) return true

            // Otherwise skip past it and continue tracing
            val step = 0.05
            cursor = hitPos.add(dir.clone().multiply(step))
            remaining -= (travelled + step)
        }

        return false
    }

    @EventHandler fun onQuit(e: PlayerQuitEvent) = cleanup(e.player)
    @EventHandler fun onDeath(e: PlayerDeathEvent) = cleanup(e.entity)
    @EventHandler fun onRespawn(e: PlayerRespawnEvent) = cleanup(e.player)
}