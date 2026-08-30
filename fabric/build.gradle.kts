@file:Suppress("UnstableApiUsage")

import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.api.fabricapi.FabricApiExtension

plugins {
    java
    id("architectury-plugin")
    id("com.gradleup.shadow")
    id("me.modmuss50.mod-publish-plugin")
}

val loader = prop("loom.platform")!!
val minecraft: String = stonecutter.current.version
val common: Project = requireNotNull(stonecutter.node.sibling("")?.project) {
    "No common project for $project"
}

// 26.1+ is unobfuscated Minecraft — no mappings, no mod* remap configurations. See build.gradle.kts
// (the common project) for the full rationale; both loom plugin IDs are declared (apply false) in
// stonecutter.gradle.kts at one shared version.
val unobfuscated: Boolean = stonecutter.eval(minecraft, ">=26.1")
apply(plugin = if (unobfuscated) "dev.architectury.loom-no-remap" else "dev.architectury.loom-remap")
val loomExt = extensions.getByType(LoomGradleExtensionAPI::class.java)
val modDep = if (unobfuscated) "implementation" else "modImplementation"
val modCompile = if (unobfuscated) "compileOnly" else "modCompileOnly"

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
    // Mod Menu comes from Modrinth's maven rather than maven.terraformersmc.com, which truncates
    // artifact downloads ("end of response with N bytes missing") — reproducible with curl, not
    // specific to CI. A machine that built before does not notice because the jar is already in the
    // Gradle cache; any cold cache fails, which is every CI run. Note that a fallback repository
    // would NOT help here: Gradle resolves the module from the first repo that has its metadata and
    // does not try elsewhere when the artifact download itself fails.
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraft")
    if (!unobfuscated) {
        add("mappings", loomExt.officialMojangMappings())
    }
    modDep("net.fabricmc:fabric-loader:${common.mod.dep("fabric_loader")}")
    modDep("net.fabricmc.fabric-api:fabric-api:${common.mod.dep("fabric_api")}")
    // ModMenu is an OPTIONAL runtime dep (users install it themselves) and we only implement its two
    // API interfaces, so compile against it without dragging in its transitive deps: across the eight
    // ModMenu majors this matrix spans, those pull mods from mavens we otherwise don't need (e.g. 9.x
    // wants eu.pb4:placeholder-api). isTransitive=false keeps the version matrix resolvable.
    modCompile("maven.modrinth:modmenu:${common.mod.dep("modmenu")}") { isTransitive = false }

    // "namedElements" is Fabric Loom's remapped/named-jar configuration — a remap-time concept that
    // simply does not exist on an unobfuscated (loom-no-remap) common project (confirmed empirically:
    // `outgoingVariants` on an unobfuscated node lists only the plain Java plugin variants). Since
    // there is nothing to remap there, the common project's own compiled classes are ALREADY in real
    // Mojang names, so the standard "runtimeElements" variant serves the same purpose.
    // "transformProductionFabric" is architectury-plugin's own configuration (created per enabled
    // platform regardless of remap mode), so it is unaffected either way.
    commonBundle(project(common.path, if (unobfuscated) "runtimeElements" else "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionFabric")) { isTransitive = false }
}

configure<LoomGradleExtensionAPI> {
    // Decompiling is a remap-time concept (turning obfuscated names into readable ones); 26.1+ is
    // already named, so there is nothing for it to do.
    if (!unobfuscated) {
        decompilers {
            get("vineflower").apply {
                options.put("mark-corresponding-synthetics", "1")
            }
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
    val javaVersion = when {
        stonecutter.eval(minecraft, ">=26") -> 25
        stonecutter.eval(minecraft, ">=1.20.5") -> 21
        else -> 17
    }
    val java = JavaVersion.toVersion(javaVersion)
    targetCompatibility = java
    sourceCompatibility = java
    // See build.gradle.kts (the common project) — required so compilation does not depend on which
    // JDK happens to be running Gradle itself (the verify harness runs Gradle on a JDK 21, which
    // cannot target release 25 at all).
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

// Client GameTest screenshot harness — only versions whose Fabric API ships the client-gametest API.
//? if >=1.21.5 {
extensions.getByType(FabricApiExtension::class.java).configureTests {
    createSourceSet = true
    modId = "${mod.id}_test"
    enableClientGameTests = true
    eula = true
}
//?}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    // Obfuscated nodes remap this jar afterwards (remapJar consumes it and produces the final
    // artifact), so it keeps a classifier. Unobfuscated nodes have no remap step — the names are
    // already real — so this IS the final artifact and must carry no classifier.
    archiveClassifier = if (unobfuscated) null else "dev-shadow"
}

if (!unobfuscated) {
    tasks.named<RemapJarTask>("remapJar") {
        injectAccessWidener = true
        input = tasks.shadowJar.get().archiveFile
        archiveClassifier = null
        dependsOn(tasks.shadowJar)
    }
}

tasks.jar {
    archiveClassifier = "dev"
}

// The task that produces the shippable jar. Unobfuscated nodes have nothing to remap, so loom
// registers no remapJar there and shadowJar (already carrying the real Mojang names) is final.
// org.gradle.jvm.tasks.Jar, not the bundling Jar the Kotlin DSL resolves by default: loom's
// RemapJarTask (and Shadow's ShadowJar) both extend the former, which is the parent of the latter.
val productionJar = tasks.named<org.gradle.jvm.tasks.Jar>(if (unobfuscated) "shadowJar" else "remapJar")
val productionSourcesJar = tasks.named(if (unobfuscated) "sourcesJar" else "remapSourcesJar")

// The verification-only screenshot mixin is registered only in verify builds, via the same
// armorhud.verify flag that compiles the mixin CLASS in. Emits the whole "mixins" key (with trailing
// comma) so release builds — where it is empty — declare no mixins at all and stay mixin-free.
val verifyMixins = if (providers.gradleProperty("armorhud.verify").isPresent)
    "\"mixins\": [\"armor_hud-verify.mixins.json\"]," else ""

tasks.processResources {
    properties(listOf("fabric.mod.json"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "minecraft" to common.mod.prop("mc_dep_fabric"),
        "verify_mixins" to verifyMixins
    )
}

tasks.build {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
}

tasks.register<Copy>("buildAndCollect") {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
    from(productionJar.get().archiveFile, productionSourcesJar.get().outputs.files)
    // All loaders/versions collect into ONE folder. Jar names already carry loader + MC
    // (armor_hud-<loader>-<ver>+<mc>.jar), so nothing collides.
    into(rootProject.layout.buildDirectory.dir("libs/${mod.version}"))
    // Loom hooks remapJar into 'build' automatically on obfuscated nodes; unobfuscated nodes have no
    // remapJar, and shadowJar (the production jar there) is not otherwise wired to 'build', so it is
    // named explicitly rather than assumed.
    dependsOn("build", productionJar, productionSourcesJar)
}

// Modrinth publishing. Runs as a dry-run unless MODRINTH_TOKEN is set (so CI can rehearse safely).
publishMods {
    val hasToken = providers.environmentVariable("MODRINTH_TOKEN").isPresent
    dryRun = !hasToken
    file = productionJar.flatMap { it.archiveFile }
    type = STABLE
    displayName = "Armor HUD ${mod.version} - ${common.mod.prop("mc_title")} ($loader)"
    version = "${mod.version}+$minecraft-$loader"
    // Supplied at release time (Jenkins prompts for it and passes -Pchangelog=...), so one
    // entry covers every upload in the matrix instead of being pasted into ~37 version pages.
    changelog = providers.gradleProperty("changelog")
        .orElse("See https://github.com/SaolGhra/Armor-Hud/releases").get()
    modLoaders.add(loader)
    // Quilt runs this Fabric jar via its Fabric-compat layer + Quilted Fabric API (the mod is a clean
    // fit — no mixins, just HudRenderCallback + optional ModMenu, which ships Quilt builds), so the
    // Fabric artifact is offered to Quilt users too rather than maintaining a separate native module.
    modLoaders.add("quilt")
    modrinth {
        // mod-publish-plugin 0.8.4 validates this as a raw Modrinth project ID, not a slug — the
        // human-readable "armor-hud" slug fails with "armor-hud is not a valid Modrinth ID" (a local
        // IllegalArgumentException, before any network call). This is the actual base62 project ID
        // behind that slug (https://api.modrinth.com/v2/project/armor-hud -> "id":"AghHBZC5").
        projectId = "AghHBZC5"
        accessToken = providers.environmentVariable("MODRINTH_TOKEN").orElse("")
        minecraftVersions.addAll(common.mod.prop("mc_targets").split(" "))

        // Declared so launchers (Modrinth App, Prism, CurseForge) resolve these at install time
        // instead of the user finding out via a loader crash. fabric-api is a hard `depends` in
        // fabric.mod.json — the HUD hook IS Fabric API — so it is required; ModMenu is genuinely
        // optional (the HUD renders fine without it, it only surfaces the config screen), and
        // marking it required would force an install plenty of users don't want.
        // Quilt users get these via Quilted Fabric API / ModMenu's Quilt builds.
        requires { slug = "fabric-api" }
        optional { slug = "modmenu" }
    }
}
