package com.wildharvest;

import com.wildharvest.chain.ChainBreaker;
import com.wildharvest.chain.ChainCooldown;
import com.wildharvest.command.WildHarvestCommand;
import com.wildharvest.config.WildHarvestConfig;
import com.wildharvest.feller.LeafDecayScheduler;
import com.wildharvest.feller.TreeFellerListener;
import com.wildharvest.integration.CoreProtectHook;
import com.wildharvest.toggle.PlayerToggleStore;
import com.wildharvest.tracker.PlacedBlockListener;
import com.wildharvest.tracker.PlacedBlockTracker;
import com.wildharvest.veinminer.VeinMinerListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WildHarvestPlugin extends JavaPlugin {

    private WildHarvestConfig config;

    @Override
    public void onEnable() {
        this.config = new WildHarvestConfig();
        this.config.load(this);

        PlacedBlockTracker tracker = new PlacedBlockTracker(this);
        PlayerToggleStore toggles = new PlayerToggleStore();
        ChainBreaker chainBreaker = new ChainBreaker(config, tracker);
        ChainCooldown cooldown = new ChainCooldown();

        CoreProtectHook coreProtect = new CoreProtectHook(this);
        coreProtect.tryHook();

        LeafDecayScheduler leafDecay = new LeafDecayScheduler(this, tracker, coreProtect);

        getServer().getPluginManager().registerEvents(new PlacedBlockListener(tracker), this);
        getServer().getPluginManager().registerEvents(
                new TreeFellerListener(config, tracker, chainBreaker, toggles, leafDecay, cooldown), this);
        getServer().getPluginManager().registerEvents(
                new VeinMinerListener(config, tracker, chainBreaker, toggles, cooldown), this);

        WildHarvestCommand cmd = new WildHarvestCommand(this, toggles);
        PluginCommand pc = getCommand("wildharvest");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        getLogger().info("WildHarvest enabled.");
    }

    public WildHarvestConfig getWildHarvestConfig() {
        return config;
    }
}
