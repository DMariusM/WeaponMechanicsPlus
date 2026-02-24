/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

package com.cjcrafter.weaponmechanicsplus

import com.cjcrafter.foliascheduler.util.ReflectionUtil
import com.cjcrafter.weaponmechanicsplus.listeners.*
import com.cjcrafter.weaponmechanicsplus.placeholders.ArmorMechanicsPlaceholderListener
import com.cjcrafter.weaponmechanicsplus.placeholders.WeaponMechanicsPlaceholderListener
import com.cjcrafter.weaponmechanicsplus.weapon.firemode.FireModeTriggerListener
import com.cjcrafter.weaponmechanicsplus.weapon.listeners.AttractMobsListener
import com.cjcrafter.weaponmechanicsplus.weapon.modifiers.attachments.Attachment
import com.cjcrafter.weaponmechanicsplus.weapon.thermal.ThermalScopeManager
import me.deecaad.core.MechanicsPlugin
import me.deecaad.core.events.QueueSerializerEvent
import me.deecaad.core.file.Configuration
import me.deecaad.core.file.IValidator
import me.deecaad.core.file.JarInstancer
import me.deecaad.core.file.JarSearcher
import me.deecaad.core.file.RootFileReader
import me.deecaad.core.file.SearchMode
import me.deecaad.core.file.SerializerInstancer
import me.deecaad.core.mechanics.Conditions
import me.deecaad.core.mechanics.Mechanics
import me.deecaad.core.mechanics.Targeters
import me.deecaad.core.mechanics.conditions.Condition
import me.deecaad.core.mechanics.defaultmechanics.Mechanic
import me.deecaad.core.mechanics.targeters.Targeter
import me.deecaad.core.placeholder.PlaceholderHandler
import me.deecaad.core.placeholder.PlaceholderHandlers
import me.deecaad.weaponmechanics.WeaponMechanics
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.jar.JarFile

class WeaponMechanicsPlus : MechanicsPlugin(bStatsId = 16382) {

    lateinit var attachmentConfiguration: Configuration
        private set

    init {
        INSTANCE = this
    }


    override fun onLoad() {
        val searcher = JarSearcher(JarFile(file))

        searcher.findAllSubclasses(PlaceholderHandler::class.java, classLoader, SearchMode.ON_DEMAND)
            .map { ReflectionUtil.getConstructor(it).newInstance() }
            .forEach { PlaceholderHandlers.REGISTRY.add(it) }
        searcher.findAllSubclasses(Mechanic::class.java, classLoader, SearchMode.ON_DEMAND)
            .map { ReflectionUtil.getConstructor(it).newInstance() }
            .forEach { Mechanics.REGISTRY.add(it) }
        searcher.findAllSubclasses(Targeter::class.java, classLoader, SearchMode.ON_DEMAND)
            .map { ReflectionUtil.getConstructor(it).newInstance() }
            .forEach { Targeters.REGISTRY.add(it) }
        searcher.findAllSubclasses(Condition::class.java, classLoader, SearchMode.ON_DEMAND)
            .map { ReflectionUtil.getConstructor(it).newInstance() }
            .forEach { Conditions.REGISTRY.add(it) }

        super.onLoad()
    }

    override fun handleCommands(): CompletableFuture<Void> {
        Command.register()
        return super.handleCommands()
    }

    override fun handleConfigs(): CompletableFuture<Void> {
        val jar = JarFile(file)
        val validators = JarInstancer(jar).createAllInstances(IValidator::class.java, classLoader, SearchMode.ON_DEMAND)
        val serializers = SerializerInstancer(jar).createAllInstances(classLoader, SearchMode.ON_DEMAND)

        val event = QueueSerializerEvent(this, dataFolder)
        event.addValidators(validators)
        event.addSerializers(serializers)
        server.pluginManager.callEvent(event)

        val weaponMechanics = WeaponMechanics.getInstance()
        attachmentConfiguration = RootFileReader(weaponMechanics.dataFolder, debugger, classLoader, Attachment::class.java, "attachments")
            .withSerializers(serializers)
            .withValidators(validators)
            .assertFiles()
            .read()
        val attachments = attachmentConfiguration.values().count { it is Attachment }
        debugger.info("Loaded $attachments attachments")

        return super.handleConfigs()
    }

    override fun handleListeners(): CompletableFuture<Void> {
        val plugin = this
        server.pluginManager.run {
            registerEvents(AddAttachment(), plugin)
            registerEvents(ModifierListeners(), plugin)
            registerEvents(PlaceholderListeners(), plugin)
            registerEvents(WeaponGenerateListener(), plugin)
            registerEvents(AttractMobsListener(), plugin)
            registerEvents(createWeaponMechanicsReloadListener(), plugin)

            val thermalManager = ThermalScopeManager(this@WeaponMechanicsPlus)
            registerEvents(thermalManager, plugin)
            registerEvents(ThermalScopeListener(thermalManager), plugin)

            if (getPlugin("ArmorMechanics") != null) {
                registerEvents(ArmorModifierListeners(), plugin)
                registerEvents(ArmorGenerateListener(), plugin)
            }

            // Do this on a delay, since we need fully serialized configs to list
            // weapons/armors. 2 ticks should be enough for both, but we do 5 here.
            foliaScheduler.global().runDelayed(Runnable {
                registerEvents(WeaponMechanicsPlaceholderListener(), plugin)
                if (getPlugin("ArmorMechanics") != null)
                    registerEvents(ArmorMechanicsPlaceholderListener(), plugin)
            }, 5L)
        }
        return super.handleListeners()
    }

    private fun createWeaponMechanicsReloadListener(): Listener {
        return object : Listener {
            @EventHandler
            fun onConfigSerialize(event: QueueSerializerEvent) {
                // Perfect place to register all things to WM ;D
                if (event.sourceName != "WeaponMechanics") return

                // Register serializers
                try {
                    val jar = JarFile(file)
                    event.addValidators(JarInstancer(jar).createAllInstances(IValidator::class.java, classLoader, SearchMode.ON_DEMAND))
                    event.addSerializers(SerializerInstancer(jar).createAllInstances(classLoader, SearchMode.ON_DEMAND))
                } catch (e: IOException) {
                    debugger.severe("Failed to add validators/serializers...", e)
                }

                // Register trigger listeners
                val weaponHandler = WeaponMechanics.getInstance().getWeaponHandler()
                weaponHandler.addTriggerListener(FireModeTriggerListener())

                // Register projectile script manager
                val projectilesRunnable = WeaponMechanics.getInstance().getProjectileSpawner()
                projectilesRunnable.addScriptManager(ProjectileScriptManager(this@WeaponMechanicsPlus))
            }
        }
    }

    companion object {
        private lateinit var INSTANCE: WeaponMechanicsPlus

        @JvmStatic
        fun getInstance(): WeaponMechanicsPlus {
            return INSTANCE
        }
    }
}