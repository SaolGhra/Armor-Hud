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
        versions("1.20.1", "1.21.1", "1.21.5")
        branch("fabric")                                     // inherits all root versions
        branch("neoforge") { versions("1.21.1", "1.21.5") }  // NeoForge exists 1.20.2+
        branch("forge") { versions("1.20.1") }               // Forge is 1.20.1-only
        // 1.21.9+ reorganised many Mojmap packages (RenderType -> ...renderer.rendertype, etc.)
        // as prep for the 26.x unobfuscation; they need a package-move guard pass (see version-guards).
        // branch("forge") { versions("1.20.1") } // added in Phase 4 (Forge is 1.20.1-only)
    }
}

rootProject.name = "Armor_Hud"
