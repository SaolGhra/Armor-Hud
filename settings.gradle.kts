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
        versions(
            "1.20.1", "1.20.5", "1.20.6",
            "1.21.1", "1.21.2", "1.21.3", "1.21.4",
            "1.21.5", "1.21.6", "1.21.7", "1.21.8",
        )
        branch("fabric") // inherits all root versions
        // NeoForge only where it has a STABLE release: 20.5/21.2/21.6/21.7 never left beta.
        branch("neoforge") { versions("1.20.6", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8") }
        branch("forge") { versions("1.20.1") }               // Forge is 1.20.1-only
        // 1.21.9+ reorganised many Mojmap packages (RenderType -> ...renderer.rendertype, etc.)
        // as prep for the 26.x unobfuscation; they need a package-move guard pass (see version-guards).
        // branch("forge") { versions("1.20.1") } // added in Phase 4 (Forge is 1.20.1-only)
    }
}

rootProject.name = "Armor_Hud"
