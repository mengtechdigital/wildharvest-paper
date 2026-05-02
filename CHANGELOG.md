# Changelog

All notable changes to this project are documented here.

## [1.0.1] — 2026-04-30

### Fixed
- **Leaf decay now works from any seed log, not just bushy oaks.** The canopy BFS previously ran *after* `chainBreaker.breakChain` had already removed every log, so it could only find leaves that were directly adjacent to the seed log's 26-neighbor cube. Felling the bottom of any normal trunk left the canopy floating. Split into `collectCanopyLeaves` (called *before* the chain break, while logs are still intact to bridge through) and `scheduleDecay` (called after, given the pre-collected list). Decay now walks bottom-of-trunk → top → canopy correctly regardless of where the player breaks the tree.
- **Tree feller chain-break now uses 26-way connectivity.** Acacia angled trunks, cherry bent trunks, jungle/dark oak 2x2 megas, and mangrove root-trunks all rely on diagonal log adjacency. Same-family matching still prevents bleeding into a neighboring tree of a different wood type.
- **Per-leaf "support log within 6 blocks" check.** When an anti-grief plugin vetoes part of the trunk mid-chain, the BFS would still strip leaves off the surviving logs. Each candidate leaf now scans for a same-family log within `leaf-decay-radius` and skips decay if one survives — mirrors vanilla's leaf-support radius behavior.
- **Tree feller BFS loops no longer force-load adjacent chunks on the main thread.** `hasConnectedLeaves`, `collectCanopy`, and `hasNearbySameFamilyLog` all called `Block.getRelative().getType()` without checking whether the neighbor's chunk was loaded — a tree at the edge of view distance, or the per-tick leaf-support scan (up to 2197 reads per leaf), could synchronously stall the server every iteration. Neighbors in unloaded chunks are now skipped.

### Changed
- `treefeller.max-logs` default `128` → `256` (jungle giants exceeded 128 logs and stopped mid-tree).
- `treefeller.leaf-decay-radius` default `6` → `8` (covers big oak, cherry, mangrove, dark oak canopies).
- `treefeller.chain-cooldown-ms` default `100` → `0` (no throttle between chains).
- `hasConnectedLeaves` BFS budget `96` → `512` (jungle/spruce mega trees were tripping the cap).
- `collectCanopy` vertical extent `radius*2` → `radius*5` and hard budget `4096` → `16384` (covers 30+-tall trees).

### Notes
- Existing `config.yml` files won't auto-update — the new defaults only apply if you delete the file or copy the new values from the bundled `config.yml`.

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
