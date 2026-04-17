package com.zonefall.hub;

import com.zonefall.arena.LocationSpec;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Owns hub return behavior. Physical join pads can plug into this later.
 */
public final class HubManager {
    private final LocationSpec spawn;

    public HubManager(LocationSpec spawn) {
        this.spawn = spawn;
    }

    public Location spawnLocation() {
        return spawn.toLocation();
    }

    public void sendToHub(Player player) {
        player.teleport(spawnLocation());
    }
}

