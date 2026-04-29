package com.wildharvest.chain;

import com.wildharvest.config.WildHarvestConfig;
import com.wildharvest.tracker.PlacedBlockTracker;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;

/**
 * Generic flood-fill chain breaker. The caller supplies a "match" predicate
 * that decides whether a neighbour is part of the same chain (e.g. same log
 * family, same ore group). The seed block is assumed to already be a valid
 * member — the caller's BlockBreakEvent handler vouches for it.
 *
 * Responsibilities (in order, per block):
 *   1. Skip if the block has been moved/changed since it was queued.
 *   2. Skip if it's a player-placed block.
 *   3. Compute drops with the player's tool (so Fortune / Silk Touch apply).
 *   4. Set the block to AIR (the seed is excluded — vanilla handles that).
 *   5. Push drops directly into the player's inventory (overflow drops at feet).
 *   6. Charge hunger exhaustion and one durability point (Unbreaking-aware).
 *   7. Stop if the tool is about to break and preserve-tool is on.
 */
public final class ChainBreaker {

    private static final int[][] OFFSETS_26 = build26();
    private static final int[][] OFFSETS_6 = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final WildHarvestConfig config;
    private final PlacedBlockTracker tracker;
    /** Players currently inside a chain — used to short-circuit the listener
     *  so the BlockBreakEvent we fire per chained block doesn't recurse. */
    private final Set<UUID> activeChains = ConcurrentHashMap.newKeySet();

    public ChainBreaker(WildHarvestConfig config, PlacedBlockTracker tracker) {
        this.config = config;
        this.tracker = tracker;
    }

    public boolean isInChain(Player p) {
        return activeChains.contains(p.getUniqueId());
    }

    /**
     * Push a collection of drops directly into the player's inventory; anything
     * that doesn't fit falls naturally at {@code loc}. Used by listeners to
     * redirect the seed block's vanilla drop into the inventory after calling
     * {@code event.setDropItems(false)} on the original BlockBreakEvent.
     */
    public void deliverDropsToPlayer(Player player, Location loc, Collection<ItemStack> drops) {
        giveOrDrop(player, loc, drops);
    }

    /**
     * Break the connected component starting at {@code seed} (exclusive — seed
     * is assumed to be broken by the caller's vanilla event flow). Uses 6-way
     * connectivity by default; pass {@code diagonal = true} for ore veins.
     */
    public int breakChain(
            Player player,
            Block seed,
            BiPredicate<Block, Material> matches,
            int hardCap,
            boolean diagonal
    ) {
        int cap = Math.min(hardCap, config.maxChainSize());
        if (cap <= 0) return 0;

        ItemStack tool = player.getInventory().getItemInMainHand();
        Material seedType = seed.getType();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;

        Set<Long> visited = new HashSet<>();
        visited.add(packKey(seed));
        Deque<Block> queue = new ArrayDeque<>();
        enqueueNeighbours(seed, queue, visited, diagonal);

        int broken = 0;
        int[][] offsets = diagonal ? OFFSETS_26 : OFFSETS_6;

        UUID id = player.getUniqueId();
        activeChains.add(id);
        try {
            while (!queue.isEmpty() && broken < cap) {
                Block b = queue.poll();
                if (!matches.test(b, seedType)) continue;
                if (tracker.isPlaced(b)) continue;
                if (config.preserveTool() && !creative && toolWillBreak(tool)) break;

                // Fire a real BlockBreakEvent so anti-grief plugins (WorldGuard,
                // GriefPrevention, CoreProtect, etc.) get a veto. Recursion into
                // our own listeners is blocked by the activeChains guard.
                //
                // Do NOT preset setDropItems(false): we don't let vanilla break
                // the block (we setType AIR ourselves), so vanilla won't drop
                // anything on its own. Reading isDropItems() afterwards lets a
                // plugin opt out of drops via the event if it wants to.
                BlockBreakEvent bbe = new BlockBreakEvent(b, player);
                Bukkit.getPluginManager().callEvent(bbe);
                if (bbe.isCancelled()) continue; // protected — skip and don't enqueue neighbours

                Collection<ItemStack> drops = bbe.isDropItems() ? b.getDrops(tool, player) : java.util.Collections.emptyList();
                int xp = bbe.getExpToDrop();
                Location dropLoc = b.getLocation();

                b.setType(Material.AIR, false);
                broken++;

                giveOrDrop(player, dropLoc, drops);
                if (xp > 0) {
                    dropLoc.getWorld().spawn(dropLoc.add(0.5, 0.5, 0.5), ExperienceOrb.class, orb -> orb.setExperience(xp));
                }

                if (!creative) {
                    player.setExhaustion(player.getExhaustion() + config.hungerExhaustionPerBlock());
                    damageTool(player, tool);
                    if (tool.getAmount() <= 0) {
                        player.getInventory().setItemInMainHand(null);
                        break;
                    }
                }

                // Continue the BFS from this block.
                for (int[] o : offsets) {
                    Block next = b.getRelative(o[0], o[1], o[2]);
                    if (visited.add(packKey(next))) queue.add(next);
                }
            }
            return broken;
        } finally {
            activeChains.remove(id);
        }
    }

    private void enqueueNeighbours(Block seed, Deque<Block> queue, Set<Long> visited, boolean diagonal) {
        int[][] offsets = diagonal ? OFFSETS_26 : OFFSETS_6;
        for (int[] o : offsets) {
            Block next = seed.getRelative(o[0], o[1], o[2]);
            if (visited.add(packKey(next))) queue.add(next);
        }
    }

    private void giveOrDrop(Player player, Location loc, Collection<ItemStack> drops) {
        if (drops.isEmpty()) return;
        PlayerInventory inv = player.getInventory();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) continue;
            var leftover = inv.addItem(drop);
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(loc, overflow);
            }
        }
    }

    private boolean toolWillBreak(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) return false;
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable d)) return false;
        int max = tool.getType().getMaxDurability();
        if (max <= 0) return false;
        return d.getDamage() >= max - 1;
    }

    private void damageTool(Player player, ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) return;
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable d)) return;
        int max = tool.getType().getMaxDurability();
        if (max <= 0) return;

        int unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0) {
            // Vanilla formula: chance to skip damage = 1 - 1/(unbreaking+1) for tools.
            if (ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) return;
        }

        int newDamage = d.getDamage() + 1;
        if (newDamage >= max) {
            tool.setAmount(0); // tool broke
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }
        d.setDamage(newDamage);
        tool.setItemMeta(meta);
    }

    private static long packKey(Block b) {
        // World-wide packing for visited set: 26 bits x, 12 bits y, 26 bits z.
        long x = ((long) b.getX()) & 0x3FFFFFFL;
        long z = ((long) b.getZ()) & 0x3FFFFFFL;
        long y = ((long) (b.getY() + 2048)) & 0xFFFL;
        return (x) | (z << 26) | (y << 52);
    }

    private static int[][] build26() {
        int[][] arr = new int[26][3];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    arr[i][0] = dx; arr[i][1] = dy; arr[i][2] = dz;
                    i++;
                }
        return arr;
    }
}
