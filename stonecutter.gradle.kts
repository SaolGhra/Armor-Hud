plugins {
    id("dev.kikugie.stonecutter")
    // "dev.architectury.loom" (plain) stays declared for forge/build.gradle.kts, which is
    // 1.20.1-only and therefore always obfuscated/remapped — it never needs the no-remap variant.
    // Root/fabric/neoforge apply loom imperatively (see build.gradle.kts / fabric/neoforge
    // build.gradle.kts) since 26.1+ needs loom-no-remap instead — a plugins{} block can't branch.
    id("dev.architectury.loom") version "1.17.491" apply false
    id("dev.architectury.loom-remap") version "1.17.491" apply false
    id("dev.architectury.loom-no-remap") version "1.17.491" apply false
    id("architectury-plugin") version "3.5.169" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("me.modmuss50.mod-publish-plugin") version "0.8.4" apply false
}
stonecutter active "1.20" /* [SC] DO NOT EDIT */

// Builds every version into `build/libs/{mod.version}/{loader}`. Use chiseled builds, NOT
// `:loader:version:build` directly — Stonecutter only generates a version's source when it is the
// active build target, so a direct node build produces an EMPTY jar for any non-active version.
// Restrict to specific nodes with -Pbuild.nodes="fabric 26.3;neoforge 26.3" (the same "<loader> <mc>"
// format HUD_NODES/PUBLISH_NODES/publish.nodes use). Absent/blank means the whole matrix, exactly as
// before this existed. Jenkinsfile.version-scan uses it to compile ONLY a newly-scaffolded version
// instead of paying for all 45 nodes to answer a question about one of them.
stonecutter registerChiseled tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    val restrict = (findProperty("build.nodes") as String?)?.trim()
    if (!restrict.isNullOrEmpty()) {
        val wanted = restrict.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            .map { it.split(Regex("\\s+"), 2) }
        versions { branch, version -> wanted.any { it[0] == branch && it.getOrNull(1) == version.version } }
    }
    ofTask("buildAndCollect")
}

// Publishes every version to Modrinth (dry-run unless MODRINTH_TOKEN is set). Restrict to specific
// nodes with -Ppublish.nodes="fabric 26.2;neoforge 26.2" (same "<loader> <mc>" format Jenkins'
// HUD_NODES/PUBLISH_NODES parameters use) -- a mod.version bump for one new Minecraft version has
// no reason to touch every other node's already-published Modrinth entry. Absent/blank means the
// whole matrix, same as before this existed.
stonecutter registerChiseled tasks.register("chiseledPublish", stonecutter.chiseled) {
    group = "project"
    val restrict = (findProperty("publish.nodes") as String?)?.trim()
    if (!restrict.isNullOrEmpty()) {
        val wanted = restrict.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            .map { it.split(Regex("\\s+"), 2) }
        versions { branch, version -> wanted.any { it[0] == branch && it.getOrNull(1) == version.version } }
    }
    ofTask("publishMods")
}

// Prints every "<loader> <mc>" node that actually exists, one per line -- the authoritative source
// for Jenkinsfile's node lists. A hand-copied list drifts the moment a version is added or removed
// here without someone remembering to update the copy too (this is exactly what happened: an older
// hand-copied list in the Jenkinsfile was silently missing every 26.x node).
tasks.register("printNodes") {
    group = "project"
    doLast {
        for (node in stonecutter.tree.nodes) {
            if (node.branch.id.isEmpty()) continue
            println("${node.branch.id} ${node.metadata.version}")
        }
    }
}

// Builds loader-specific versions into `build/libs/{mod.version}/{loader}`
for (it in stonecutter.tree.branches) {
    if (it.id.isEmpty()) continue
    val loader = it.id.upperCaseFirst()
    stonecutter registerChiseled tasks.register("chiseledBuild$loader", stonecutter.chiseled) {
        group = "project"
        versions { branch, _ -> branch == it.id }
        ofTask("buildAndCollect")
    }
}

// Runs active versions for each loader
for (it in stonecutter.tree.nodes) {
    if (it.metadata != stonecutter.current || it.branch.id.isEmpty()) continue
    val types = listOf("Client", "Server")
    val loader = it.branch.id.upperCaseFirst()
    for (type in types) tasks.register("runActive$type$loader") {
        group = "project"
        dependsOn("${it.hierarchy}run$type")
    }
}
