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
    neoForge()
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
    get("developmentNeoForge").extendsFrom(commonBundle)
}

repositories {
    maven("https://maven.neoforged.net/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    // NeoForge is natively Mojmap — no Yarn or mapping patch needed.
    mappings(loom.officialMojangMappings())
    "neoForge"("net.neoforged:neoforge:${common.mod.dep("neoforge_loader")}")
    "io.github.llamalad7:mixinextras-neoforge:${mod.dep("mixin_extras")}".let {
        implementation(it)
        include(it)
    }

    commonBundle(project(common.path, "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionNeoForge")) { isTransitive = false }
}

loom {
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

// Require the NeoForge line this jar was actually built against: e.g. loader 21.0.167 -> "[21.0,)",
// 21.9.16-beta -> "[21.9,)". A hardcoded range would (wrongly) reject the jar on 1.21/1.20.6 whose
// NeoForge is <21.1.
val neoforgeParts = common.mod.dep("neoforge_loader").substringBefore("-").split(".")
val neoforgeRange = "[" + neoforgeParts.take(2).joinToString(".") + ",)"

// NeoForge renamed the mod manifest META-INF/mods.toml -> META-INF/neoforge.mods.toml at 20.5. On
// 20.2-20.4 the loader only reads the OLD name, so shipping neoforge.mods.toml there loads NO mod (the
// HUD silently never registers). Emit the legacy filename for those nodes. (The TOML *content* is the
// same javafml schema both eras understand — only the filename differs.)
val nfMajor = neoforgeParts[0].toInt()
val nfMinor = neoforgeParts.getOrElse(1) { "0" }.toInt()
val legacyManifest = nfMajor < 20 || (nfMajor == 20 && nfMinor < 5)   // filename flipped at 20.5

// The dependency declaration flipped a version earlier, at 20.4: NeoForge 20.2/20.3 use the old Forge
// schema `mandatory = true`, and reject a block that has `type` but no `mandatory` (InvalidModFileException
// "Missing required field mandatory"); 20.4+ use `type = "required"`. Emit the era-correct field.
val legacyDeps = nfMajor < 20 || (nfMajor == 20 && nfMinor < 4)
val requiredField = if (legacyDeps) "mandatory = true" else "type = \"required\""

tasks.processResources {
    properties(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "minecraft" to common.mod.prop("mc_dep_forgelike"),
        "neoforge" to neoforgeRange,
        "required" to requiredField
    )
    if (legacyManifest) {
        rename("""neoforge\.mods\.toml""", "mods.toml")
    }
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
    changelog = "See https://github.com/SaolGhra/Armor-Hud/releases"
    modLoaders.add(loader)
    modrinth {
        projectId = "armor-hud"
        accessToken = providers.environmentVariable("MODRINTH_TOKEN").orElse("")
        minecraftVersions.addAll(common.mod.prop("mc_targets").split(" "))
    }
}
