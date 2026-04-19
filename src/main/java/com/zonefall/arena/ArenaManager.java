package com.zonefall.arena;

import com.zonefall.core.ZonefallConfig;
import com.zonefall.core.ZonefallServices;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registry for the configured prebuilt hub arenas.
 */
public final class ArenaManager {
    private final Map<String, ArenaController> arenas = new LinkedHashMap<>();
    private final Plugin plugin;
    private final ZonefallServices services;
    private final ArenaAnnouncementService announcements = new ArenaAnnouncementService();

    public ArenaManager(Plugin plugin, ZonefallConfig config, ZonefallServices services) {
        this.plugin = plugin;
        this.services = services;
        load(config);
    }

    public void reload(ZonefallConfig config) {
        shutdown();
        arenas.clear();
        load(config);
    }

    private void load(ZonefallConfig config) {
        for (ArenaDefinition definition : config.arenas()) {
            ArenaValidator.validate(plugin, config, definition);
            arenas.put(definition.id().toLowerCase(), new ArenaController(plugin, config, services, definition, announcements));
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " Zonefall arenas.");
    }

    public Collection<ArenaController> arenas() {
        return arenas.values();
    }

    public Optional<ArenaController> find(String id) {
        return Optional.ofNullable(arenas.get(id.toLowerCase()));
    }

    public Optional<ArenaController> findForPlayer(UUID playerId) {
        return arenas.values().stream().filter(arena -> arena.contains(playerId)).findFirst();
    }

    public Optional<ArenaController> findProtecting(Location location) {
        return arenas.values().stream().filter(arena -> arena.protects(location)).findFirst();
    }

    public Optional<ArenaController> findActivePlayableRegion(Location location) {
        return arenas.values().stream().filter(arena -> arena.allowsArenaMobSpawn(location)).findFirst();
    }

    public Optional<ArenaController> findJoinPoint(Location location) {
        return arenas.values().stream().filter(arena -> arena.containsJoinPoint(location)).findFirst();
    }

    public void handleMove(Player player) {
        findForPlayer(player.getUniqueId()).ifPresent(arena -> arena.handleMove(player));
    }

    public void handleDeath(Player player) {
        findForPlayer(player.getUniqueId()).ifPresent(arena -> arena.handleDeath(player));
    }

    public void handleMobKill(Player player) {
        findForPlayer(player.getUniqueId()).ifPresent(arena -> arena.handleMobKill(player));
    }

    public void handleInteract(Player player, Block block) {
        findForPlayer(player.getUniqueId()).ifPresent(arena -> arena.handleInteract(player, block));
    }

    public void shutdown() {
        arenas.values().forEach(ArenaController::shutdown);
    }

    public void drawJoinPointMarkers() {
        arenas.values().forEach(ArenaController::drawJoinPointMarkers);
    }
}
