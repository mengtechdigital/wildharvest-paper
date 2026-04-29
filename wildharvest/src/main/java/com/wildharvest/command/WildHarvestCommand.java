package com.wildharvest.command;

import com.wildharvest.WildHarvestPlugin;
import com.wildharvest.toggle.PlayerToggleStore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class WildHarvestCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            Arrays.asList("treefeller", "veinminer", "both", "on", "off", "status", "reload");

    private final WildHarvestPlugin plugin;
    private final PlayerToggleStore toggles;

    public WildHarvestCommand(WildHarvestPlugin plugin, PlayerToggleStore toggles) {
        this.plugin = plugin;
        this.toggles = toggles;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GRAY + "/" + label + " <treefeller|veinminer|both|on|off|status|reload>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!sender.hasPermission("wildharvest.reload")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            plugin.getWildHarvestConfig().load(plugin);
            sender.sendMessage(ChatColor.GREEN + "WildHarvest config reloaded.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can toggle.");
            return true;
        }

        switch (sub) {
            case "treefeller" -> {
                boolean now = toggles.toggleTreeFeller(player);
                player.sendMessage(label("TreeFeller") + state(now));
            }
            case "veinminer" -> {
                boolean now = toggles.toggleVeinMiner(player);
                player.sendMessage(label("VeinMiner") + state(now));
            }
            case "both", "on" -> {
                toggles.setBoth(player, true);
                player.sendMessage(ChatColor.GREEN + "WildHarvest fully enabled.");
            }
            case "off" -> {
                toggles.setBoth(player, false);
                player.sendMessage(ChatColor.YELLOW + "WildHarvest fully disabled.");
            }
            case "status" -> {
                player.sendMessage(label("TreeFeller") + state(toggles.isTreeFellerEnabled(player)));
                player.sendMessage(label("VeinMiner")  + state(toggles.isVeinMinerEnabled(player)));
            }
            default -> sender.sendMessage(ChatColor.GRAY + "Unknown sub-command.");
        }
        return true;
    }

    private String label(String name) {
        return ChatColor.AQUA + name + ChatColor.GRAY + ": ";
    }

    private String state(boolean enabled) {
        return enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return Collections.emptyList();
    }
}
