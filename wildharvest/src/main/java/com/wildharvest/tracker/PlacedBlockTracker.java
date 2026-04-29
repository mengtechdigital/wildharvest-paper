package com.wildharvest.tracker;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

/**
 * Tracks which blocks were placed by players (vs. naturally generated) so the
 * chain-breaker can refuse to consume player-built structures. Storage is a
 * sorted long[] in each chunk's PersistentDataContainer — chunk-local coords
 * are packed into one long, giving O(log n) membership checks and zero extra
 * persistence layer.
 *
 * Y is shifted by +Y_OFFSET so the negative world floor (-64) packs cleanly.
 */
public final class PlacedBlockTracker {

    private static final int Y_OFFSET = 128; // covers worlds from y=-128 upward
    private final NamespacedKey key;

    public PlacedBlockTracker(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "placed_blocks");
    }

    public boolean isPlaced(Block block) {
        long[] arr = read(block.getChunk());
        if (arr.length == 0) return false;
        return Arrays.binarySearch(arr, pack(block)) >= 0;
    }

    public void markPlaced(Block block) {
        Chunk chunk = block.getChunk();
        long[] arr = read(chunk);
        long packed = pack(block);
        int idx = Arrays.binarySearch(arr, packed);
        if (idx >= 0) return; // already tracked
        int insert = -idx - 1;
        long[] next = new long[arr.length + 1];
        System.arraycopy(arr, 0, next, 0, insert);
        next[insert] = packed;
        System.arraycopy(arr, insert, next, insert + 1, arr.length - insert);
        write(chunk, next);
    }

    public void unmarkPlaced(Block block) {
        Chunk chunk = block.getChunk();
        long[] arr = read(chunk);
        if (arr.length == 0) return;
        int idx = Arrays.binarySearch(arr, pack(block));
        if (idx < 0) return;
        long[] next = new long[arr.length - 1];
        System.arraycopy(arr, 0, next, 0, idx);
        System.arraycopy(arr, idx + 1, next, idx, arr.length - idx - 1);
        write(chunk, next);
    }

    private long[] read(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        long[] arr = pdc.get(key, PersistentDataType.LONG_ARRAY);
        return arr == null ? new long[0] : arr;
    }

    private void write(Chunk chunk, long[] arr) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (arr.length == 0) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.LONG_ARRAY, arr);
        }
    }

    private static long pack(Block block) {
        int lx = block.getX() & 0xF;          // 0..15  (4 bits)
        int lz = block.getZ() & 0xF;          // 0..15  (4 bits)
        long y = block.getY() + Y_OFFSET;     // 0..511 (9 bits is enough, use 16 for headroom)
        return (lx & 0xFL) | ((lz & 0xFL) << 4) | (y << 8);
    }
}
