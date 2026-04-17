package com.zonefall.ui;

import com.zonefall.arena.ArenaController;
import com.zonefall.arena.ArenaManager;
import com.zonefall.core.ZonefallConfig;
import com.zonefall.extract.ExtractionZone;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight labels for spectator points and active extraction routes.
 */
public final class WorldMarkerLabelService {
    private final Plugin plugin;
    private final ArenaManager arenaManager;
    private final ZonefallConfig config;
    private final Map<String, ArmorStand> labels = new HashMap<>();
    private BukkitTask task;

    public WorldMarkerLabelService(Plugin plugin, ArenaManager arenaManager, ZonefallConfig config) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.config = config;
    }

    public void start() {
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 40L, 40L);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        labels.values().forEach(label -> {
            if (label != null && label.isValid()) {
                label.remove();
            }
        });
        labels.clear();
    }

    private void refresh() {
        Set<String> liveKeys = new HashSet<>();
        for (ArenaController arena : arenaManager.arenas()) {
            if (config.spectatorLabelsEnabled()) {
                String key = arena.id() + ":spectator";
                liveKeys.add(key);
                refreshLabel(key, arena.spectatorLocation(), "Spectate " + arena.displayName(), config.spectatorLabelHeight());
            }
            if (config.extractionLabelsEnabled()) {
                for (ExtractionZone zone : arena.activeExtractionZones()) {
                    String key = arena.id() + ":extract:" + zone.id();
                    liveKeys.add(key);
                    refreshLabel(key, zone.center(), "ACTIVE EXTRACTION | " + arena.displayName(), config.extractionLabelHeight());
                }
            }
        }
        for (String key : Set.copyOf(labels.keySet())) {
            if (!liveKeys.contains(key)) {
                ArmorStand removed = labels.remove(key);
                if (removed != null && removed.isValid()) {
                    removed.remove();
                }
            }
        }
    }

    private void refreshLabel(String key, Location baseLocation, String text, double height) {
        ArmorStand label = labels.get(key);
        if (label == null || !label.isValid()) {
            label = spawn(baseLocation.clone().add(0, height, 0));
            labels.put(key, label);
        }
        label.customName(Component.text(text));
    }

    private ArmorStand spawn(Location location) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setPersistent(false);
        stand.setCustomNameVisible(true);
        return stand;
    }
}

