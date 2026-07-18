@file:Suppress("UnstableApiUsage")

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.github.johnrengelman.shadow")
    id("me.modmuss50.mod-publish-plugin")
}

val loader = prop("loom.platform")!!
val minecraft: String = stonecutter.current.version
val common: Project = requireNotNull(stonecutter.node.sibling("")?.project) {
    "No common project for $project"
}

version = "${mod.version}+$minecraft"
base {
    archivesName.set("${mod.id}-$loader")
}
architectury {
    platformSetupLoomIde()
    forge()
}

val commonBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

configurations {
    compileClasspath.get().extendsFrom(commonBundle)
    runtimeClasspath.get().extendsFrom(commonBundle)
    get("developmentForge").extendsFrom(commonBundle)
}

repositories {
    maven("https://maven.minecraftforge.net")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings(loom.officialMojangMappings())
    "forge"("net.minecraftforge:forge:$minecraft-${common.mod.dep("forge_loader")}")
    "io.github.llamalad7:mixinextras-forge:${mod.dep("mixin_extras")}".let {
        implementation(it)
        include(it)
    }

    commonBundle(project(common.path, "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionForge")) { isTransitive = false }
}

loom {
    forge {
        convertAccessWideners = true
    }

    decompilers {
        get("vineflower").apply {
            options.put("mark-corresponding-synthetics", "1")
        }
    }

    runConfigs.all {
        isIdeConfigGenerated = true
        runDir = "../../run"
        vmArgs("-Dmixin.debug.export=true")
    }
}

java {
    withSourcesJar()
    val java = if (stonecutter.eval(minecraft, ">=1.20.5"))
        JavaVersion.VERSION_21 else JavaVersion.VERSION_17
    targetCompatibility = java
    sourceCompatibility = java
}

tasks.jar {
    archiveClassifier = "dev"
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier = "dev-shadow"
    exclude("fabric.mod.json", "architectury.common.json")
}

tasks.remapJar {
    injectAccessWidener = true
    input = tasks.shadowJar.get().archiveFile
    archiveClassifier = null
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    properties(listOf("META-INF/mods.toml", "pack.mcmeta"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "minecraft" to common.mod.prop("mc_dep_forgelike")
    )
}

tasks.build {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
}

tasks.register<Copy>("buildAndCollect") {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
    from(tasks.remapJar.get().archiveFile, tasks.remapSourcesJar.get().archiveFile)
    into(rootProject.layout.buildDirectory.dir("libs/${mod.version}"))
    dependsOn("build")
}

// Modrinth publishing. Runs as a dry-run unless MODRINTH_TOKEN is set (so CI can rehearse safely).
publishMods {
    val hasToken = providers.environmentVariable("MODRINTH_TOKEN").isPresent
    dryRun = !hasToken
    file = tasks.remapJar.get().archiveFile
    type = STABLE
    displayName = "Armor HUD ${mod.version} - ${common.mod.prop("mc_title")} ($loader)"
    version = "${mod.version}+$minecraft-$loader"
    // One CHANGELOG.md drives every upload in the matrix, so a release is a single edit here
    // rather than pasting the same notes into ~37 Modrinth version pages by hand.
    changelog = rootProject.file("CHANGELOG.md").takeIf { it.exists() }?.readText()
        ?: "See https://github.com/SaolGhra/Armor-Hud/releases"
    modLoaders.add(loader)
    modrinth {
        projectId = "armor-hud"
        accessToken = providers.environmentVariable("MODRINTH_TOKEN").orElse("")
        minecraftVersions.addAll(common.mod.prop("mc_targets").split(" "))
    }
}
