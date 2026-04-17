package com.zonefall.loot;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative match-scoped loot state separate from normal Minecraft inventories.
 */
public final class MatchLootTracker {
    private final Plugin plugin;
    private final Map<UUID, LootBundle> carriedLoot = new LinkedHashMap<>();
    private final Map<UUID, DroppedLootContainer> droppedContainers = new LinkedHashMap<>();
    private final Map<UUID, LootBundle> extractedLoot = new LinkedHashMap<>();
    private final Map<UUID, LootBundle> lostOnDeathLoot = new LinkedHashMap<>();

    public MatchLootTracker(Plugin plugin) {
        this.plugin = plugin;
    }

    public void ensurePlayer(UUID playerId) {
        carriedLoot.computeIfAbsent(playerId, ignored -> LootBundle.empty());
    }

    public void removePlayer(UUID playerId) {
        carriedLoot.remove(playerId);
    }

    public LootBundle carriedLoot(UUID playerId) {
        return carriedLoot.computeIfAbsent(playerId, ignored -> LootBundle.empty()).copy();
    }

    public void grant(UUID playerId, LootBundle loot) {
        if (loot.isEmpty()) {
            return;
        }
        carriedLoot.computeIfAbsent(playerId, ignored -> LootBundle.empty()).addAll(loot);
    }

    public LootBundle extract(UUID playerId) {
        LootBundle carried = carriedLoot.computeIfAbsent(playerId, ignored -> LootBundle.empty());
        LootBundle extracted = carried.copy();
        if (!extracted.isEmpty()) {
            extractedLoot.computeIfAbsent(playerId, ignored -> LootBundle.empty()).addAll(extracted);
        }
        carriedLoot.put(playerId, LootBundle.empty());
        return extracted;
    }

    public Optional<DroppedLootContainer> dropCarriedLoot(UUID playerId, Location location) {
        LootBundle carried = carriedLoot.computeIfAbsent(playerId, ignored -> LootBundle.empty());
        if (carried.isEmpty()) {
            return Optional.empty();
        }
        LootBundle dropped = carried.copy();
        carriedLoot.put(playerId, LootBundle.empty());
        lostOnDeathLoot.computeIfAbsent(playerId, ignored -> LootBundle.empty()).addAll(dropped);

        DroppedLootContainer container = new DroppedLootContainer(UUID.randomUUID(), playerId, location.clone(), dropped);
        droppedContainers.put(container.id(), container);
        return Optional.of(container);
    }

    public List<DroppedLootContainer> pickupNearby(UUID playerId, Location location, double radius) {
        List<DroppedLootContainer> pickedUp = new ArrayList<>();
        for (DroppedLootContainer container : List.copyOf(droppedContainers.values())) {
            if (!container.isWithinPickupRange(location, radius)) {
                continue;
            }
            droppedContainers.remove(container.id());
            grant(playerId, container.contents());
            pickedUp.add(container);
        }
        return pickedUp;
    }

    public void drawDropMarkers() {
        for (DroppedLootContainer container : droppedContainers.values()) {
            Location location = container.location().clone().add(0.0, 0.4, 0.0);
            location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 8, 0.35, 0.25, 0.35, 0.0);
        }
    }

    public void clearAll() {
        carriedLoot.clear();
        droppedContainers.clear();
        extractedLoot.clear();
        lostOnDeathLoot.clear();
    }

    public Map<UUID, LootBundle> extractedLootSnapshot() {
        return copyMap(extractedLoot);
    }

    public Map<UUID, LootBundle> lostOnDeathLootSnapshot() {
        return copyMap(lostOnDeathLoot);
    }

    public String describeCarried(UUID playerId) {
        return carriedLoot(playerId).describe();
    }

    public String describeDrops() {
        if (droppedContainers.isEmpty()) {
            return "none";
        }
        List<String> descriptions = new ArrayList<>();
        for (DroppedLootContainer container : droppedContainers.values()) {
            Location loc = container.location();
            descriptions.add(container.contents().describe() + " at "
                    + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
        }
        return String.join("; ", descriptions);
    }

    private Map<UUID, LootBundle> copyMap(Map<UUID, LootBundle> source) {
        Map<UUID, LootBundle> copy = new LinkedHashMap<>();
        source.forEach((playerId, loot) -> copy.put(playerId, loot.copy()));
        return Map.copyOf(copy);
    }
}
