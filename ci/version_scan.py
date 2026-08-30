#!/usr/bin/env python3
"""
Detect newly-released Minecraft versions that Fabric and/or NeoForge already support but this
repo does not yet have a `versions/<mc>/` node for, and scaffold that node.

This is the engine behind Jenkinsfile.version-scan. It deliberately does TWO things and no more:

  * `detect`   — say what is missing, with every dependency pin already resolved from upstream.
  * `scaffold` — write `versions/<mc>/gradle.properties` and add the version to
                 settings.gradle.kts's `versions(...)` list(s).

It never touches mod.version, never edits Stonecutter guards, never commits, and never publishes.
A new Minecraft version has broken this mod's compile every single time so far; deciding where the
new API boundary sits is a human's job. All this does is put a reviewable draft in front of them.

Upstream sources (all public, no auth):
  Fabric game list      https://meta.fabricmc.net/v2/versions/game
  Fabric intermediary   https://meta.fabricmc.net/v2/versions/intermediary/<mc>
  Fabric loader         https://meta.fabricmc.net/v2/versions/loader
  Fabric API            https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
  NeoForge              https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml
  NeoForge <-> MC       .../neoforge/<v>/neoforge-<v>-moddev-config.json  ("mcp": neoform:<mc>-<build>)
  Mod Menu              https://api.modrinth.com/v2/project/modmenu/version

Forge is never scaffolded: per .claude/CLAUDE.md it is permanently 1.20.1-only (binary-incompatible
with NeoForge on 1.21+).

Usage:
    version_scan.py detect  [--json FILE] [--repo DIR] [--force-missing MC]
    version_scan.py scaffold MC --pins FILE [--repo DIR]
    version_scan.py selftest [--repo DIR] [--only MC,MC,...]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

FABRIC_META = "https://meta.fabricmc.net/v2"
FABRIC_MAVEN = "https://maven.fabricmc.net"
NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge"
MODRINTH_API = "https://api.modrinth.com/v2"

USER_AGENT = "armor-hud-version-scan (+https://github.com/SaolGhra/Armor-Hud)"

# A plain numeric Minecraft version: 1.20, 1.21.11, 26.2. Anything else the Fabric game list carries
# (snapshots, "22w13oneblockatatime", April Fools builds) is not a release and is never scaffolded.
RELEASE_RE = re.compile(r"^\d+(?:\.\d+)*$")


# ---------------------------------------------------------------------------- http


def _get(url: str, *, accept: str = "*/*") -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": accept})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read()


def get_json(url: str):
    return json.loads(_get(url, accept="application/json").decode("utf-8"))


def get_json_or_none(url: str):
    try:
        return get_json(url)
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise


def maven_versions(url: str) -> list[str]:
    """Every <version> in a maven-metadata.xml, in publication order (NOT sorted)."""
    return re.findall(r"<version>([^<]+)</version>", _get(url).decode("utf-8"))


# ---------------------------------------------------------------- version arithmetic


def mc_key(version: str) -> tuple[int, ...]:
    """Sort key for a Minecraft release. Tuple-of-ints orders every case this repo spans:
    (1,20) < (1,20,1) < (1,21,2) < (1,21,11) < (26,1) < (26,1,1) < (26,2)."""
    return tuple(int(p) for p in version.split("."))


def nf_key(version: str) -> tuple:
    """Sort key for a NeoForge version. A `-beta` suffix sorts BELOW the same numeric core, so a
    stable release always wins a tie."""
    core, _, suffix = version.partition("-")
    nums = tuple(int(p) for p in core.split(".") if p.isdigit())
    return nums, (0 if suffix else 1)


def neoforge_prefix(mc: str) -> str:
    """The NeoForge version prefix for a Minecraft version, trailing dot included.

    NeoForge numbers itself from the Minecraft version with the leading "1." dropped:
      1.20.2 -> 20.2.x     1.21 -> 21.0.x     1.21.11 -> 21.11.x
    The 26.x line kept a third Minecraft component, so NeoForge grew a fourth:
      26.1 -> 26.1.0.x     26.1.2 -> 26.1.2.x     26.2 -> 26.2.0.x
    Every value in versions/*/gradle.properties satisfies this (see `selftest`). It is still only an
    inference, so resolve_neoforge() confirms the winner against its own moddev-config.json.
    """
    parts = mc.split(".")
    if parts[0] == "1":
        parts = parts[1:]
        width = 2
    else:
        width = 3
    parts = (parts + ["0"] * width)[:width]
    return ".".join(parts) + "."


def neoforge_mc(nf_version: str):
    """The Minecraft version a NeoForge build actually targets, straight from its own metadata:
    moddev-config.json's "mcp" is `net.neoforged:neoform:<mc>-<build>@zip`. Returns None for
    pre-spec-2 NeoForge (roughly <= 20.4), which publishes no such file."""
    url = f"{NEOFORGE_MAVEN}/{nf_version}/neoforge-{nf_version}-moddev-config.json"
    cfg = get_json_or_none(url)
    if not cfg or "mcp" not in cfg:
        return None
    coords = cfg["mcp"].split(":")           # net.neoforged:neoform:26.2-2@zip
    if len(coords) < 3:
        return None
    return coords[2].split("@")[0].rsplit("-", 1)[0]


def java_target(mc: str) -> int:
    """Mirrors the `when` block in build.gradle.kts / fabric / neoforge. Kept in sync by selftest."""
    k = mc_key(mc)
    if k >= (26,):
        return 25
    if k >= (1, 20, 5):
        return 21
    return 17


# ------------------------------------------------------------------ pin resolution


def fabric_releases() -> list[str]:
    """Every stable, numerically-named Minecraft release Fabric knows about, newest first."""
    games = get_json(f"{FABRIC_META}/versions/game")
    return [g["version"] for g in games if g.get("stable") and RELEASE_RE.match(g["version"])]


def resolve_fabric_loader() -> str:
    """The newest published fabric-loader.

    Newest, not newest-*stable*: fabric-loader is deliberately backwards compatible with older
    Minecraft, and a brand-new Minecraft version is exactly the case that needs the newest loader —
    26.1 needed 0.19.5 for unobfuscated support while 0.19.3 was still the "stable" flag holder.
    Every version this repo has added by hand pinned whatever was newest at the time.
    """
    loaders = get_json(f"{FABRIC_META}/versions/loader")
    return loaders[0]["version"]


def resolve_intermediary(mc: str):
    """Fabric's intermediary mapping version for a Minecraft version, or None if there is none.
    Unobfuscated Minecraft (26.1+) reports the sentinel "0.0.0" — nothing to map."""
    data = get_json_or_none(f"{FABRIC_META}/versions/intermediary/{urllib.parse.quote(mc)}")
    if not data:
        return None
    return data[0].get("version")


def resolve_fabric_api(mc: str, *, versions: list[str] | None = None):
    """The newest fabric-api build for this Minecraft version, or None if Fabric API has not shipped
    one yet. Fabric API is what actually gates the Fabric node: the mod's HUD hook IS Fabric API, so
    loader support alone is not enough to scaffold against."""
    if versions is None:
        versions = maven_versions(
            f"{FABRIC_MAVEN}/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml")
    suffix = "+" + mc
    matches = [v for v in versions if v.endswith(suffix)]
    if not matches:
        return None
    # e.g. 0.158.0+26.2 -> (0, 158, 0)
    return max(matches, key=lambda v: tuple(int(p) for p in v.split("+")[0].split(".")))


def resolve_neoforge(mc: str, *, versions: list[str] | None = None, confirm: bool = True):
    """(version, is_beta, confirmed) for the NeoForge build this repo should pin for `mc`, or
    (None, False, False) if NeoForge has not released for it.

    Prefers a stable release; falls back to the newest beta, which is what this repo already does
    wherever NeoForge only ever shipped a beta for a Minecraft version (1.20.3, 1.21.2, 26.1, ...).
    """
    if versions is None:
        versions = maven_versions(f"{NEOFORGE_MAVEN}/maven-metadata.xml")
    prefix = neoforge_prefix(mc)
    candidates = [v for v in versions if v.startswith(prefix)]
    if not candidates:
        return None, False, False
    stable = [v for v in candidates if "-" not in v]
    pick = max(stable, key=nf_key) if stable else max(candidates, key=nf_key)
    confirmed = False
    if confirm:
        actual = neoforge_mc(pick)
        if actual is not None:
            if actual != mc:
                # The prefix inference and NeoForge's own metadata disagree. Never guess past that.
                raise RuntimeError(
                    f"NeoForge {pick} was inferred for Minecraft {mc} but its moddev-config.json "
                    f"says it targets {actual}. The NeoForge<->Minecraft numbering rule has changed "
                    f"— a human needs to look at this.")
            confirmed = True
    return pick, "-" in pick, confirmed


def resolve_modmenu(mc: str, fallback_mc: str | None = None):
    """(version, exact) — the newest Mod Menu release for this Minecraft version.

    When Mod Menu has not caught up yet, falls back to the pin of the newest Minecraft version this
    repo already has, and reports exact=False so the notification can say so. That is safe to build
    against: Mod Menu is an optional compileOnly dependency and the mod only implements two of its
    interfaces, but the pin is still a thing a human should replace once Mod Menu ships.
    """
    query = urllib.parse.urlencode({
        "game_versions": json.dumps([mc]),
        "loaders": json.dumps(["fabric"]),
    })
    data = get_json_or_none(f"{MODRINTH_API}/project/modmenu/version?{query}") or []
    releases = [v for v in data if v.get("version_type") == "release"]
    pool = releases or data
    if pool:
        newest = max(pool, key=lambda v: v["date_published"])
        return newest["version_number"], True
    return fallback_mc, False


# ------------------------------------------------------------------------- repo io


def repo_versions(repo: str) -> list[str]:
    root = os.path.join(repo, "versions")
    out = []
    for name in os.listdir(root):
        if os.path.isfile(os.path.join(root, name, "gradle.properties")) and RELEASE_RE.match(name):
            out.append(name)
    return sorted(out, key=mc_key)


def read_props(path: str) -> dict[str, str]:
    props = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            props[key.strip()] = value.strip()
    return props


def settings_path(repo: str) -> str:
    return os.path.join(repo, "settings.gradle.kts")


# ---------------------------------------------------------------------- detection


def detect(repo: str, force_missing: list[str]) -> dict:
    """What is released upstream but absent here.

    Only versions NEWER than the newest node already present count. This is release detection, not
    backfill: Fabric's game list goes back to 1.14, and every one of those was left out on purpose.
    """
    existing = repo_versions(repo)
    highest = max(existing, key=mc_key)
    pretend_missing = set(force_missing)
    present = set(existing) - pretend_missing

    releases = fabric_releases()
    candidates = sorted(
        {v for v in releases if v not in present and mc_key(v) > mc_key(highest)} | pretend_missing,
        key=mc_key)

    fapi_versions = maven_versions(
        f"{FABRIC_MAVEN}/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml")
    nf_versions = maven_versions(f"{NEOFORGE_MAVEN}/maven-metadata.xml")
    loader = resolve_fabric_loader()

    # Pin to fall back on when Mod Menu has not published for the new version yet.
    prev_modmenu = read_props(
        os.path.join(repo, "versions", highest, "gradle.properties")).get("deps.modmenu")

    found = []
    for mc in candidates:
        fabric_api = resolve_fabric_api(mc, versions=fapi_versions)
        neoforge, nf_beta, nf_confirmed = resolve_neoforge(mc, versions=nf_versions)
        if not fabric_api and not neoforge:
            continue  # Released, but no loader has shipped for it yet — nothing to scaffold.
        intermediary = resolve_intermediary(mc)
        modmenu, modmenu_exact = resolve_modmenu(mc, prev_modmenu)
        found.append({
            "mc": mc,
            "kind": "new",
            "slug": mc,
            "fabric": bool(fabric_api),
            "neoforge": bool(neoforge),
            "fabric_loader": loader,
            "fabric_api": fabric_api,
            "neoforge_loader": neoforge,
            "neoforge_beta": nf_beta,
            "neoforge_confirmed": nf_confirmed,
            "modmenu": modmenu,
            "modmenu_exact": modmenu_exact,
            # "0.0.0" is Fabric's sentinel for unobfuscated Minecraft (26.1+): there is no
            # intermediary because there is nothing to map.
            "unobfuscated": intermediary in (None, "0.0.0"),
            "java": java_target(mc),
        })

    # NeoForge always lags Fabric, so a version scaffolded Fabric-only is not finished — it needs
    # revisiting when NeoForge catches up, which the "newer than the newest node" rule above would
    # never surface (the version is already present). Look for exactly that: a node this repo has,
    # marked [UNSUPPORTED] for NeoForge, that NeoForge has since released for.
    for mc in existing:
        if mc in pretend_missing:
            continue
        props = read_props(os.path.join(repo, "versions", mc, "gradle.properties"))
        if props.get("deps.neoforge_loader", "[UNSUPPORTED]") != "[UNSUPPORTED]":
            continue
        neoforge, nf_beta, nf_confirmed = resolve_neoforge(mc, versions=nf_versions)
        if not neoforge:
            continue  # 1.20 / 1.20.1: NeoForge did not exist yet and never will for them.
        found.append({
            "mc": mc,
            "kind": "neoforge-catchup",
            "slug": f"{mc}-neoforge",
            "fabric": False,
            "neoforge": True,
            "fabric_loader": props.get("deps.fabric_loader"),
            "fabric_api": props.get("deps.fabric_api"),
            "neoforge_loader": neoforge,
            "neoforge_beta": nf_beta,
            "neoforge_confirmed": nf_confirmed,
            "modmenu": props.get("deps.modmenu"),
            "modmenu_exact": True,
            "unobfuscated": props.get("mod.unobfuscated") == "true",
            "java": java_target(mc),
        })

    return {"highest_existing": highest, "existing": existing, "new": found}


def describe(entry: dict) -> list[str]:
    """Human-readable warnings worth putting in front of a reviewer."""
    notes = []
    if entry.get("kind") == "neoforge-catchup":
        notes.append(f"NeoForge has caught up with Minecraft {entry['mc']}, which this repo has as "
                     f"Fabric-only. Adds the NeoForge node; the Fabric pins are left alone.")
        if entry["neoforge_beta"]:
            notes.append(f"NeoForge pin {entry['neoforge_loader']} is a BETA (no stable exists yet).")
        if not entry["neoforge_confirmed"]:
            notes.append("NeoForge pin could not be confirmed against its own moddev-config.json.")
        return notes
    if entry["fabric"] and not entry["neoforge"]:
        notes.append("Fabric only — NeoForge has not released for this version yet.")
    if entry["neoforge"] and not entry["fabric"]:
        notes.append("NeoForge only — Fabric API has not released for this version yet.")
    if entry["neoforge_beta"]:
        notes.append(f"NeoForge pin {entry['neoforge_loader']} is a BETA (no stable exists yet).")
    if entry["neoforge"] and not entry["neoforge_confirmed"]:
        notes.append("NeoForge pin could not be confirmed against its own moddev-config.json.")
    if not entry["modmenu_exact"]:
        notes.append(f"Mod Menu has no build for this version; carried over {entry['modmenu']}.")
    if entry["unobfuscated"] and mc_key(entry["mc"]) < (26, 1):
        notes.append("Unobfuscated but below 26.1 — build.gradle.kts's `>=26.1` loom-no-remap "
                     "predicate is now wrong and needs a human.")
    if not entry["unobfuscated"] and mc_key(entry["mc"]) >= (26, 1):
        notes.append("Obfuscated but at/above 26.1 — build.gradle.kts's `>=26.1` loom-no-remap "
                     "predicate is now wrong and needs a human.")
    return notes


# ---------------------------------------------------------------------- scaffolding


def render_props(entry: dict) -> str:
    mc = entry["mc"]
    lines = [
        f"mod.mc_dep_fabric=~{mc}",
        f"mod.mc_dep_forgelike=[{mc}]",
        f"mod.mc_title={mc}",
        f"mod.mc_targets={mc}",
    ]
    if entry["unobfuscated"]:
        lines += [
            "# Unobfuscated Minecraft (no Mojang mappings, no Fabric intermediary) — this node uses",
            "# dev.architectury.loom-no-remap instead of loom-remap, and Java "
            f"{entry['java']} (26.x's own runtime).",
            "mod.unobfuscated=true",
            f"mod.java={entry['java']}",
        ]
    lines.append("")
    lines.append(f"deps.fabric_loader={entry['fabric_loader']}")
    lines.append(f"deps.fabric_api={entry['fabric_api'] or '[UNSUPPORTED]'}")
    if entry["neoforge_beta"]:
        lines.append("# No stable NeoForge release exists for this version yet; the beta IS the")
        lines.append("# shipping loader here, same rule as the earlier beta-only NeoForge nodes.")
    if not entry["neoforge"]:
        lines.append("# NeoForge has not released for this Minecraft version yet — this node is")
        lines.append("# Fabric-only until it does, and is absent from settings.gradle.kts's")
        lines.append('# branch("neoforge") list.')
    lines.append(f"deps.neoforge_loader={entry['neoforge_loader'] or '[UNSUPPORTED]'}")
    lines.append("deps.forge_loader=[UNSUPPORTED]")
    if not entry["modmenu_exact"]:
        lines.append("# Mod Menu has not published for this Minecraft version yet; this pin is")
        lines.append("# carried over from the previous version (it is an optional compileOnly dep,")
        lines.append("# so it compiles, but replace it once Mod Menu ships).")
    lines.append(f"deps.modmenu={entry['modmenu']}")
    return "\n".join(lines) + "\n"


def _find_block(text: str, start: int) -> tuple[int, int]:
    """(open_paren_index, close_paren_index) of the `versions(...)` call at/after `start`."""
    open_idx = text.index("versions(", start) + len("versions(") - 1
    depth = 0
    for i in range(open_idx, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return open_idx, i
    raise RuntimeError("unbalanced parentheses in settings.gradle.kts versions(...) block")


def _insert_into_block(text: str, close_idx: int, mc: str) -> str:
    """Append `"<mc>",` as its own line just before a versions(...) block's closing paren."""
    line_start = text.rfind("\n", 0, close_idx) + 1
    indent = re.match(r"[ \t]*", text[line_start:close_idx]).group(0) + "    "

    head = text[:line_start]
    # The lists all end with a trailing comma today; refuse to guess if that ever changes, rather
    # than emitting Kotlin that does not parse.
    prev = head.rstrip()
    if not prev.endswith(",") and not prev.endswith("("):
        raise RuntimeError(
            "settings.gradle.kts versions(...) list does not end in a trailing comma; "
            "the insertion rule no longer holds and a human should add the version by hand")
    return head + f'{indent}"{mc}",\n' + text[line_start:]


def scaffold_neoforge_catchup(repo: str, entry: dict) -> list[str]:
    """Turn an existing Fabric-only node into a Fabric+NeoForge one, now that NeoForge has released
    for that Minecraft version.

    Deliberately surgical: it rewrites the one `deps.neoforge_loader=` line and adds the version to
    the neoforge branch list. It does NOT regenerate the whole properties file — that would drag the
    Fabric pins of an already-shipped node forward as a side effect of an unrelated change.
    """
    mc = entry["mc"]
    touched = []

    props_path = os.path.join(repo, "versions", mc, "gradle.properties")
    with open(props_path, encoding="utf-8") as fh:
        lines = fh.readlines()
    out, replaced = [], False
    for line in lines:
        if line.startswith("deps.neoforge_loader="):
            if entry["neoforge_beta"]:
                out.append("# No stable NeoForge release exists for this version yet; the beta IS\n"
                           "# the shipping loader here.\n")
            out.append(f"deps.neoforge_loader={entry['neoforge_loader']}\n")
            replaced = True
        elif line.lstrip().startswith("#") and "NeoForge has not released" in line:
            continue          # drop the now-false "Fabric-only until it does" note
        elif line.lstrip().startswith("#") and "Fabric-only until it does" in line:
            continue
        elif line.lstrip().startswith("#") and 'branch("neoforge") list' in line:
            continue
        else:
            out.append(line)
    if not replaced:
        raise RuntimeError(f"versions/{mc}/gradle.properties has no deps.neoforge_loader line")
    with open(props_path, "w", encoding="utf-8") as fh:
        fh.writelines(out)
    touched.append(os.path.relpath(props_path, repo))

    spath = settings_path(repo)
    with open(spath, encoding="utf-8") as fh:
        text = fh.read()
    nf_anchor = text.index('branch("neoforge")')
    nf_open, nf_close = _find_block(text, nf_anchor)
    if f'"{mc}"' in text[nf_open:nf_close]:
        raise RuntimeError(f'{mc} is already in settings.gradle.kts branch("neoforge")')
    text = _insert_into_block(text, nf_close, mc)
    with open(spath, "w", encoding="utf-8") as fh:
        fh.write(text)
    touched.append(os.path.relpath(spath, repo))
    return touched


def scaffold(repo: str, entry: dict) -> list[str]:
    """Write versions/<mc>/gradle.properties and register the version in settings.gradle.kts.
    Returns the paths touched."""
    if entry.get("kind") == "neoforge-catchup":
        return scaffold_neoforge_catchup(repo, entry)

    mc = entry["mc"]
    touched = []

    vdir = os.path.join(repo, "versions", mc)
    os.makedirs(vdir, exist_ok=True)
    props = os.path.join(vdir, "gradle.properties")
    with open(props, "w", encoding="utf-8") as fh:
        fh.write(render_props(entry))
    touched.append(os.path.relpath(props, repo))

    spath = settings_path(repo)
    with open(spath, encoding="utf-8") as fh:
        text = fh.read()
    if f'"{mc}"' in text:
        # Reached two ways, both fine to continue from: a `detect --force-missing` rehearsal of a
        # version the repo already has, or a half-finished earlier scaffold. Rewriting the pins is
        # still the useful half; adding the version to the list a second time would not parse.
        print(f"note settings.gradle.kts already lists {mc} — left unchanged")
        return touched

    # The neoforge block first, so its indices stay valid while the (earlier) root block is edited.
    if entry["neoforge"]:
        nf_anchor = text.index('branch("neoforge")')
        _, nf_close = _find_block(text, nf_anchor)
        text = _insert_into_block(text, nf_close, mc)

    root_anchor = text.index("create(rootProject)")
    _, root_close = _find_block(text, root_anchor)
    text = _insert_into_block(text, root_close, mc)

    with open(spath, "w", encoding="utf-8") as fh:
        fh.write(text)
    touched.append(os.path.relpath(spath, repo))
    return touched


def nodes_for(entry: dict) -> list[str]:
    """The "<loader> <mc>" node ids this scaffold creates — the argument for
    `./gradlew chiseledBuild -Pbuild.nodes=...`."""
    nodes = []
    if entry["fabric"]:
        nodes.append(f"fabric {entry['mc']}")
    if entry["neoforge"]:
        nodes.append(f"neoforge {entry['mc']}")
    return nodes


# ------------------------------------------------------------------------ selftest


def selftest(repo: str, only: list[str] | None) -> int:
    """Re-derive every existing node's pins from upstream and compare with what is committed.

    Pin VALUES are expected to drift upward (Fabric API and NeoForge keep publishing for old
    Minecraft versions long after this repo pinned one), so a newer resolved value is reported as
    `newer`, not as a failure. What must match exactly is the structure: which loaders exist for a
    version, the NeoForge line, beta-vs-stable, obfuscation state and Java target. Those are the
    things a scaffold gets wrong silently.
    """
    existing = repo_versions(repo)
    if only:
        existing = [v for v in existing if v in only]
    fapi_versions = maven_versions(
        f"{FABRIC_MAVEN}/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml")
    nf_versions = maven_versions(f"{NEOFORGE_MAVEN}/maven-metadata.xml")

    failures = 0
    for mc in existing:
        props = read_props(os.path.join(repo, "versions", mc, "gradle.properties"))
        problems, notes = [], []

        want_nf = props.get("deps.neoforge_loader", "[UNSUPPORTED]")
        got_nf, got_beta, _ = resolve_neoforge(mc, versions=nf_versions, confirm=False)
        if want_nf == "[UNSUPPORTED]":
            if got_nf:
                problems.append(f"neoforge: repo says unsupported, upstream has {got_nf}")
        elif not got_nf:
            problems.append(f"neoforge: repo pins {want_nf}, resolver found nothing")
        else:
            if neoforge_prefix(mc) != want_nf[:len(neoforge_prefix(mc))]:
                problems.append(f"neoforge line: derived {neoforge_prefix(mc)}x, repo pins {want_nf}")
            if got_beta != want_nf.endswith("-beta"):
                problems.append(f"neoforge beta flag: derived beta={got_beta}, repo pins {want_nf}")
            if got_nf != want_nf:
                notes.append(f"neoforge {want_nf} -> {got_nf}")

        want_api = props.get("deps.fabric_api", "[UNSUPPORTED]")
        got_api = resolve_fabric_api(mc, versions=fapi_versions)
        if not got_api:
            problems.append(f"fabric-api: repo pins {want_api}, resolver found nothing")
        elif got_api != want_api:
            notes.append(f"fabric-api {want_api} -> {got_api}")

        want_unobf = props.get("mod.unobfuscated") == "true"
        got_unobf = resolve_intermediary(mc) in (None, "0.0.0")
        if want_unobf != got_unobf:
            problems.append(f"unobfuscated: repo says {want_unobf}, upstream says {got_unobf}")

        if "mod.java" in props and int(props["mod.java"]) != java_target(mc):
            problems.append(f"java: repo says {props['mod.java']}, rule says {java_target(mc)}")

        status = "FAIL" if problems else "ok"
        failures += bool(problems)
        detail = "; ".join(problems) or ("newer upstream: " + ", ".join(notes) if notes else "exact")
        print(f"  {status:4} {mc:9} {detail}")

    print(f"\nselftest: {len(existing)} versions checked, {failures} structural failure(s)")
    return 1 if failures else 0


# ---------------------------------------------------------------------------- main


def main() -> int:
    # --repo is on a shared parent so it is accepted on either side of the subcommand; a pipeline
    # step that writes `detect --repo "$WORKSPACE"` should not fail on argument order.
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--repo", default=os.environ.get("ARMOR_HUD_ROOT", "."))

    ap = argparse.ArgumentParser(description=__doc__, parents=[common],
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    d = sub.add_parser("detect", parents=[common],
                       help="report Minecraft versions released upstream but absent here")
    d.add_argument("--json", help="also write the full result to this file")
    d.add_argument("--versions-out",
                   help="write just the new version numbers here, one per line, oldest first "
                        "(what the Jenkins pipeline iterates over)")
    d.add_argument("--force-missing", default="",
                   help="comma-separated versions to treat as if absent (positive-path testing)")

    s = sub.add_parser("scaffold", parents=[common],
                       help="write versions/<mc>/ and register it in settings.gradle.kts")
    s.add_argument("slug", help="a slug from `detect --versions-out` (a bare version also works)")
    s.add_argument("--pins", required=True, help="the JSON written by `detect --json`")

    t = sub.add_parser("selftest", parents=[common],
                       help="re-derive every existing node's pins and compare")
    t.add_argument("--only", default="", help="comma-separated subset of versions to check")

    args = ap.parse_args()
    repo = os.path.abspath(args.repo)

    if args.cmd == "selftest":
        only = [v for v in args.only.split(",") if v]
        return selftest(repo, only or None)

    if args.cmd == "detect":
        force = [v for v in args.force_missing.split(",") if v]
        result = detect(repo, force)
        if args.json:
            with open(args.json, "w", encoding="utf-8") as fh:
                json.dump(result, fh, indent=2)
        if args.versions_out:
            # Oldest first: versions are scaffolded in release order, so if two land between runs the
            # older one gets its branch before the newer one is even attempted.
            # Slugs, not bare versions: a NeoForge catch-up for a version the repo already has is a
            # different job from a brand-new version, and needs its own branch name.
            with open(args.versions_out, "w", encoding="utf-8") as fh:
                fh.write("".join(e["slug"] + "\n" for e in result["new"]))
        print(f"newest node in repo: {result['highest_existing']}")
        if not result["new"]:
            print("nothing new — every released Minecraft version with loader support is present")
            return 0
        for entry in result["new"]:
            loaders = "+".join(l for l, on in
                               (("fabric", entry["fabric"]), ("neoforge", entry["neoforge"])) if on)
            label = "NEW" if entry["kind"] == "new" else "CATCHUP"
            print(f"{label} {entry['mc']} ({loaders})  [slug {entry['slug']}]")
            print(f"    fabric_loader   {entry['fabric_loader']}")
            print(f"    fabric_api      {entry['fabric_api']}")
            print(f"    neoforge_loader {entry['neoforge_loader']}")
            print(f"    modmenu         {entry['modmenu']}")
            print(f"    unobfuscated    {entry['unobfuscated']}   java {entry['java']}")
            for note in describe(entry):
                print(f"    ! {note}")
            print(f"    nodes           {'; '.join(nodes_for(entry))}")
        return 0

    if args.cmd == "scaffold":
        with open(args.pins, encoding="utf-8") as fh:
            data = json.load(fh)
        entry = next((e for e in data["new"] if e.get("slug", e["mc"]) == args.slug), None)
        if entry is None:
            entry = next((e for e in data["new"] if e["mc"] == args.slug), None)
        if entry is None:
            print(f"!! {args.slug} is not in {args.pins}", file=sys.stderr)
            return 1
        touched = scaffold(repo, entry)
        # Everything below "nodes " / "warn " is consumed by Jenkinsfile.version-scan, which greps it
        # rather than parsing JSON (the Groovy sandbox blocks JsonSlurper and readJSON needs a plugin
        # that may not be installed). Keep these prefixes stable.
        for path in touched:
            print(f"wrote {path}")
        print(f"mc {entry['mc']}")
        print(f"kind {entry['kind']}")
        print("nodes " + "; ".join(nodes_for(entry)))
        print(f"pins fabric_loader={entry['fabric_loader']} "
              f"fabric_api={entry['fabric_api']} "
              f"neoforge={entry['neoforge_loader']} "
              f"modmenu={entry['modmenu']} "
              f"unobfuscated={entry['unobfuscated']} java={entry['java']}")
        for note in describe(entry):
            print(f"warn {note}")
        return 0

    return 2


if __name__ == "__main__":
    sys.exit(main())
