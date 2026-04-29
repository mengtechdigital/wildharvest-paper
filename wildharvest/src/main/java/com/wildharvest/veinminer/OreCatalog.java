package com.wildharvest.veinminer;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/**
 * The set of ore-like materials VeinMiner will chain through, plus the
 * grouping rules used to decide which ores belong to the same vein.
 *
 * Two ores are in the same vein iff they share an OreGroup — so a regular
 * iron ore won't chain into a deepslate iron ore, but iron+iron will, and
 * raw mineral blocks are intentionally NOT included (they're crafting
 * blocks, not ores, and players move them around).
 */
public final class OreCatalog {

    public enum OreGroup {
        COAL, IRON, COPPER, GOLD, REDSTONE, LAPIS, DIAMOND, EMERALD,
        DEEPSLATE_COAL, DEEPSLATE_IRON, DEEPSLATE_COPPER, DEEPSLATE_GOLD,
        DEEPSLATE_REDSTONE, DEEPSLATE_LAPIS, DEEPSLATE_DIAMOND, DEEPSLATE_EMERALD,
        NETHER_GOLD, NETHER_QUARTZ, ANCIENT_DEBRIS
    }

    private OreCatalog() {}

    private static final Set<Material> ORES = EnumSet.of(
        Material.COAL_ORE, Material.IRON_ORE, Material.COPPER_ORE, Material.GOLD_ORE,
        Material.REDSTONE_ORE, Material.LAPIS_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE,
        Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS
    );

    public static boolean isOre(Material m) {
        return ORES.contains(m);
    }

    public static OreGroup groupOf(Material m) {
        return switch (m) {
            case COAL_ORE -> OreGroup.COAL;
            case IRON_ORE -> OreGroup.IRON;
            case COPPER_ORE -> OreGroup.COPPER;
            case GOLD_ORE -> OreGroup.GOLD;
            case REDSTONE_ORE -> OreGroup.REDSTONE;
            case LAPIS_ORE -> OreGroup.LAPIS;
            case DIAMOND_ORE -> OreGroup.DIAMOND;
            case EMERALD_ORE -> OreGroup.EMERALD;
            case DEEPSLATE_COAL_ORE -> OreGroup.DEEPSLATE_COAL;
            case DEEPSLATE_IRON_ORE -> OreGroup.DEEPSLATE_IRON;
            case DEEPSLATE_COPPER_ORE -> OreGroup.DEEPSLATE_COPPER;
            case DEEPSLATE_GOLD_ORE -> OreGroup.DEEPSLATE_GOLD;
            case DEEPSLATE_REDSTONE_ORE -> OreGroup.DEEPSLATE_REDSTONE;
            case DEEPSLATE_LAPIS_ORE -> OreGroup.DEEPSLATE_LAPIS;
            case DEEPSLATE_DIAMOND_ORE -> OreGroup.DEEPSLATE_DIAMOND;
            case DEEPSLATE_EMERALD_ORE -> OreGroup.DEEPSLATE_EMERALD;
            case NETHER_GOLD_ORE -> OreGroup.NETHER_GOLD;
            case NETHER_QUARTZ_ORE -> OreGroup.NETHER_QUARTZ;
            case ANCIENT_DEBRIS -> OreGroup.ANCIENT_DEBRIS;
            default -> null;
        };
    }

    public static boolean canToolMine(Material tool, Material ore) {
        OreGroup g = groupOf(ore);
        if (g == null) return false;
        int toolTier = pickaxeTier(tool);
        if (toolTier < 0) return false;
        return toolTier >= requiredTier(g);
    }

    private static int pickaxeTier(Material tool) {
        return switch (tool) {
            case WOODEN_PICKAXE -> 0;
            case GOLDEN_PICKAXE, STONE_PICKAXE -> 1;
            case IRON_PICKAXE -> 2;
            case DIAMOND_PICKAXE -> 3;
            case NETHERITE_PICKAXE -> 4;
            default -> -1;
        };
    }

    private static int requiredTier(OreGroup g) {
        return switch (g) {
            case COAL, COPPER, NETHER_GOLD, NETHER_QUARTZ,
                 DEEPSLATE_COAL, DEEPSLATE_COPPER -> 0;
            case IRON, LAPIS, DEEPSLATE_IRON, DEEPSLATE_LAPIS -> 1;
            case GOLD, REDSTONE, DIAMOND, EMERALD,
                 DEEPSLATE_GOLD, DEEPSLATE_REDSTONE, DEEPSLATE_DIAMOND, DEEPSLATE_EMERALD,
                 ANCIENT_DEBRIS -> 2;
        };
    }
}
