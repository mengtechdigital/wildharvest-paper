package com.wildharvest.feller;

import com.wildharvest.integration.CoreProtectHook;
import com.wildharvest.tracker.PlacedBlockTracker;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * After a tree is felled, schedule its leaves to decay quickly so the canopy
 * doesn't hang in the air. The flow is two-phase:
 *
 *   1. {@link #collectCanopyLeaves} — call this BEFORE the chain break runs,
 *      while every log in the trunk is still intact. A two-phase BFS first
 *      discovers all logs belonging to the tree, then flood-fills through the
 *      canopy collecting only leaves within vanilla's support distance of a
 *      log. This prevents missed leaves on wide canopies (jungle giant, mega
 *      spruce, dark oak) and avoids grabbing leaves from adjacent same-family
 *      trees.
 *   2. {@link #scheduleDecay} — call this AFTER the chain break with the
 *      result from step 1. Pops leaves one-by-one from a single repeating task.
 *
 * Leaves use Block.breakNaturally() so vanilla drop tables apply (rare
 * saplings, apples, sticks). They drop on the ground rather than into the
 * player's inventory because the player isn't actively breaking them.
 */
public final class LeafDecayScheduler {

    private final Plugin plugin;
    private final PlacedBlockTracker tracker;
    private final CoreProtectHook coreProtect;

    public LeafDecayScheduler(Plugin plugin, PlacedBlockTracker tracker, CoreProtectHook coreProtect) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.coreProtect = coreProtect;
    }

    /**
     * Result of canopy collection: the leaves to decay and the set of log
     * positions that belong to the tree (used for fast anti-grief checks).
     */
    public static final class CanopyResult {
        public final List<Block> leaves;
        public final Set<Long> logKeys;

        public CanopyResult(List<Block> leaves, Set<Long> logKeys) {
            this.leaves = leaves;
            this.logKeys = logKeys;
        }
    }

    /**
     * Walk the still-intact trunk to find every same-family leaf in the
     * canopy. MUST be called before logs are broken — once the trunk turns
     * to AIR the BFS can't bridge from the seed up to the leaves.
     */
    public CanopyResult collectCanopyLeaves(Block trunkOrigin, Material logType, int radius) {
        Set<Material> targetLeaves = LogCatalog.leavesOf(logType);
        if (targetLeaves.isEmpty()) return new CanopyResult(Collections.emptyList(), Collections.emptySet());
        return collectCanopy(trunkOrigin, logType, targetLeaves, radius);
    }

    /**
     * Vanilla leaf decay strips leaves that are >6 blocks from any log. We
     * use the same radius to decide whether a pre-collected leaf is still
     * supported by a surviving log (partial-fell case where anti-grief
     * vetoes part of the trunk) and should NOT be force-decayed.
     */
    private static final int SUPPORT_RADIUS = 6;
    /** Leaves farther than this from any tree log are not collected. */
    private static final int MAX_LEAF_DIST_FROM_LOG = 7;

    /**
     * @param feller        the player whose felling triggered this decay; used
     *                      to attribute the leaf removals in CoreProtect logs.
     * @param logType       the felled trunk's material — used to find the
     *                      matching leaf and to recognise still-standing logs
     *                      that should keep their canopy alive.
     * @param result        canopy result from {@link #collectCanopyLeaves}.
     * @param ticksPerLeaf  delay between consecutive leaf pops.
     */
    public void scheduleDecay(Player feller, Material logType, CanopyResult result, long ticksPerLeaf) {
        List<Block> leaves = result.leaves;
        Set<Long> logKeys = result.logKeys;
        if (leaves == null || leaves.isEmpty()) return;
        Set<Material> targetLeaves = LogCatalog.leavesOf(logType);
        if (targetLeaves.isEmpty()) return;

        // Defensive copy + random order so the canopy crumbles organically
        // rather than in BFS rings, and so the caller can reuse their list.
        List<Block> popOrder = new ArrayList<>(leaves);
        Collections.shuffle(popOrder);
        long period = Math.max(1L, ticksPerLeaf);

        new BukkitRunnable() {
            int idx = 0;
            @Override public void run() {
                if (idx >= popOrder.size()) { cancel(); return; }
                Block leaf = popOrder.get(idx++);
                // Skip leaves whose chunk has unloaded — accessing them would
                // force a chunk load (or throw on stricter Paper modes) and
                // we'd rather just let those leaves decay vanilla-style later.
                if (!leaf.getWorld().isChunkLoaded(leaf.getX() >> 4, leaf.getZ() >> 4)) return;
                if (!targetLeaves.contains(leaf.getType())) return; // already decayed by other means
                if (tracker.isPlaced(leaf)) return;       // safety: re-check placed
                if (isPersistent(leaf)) return;           // shears-set leaves stay
                // Anti-grief plugins can veto part of the chain (e.g. a claim
                // boundary cuts the trunk in half). Surviving same-family logs
                // would normally keep this leaf alive, so don't force-decay
                // it — vanilla won't either. Without this, a partial fell
                // strips canopy leaves off logs that are still standing.
                if (hasNearbySameFamilyLog(leaf, logType, logKeys)) return;

                // Vanilla decay sound + particles before breaking.
                leaf.getWorld().spawnParticle(Particle.BLOCK, leaf.getLocation().add(0.5, 0.5, 0.5),
                        12, 0.3, 0.3, 0.3, 0, leaf.getBlockData());
                leaf.getWorld().playSound(leaf.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.4f, 1.0f);

                // Block.breakNaturally() doesn't fire BlockBreakEvent, so
                // CoreProtect can't see it — log the removal manually before
                // we destroy the block (need the original material/loc).
                coreProtect.logBreak(feller, leaf.getLocation(), leaf.getType());
                leaf.breakNaturally();
            }
        }.runTaskTimer(plugin, 0L, period);
    }

    /**
     * Instead of scanning a 13³ cube in the world for every leaf, we only
     * check positions that belonged to the original tree. If a collected log
     * is within support radius and still exists, the leaf is protected.
     * This reduces lookups from ~2,200 per leaf to a handful.
     */
    private boolean hasNearbySameFamilyLog(Block leaf, Material logType, Set<Long> logKeys) {
        int lx = leaf.getX(), ly = leaf.getY(), lz = leaf.getZ();
        for (int dy = -SUPPORT_RADIUS; dy <= SUPPORT_RADIUS; dy++) {
            for (int dx = -SUPPORT_RADIUS; dx <= SUPPORT_RADIUS; dx++) {
                for (int dz = -SUPPORT_RADIUS; dz <= SUPPORT_RADIUS; dz++) {
                    long key = LogCatalog.packKey(lx + dx, ly + dy, lz + dz);
                    if (logKeys.contains(key)) {
                        Block candidate = leaf.getRelative(dx, dy, dz);
                        Material t = candidate.getType();
                        if (LogCatalog.isLog(t) && LogCatalog.sameFamily(t, logType)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private CanopyResult collectCanopy(Block start, Material logType, Set<Material> targetLeaves, int radius) {
        var world = start.getWorld();
        Set<Long> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();

        // Phase 1 — discover every log in the tree.
        Set<Long> logKeys = new HashSet<>();
        List<Block> logBlocks = new ArrayList<>();
        queue.add(start);
        visited.add(LogCatalog.packKey(start));

        int verticalCap = radius * 5;
        int logBudget = 8192;
        while (!queue.isEmpty() && logBudget-- > 0) {
            Block b = queue.poll();
            int dx = Math.abs(b.getX() - start.getX());
            int dy = Math.abs(b.getY() - start.getY());
            int dz = Math.abs(b.getZ() - start.getZ());
            if (dx > radius || dy > verticalCap || dz > radius) continue;

            if (LogCatalog.isLog(b.getType()) && LogCatalog.sameFamily(b.getType(), logType)) {
                long key = LogCatalog.packKey(b);
                if (logKeys.add(key)) {
                    logBlocks.add(b);
                }
                for (int ox = -1; ox <= 1; ox++)
                    for (int oy = -1; oy <= 1; oy++)
                        for (int oz = -1; oz <= 1; oz++) {
                            if (ox == 0 && oy == 0 && oz == 0) continue;
                            int nx = b.getX() + ox;
                            int nz = b.getZ() + oz;
                            if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;
                            Block n = b.getRelative(ox, oy, oz);
                            if (visited.add(LogCatalog.packKey(n))) queue.add(n);
                        }
            }
        }

        if (logKeys.isEmpty()) return new CanopyResult(Collections.emptyList(), Collections.emptySet());

        // Phase 2 — flood-fill from all collected logs through leaves.
        // Distance from the nearest log is tracked; leaves beyond
        // MAX_LEAF_DIST_FROM_LOG are ignored. We do NOT walk through new logs
        // here — that prevents leaking into an adjacent tree whose trunk is
        // close to our canopy.
        visited.clear();
        queue.clear();
        Deque<Integer> distQueue = new ArrayDeque<>();
        for (Block log : logBlocks) {
            long key = LogCatalog.packKey(log);
            visited.add(key);
            queue.add(log);
            distQueue.add(0);
        }

        List<Block> found = new ArrayList<>();
        int leafBudget = 65536;
        while (!queue.isEmpty() && leafBudget-- > 0) {
            Block b = queue.poll();
            int dist = distQueue.poll();

            for (int ox = -1; ox <= 1; ox++)
                for (int oy = -1; oy <= 1; oy++)
                    for (int oz = -1; oz <= 1; oz++) {
                        if (ox == 0 && oy == 0 && oz == 0) continue;
                        int nx = b.getX() + ox;
                        int ny = b.getY() + oy;
                        int nz = b.getZ() + oz;
                        if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;

                        int nextDist = dist + 1;
                        if (nextDist > MAX_LEAF_DIST_FROM_LOG) continue;

                        long nKey = LogCatalog.packKey(nx, ny, nz);
                        if (!visited.add(nKey)) continue;

                        Block n = world.getBlockAt(nx, ny, nz);
                        Material t = n.getType();
                        if (targetLeaves.contains(t)) {
                            if (!tracker.isPlaced(n) && !isPersistent(n)) found.add(n);
                            queue.add(n);
                            distQueue.add(nextDist);
                        }
                        // Intentionally do NOT enqueue logs here — all tree
                        // logs are already in the starting set.
                    }
        }

        return new CanopyResult(found, logKeys);
    }

    private boolean isPersistent(Block leaf) {
        BlockData data = leaf.getBlockData();
        return data instanceof Leaves l && l.isPersistent();
    }

}
