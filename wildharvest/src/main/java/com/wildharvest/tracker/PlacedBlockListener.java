package com.wildharvest.tracker;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Marks player-placed blocks and clears the marker when the block is removed
 * by any means (break, burn, explosion, piston, fluid). Without the cleanup,
 * a placed-then-broken-then-naturally-regenerated block would still register
 * as "placed" forever.
 */
public final class PlacedBlockListener implements Listener {

    private final PlacedBlockTracker tracker;

    public PlacedBlockListener(PlacedBlockTracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        tracker.markPlaced(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        // Always clear the marker when a tracked block is removed, regardless
        // of who broke it. The chain-breaker reads the marker BEFORE the
        // event reaches MONITOR, so this clean-up is correct.
        tracker.unmarkPlaced(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        tracker.unmarkPlaced(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent event) {
        tracker.unmarkPlaced(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        // A flowing fluid replacing the destination removes whatever block
        // was there.
        tracker.unmarkPlaced(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block b : event.blockList()) tracker.unmarkPlaced(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) tracker.unmarkPlaced(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        BlockFace dir = event.getDirection();
        // Pistons move blocks; the marker has to follow. Iterate in reverse
        // to avoid clobbering destinations before sources are read.
        var blocks = event.getBlocks();
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Block from = blocks.get(i);
            Block to = from.getRelative(dir);
            moveMarker(from, to);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        BlockFace dir = event.getDirection();
        for (Block from : event.getBlocks()) {
            Block to = from.getRelative(dir);
            moveMarker(from, to);
        }
    }

    private void moveMarker(Block from, Block to) {
        if (tracker.isPlaced(from)) {
            tracker.unmarkPlaced(from);
            tracker.markPlaced(to);
        }
    }
}
