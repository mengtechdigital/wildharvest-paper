package com.wildharvest.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Snapshot of config.yml. Re-read on /wildharvest reload — listeners hold a
 * reference to this object and pick up new values on the next event.
 */
public final class WildHarvestConfig {

    private int maxChainSize;
    private boolean sneakDisablesChain;
    private float hungerExhaustionPerBlock;
    private int minFoodLevel;
    private long chainCooldownMs;
    private boolean preserveTool;

    private boolean treeFellerEnabled;
    private boolean treeFellerRequireAxe;
    private boolean treeFellerDecayLeaves;
    private int treeFellerLeafDecayRadius;
    private long treeFellerLeafDecayTicks;
    private int treeFellerMaxLogs;
    private boolean treeFellerRequireLeaves;

    private boolean veinMinerEnabled;
    private boolean veinMinerRequireCorrectTool;
    private int veinMinerMaxOres;

    public void load(Plugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        this.maxChainSize = c.getInt("max-chain-size", 256);
        this.sneakDisablesChain = c.getBoolean("sneak-disables-chain", true);
        this.hungerExhaustionPerBlock = (float) c.getDouble("hunger-exhaustion-per-block", 0.005);
        this.minFoodLevel = c.getInt("min-food-level", 1);
        this.chainCooldownMs = c.getLong("chain-cooldown-ms", 0);
        this.preserveTool = c.getBoolean("preserve-tool", true);

        this.treeFellerEnabled = c.getBoolean("treefeller.enabled", true);
        this.treeFellerRequireAxe = c.getBoolean("treefeller.require-axe", true);
        this.treeFellerDecayLeaves = c.getBoolean("treefeller.decay-leaves", true);
        this.treeFellerLeafDecayRadius = c.getInt("treefeller.leaf-decay-radius", 12);
        this.treeFellerLeafDecayTicks = c.getLong("treefeller.leaf-decay-ticks-per-leaf", 1);
        this.treeFellerMaxLogs = c.getInt("treefeller.max-logs", 256);
        this.treeFellerRequireLeaves = c.getBoolean("treefeller.require-leaves", true);

        this.veinMinerEnabled = c.getBoolean("veinminer.enabled", true);
        this.veinMinerRequireCorrectTool = c.getBoolean("veinminer.require-correct-tool", true);
        this.veinMinerMaxOres = c.getInt("veinminer.max-ores", 64);
    }

    public int maxChainSize() { return maxChainSize; }
    public boolean sneakDisablesChain() { return sneakDisablesChain; }
    public float hungerExhaustionPerBlock() { return hungerExhaustionPerBlock; }
    public int minFoodLevel() { return minFoodLevel; }
    public long chainCooldownMs() { return chainCooldownMs; }
    public boolean preserveTool() { return preserveTool; }

    public boolean treeFellerEnabled() { return treeFellerEnabled; }
    public boolean treeFellerRequireAxe() { return treeFellerRequireAxe; }
    public boolean treeFellerDecayLeaves() { return treeFellerDecayLeaves; }
    public int treeFellerLeafDecayRadius() { return treeFellerLeafDecayRadius; }
    public long treeFellerLeafDecayTicks() { return treeFellerLeafDecayTicks; }
    public int treeFellerMaxLogs() { return treeFellerMaxLogs; }
    public boolean treeFellerRequireLeaves() { return treeFellerRequireLeaves; }

    public boolean veinMinerEnabled() { return veinMinerEnabled; }
    public boolean veinMinerRequireCorrectTool() { return veinMinerRequireCorrectTool; }
    public int veinMinerMaxOres() { return veinMinerMaxOres; }
}
