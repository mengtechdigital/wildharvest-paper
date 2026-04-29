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
 * doesn't hang in the air. We collect candidate leaves once (BFS from the
 * trunk through same-family leaves and remaining logs), filter out placed
 * and persistent ones, then pop them one-by-one from a single repeating
 * task. One task per fell — cheaper than N delayed tasks.
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
     * @param feller        the player whose felling triggered this decay; used
     *                      to attribute the leaf removals in CoreProtect logs.
     * @param trunkOrigin   a log block from the felled tree (used as BFS seed).
     * @param logType       the trunk's material — used to find the matching leaf.
     * @param radius        search radius around each trunk block.
     * @param ticksPerLeaf  delay between consecutive leaf pops.
     */
    public void scheduleDecay(Player feller, Block trunkOrigin, Material logType, int radius, long ticksPerLeaf) {
        Material targetLeaf = LogCatalog.leafOf(logType);
        if (targetLeaf == null) return;

        List<Block> leaves = collectCanopy(trunkOrigin, logType, targetLeaf, radius);
        if (leaves.isEmpty()) return;

        // Random order so the canopy crumbles organically rather than in BFS rings.
        Collections.shuffle(leaves);
        long period = Math.max(1L, ticksPerLeaf);

        new BukkitRunnable() {
            int idx = 0;
            @Override public void run() {
                if (idx >= leaves.size()) { cancel(); return; }
                Block leaf = leaves.get(idx++);
                // Skip leaves whose chunk has unloaded — accessing them would
                // force a chunk load (or throw on stricter Paper modes) and
                // we'd rather just let those leaves decay vanilla-style later.
                if (!leaf.getWorld().isChunkLoaded(leaf.getX() >> 4, leaf.getZ() >> 4)) return;
                if (leaf.getType() != targetLeaf) return; // already decayed by other means
                if (tracker.isPlaced(leaf)) return;       // safety: re-check placed
                if (isPersistent(leaf)) return;           // shears-set leaves stay

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

    private List<Block> collectCanopy(Block start, Material logType, Material targetLeaf, int radius) {
        List<Block> found = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(packKey(start));

        int budget = 4096; // hard ceiling so we never walk forever
        while (!queue.isEmpty() && budget-- > 0) {
            Block b = queue.poll();
            int dx = Math.abs(b.getX() - start.getX());
            int dy = Math.abs(b.getY() - start.getY());
            int dz = Math.abs(b.getZ() - start.getZ());
            if (dx > radius || dy > radius * 2 || dz > radius) continue;

            for (int ox = -1; ox <= 1; ox++)
                for (int oy = -1; oy <= 1; oy++)
                    for (int oz = -1; oz <= 1; oz++) {
                        if (ox == 0 && oy == 0 && oz == 0) continue;
                        Block n = b.getRelative(ox, oy, oz);
                        if (!visited.add(packKey(n))) continue;
                        Material t = n.getType();
                        if (t == targetLeaf) {
                            if (!tracker.isPlaced(n) && !isPersistent(n)) found.add(n);
                            queue.add(n); // walk through leaves to reach more leaves
                        } else if (LogCatalog.isLog(t) && LogCatalog.sameFamily(t, logType)) {
                            queue.add(n); // remaining/uncut log — keep searching from it
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
