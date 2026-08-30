@file:Suppress("UnstableApiUsage")

import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask

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

// 26.1+ is unobfuscated Minecraft — no mappings dependency, nothing to remap. See build.gradle.kts
// (the common project) for the full rationale; both loom plugin IDs are declared (apply false) in
// stonecutter.gradle.kts at one shared version. NeoForge deps are never remapped either way (they
// already ship real names), so only the mappings dependency itself is conditional below.
val unobfuscated: Boolean = stonecutter.eval(minecraft, ">=26.1")
apply(plugin = if (unobfuscated) "dev.architectury.loom-no-remap" else "dev.architectury.loom-remap")
val loomExt = extensions.getByType(LoomGradleExtensionAPI::class.java)

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
    "minecraft"("com.mojang:minecraft:$minecraft")
    // NeoForge is natively Mojmap — no Yarn or mapping patch needed. 26.1+ is unobfuscated (no
    // mappings exist to give loom at all), so this is skipped entirely there.
    if (!unobfuscated) {
        add("mappings", loomExt.officialMojangMappings())
    }
    "neoForge"("net.neoforged:neoforge:${common.mod.dep("neoforge_loader")}")
    "io.github.llamalad7:mixinextras-neoforge:${mod.dep("mixin_extras")}".let {
        implementation(it)
        add("include", it)
    }

    // "namedElements" is Fabric Loom's remapped/named-jar configuration — a remap-time concept that
    // does not exist on an unobfuscated (loom-no-remap) common project (confirmed empirically via
    // `outgoingVariants`, which lists only the plain Java plugin variants there). Since there is
    // nothing to remap, the common project's own compiled classes are already in real Mojang names,
    // so the standard "runtimeElements" variant serves the same purpose. "transformProductionNeoForge"
    // is architectury-plugin's own configuration (created per enabled platform regardless of remap
    // mode), so it is unaffected either way.
    commonBundle(project(common.path, if (unobfuscated) "runtimeElements" else "namedElements")) { isTransitive = false }
    shadowBundle(project(common.path, "transformProductionNeoForge")) { isTransitive = false }
}

configure<LoomGradleExtensionAPI> {
    // Decompiling is a remap-time concept; 26.1+ is already named, so there is nothing to do.
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

tasks.jar {
    archiveClassifier = "dev"
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    // Obfuscated nodes remap this jar afterwards; unobfuscated nodes have no remap step, so this IS
    // the final artifact and must carry no classifier (see fabric/build.gradle.kts for the same rule).
    archiveClassifier = if (unobfuscated) null else "dev-shadow"
    exclude("fabric.mod.json", "architectury.common.json")
}

if (!unobfuscated) {
    tasks.named<RemapJarTask>("remapJar") {
        injectAccessWidener = true
        input = tasks.shadowJar.get().archiveFile
        archiveClassifier = null
        dependsOn(tasks.shadowJar)
    }
}

// The task that produces the shippable jar — see fabric/build.gradle.kts for the full rationale.
val productionJar = tasks.named<org.gradle.jvm.tasks.Jar>(if (unobfuscated) "shadowJar" else "remapJar")
val productionSourcesJar = tasks.named(if (unobfuscated) "sourcesJar" else "remapSourcesJar")

// Require the NeoForge line this jar was actually built against: e.g. loader 21.0.167 -> "[21.0,)",
// 21.9.16-beta -> "[21.9,)", 26.1.2.100 -> "[26.1,)". A hardcoded range would (wrongly) reject the
// jar on 1.21/1.20.6 whose NeoForge is <21.1. Always exactly 2 components — this is deliberately the
// same coarse "major.minor" precision the pre-26 range has always used (and what
// scripts/verify/audit_jars.py's expect_nf_range() checks against); a finer 3-component range for
// 26.1.1/26.1.2 (whose loader version mirrors the full 3-part MC version) was tried and reverted —
// it is more precise but the verification tooling was not updated to match it, and is out of reach
// to update from here.
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

// The verification-only screenshot mixin is registered only in verify builds, via the same
// armorhud.verify flag that compiles the mixin CLASS in. Templated through the manifest rather than
// Stonecutter-guarded because Stonecutter does not toggle this .toml. Release: empty, no mixin.
val verifyMixins = if (providers.gradleProperty("armorhud.verify").isPresent)
    "[[mixins]]\nconfig = \"armor_hud-verify.mixins.json\"" else ""

tasks.processResources {
    properties(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "minecraft" to common.mod.prop("mc_dep_forgelike"),
        "neoforge" to neoforgeRange,
        "required" to requiredField,
        "verify_mixins" to verifyMixins
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
    from(productionJar.get().archiveFile, productionSourcesJar.get().outputs.files)
    into(rootProject.layout.buildDirectory.dir("libs/${mod.version}"))
    // Loom hooks remapJar into 'build' automatically on obfuscated nodes; unobfuscated nodes have no
    // remapJar, and shadowJar (the production jar there) is not otherwise wired to 'build'.
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
    modrinth {
        projectId = "armor-hud"
        accessToken = providers.environmentVariable("MODRINTH_TOKEN").orElse("")
        minecraftVersions.addAll(common.mod.prop("mc_targets").split(" "))
    }
}
