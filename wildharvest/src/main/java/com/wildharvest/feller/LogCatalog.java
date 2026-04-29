package com.wildharvest.feller;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.EnumMap;
import java.util.Map;

/**
 * Maps log materials to the leaf material of the same wood family. Used by
 * the TreeFeller's "require leaves" check so we can confirm a fellable tree
 * is actually a tree (and not, say, a stack of oak logs in a player's house).
 */
public final class LogCatalog {

    private static final Map<Material, Material> LOG_TO_LEAVES = new EnumMap<>(Material.class);

    static {
        pair(Material.OAK_LOG, Material.OAK_WOOD, Material.STRIPPED_OAK_LOG, Material.STRIPPED_OAK_WOOD, Material.OAK_LEAVES);
        pair(Material.SPRUCE_LOG, Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_SPRUCE_WOOD, Material.SPRUCE_LEAVES);
        pair(Material.BIRCH_LOG, Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_BIRCH_WOOD, Material.BIRCH_LEAVES);
        pair(Material.JUNGLE_LOG, Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_JUNGLE_WOOD, Material.JUNGLE_LEAVES);
        pair(Material.ACACIA_LOG, Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_ACACIA_WOOD, Material.ACACIA_LEAVES);
        pair(Material.DARK_OAK_LOG, Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_WOOD, Material.DARK_OAK_LEAVES);
        pair(Material.MANGROVE_LOG, Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_MANGROVE_WOOD, Material.MANGROVE_LEAVES);
        pair(Material.CHERRY_LOG, Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_CHERRY_WOOD, Material.CHERRY_LEAVES);
        pair(Material.AZALEA_LEAVES, Material.AZALEA_LEAVES); // azalea trees grow with these
        // Nether "trees" — no leaves; we still allow felling but require-leaves check is bypassed for these.
        pair(Material.CRIMSON_STEM, Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_CRIMSON_HYPHAE, Material.NETHER_WART_BLOCK);
        pair(Material.WARPED_STEM, Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_STEM, Material.STRIPPED_WARPED_HYPHAE, Material.WARPED_WART_BLOCK);
    }

    private LogCatalog() {}

    private static void pair(Material... mats) {
        Material leaves = mats[mats.length - 1];
        for (int i = 0; i < mats.length - 1; i++) {
            LOG_TO_LEAVES.put(mats[i], leaves);
        }
        LOG_TO_LEAVES.put(leaves, leaves);
    }

    public static boolean isLog(Material m) {
        return Tag.LOGS.isTagged(m);
    }

    public static boolean isLeaf(Material m) {
        return Tag.LEAVES.isTagged(m) || m == Material.NETHER_WART_BLOCK || m == Material.WARPED_WART_BLOCK || m == Material.SHROOMLIGHT;
    }

    /** Two logs belong to the same family if they share a leaf material. */
    public static boolean sameFamily(Material a, Material b) {
        Material la = LOG_TO_LEAVES.get(a);
        Material lb = LOG_TO_LEAVES.get(b);
        return la != null && la == lb;
    }

    public static Material leafOf(Material log) {
        return LOG_TO_LEAVES.get(log);
    }

    /** Nether "trees" don't have leaves — skip the leaf requirement for them. */
    public static boolean isNetherStem(Material m) {
        return m == Material.CRIMSON_STEM || m == Material.WARPED_STEM
            || m == Material.CRIMSON_HYPHAE || m == Material.WARPED_HYPHAE
            || m == Material.STRIPPED_CRIMSON_STEM || m == Material.STRIPPED_WARPED_STEM
            || m == Material.STRIPPED_CRIMSON_HYPHAE || m == Material.STRIPPED_WARPED_HYPHAE;
    }
}
