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
    fabric()
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
    get("developmentFabric").extendsFrom(commonBundle)
}

repositories {
    maven("https://maven.terraformersmc.com/releases")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${common.mod.dep("fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${common.mod.dep("fabric_api")}")
    // ModMenu is an OPTIONAL runtime dep (users install it themselves) and we only implement its two
    // API interfaces, so compile against it without dragging in its transitive deps: across the eight
    // ModMenu majors this matrix spans, those pull mods from mavens we otherwise don't need (e.g. 9.x
    // wants eu.pb4:placeholder-api). isTransitive=false keeps the version matrix resolvable.
    modCompileOnly("com.terraformersmc:modmenu:${common.mod.dep("modmenu")}") { isTransitive = false }

    commonBundle(project(common.path, "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionFabric")) { isTransitive = false }
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

// Client GameTest screenshot harness — only versions whose Fabric API ships the client-gametest API.
//? if >=1.21.5 {
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "${mod.id}_test"
        enableClientGameTests = true
        eula = true
    }
}
//?}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier = "dev-shadow"
}

tasks.remapJar {
    injectAccessWidener = true
    input = tasks.shadowJar.get().archiveFile
    archiveClassifier = null
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier = "dev"
}

tasks.processResources {
    properties(listOf("fabric.mod.json"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "minecraft" to common.mod.prop("mc_dep_fabric")
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
    // All loaders/versions collect into ONE folder. Jar names already carry loader + MC
    // (armor_hud-<loader>-<ver>+<mc>.jar), so nothing collides.
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
    // Quilt runs this Fabric jar via its Fabric-compat layer + Quilted Fabric API (the mod is a clean
    // fit — no mixins, just HudRenderCallback + optional ModMenu, which ships Quilt builds), so the
    // Fabric artifact is offered to Quilt users too rather than maintaining a separate native module.
    modLoaders.add("quilt")
    modrinth {
        projectId = "armor-hud"
        accessToken = providers.environmentVariable("MODRINTH_TOKEN").orElse("")
        minecraftVersions.addAll(common.mod.prop("mc_targets").split(" "))
    }
}
