package com.zonefall.arena;

import com.zonefall.core.ZonefallConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Creates local test arenas. Later this can own arena definitions, world templates, and sessions.
 */
public final class ArenaManager {
    private final Plugin plugin;
    private final ZonefallConfig config;

    public ArenaManager(Plugin plugin, ZonefallConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public Arena createLocalArena(Player creator) {
        Location center = creator.getLocation().clone();
        Location extraction = center.clone().add(Math.max(12.0, config.extractionRadius() * 3.0), 0.0, 0.0);
        return new Arena("local-" + System.currentTimeMillis(), creator.getWorld(), center, extraction);
    }

    public Arena createLocalArenaFromDefaultWorld() {
        World world = Bukkit.getWorlds().get(0);
        Location center = world.getSpawnLocation().clone();
        Location extraction = center.clone().add(Math.max(12.0, config.extractionRadius() * 3.0), 0.0, 0.0);
        plugin.getLogger().info("Creating console local arena in world " + world.getName());
        return new Arena("local-" + System.currentTimeMillis(), world, center, extraction);
    }
}

