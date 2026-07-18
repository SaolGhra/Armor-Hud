# Armor HUD

A lightweight, client-side Minecraft mod that shows your armour next to the hotbar with durability
bars and a low-durability warning. Created by [SaolGhra](https://github.com/SaolGhra) —
[available on Modrinth](https://modrinth.com/mod/armor-hud).

Client-side only: it does **not** need to be installed on the server, and it has no mixins.

## Features

- **Armour slots by the hotbar** — helmet, chestplate, leggings and boots, each on a vanilla-style slot.
- **Durability bar** with a smooth colour gradient from green (full) through yellow (half) to red
  (nearly broken), or an exact **durability number** instead if you prefer.
- **Low-durability warning** — a bobbing `!` icon appears once a piece drops below 20% durability
  (toggleable in-game; the threshold itself is editable in the config file).
- **Position it anywhere** — drag the HUD where you want it, or lay the slots out vertically.
- Hides with the rest of the HUD when you press F1.

## Supported versions

| Loader | Minecraft |
|---|---|
| **Fabric** | 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11 |
| **Quilt** | same as Fabric (runs the Fabric build) |
| **NeoForge** | 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11 |
| **Forge** | 1.20.1 |

**Quilt** uses the Fabric build — Quilt runs Fabric mods through its Fabric-compat layer, and Armor HUD
is a clean fit (no mixins). On Quilt, install the Fabric jar plus
[Quilted Fabric API](https://modrinth.com/mod/qsl) (QFAPI) instead of Fabric API.

NeoForge covers everything it exists for — it started at 1.20.2, so 1.20/1.20.1 are Fabric-only. Where
a Minecraft version only has a NeoForge **beta** (1.20.3/1.20.5/1.21.2/1.21.6/1.21.7/1.21.9), that beta
is the shipping loader — it's the only option there. Forge is 1.20.1-only (legacy; binary-incompatible
with NeoForge on 1.21+).

**Minecraft 26.x**: builds for 26.x are published from the previous branch for now. 26.1 is the first
fully unobfuscated Minecraft release, and the multi-loader build tooling (architectury-loom) has not
yet shipped support for it.

## Configuration

All settings are saved to `config/armor_hud.json`, and there is a settings screen with a
drag-to-position mode:

- **Fabric / Quilt** — install [Mod Menu](https://modrinth.com/mod/modmenu) (optional; it has Quilt
  builds), then *Mods → Armor HUD → Config*.
- **NeoForge / Forge** — *Mods → Armor HUD → Config* (built in, no extra mod needed).

In the screen, hit **Interactive Positioning** and drag the HUD preview to move it; the X/Y offsets
update live and save automatically.

## Installation

1. Install Fabric, Quilt, NeoForge, or Forge for your Minecraft version.
2. Download the matching jar from [Modrinth](https://modrinth.com/mod/armor-hud) or the
   [releases page](https://github.com/SaolGhra/Armor-Hud/releases). On Quilt, use the **Fabric** jar.
3. Drop it into your `mods` folder. On Fabric you also need
   [Fabric API](https://modrinth.com/mod/fabric-api); on Quilt,
   [Quilted Fabric API](https://modrinth.com/mod/qsl).

## Building

The repo is a [Stonecutter](https://stonecutter.kikugie.dev/) + [Architectury Loom](https://github.com/architectury/architectury-loom)
monorepo: one shared source tree, with per-version differences handled by version-guarded comments.
Build every version and loader in one go (requires **JDK 21**):

```bash
./gradlew chiseledBuild -x runGameTest -x runClientGameTest
```

Every jar for every version and loader lands together in `build/libs/<mod-version>/` (jar names carry
the loader and MC, e.g. `armor_hud-neoforge-4.0.0+1.21.5.jar`, so nothing collides). Build **only**
through the chiseled tasks — a direct `:fabric:<mc>:build` silently produces an empty jar for any
version that isn't the active one.

Other useful tasks:

```bash
./gradlew :1.21.5:test                      # unit tests (pure logic, no game launch)
./gradlew :fabric:1.21.5:runClientGameTest  # screenshot tests (Fabric >=1.21.5, needs a display)
```

## Contributing

Contributions are welcome. Please open an issue or a pull request.

## Support

Questions and bug reports: the [issues page](https://github.com/SaolGhra/Armor-Hud/issues).

## License

Licensed under the [MIT License](LICENSE.txt).
