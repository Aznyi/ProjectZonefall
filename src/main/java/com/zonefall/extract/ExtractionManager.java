package com.zonefall.extract;

import com.zonefall.arena.Arena;
import com.zonefall.core.ZonefallConfig;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Maintains extraction zones and their simple prototype marker effects.
 */
public final class ExtractionManager {
    private final Plugin plugin;
    private final Arena arena;
    private final List<ExtractionZone> zones = new ArrayList<>();
    private BukkitTask markerTask;

    public ExtractionManager(Plugin plugin, ZonefallConfig config, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        zones.add(new ExtractionZone(
                "alpha",
                arena.extractionLocation(),
                config.extractionRadius(),
                config.extractionVerticalTolerance()
        ));
    }

    public void start() {
        plugin.getLogger().info("Extraction zones active: " + describeZones());
        markerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::drawMarkers, 1L, 20L);
    }

    public void stop() {
        if (markerTask != null) {
            markerTask.cancel();
            markerTask = null;
        }
    }

    public boolean isInsideExtraction(Location location) {
        return zones.stream().anyMatch(zone -> zone.contains(location));
    }

    public String describeZones() {
        return zones.stream().map(ExtractionZone::describe).toList().toString();
    }

    private void drawMarkers() {
        for (ExtractionZone zone : zones) {
            Location center = zone.center();
            center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 12, zone.radius(), 0.4, zone.radius(), 0.0);
        }
    }
}

