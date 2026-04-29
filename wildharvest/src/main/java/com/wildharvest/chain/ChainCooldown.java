package com.wildharvest.chain;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the timestamp of each player's last chain trigger. Used to throttle
 * click-spam without forcing a long delay — defaults are sub-second.
 */
public final class ChainCooldown {

    private final ConcurrentHashMap<UUID, Long> lastTrigger = new ConcurrentHashMap<>();

    /**
     * @return true if {@code player} is past the cooldown and may trigger now.
     *         Also stamps "now" as the new last-trigger time on success.
     */
    public boolean tryConsume(Player player, long cooldownMs) {
        if (cooldownMs <= 0) return true;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastTrigger.get(id);
        if (last != null && now - last < cooldownMs) return false;
        lastTrigger.put(id, now);
        return true;
    }
}
