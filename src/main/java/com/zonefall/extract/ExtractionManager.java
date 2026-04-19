package com.zonefall.extract;

import com.zonefall.arena.Arena;
import com.zonefall.core.ZonefallConfig;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maintains extraction zones and their simple prototype marker effects.
 */
public final class ExtractionManager {
    private final Plugin plugin;
    private final Arena arena;
    private final List<ExtractionZone> zones = new ArrayList<>();
    private final Set<String> activeZoneIds = new HashSet<>();
    private final ExtractionActivationMode activationMode;
    private final int activeCount;
    private final ExtractionRevealMode revealMode;
    private final int revealSecondsRemaining;
    private boolean revealed;
    private BukkitTask markerTask;

    public ExtractionManager(Plugin plugin, ZonefallConfig config, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.activationMode = ExtractionActivationMode.ALL_ACTIVE;
        this.activeCount = 1;
        this.revealMode = ExtractionRevealMode.ROUND_START;
        this.revealSecondsRemaining = 0;
        zones.add(new ExtractionZone(
                "alpha",
                arena.extractionLocation(),
                config.extractionRadius(),
                config.extractionVerticalTolerance()
        ));
    }

    public ExtractionManager(Plugin plugin, ZonefallConfig config, Arena arena, List<Location> extractionLocations) {
        this(plugin, config, arena, extractionLocations, ExtractionActivationMode.ALL_ACTIVE, 1, ExtractionRevealMode.ROUND_START, 0);
    }

    public ExtractionManager(Plugin plugin, ZonefallConfig config, Arena arena, List<Location> extractionLocations,
                             ExtractionActivationMode activationMode, int activeCount) {
        this(plugin, config, arena, extractionLocations, activationMode, activeCount, ExtractionRevealMode.ROUND_START, 0);
    }

    public ExtractionManager(Plugin plugin, ZonefallConfig config, Arena arena, List<Location> extractionLocations,
                             ExtractionActivationMode activationMode, int activeCount,
                             ExtractionRevealMode revealMode, int revealSecondsRemaining) {
        this.plugin = plugin;
        this.arena = arena;
        this.activationMode = activationMode;
        this.activeCount = Math.max(1, activeCount);
        this.revealMode = revealMode;
        this.revealSecondsRemaining = Math.max(0, revealSecondsRemaining);
        if (extractionLocations.isEmpty()) {
            zones.add(new ExtractionZone("alpha", arena.extractionLocation(), config.extractionRadius(), config.extractionVerticalTolerance()));
            return;
        }
        for (int i = 0; i < extractionLocations.size(); i++) {
            zones.add(new ExtractionZone(
                    "extract-" + (i + 1),
                    extractionLocations.get(i),
                    config.extractionRadius(),
                    config.extractionVerticalTolerance()
            ));
        }
    }

    public void start() {
        selectActiveZones();
        revealed = revealMode == ExtractionRevealMode.ROUND_START;
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
        return revealed && zones.stream().anyMatch(zone -> activeZoneIds.contains(zone.id()) && zone.contains(location));
    }

    public String describeZones() {
        return zones.stream().map(zone -> zone.describe() + (activeZoneIds.contains(zone.id()) ? " selected" : " inactive")
                + (revealed && activeZoneIds.contains(zone.id()) ? " revealed" : " hidden")).toList().toString();
    }

    public String describeActiveZones() {
        if (!revealed) {
            return "hidden";
        }
        return zones.stream().filter(zone -> activeZoneIds.contains(zone.id())).map(ExtractionZone::describe).toList().toString();
    }

    public String describeInactiveZones() {
        return zones.stream().filter(zone -> !activeZoneIds.contains(zone.id()) || !revealed).map(ExtractionZone::describe).toList().toString();
    }

    public List<ExtractionZone> activeZones() {
        if (!revealed) {
            return List.of();
        }
        return zones.stream().filter(zone -> activeZoneIds.contains(zone.id())).toList();
    }

    public boolean revealed() {
        return revealed;
    }

    public String revealState() {
        return revealed ? "REVEALED" : "PENDING(" + revealMode + ", " + revealSecondsRemaining + "s)";
    }

    public boolean shouldReveal(int remainingSeconds, boolean finalExtraction) {
        if (revealed) {
            return false;
        }
        return switch (revealMode) {
            case ROUND_START -> true;
            case FINAL_EXTRACTION -> finalExtraction;
            case CUSTOM_TIME_REMAINING -> remainingSeconds <= revealSecondsRemaining;
        };
    }

    public boolean revealNow() {
        if (revealed) {
            return false;
        }
        revealed = true;
        return true;
    }

    public java.util.Optional<String> activateAdditionalZone() {
        for (ExtractionZone zone : zones) {
            if (activeZoneIds.contains(zone.id())) {
                continue;
            }
            activeZoneIds.add(zone.id());
            revealed = true;
            return java.util.Optional.of(zone.describe());
        }
        return java.util.Optional.empty();
    }

    private void selectActiveZones() {
        activeZoneIds.clear();
        if (zones.isEmpty()) {
            return;
        }
        int count = switch (activationMode) {
            case ALL_ACTIVE -> zones.size();
            case RANDOM_ONE -> 1;
            case RANDOM_TWO -> 2;
            case RANDOM_COUNT -> activeCount;
        };
        List<ExtractionZone> shuffled = new ArrayList<>(zones);
        Collections.shuffle(shuffled);
        shuffled.stream()
                .limit(Math.min(count, shuffled.size()))
                .map(ExtractionZone::id)
                .forEach(activeZoneIds::add);
    }

    private void drawMarkers() {
        for (ExtractionZone zone : zones) {
            if (!revealed) {
                return;
            }
            if (!activeZoneIds.contains(zone.id())) {
                continue;
            }
            Location center = zone.center();
            center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 18, zone.radius(), 0.55, zone.radius(), 0.0);
            center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, 1.2, 0), 4, 0.45, 0.4, 0.45, 0.0);
        }
    }
}
