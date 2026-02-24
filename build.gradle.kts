/*
 * Copyright (c) 2026. All rights reserved. Distribution of this file, similar
 * files, related files, or related projects is strictly controlled.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml

group = "com.cjcrafter"
version = "2.2.0"

plugins {
    `java-library`
    kotlin("jvm") version "1.9.22"
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
}

paperPluginYaml {
    main = "com.cjcrafter.weaponmechanicsplus.WeaponMechanicsPlus"
    name = "WeaponMechanicsPlus"
    apiVersion = "1.21"
    foliaSupported = true

    authors = listOf("DeeCaaD", "CJCrafter")
    dependencies {
        server("MechanicsCore", required = true, load = PaperPluginYaml.Load.BEFORE)
        server("WeaponMechanics", required = false, load = PaperPluginYaml.Load.BEFORE)
        server("ArmorMechanics", required = false, load = PaperPluginYaml.Load.BEFORE)
    }
}

repositories {
    mavenCentral()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots/") // MechanicsCore Snapshots
    maven(url = "https://repo.papermc.io/repository/maven-public/") // Paper
    maven(url = "https://mvn.lumine.io/repository/maven-public/") // MythicMobs
    maven(url = "https://repo.codemc.io/repository/maven-releases/") // PacketEvents
}

dependencies {
    // Core Minecraft dependencies
    compileOnly(libs.armorMechanics)
    compileOnly(libs.paper)
    compileOnly(libs.mechanicsCore)
    compileOnly(libs.weaponMechanics)
    compileOnly(libs.packetEvents)

    // External "hooks" or plugins that we might interact with
    compileOnly(libs.mythicMobs)

    // Shaded dependencies
    compileOnly(libs.bstats)
    compileOnly(libs.commandApi)
    compileOnly(libs.commandApiKotlin)
    compileOnly(libs.foliaScheduler)
}

tasks.shadowJar {
    archiveFileName.set("WeaponMechanicsPlus-${project.version}.jar")

    // the kotlin plugin adds kotlin-stdlib to the classpath, but we don't want it in the shadow jar
    exclude("kotlin/**")
    exclude("META-INF/*.kotlin_module")
    exclude("org/jetbrains/annotations/**")
    exclude("META-INF/annotations/**")

    val libPackage = "me.deecaad.core.lib"

    relocate("org.bstats", "$libPackage.bstats")
    relocate("dev.jorel.commandapi", "$libPackage.commandapi")
    relocate("com.cjcrafter.foliascheduler", "$libPackage.scheduler")
    relocate("kotlin.", "$libPackage.kotlin.")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
        options.release.set(21)
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
}

tasks.test {
    useJUnitPlatform()
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "21"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "21"
}