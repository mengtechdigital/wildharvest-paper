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
 *      while every log in the trunk is still intact. The BFS walks 26-way
 *      through same-family logs to reach the canopy and collects the leaves.
 *      Doing this AFTER the chain break would fail on anything bigger than a
 *      bushy oak: the trunk would already be AIR and the BFS couldn't bridge
 *      from the seed block up to the canopy.
 *   2. {@link #scheduleDecay} — call this AFTER the chain break with the list
 *      from step 1. Pops leaves one-by-one from a single repeating task.
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
     * Walk the still-intact trunk to find every same-family leaf in the
     * canopy. MUST be called before logs are broken — once the trunk turns
     * to AIR the BFS can't bridge from the seed up to the leaves.
     */
    public List<Block> collectCanopyLeaves(Block trunkOrigin, Material logType, int radius) {
        Material targetLeaf = LogCatalog.leafOf(logType);
        if (targetLeaf == null) return Collections.emptyList();
        return collectCanopy(trunkOrigin, logType, targetLeaf, radius);
    }

    /**
     * Vanilla leaf decay strips leaves that are >6 blocks from any log. We
     * use the same radius to decide whether a pre-collected leaf is still
     * supported by a surviving log (partial-fell case where anti-grief
     * vetoes part of the trunk) and should NOT be force-decayed.
     */
    private static final int SUPPORT_RADIUS = 6;

    /**
     * @param feller        the player whose felling triggered this decay; used
     *                      to attribute the leaf removals in CoreProtect logs.
     * @param logType       the felled trunk's material — used to find the
     *                      matching leaf and to recognise still-standing logs
     *                      that should keep their canopy alive.
     * @param leaves        leaves to pop, pre-collected via
     *                      {@link #collectCanopyLeaves}.
     * @param ticksPerLeaf  delay between consecutive leaf pops.
     */
    public void scheduleDecay(Player feller, Material logType, List<Block> leaves, long ticksPerLeaf) {
        if (leaves == null || leaves.isEmpty()) return;
        Material targetLeaf = LogCatalog.leafOf(logType);
        if (targetLeaf == null) return;

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
                if (leaf.getType() != targetLeaf) return; // already decayed by other means
                if (tracker.isPlaced(leaf)) return;       // safety: re-check placed
                if (isPersistent(leaf)) return;           // shears-set leaves stay
                // Anti-grief plugins can veto part of the chain (e.g. a claim
                // boundary cuts the trunk in half). Surviving same-family logs
                // would normally keep this leaf alive, so don't force-decay
                // it — vanilla won't either. Without this, a partial fell
                // strips canopy leaves off logs that are still standing.
                if (hasNearbySameFamilyLog(leaf, logType)) return;

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
        }.runTaskTimer(plugin, period, period);
    }

    /**
     * Cube scan for any same-family log within {@link #SUPPORT_RADIUS}.
     * Skips positions in unloaded chunks rather than force-loading them —
     * a leaf at a chunk border could otherwise pull in 4+ neighbouring
     * chunks synchronously on the main thread, every tick.
     */
    private boolean hasNearbySameFamilyLog(Block leaf, Material logType) {
        var world = leaf.getWorld();
        for (int dy = -SUPPORT_RADIUS; dy <= SUPPORT_RADIUS; dy++)
            for (int dx = -SUPPORT_RADIUS; dx <= SUPPORT_RADIUS; dx++)
                for (int dz = -SUPPORT_RADIUS; dz <= SUPPORT_RADIUS; dz++) {
                    int bx = leaf.getX() + dx;
                    int bz = leaf.getZ() + dz;
                    if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
                    Material t = leaf.getRelative(dx, dy, dz).getType();
                    if (LogCatalog.isLog(t) && LogCatalog.sameFamily(t, logType)) return true;
                }
        return false;
    }

    private List<Block> collectCanopy(Block start, Material logType, Material targetLeaf, int radius) {
        var world = start.getWorld();
        List<Block> found = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(packKey(start));

        // Vertical extent has to be much larger than horizontal: jungle giants
        // and spruce megas are ~30 blocks tall but only ~10 wide, and we BFS
        // from the bottom trunk block. Cap horizontally with `radius` (stops
        // the BFS leaking into adjacent trees through bridging leaves) and
        // vertically with `radius * 5` (covers any vanilla tree height).
        int verticalCap = radius * 5;
        int budget = 16384; // hard ceiling so we never walk forever
        while (!queue.isEmpty() && budget-- > 0) {
            Block b = queue.poll();
            int dx = Math.abs(b.getX() - start.getX());
            int dy = Math.abs(b.getY() - start.getY());
            int dz = Math.abs(b.getZ() - start.getZ());
            if (dx > radius || dy > verticalCap || dz > radius) continue;

            for (int ox = -1; ox <= 1; ox++)
                for (int oy = -1; oy <= 1; oy++)
                    for (int oz = -1; oz <= 1; oz++) {
                        if (ox == 0 && oy == 0 && oz == 0) continue;
                        // Skip neighbours in unloaded chunks rather than
                        // force-loading them on the main thread — a tree at
                        // the edge of view distance could pull in adjacent
                        // chunks synchronously otherwise.
                        int nx = b.getX() + ox;
                        int nz = b.getZ() + oz;
                        if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;
                        Block n = b.getRelative(ox, oy, oz);
                        if (!visited.add(packKey(n))) continue;
                        Material t = n.getType();
                        if (t == targetLeaf) {
                            if (!tracker.isPlaced(n) && !isPersistent(n)) found.add(n);
                            queue.add(n); // walk through leaves to reach more leaves
                        } else if (LogCatalog.isLog(t) && LogCatalog.sameFamily(t, logType)) {
                            queue.add(n); // intact log — keep searching from it
                        }
                    }
        }
        return found;
    }

    private boolean isPersistent(Block leaf) {
        BlockData data = leaf.getBlockData();
        return data instanceof Leaves l && l.isPersistent();
    }

    private static long packKey(Block b) {
        long x = ((long) b.getX()) & 0x3FFFFFFL;
        long z = ((long) b.getZ()) & 0x3FFFFFFL;
        long y = ((long) (b.getY() + 2048)) & 0xFFFL;
        return x | (z << 26) | (y << 52);
    }
}
