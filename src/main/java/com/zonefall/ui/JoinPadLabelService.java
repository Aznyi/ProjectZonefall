package com.zonefall.ui;

import com.zonefall.arena.ArenaController;
import com.zonefall.arena.ArenaJoinPoint;
import com.zonefall.arena.ArenaManager;
import com.zonefall.core.ZonefallConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders lightweight floating labels above hub join pads using marker armor stands.
 */
public final class JoinPadLabelService {
    private final Plugin plugin;
    private final ArenaManager arenaManager;
    private final ZonefallConfig config;
    private final Map<String, ArmorStand> labels = new HashMap<>();
    private BukkitTask task;

    public JoinPadLabelService(Plugin plugin, ArenaManager arenaManager, ZonefallConfig config) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.config = config;
    }

    public void start() {
        if (!config.joinPadLabelsEnabled() || task != null) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L, 40L);
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
        for (ArenaController arena : arenaManager.arenas()) {
            for (ArenaJoinPoint joinPoint : arena.definition().joinPoints()) {
                String key = arena.id() + ":" + joinPoint.id();
                ArmorStand label = labels.get(key);
                if (label == null || !label.isValid()) {
                    label = spawnLabel(joinPoint);
                    labels.put(key, label);
                }
                label.customName(Component.text(labelText(arena)));
            }
        }
    }

    private ArmorStand spawnLabel(ArenaJoinPoint joinPoint) {
        Location location = joinPoint.location().toLocation().clone().add(0, config.joinPadLabelHeight(), 0);
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setPersistent(false);
        stand.customName(Component.text("Zonefall"));
        stand.setCustomNameVisible(true);
        return stand;
    }

    private String labelText(ArenaController arena) {
        return arena.displayName()
                + " | " + arena.state()
                + " | " + arena.remainingSeconds() + "s"
                + " | " + arena.activePlayerCount() + "p"
                + " | " + (arena.joinWindowOpen() ? "JOIN OPEN" : "CLOSED");
    }
}

