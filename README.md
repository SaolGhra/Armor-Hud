# Armor HUD

See your armour and its durability at a glance, right next to the hotbar.

Armor HUD draws your helmet, chestplate, leggings and boots as vanilla-style slots beside the hotbar,
each with a colour-coded durability bar, and warns you before a piece breaks. It is **client-side
only** — it does not need to be on the server, and it has no mixins.

[Download on Modrinth](https://modrinth.com/mod/armor-hud) · [Report a bug](https://github.com/SaolGhra/Armor-Hud/issues) · by [SaolGhra](https://github.com/SaolGhra)

## Features

- **Armour at a glance** — all four pieces on vanilla-style slots next to the hotbar.
- **Colour-coded durability bars** — a smooth gradient from green through yellow to red as a piece
  wears down.
- **Exact durability numbers** — prefer figures to bars? Turn on *Show Durability Points*.
- **Low-durability warning** — a bobbing `!` appears once a piece drops below 20% (threshold editable
  in the config file).
- **Put it where you want it** — *Interactive Positioning* lets you drag the HUD anywhere on screen,
  with the offsets saved automatically.
- **Horizontal or vertical** — lay the slots out in a row or a column.
- **Resource pack friendly** — the slot background and warning icon are ordinary textures under
  `assets/armor_hud/textures/gui/`, so any resource pack can restyle them.
- **Hides with the HUD** — press F1 and it goes away with everything else.

## Supported versions

| Loader | Minecraft |
|---|---|
| **Fabric** | 1.20 – 1.21.11 |
| **Quilt** | 1.20 – 1.21.11 (install the Fabric build) |
| **NeoForge** | 1.20.2 – 1.21.11 |
| **Forge** | 1.20.1 |

<details>
<summary>Why some loaders cover fewer versions</summary>

NeoForge did not exist before **1.20.2**, so 1.20 and 1.20.1 are Fabric/Quilt only. Where a Minecraft
version only ever had a NeoForge **beta** (1.20.3, 1.20.5, 1.21.2, 1.21.6, 1.21.7, 1.21.9), that beta
is the supported build, because it is the only one there is. **Forge** is 1.20.1 only — it is legacy,
and binary-incompatible with NeoForge from 1.21 onwards.

**Quilt** runs the Fabric build through its Fabric-compat layer. Armor HUD is a clean fit for this
(no mixins), so there is no separate Quilt download — just use the Fabric jar with
[Quilted Fabric API](https://modrinth.com/mod/qsl).

</details>

> **Minecraft 26.x** builds are published separately for now. 26.1 was the first fully unobfuscated
> Minecraft release, and the multi-loader build tooling has not caught up yet.

## Installing

1. Install **Fabric**, **Quilt**, **NeoForge** or **Forge** for your Minecraft version.
2. Download the matching file from [Modrinth](https://modrinth.com/mod/armor-hud) or the
   [releases page](https://github.com/SaolGhra/Armor-Hud/releases). On Quilt, take the **Fabric** file.
3. Drop it in your `mods` folder, along with its dependency:
   - **Fabric** → [Fabric API](https://modrinth.com/mod/fabric-api)
   - **Quilt** → [Quilted Fabric API](https://modrinth.com/mod/qsl)
   - **NeoForge / Forge** → nothing else needed

Launchers that read Modrinth metadata (Modrinth App, Prism, ATLauncher) will offer to pull Fabric API
in for you.

## Settings

Open the settings screen in game:

- **Fabric / Quilt** — install [Mod Menu](https://modrinth.com/mod/modmenu), then *Mods → Armor HUD →
  the settings icon*.
- **NeoForge / Forge** — *Mods → Armor HUD → Config*. Built in, nothing extra required.

From there you can toggle the HUD, switch to vertical layout, turn warning marks or durability
numbers on and off, reset everything, and hit **Interactive Positioning** to drag the HUD into place.

Everything is stored in `config/armor_hud.json`, which also exposes a few values the screen does not:

| Key | Default | What it does |
|---|---|---|
| `durabilityWarningThreshold` | `0.20` | Fraction of durability below which the `!` appears |
| `boxSize` | `22` | Size of each armour slot, in GUI pixels |
| `spacing` | `2` | Gap between slots, in GUI pixels |

> On **NeoForge 1.21.9** the loader's own mod list crashes before any mod's settings screen can open
> (a NeoForge 21.9.16-beta bug — it happens with no mods installed at all). Edit
> `config/armor_hud.json` directly on that version.

## Building from source

One shared source tree covers every version and loader, using
[Stonecutter](https://stonecutter.kikugie.dev/) and
[Architectury Loom](https://github.com/architectury/architectury-loom). Build the lot with **JDK 21**:

```bash
./gradlew chiseledBuild -x runGameTest -x runClientGameTest
```

Every jar lands in `build/libs/<mod-version>/`, named by loader and Minecraft version (for example
`armor_hud-neoforge-4.0.0+1.21.5.jar`).

> Build through the `chiseled*` tasks only. A direct `:fabric:<mc>:build` silently produces an **empty**
> jar for any version that is not the currently active one.

Other useful tasks:

```bash
./gradlew :1.21.5:test                      # unit tests (pure logic, no game launch)
./gradlew :fabric:1.21.5:runClientGameTest  # screenshot tests (Fabric >=1.21.5, needs a display)
```

Release verification lives in `scripts/verify/` — see the README there.

## Contributing

Issues and pull requests are welcome on the
[issue tracker](https://github.com/SaolGhra/Armor-Hud/issues).

## Licence

[MIT](LICENSE.txt).
