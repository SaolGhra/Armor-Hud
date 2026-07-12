pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.6"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    create(rootProject) {
        // Root `src/` acts as the loader-agnostic 'common' project.
        // Expand this list as each version is validated (Phase 3/4).
        versions("1.21.1", "1.21.5", "1.21.11")
        branch("fabric")                                                // inherits all root versions
        branch("neoforge") { versions("1.21.1", "1.21.5", "1.21.11") }  // NeoForge exists 1.20.2+
        // branch("forge") { versions("1.20.1") } // added in Phase 4 (Forge is 1.20.1-only)
    }
}

rootProject.name = "Armor_Hud"
