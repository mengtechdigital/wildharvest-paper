package com.wildharvest.veinminer;

import com.wildharvest.chain.ChainBreaker;
import com.wildharvest.chain.ChainCooldown;
import com.wildharvest.config.WildHarvestConfig;
import com.wildharvest.toggle.PlayerToggleStore;
import com.wildharvest.tracker.PlacedBlockTracker;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Triggers a vein-mining chain when a player breaks an ore.
 *
 * Smart-detection rules:
 *   - Player must hold a pickaxe of correct tier (configurable).
 *   - Block must be an ore (see OreCatalog).
 *   - Block must NOT be a player-placed ore. This is the rule that stops a
 *     silk-touched diamond ore being placed and re-mined to "duplicate" the
 *     vein effect.
 *   - Chain only walks through ores in the same OreGroup.
 */
public final class VeinMinerListener implements Listener {

    private final WildHarvestConfig config;
    private final PlacedBlockTracker tracker;
    private final ChainBreaker chainBreaker;
    private final PlayerToggleStore toggles;
    private final ChainCooldown cooldown;

    public VeinMinerListener(
            WildHarvestConfig config,
            PlacedBlockTracker tracker,
            ChainBreaker chainBreaker,
            PlayerToggleStore toggles,
            ChainCooldown cooldown
    ) {
        this.config = config;
        this.tracker = tracker;
        this.chainBreaker = chainBreaker;
        this.toggles = toggles;
        this.cooldown = cooldown;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.veinMinerEnabled()) return;

        Player player = event.getPlayer();
        if (chainBreaker.isInChain(player)) return;
        if (!player.hasPermission("wildharvest.use")) return;
        if (!player.hasPermission("wildharvest.veinminer")) return;
        if (!toggles.isVeinMinerEnabled(player)) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        boolean sneaking = player.isSneaking();
        if (config.sneakDisablesChain() ? sneaking : !sneaking) return;

        Block block = event.getBlock();
        Material type = block.getType();
        if (!OreCatalog.isOre(type)) return;
        if (tracker.isPlaced(block) && !player.hasPermission("wildharvest.bypass-protection")) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (config.veinMinerRequireCorrectTool() && !OreCatalog.canToolMine(tool.getType(), type)) return;

        if (player.getGameMode() != GameMode.CREATIVE
                && player.getFoodLevel() < config.minFoodLevel()) return;
        if (!cooldown.tryConsume(player, config.chainCooldownMs())) return;

        // Redirect the seed ore's drop into the player's inventory: same
        // pattern as TreeFeller — vanilla still breaks the seed and damages
        // the tool, but the dropped item is delivered to inventory instead.
        if (event.isDropItems()) {
            chainBreaker.deliverDropsToPlayer(
                    player,
                    block.getLocation(),
                    block.getDrops(tool, player)
            );
            event.setDropItems(false);
        }

        OreCatalog.OreGroup seedGroup = OreCatalog.groupOf(type);

        chainBreaker.breakChain(
                player,
                block,
                (b, seedType) -> OreCatalog.groupOf(b.getType()) == seedGroup,
                config.veinMinerMaxOres(),
                true
        );
    }
}
