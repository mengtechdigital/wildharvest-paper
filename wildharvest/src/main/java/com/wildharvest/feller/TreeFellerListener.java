package com.wildharvest.feller;

import com.wildharvest.chain.ChainBreaker;
import com.wildharvest.chain.ChainCooldown;
import com.wildharvest.config.WildHarvestConfig;
import com.wildharvest.toggle.PlayerToggleStore;
import com.wildharvest.tracker.PlacedBlockTracker;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Triggers a tree-felling chain when a player breaks the bottom of a tree.
 *
 * Smart-detection rules (in order of cheapness):
 *   - Player must hold an axe (configurable).
 *   - Block must be a log type.
 *   - Block must NOT be a player-placed log.
 *   - Connected leaves of the same wood family must exist (configurable).
 *     This is the key signal that distinguishes a tree from a wooden pillar
 *     in someone's house — houses don't carry leaves around.
 */
public final class TreeFellerListener implements Listener {

    private final WildHarvestConfig config;
    private final PlacedBlockTracker tracker;
    private final ChainBreaker chainBreaker;
    private final PlayerToggleStore toggles;
    private final LeafDecayScheduler leafDecay;
    private final ChainCooldown cooldown;

    public TreeFellerListener(
            WildHarvestConfig config,
            PlacedBlockTracker tracker,
            ChainBreaker chainBreaker,
            PlayerToggleStore toggles,
            LeafDecayScheduler leafDecay,
            ChainCooldown cooldown
    ) {
        this.config = config;
        this.tracker = tracker;
        this.chainBreaker = chainBreaker;
        this.toggles = toggles;
        this.leafDecay = leafDecay;
        this.cooldown = cooldown;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.treeFellerEnabled()) return;

        Player player = event.getPlayer();
        // Guard against recursion: ChainBreaker fires BlockBreakEvent for each
        // chained log so anti-grief plugins can veto, which would otherwise
        // re-enter this handler.
        if (chainBreaker.isInChain(player)) return;
        if (!player.hasPermission("wildharvest.use")) return;
        if (!player.hasPermission("wildharvest.treefeller")) return;
        if (!toggles.isTreeFellerEnabled(player)) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        boolean sneaking = player.isSneaking();
        if (config.sneakDisablesChain() ? sneaking : !sneaking) return;

        Block block = event.getBlock();
        Material type = block.getType();
        if (!LogCatalog.isLog(type)) return;
        if (tracker.isPlaced(block) && !player.hasPermission("wildharvest.bypass-protection")) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (config.treeFellerRequireAxe() && !isAxe(tool.getType())) return;

        if (config.treeFellerRequireLeaves() && !LogCatalog.isNetherStem(type)) {
            if (!hasConnectedLeaves(block)) return;
        }

        if (player.getGameMode() != GameMode.CREATIVE
                && player.getFoodLevel() < config.minFoodLevel()) return;
        if (!cooldown.tryConsume(player, config.chainCooldownMs())) return;

        // Redirect the seed block's drop into the player's inventory: compute
        // drops based on the still-intact block, suppress vanilla's ground drop
        // via the event, and deliver them ourselves. Vanilla still handles the
        // actual setType(AIR), durability damage, and exhaustion for the seed.
        if (event.isDropItems()) {
            chainBreaker.deliverDropsToPlayer(
                    player,
                    block.getLocation(),
                    block.getDrops(tool, player)
            );
            event.setDropItems(false);
        }

        // Chain-break the rest of the tree with same-family log matching.
        // 26-way (diagonal) connectivity is required for the weird-shaped
        // trees: acacia angled trunks, cherry bent trunks, jungle 2x2 with
        // horizontal branches, dark oak 2x2, mangrove. 6-way only works for
        // perfectly vertical oak/spruce/birch and would silently miss every
        // diagonal segment. Same-family matching still prevents bleeding
        // into a neighbouring tree of a different wood.
        chainBreaker.breakChain(
                player,
                block,
                (b, seedType) -> {
                    Material m = b.getType();
                    return LogCatalog.isLog(m) && LogCatalog.sameFamily(m, seedType);
                },
                config.treeFellerMaxLogs(),
                true
        );

        if (config.treeFellerDecayLeaves() && !LogCatalog.isNetherStem(type)) {
            leafDecay.scheduleDecay(
                    player,
                    block,
                    type,
                    config.treeFellerLeafDecayRadius(),
                    config.treeFellerLeafDecayTicks()
            );
        }
    }

    private boolean isAxe(Material m) {
        return switch (m) {
            case WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> true;
            default -> false;
        };
    }

    /** Cheap BFS up to a small radius looking for any leaf block of the same family. */
    private boolean hasConnectedLeaves(Block start) {
        Material targetLeaf = LogCatalog.leafOf(start.getType());
        if (targetLeaf == null) return false;

        Set<Long> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(packKey(start));

        int budget = 96; // small, but enough to walk up most trunks and find a leaf
        while (!queue.isEmpty() && budget-- > 0) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = b.getRelative(dx, dy, dz);
                        Material t = n.getType();
                        if (Tag.LEAVES.isTagged(t) && t == targetLeaf) return true;
                        if (LogCatalog.isLog(t) && LogCatalog.sameFamily(t, start.getType())) {
                            if (visited.add(packKey(n))) queue.add(n);
                        }
                    }
        }
        return false;
    }

    private static long packKey(Block b) {
        long x = ((long) b.getX()) & 0x3FFFFFFL;
        long z = ((long) b.getZ()) & 0x3FFFFFFL;
        long y = ((long) (b.getY() + 2048)) & 0xFFFL;
        return x | (z << 26) | (y << 52);
    }
}
