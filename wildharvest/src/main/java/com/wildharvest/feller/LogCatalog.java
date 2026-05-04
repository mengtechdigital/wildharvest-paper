package com.wildharvest.feller;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps log materials to the leaf material(s) of the same wood family. Used by
 * the TreeFeller's "require leaves" check so we can confirm a fellable tree
 * is actually a tree (and not, say, a stack of oak logs in a player's house).
 *
 * Vanilla note — azalea trees are an oak/azalea hybrid: the trunk is an
 * OAK_LOG but the canopy is AZALEA_LEAVES (and sometimes FLOWERING_AZALEA_LEAVES),
 * not OAK_LEAVES. Treating "leaf of oak" as a single material makes azalea
 * trees undetectable, so each log family carries a set of accepted leaves.
 */
public final class LogCatalog {

    /** Family identity per log/leaf material, used by {@link #sameFamily}. */
    private static final Map<Material, Material> FAMILY_KEY = new EnumMap<>(Material.class);
    /** Every leaf material vanilla generates inside a given log's family. */
    private static final Map<Material, Set<Material>> LOG_TO_LEAVES = new EnumMap<>(Material.class);

    static {
        family(Material.OAK_LEAVES,
                Set.of(Material.OAK_LEAVES, Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES),
                Material.OAK_LOG, Material.OAK_WOOD, Material.STRIPPED_OAK_LOG, Material.STRIPPED_OAK_WOOD);
        family(Material.SPRUCE_LEAVES, Set.of(Material.SPRUCE_LEAVES),
                Material.SPRUCE_LOG, Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_SPRUCE_WOOD);
        family(Material.BIRCH_LEAVES, Set.of(Material.BIRCH_LEAVES),
                Material.BIRCH_LOG, Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_BIRCH_WOOD);
        family(Material.JUNGLE_LEAVES, Set.of(Material.JUNGLE_LEAVES),
                Material.JUNGLE_LOG, Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_JUNGLE_WOOD);
        family(Material.ACACIA_LEAVES, Set.of(Material.ACACIA_LEAVES),
                Material.ACACIA_LOG, Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_ACACIA_WOOD);
        family(Material.DARK_OAK_LEAVES, Set.of(Material.DARK_OAK_LEAVES),
                Material.DARK_OAK_LOG, Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_WOOD);
        family(Material.MANGROVE_LEAVES, Set.of(Material.MANGROVE_LEAVES),
                Material.MANGROVE_LOG, Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_MANGROVE_WOOD);
        family(Material.CHERRY_LEAVES, Set.of(Material.CHERRY_LEAVES),
                Material.CHERRY_LOG, Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_CHERRY_WOOD);
        // Nether "trees" — no leaves; the require-leaves check is bypassed for these.
        family(Material.NETHER_WART_BLOCK, Set.of(Material.NETHER_WART_BLOCK),
                Material.CRIMSON_STEM, Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_CRIMSON_HYPHAE);
        family(Material.WARPED_WART_BLOCK, Set.of(Material.WARPED_WART_BLOCK),
                Material.WARPED_STEM, Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_STEM, Material.STRIPPED_WARPED_HYPHAE);
    }

    private LogCatalog() {}

    private static void family(Material familyKey, Set<Material> leaves, Material... logs) {
        for (Material log : logs) {
            FAMILY_KEY.put(log, familyKey);
            LOG_TO_LEAVES.put(log, leaves);
        }
        for (Material leaf : leaves) {
            FAMILY_KEY.put(leaf, familyKey);
        }
    }

    public static boolean isLog(Material m) {
        return Tag.LOGS.isTagged(m);
    }

    public static boolean isLeaf(Material m) {
        return Tag.LEAVES.isTagged(m) || m == Material.NETHER_WART_BLOCK || m == Material.WARPED_WART_BLOCK || m == Material.SHROOMLIGHT;
    }

    /** Two materials belong to the same family if they share a family key. */
    public static boolean sameFamily(Material a, Material b) {
        Material ka = FAMILY_KEY.get(a);
        Material kb = FAMILY_KEY.get(b);
        return ka != null && ka == kb;
    }

    /**
     * Every leaf material valid for this log's family. Empty set for unknown
     * logs. Oak's set includes azalea leaves because vanilla azalea trees grow
     * with an oak trunk.
     */
    public static Set<Material> leavesOf(Material log) {
        Set<Material> set = LOG_TO_LEAVES.get(log);
        return set != null ? set : Collections.emptySet();
    }

    /** Nether "trees" don't have leaves — skip the leaf requirement for them. */
    public static boolean isNetherStem(Material m) {
        return m == Material.CRIMSON_STEM || m == Material.WARPED_STEM
            || m == Material.CRIMSON_HYPHAE || m == Material.WARPED_HYPHAE
            || m == Material.STRIPPED_CRIMSON_STEM || m == Material.STRIPPED_WARPED_STEM
            || m == Material.STRIPPED_CRIMSON_HYPHAE || m == Material.STRIPPED_WARPED_HYPHAE;
    }

    /**
     * Pack a block's coordinates into a single long for use as a HashSet key
     * during BFS visited-tracking. Bias x/z by 2^25 so negative coordinates
     * map cleanly into the 26-bit field — world limits are ±30,000,000
     * (< 2^25 = 33,554,432), so the biased value always lies in [0, 2^26).
     */
    public static long packKey(Block b) {
        long x = ((long) (b.getX() + (1 << 25))) & 0x3FFFFFFL;
        long z = ((long) (b.getZ() + (1 << 25))) & 0x3FFFFFFL;
        long y = ((long) (b.getY() + 2048)) & 0xFFFL;
        return x | (z << 26) | (y << 52);
    }
}
