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
    // Without this, Gradle can only use a JDK it finds already installed for a toolchain request
    // (e.g. Forge 1.20.1's Java 17 compile target) and fails outright on an agent that lacks one —
    // this is what broke CI build 130 after the loom 1.17.491 bump started requiring an exact-match
    // JDK 17 toolchain for its Forge tooling. This lets Gradle download whichever JDK a node needs.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true
    create(rootProject) {
        // Root `src/` acts as the loader-agnostic 'common' project.
        // Expand this list as each version is validated (Phase 3/4).
        versions(
            "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
            "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4",
            "1.21.5", "1.21.6", "1.21.7", "1.21.8",
            "1.21.9", "1.21.10", "1.21.11",
            // 26.1+ is unobfuscated Minecraft (no Mojang mappings, no Fabric intermediary) — each of
            // these nodes flips to dev.architectury.loom-no-remap instead of loom-remap (see
            // stonecutter.gradle.kts / build.gradle.kts). Forge is NOT extended here: it stays
            // 1.20.1-only, permanently.
            "26.1", "26.1.1", "26.1.2", "26.2",
        )
        branch("fabric") // inherits all root versions
        // NeoForge everywhere it exists (starts at 1.20.2). Where a released MC only has a NeoForge
        // beta (1.20.3/1.20.5/1.21.2/1.21.6/1.21.7/1.21.9, and 26.1/26.1.1 below), that beta is the
        // shipping loader — the only option. The entrypoint has two forms: modern
        // IConfigScreenFactory + @Mod(dist=…) for >=1.20.5, and the legacy Forge-style
        // ConfigScreenHandler + DistExecutor for <=1.20.4 — see the Stonecutter guard in
        // ArmorHudNeoForge.
        branch("neoforge") {
            versions(
                "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
                "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4",
                "1.21.5", "1.21.6", "1.21.7", "1.21.8",
                "1.21.9", "1.21.10", "1.21.11",
                "26.1", "26.1.1", "26.1.2", "26.2",
            )
        }
        branch("forge") { versions("1.20.1") }               // Forge is 1.20.1-only
        // 1.21.9+ reorganised many Mojmap packages (RenderType -> ...renderer.rendertype, etc.)
        // as prep for the 26.x unobfuscation; they need a package-move guard pass (see version-guards).
        // branch("forge") { versions("1.20.1") } // added in Phase 4 (Forge is 1.20.1-only)
    }
}

rootProject.name = "Armor_Hud"
