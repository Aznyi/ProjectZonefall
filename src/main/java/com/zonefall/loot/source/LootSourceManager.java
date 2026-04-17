package com.zonefall.loot.source;

import com.zonefall.arena.Arena;
import com.zonefall.arena.ArenaLootSourcePlacement;
import com.zonefall.core.ZonefallConfig;
import com.zonefall.loot.LootBundle;
import com.zonefall.loot.table.WeightedLootTable;
import com.zonefall.util.Messages;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns match-scoped interactable loot sources and their consumed state.
 */
public final class LootSourceManager {
    private final Plugin plugin;
    private final ZonefallConfig config;
    private final Arena arena;
    private final Map<LootSourceType, WeightedLootTable> tables;
    private final List<LootSource> sources = new ArrayList<>();
    private LootActivationMode activationMode = LootActivationMode.ALL_ACTIVE;
    private int activeCount = 1;

    public LootSourceManager(Plugin plugin, ZonefallConfig config, Arena arena) {
        this.plugin = plugin;
        this.config = config;
        this.arena = arena;
        this.tables = new EnumMap<>(config.lootTables());
    }

    public void registerDefaults() {
        Location center = arena.center();
        addSource(LootSourceType.SUPPLY_CRATE, center.clone().add(8, 0, 4), true);
        addSource(LootSourceType.SCRAP_CACHE, center.clone().add(-7, 0, 6), true);
        addSource(LootSourceType.ORE_NODE, center.clone().add(4, 0, -9), true);
        plugin.getLogger().info("Registered " + sources.size() + " default loot sources.");
    }

    public void registerPlacements(List<ArenaLootSourcePlacement> placements, LootActivationMode activationMode, int activeCount) {
        this.activationMode = activationMode;
        this.activeCount = Math.max(1, activeCount);
        if (placements.isEmpty()) {
            registerDefaults();
            return;
        }
        for (ArenaLootSourcePlacement placement : placements) {
            addSource(placement.type(), placement.location().toLocation(), false);
        }
        selectActiveSources();
        applyMarkerBlocks();
        plugin.getLogger().info("Registered " + sources.size() + " configured loot sources for " + arena.id() + ".");
    }

    public LootSource addSource(LootSourceType type, Location requestedLocation) {
        return addSource(type, requestedLocation, true);
    }

    public LootSource addSource(LootSourceType type, Location requestedLocation, boolean active) {
        Location location = requestedLocation.clone();
        location.setY(location.getWorld().getHighestBlockYAt(location) + 1.0);
        Block block = location.getBlock();
        LootSource source = new LootSource(UUID.randomUUID(), type, block.getLocation(), block.getType(), active);
        if (active) {
            block.setType(type.markerMaterial());
        }
        sources.add(source);
        return source;
    }

    public Optional<LootBundle> handleInteract(Player player, Block block) {
        if (block == null || !block.getWorld().equals(arena.world())) {
            return Optional.empty();
        }
        for (LootSource source : sources) {
            if (!sameBlock(source.location(), block.getLocation())) {
                continue;
            }
            if (!source.active()) {
                player.sendMessage(Messages.error(source.type().displayName() + " is dormant this round."));
                return Optional.empty();
            }
            if (source.consumed()) {
                player.sendMessage(Messages.error(source.type().displayName() + " is already empty."));
                return Optional.empty();
            }
            source.markConsumed();
            block.setType(Material.COBBLESTONE);
            LootBundle reward = tables.getOrDefault(source.type(), WeightedLootTable.fallback()).roll();
            player.sendMessage(Messages.ok("Looted " + source.type().displayName() + ": " + reward.describe() + "."));
            return Optional.of(reward);
        }
        return Optional.empty();
    }

    public void drawMarkers() {
        for (LootSource source : sources) {
            if (!source.active() || source.consumed()) {
                continue;
            }
            Location marker = source.location().clone().add(0.5, 1.15, 0.5);
            marker.getWorld().spawnParticle(Particle.ENCHANT, marker, 10, 0.35, 0.25, 0.35, 0.0);
        }
    }

    public void stop() {
        for (LootSource source : sources) {
            source.location().getBlock().setType(source.originalMaterial());
        }
        sources.clear();
    }

    public int sourceCount() {
        return sources.size();
    }

    public long openedCount() {
        return sources.stream().filter(LootSource::consumed).count();
    }

    public long activeCount() {
        return sources.stream().filter(LootSource::active).count();
    }

    public long inactiveCount() {
        return sources.stream().filter(source -> !source.active()).count();
    }

    public LootActivationMode activationMode() {
        return activationMode;
    }

    public String describeActiveSources() {
        return sources.stream().filter(LootSource::active).map(LootSource::describe).toList().toString();
    }

    public String describeInactiveSources() {
        return sources.stream().filter(source -> !source.active()).map(LootSource::describe).toList().toString();
    }

    public String describe() {
        if (sources.isEmpty()) {
            return "none";
        }
        List<String> descriptions = sources.stream().map(LootSource::describe).toList();
        return String.join("; ", descriptions);
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private void selectActiveSources() {
        Set<UUID> activeIds = new HashSet<>();
        int count = switch (activationMode) {
            case ALL_ACTIVE -> sources.size();
            case RANDOM_ONE -> 1;
            case RANDOM_TWO -> 2;
            case RANDOM_COUNT -> activeCount;
        };
        List<LootSource> shuffled = new ArrayList<>(sources);
        Collections.shuffle(shuffled);
        shuffled.stream()
                .limit(Math.min(count, shuffled.size()))
                .map(LootSource::id)
                .forEach(activeIds::add);
        for (LootSource source : sources) {
            source.setActive(activeIds.contains(source.id()));
        }
    }

    private void applyMarkerBlocks() {
        for (LootSource source : sources) {
            source.location().getBlock().setType(source.active() ? source.type().markerMaterial() : source.originalMaterial());
        }
    }
}
