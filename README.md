# wildharvest-paper

A PaperMC plugin that combines **TreeFeller** and **VeinMiner** into one jar — with **smart natural-only detection** so it doesn't eat player-built houses or vein-mine silk-touched ores you replaced.

> Built for **Paper 1.21.x**, requires **Java 21+**.

---

## Highlights

- **Two-in-one** — felling trees and vein-mining ores in one plugin, sharing the same chain engine, cooldown, and detection layer.
- **Placed-block protection** — every player-placed block is tagged in the chunk's `PersistentDataContainer`. Chains refuse to consume tagged blocks, so chopping a wooden house never destroys it, and a silk-touched diamond ore placed back down won't trigger a vein-mine.
- **Tree-vs-pillar detection** — TreeFeller additionally requires connected leaves of the same wood family. A stack of oak logs without leaves on top is a pillar, not a tree, and won't fell.
- **Drops straight to inventory** — felled logs, mined ores, and Fortune/Silk-Touch loot all skip the dropped-item entity stage and go directly into the player's inventory. Overflow falls at the block.
- **Vanilla parity** — per-block hunger exhaustion (default 0.005f), durability damage with full Unbreaking RNG, vanilla XP orbs from ores.
- **Quick canopy decay** — felled trunks schedule their leaves to decay one-per-tick (configurable) with vanilla particles + sounds. Persistent / shears-set leaves stay.
- **Anti-grief friendly** — every chained block fires a real `BlockBreakEvent`, so WorldGuard, GriefPrevention, Towny, etc. get to veto each block individually. Recursion guard prevents the plugin from re-entering its own listener.
- **CoreProtect logging** (soft-dependency) — chained blocks log via the standard event; leaf decay calls `logRemoval` directly so the felling player gets attributed.
- **Per-player toggles** — `/wildharvest treefeller` / `/wh veinminer` to enable/disable per feature, plus `on` / `off` / `status`.
- **Min food level + cooldown** — won't trigger when starving, short throttle stops click-spam abuse without affecting normal play.

---

## Install

1. Drop `WildHarvest.jar` into your server's `plugins/` folder.
2. Restart. It generates `plugins/WildHarvest/config.yml`.
3. Tweak to taste, then run `/wildharvest reload`.

(Optional) Install **CoreProtect** alongside for full audit logging of chain breaks and canopy decay.

---

## Commands & permissions

| Command | Permission | Description |
|---|---|---|
| `/wildharvest` (or `/wh`) | `wildharvest.use` (true) | Show usage |
| `/wh treefeller` | `wildharvest.treefeller` (true) | Toggle TreeFeller for self |
| `/wh veinminer` | `wildharvest.veinminer` (true) | Toggle VeinMiner for self |
| `/wh both` / `/wh on` | — | Enable both |
| `/wh off` | — | Disable both |
| `/wh status` | — | Show current toggles |
| `/wh reload` | `wildharvest.reload` (op) | Reload `config.yml` |
| (perm only) | `wildharvest.bypass-protection` (op) | Bypass placed-block protection |

---

## Configuration

See [config.yml](wildharvest/src/main/resources/config.yml) for the annotated defaults. Highlights:

- `max-chain-size` (default 256) — hard cap shared by TreeFeller and VeinMiner.
- `sneak-disables-chain` (default true) — invert with `false` if you want sneak-to-activate.
- `min-food-level` (default 1) — minimum hunger to trigger a chain. 0 disables.
- `chain-cooldown-ms` (default 100) — short per-player throttle.
- `treefeller.require-leaves` (default true) — the pillar/tree distinguisher.
- `treefeller.leaf-decay-ticks-per-leaf` (default 1) — canopy decay speed.
- `veinminer.require-correct-tool` (default true) — must hold a high-enough-tier pickaxe for the ore.

---

## Build

```bash
cd wildharvest
./mvnw -DskipTests package
```

Produces `wildharvest/target/WildHarvest.jar`.

The repo bundles `apache-maven-3.9.9/` so `mvnw` works without a system Maven install.
