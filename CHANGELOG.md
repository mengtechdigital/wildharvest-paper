# Changelog

All notable changes to this project are documented here.

## [1.0.0] — 2026-04-29

Initial release. TreeFeller + VeinMiner in one plugin, with placed-block detection so chains never eat player-built structures.

### Added
- **Smart natural-only detection.** Every player-placed block is recorded in the chunk's `PersistentDataContainer` (sorted long-array, binary-searched). Chains refuse to consume placed blocks. Markers are cleared when the block is broken, burned, exploded, decayed, pushed by a piston, or replaced by flowing fluid.
- **TreeFeller.** Chops the connected log component when an axe breaks the trunk. Same-family logs only (oak ≠ spruce). By default also requires connected leaves of the same family — a stack of logs in a player's house won't fell because there are no leaves on top.
- **VeinMiner.** Mines the connected ore vein when a pickaxe breaks an ore. Vanilla tier rules (iron+ for diamond, etc.) and OreGroup matching (regular iron ≠ deepslate iron).
- **Drops to inventory.** Logs, ores, and Fortune/Silk drops all go straight into the player's inventory, with overflow at the block.
- **Quick canopy decay.** After felling, the trunk's leaves are scheduled to pop one-per-tick (configurable) with vanilla particles + sounds. Persistent and player-placed leaves are spared. Chunk-unload safe.
- **Vanilla parity.** Per-block hunger exhaustion (`setExhaustion`), per-block durability with full Unbreaking RNG, XP orbs from ore breaks via `BlockBreakEvent.getExpToDrop()`.
- **Anti-grief integration.** Every chained block fires a real `BlockBreakEvent`. WorldGuard / GriefPrevention / Towny / CoreProtect see each block and can veto individually. Recursion guard prevents the listener from re-entering itself.
- **CoreProtect soft-dependency.** Chained logs/ores log automatically via the event. Leaf decay calls `CoreProtectAPI.logRemoval` directly so the felling player gets attributed for the canopy too.
- **Per-player toggles.** `/wildharvest treefeller`, `/wh veinminer`, `/wh both`, `/wh on`, `/wh off`, `/wh status`. Tab-completed.
- **Min food level + cooldown.** `min-food-level` (default 1, won't fire on empty hunger). `chain-cooldown-ms` (default 100ms, sub-noticeable throttle to stop click-spam).
- **Sneak gating.** `sneak-disables-chain: true` by default — invert for sneak-to-activate.

### Build
- Java 21, Paper API 1.21.1, Maven.
- `softdepend: [CoreProtect]`, `net.coreprotect:coreprotect:23.1` declared `provided`.
- `mvnw` wrapper bundled; `apache-maven-3.9.9` bundled at repo root.
