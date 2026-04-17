package com.zonefall.pve;

import com.zonefall.core.ZonefallConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Spawns simple hostile mobs near unresolved players at a configured interval.
 */
public final class PvePressureManager {
    private static final List<EntityType> HOSTILES = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER);

    private final Plugin plugin;
    private final ZonefallConfig config;
    private final ActivePlayerProvider activePlayers;
    private final Random random = new Random();

    public PvePressureManager(Plugin plugin, ZonefallConfig config, ActivePlayerProvider activePlayers) {
        this.plugin = plugin;
        this.config = config;
        this.activePlayers = activePlayers;
    }

    public void start() {
        plugin.getLogger().info("PvE pressure enabled. Interval=" + config.mobSpawnIntervalSeconds() + "s.");
    }

    public void tick(int elapsedSeconds) {
        if (elapsedSeconds <= 0 || elapsedSeconds % config.mobSpawnIntervalSeconds() != 0) {
            return;
        }
        for (UUID playerId : activePlayers.participants()) {
            if (!activePlayers.isActiveParticipant(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            spawnNear(player);
        }
    }

    public void stop() {
        plugin.getLogger().info("PvE pressure stopped.");
    }

    private void spawnNear(Player player) {
        World world = player.getWorld();
        for (int i = 0; i < config.mobsPerPlayer(); i++) {
            double distance = config.spawnMinDistance()
                    + random.nextDouble(config.spawnMaxDistance() - config.spawnMinDistance());
            double angle = random.nextDouble(Math.PI * 2.0);
            int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location spawn = new Location(world, x + 0.5, y, z + 0.5);
            EntityType type = HOSTILES.get(random.nextInt(HOSTILES.size()));
            world.spawnEntity(spawn, type);
            if (config.debug()) {
                plugin.getLogger().info("Spawned " + type + " near " + player.getName()
                        + " at " + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ());
            }
        }
    }
}
