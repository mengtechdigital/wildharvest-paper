package com.wildharvest.toggle;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player on/off toggles for TreeFeller and VeinMiner. Toggles are
 * in-memory only — players default to enabled on join. Persisting across
 * restarts isn't worth the storage layer for a feature most players just
 * leave on.
 */
public final class PlayerToggleStore {

    private final Set<UUID> treeFellerDisabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> veinMinerDisabled = ConcurrentHashMap.newKeySet();

    public boolean isTreeFellerEnabled(Player p) { return !treeFellerDisabled.contains(p.getUniqueId()); }
    public boolean isVeinMinerEnabled(Player p)  { return !veinMinerDisabled.contains(p.getUniqueId()); }

    public boolean toggleTreeFeller(Player p) {
        UUID id = p.getUniqueId();
        if (treeFellerDisabled.remove(id)) return true;
        treeFellerDisabled.add(id);
        return false;
    }

    public boolean toggleVeinMiner(Player p) {
        UUID id = p.getUniqueId();
        if (veinMinerDisabled.remove(id)) return true;
        veinMinerDisabled.add(id);
        return false;
    }

    public void setBoth(Player p, boolean enabled) {
        UUID id = p.getUniqueId();
        if (enabled) {
            treeFellerDisabled.remove(id);
            veinMinerDisabled.remove(id);
        } else {
            treeFellerDisabled.add(id);
            veinMinerDisabled.add(id);
        }
    }
}
