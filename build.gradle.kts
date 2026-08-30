import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    java
    id("architectury-plugin")
}

val minecraft = stonecutter.current.version

// 26.1+ ships unobfuscated Minecraft: no Mojang mappings, no Fabric intermediary, nothing to remap.
// That needs a different loom plugin ID, not just a different mappings dependency, so loom is
// applied imperatively here rather than via the plugins{} block (which can't branch). Both IDs are
// declared (apply false) at one shared version in stonecutter.gradle.kts.
val unobfuscated: Boolean = stonecutter.eval(minecraft, ">=26.1")
apply(plugin = if (unobfuscated) "dev.architectury.loom-no-remap" else "dev.architectury.loom-remap")
val loomExt = extensions.getByType(LoomGradleExtensionAPI::class.java)

// loom-no-remap creates no mod* dependency configurations — with nothing to remap, a mod is just a
// normal dependency.
val modDep = if (unobfuscated) "implementation" else "modImplementation"

// `verify` gates the ArmorHudVerifyHook test seam (the release harness uses it to open the config screen
// without input automation). Release builds compile it out entirely — call site AND class — so the
// published jars carry no test scaffolding. Verification runs pass -Parmorhud.verify=true.
// NOTE: this makes the verified build differ from the shipped build by that one class. Tier 1 audits
// the real jars and asserts the hook is absent from them.
stonecutter.const("verify", providers.gradleProperty("armorhud.verify").isPresent)

version = "${mod.version}+$minecraft"
base {
    archivesName.set("${mod.id}-common")
}

// Enable the loader platforms that include this common version (fabric/neoforge/forge).
architectury.common(stonecutter.tree.branches.mapNotNull {
    if (stonecutter.current.project !in it) null
    else it.project.prop("loom.platform")
})

repositories {
    mavenCentral()
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraft")
    // Official Mojang mappings for every obfuscated version — Mojang ships these for every release
    // AND snapshot, which eliminates the Yarn-lag / mappings=none "wrong class names" problem. 26.1+
    // is unobfuscated (real names already), so there is nothing to give loom to remap against.
    if (!unobfuscated) {
        add("mappings", loomExt.officialMojangMappings())
    }
    modDep("net.fabricmc:fabric-loader:${mod.dep("fabric_loader")}")

    "io.github.llamalad7:mixinextras-common:${mod.dep("mixin_extras")}".let {
        annotationProcessor(it)
        implementation(it)
    }

    // Pure-logic unit tests (ArmorHudMath); no Minecraft needed.
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// The access widener is currently empty (no widened members) and access widening is itself a
// remap-time concept, so it's only meaningful — and only registered — on obfuscated nodes.
if (!unobfuscated) {
    configure<LoomGradleExtensionAPI> {
        accessWidenerPath = rootProject.file("src/main/resources/armor_hud.accesswidener")
        decompilers {
            get("vineflower").apply {
                options.put("mark-corresponding-synthetics", "1")
            }
        }
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
    // Without this, javac's --release flag comes from whatever JDK is running the Gradle daemon
    // itself — fine for the 26.x nodes when the daemon runs on a new-enough JDK, but the verify
    // harness (scripts/verify/*.sh) defaults JAVA_HOME to a JDK 21, which does not know release 25
    // at all ("invalid source release: 25"). An explicit toolchain lets Gradle provision/select the
    // right JDK for compilation regardless of which JDK is running Gradle itself.
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

tasks.build {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
}
