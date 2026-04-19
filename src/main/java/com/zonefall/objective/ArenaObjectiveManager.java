package com.zonefall.objective;

import com.zonefall.arena.Arena;
import com.zonefall.core.ZonefallConfig;
import com.zonefall.loot.LootBundle;
import com.zonefall.loot.table.WeightedLootTable;
import com.zonefall.util.Messages;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Arena-scoped objective runtime. It keeps objective state out of the core arena loop.
 */
public final class ArenaObjectiveManager {
    private final Plugin plugin;
    private final ZonefallConfig config;
    private final Arena arena;
    private final ObjectiveActivationMode highValueActivationMode;
    private final int activeHighValueCount;
    private final List<ArenaObjective> objectives = new ArrayList<>();

    public ArenaObjectiveManager(Plugin plugin, ZonefallConfig config, Arena arena, List<ObjectiveDefinition> definitions,
                                 ObjectiveActivationMode highValueActivationMode, int activeHighValueCount) {
        this.plugin = plugin;
        this.config = config;
        this.arena = arena;
        this.highValueActivationMode = highValueActivationMode;
        this.activeHighValueCount = Math.max(1, activeHighValueCount);
        definitions.stream()
                .filter(ObjectiveDefinition::enabled)
                .map(ArenaObjective::new)
                .forEach(objectives::add);
    }

    public void startRound() {
        objectives.forEach(ArenaObjective::resetForRound);
        selectActiveHighValueObjectives();
        objectives.stream()
                .filter(ArenaObjective::active)
                .filter(ArenaObjective::revealed)
                .forEach(ArenaObjective::applyPresentation);
    }

    public void stop() {
        objectives.forEach(ArenaObjective::clearPresentation);
        objectives.forEach(ArenaObjective::resetForRound);
    }

    public void tick(int remainingSeconds, boolean finalExtraction, Consumer<String> announcement) {
        for (ArenaObjective objective : objectives) {
            if (!objective.active() || objective.revealed() || objective.completed()) {
                continue;
            }
            if (shouldReveal(objective.definition(), remainingSeconds, finalExtraction)) {
                objective.reveal();
                announcement.accept("Objective revealed: " + objective.definition().displayName() + ".");
            }
        }
    }

    public Optional<LootBundle> handleInteract(Player player, Block block, Consumer<String> announcement,
                                               Consumer<ObjectiveDefinition> completionEffects) {
        if (block == null || !block.getWorld().equals(arena.world())) {
            return Optional.empty();
        }
        for (ArenaObjective objective : objectives) {
            ObjectiveDefinition definition = objective.definition();
            if (!objective.active() || definition.type() != ObjectiveType.HIGH_VALUE || objective.completed() || !objective.revealed()) {
                continue;
            }
            if (!sameBlock(definition.location().toLocation(), block.getLocation())) {
                continue;
            }
            objective.complete();
            LootBundle reward = rewardFor(definition);
            player.sendMessage(Messages.ok("Secured objective " + definition.displayName() + ": " + reward.describe() + "."));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            completionEffects.accept(definition);
            announcement.accept(player.getName() + " completed objective " + definition.displayName() + ".");
            return Optional.of(reward);
        }
        return Optional.empty();
    }

    public void onLootSourceOpened(Player player, Consumer<LootBundle> rewardConsumer, Consumer<String> announcement,
                                   Consumer<ObjectiveDefinition> completionEffects) {
        for (ArenaObjective objective : objectives) {
            ObjectiveDefinition definition = objective.definition();
            if (!objective.active() || definition.type() != ObjectiveType.SCAVENGE || objective.completed()) {
                continue;
            }
            objective.incrementProgress();
            player.sendMessage(Messages.info("Scavenge objective " + definition.displayName() + ": "
                    + Math.min(objective.progress(), definition.progressTarget()) + "/" + definition.progressTarget() + "."));
            if (objective.progress() >= definition.progressTarget()) {
                objective.complete();
                LootBundle reward = rewardFor(definition);
                rewardConsumer.accept(reward);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                completionEffects.accept(definition);
                announcement.accept(player.getName() + " completed scavenge objective " + definition.displayName()
                        + " and earned " + reward.describe() + ".");
            }
        }
    }

    public void drawMarkers() {
        for (ArenaObjective objective : objectives) {
            if (!objective.active() || !objective.revealed() || objective.definition().type() != ObjectiveType.HIGH_VALUE) {
                continue;
            }
            Location location = objective.definition().location().toLocation().clone().add(0.5, 1.2, 0.5);
            int count = objective.completed() ? 6 : 18;
            location.getWorld().spawnParticle(Particle.END_ROD, location, count, 0.45, 0.45, 0.45, 0.01);
            location.getWorld().spawnParticle(Particle.ENCHANT, location, count, 0.35, 0.35, 0.35, 0.0);
            if (!objective.completed()) {
                location.getWorld().spawnParticle(Particle.WAX_ON, location.clone().add(0, 0.5, 0), 4, 0.25, 0.25, 0.25, 0.0);
            }
        }
    }

    public List<ObjectiveMarker> markers() {
        return objectives.stream()
                .filter(ArenaObjective::revealed)
                .filter(ArenaObjective::active)
                .filter(objective -> objective.definition().type() == ObjectiveType.HIGH_VALUE)
                .map(objective -> new ObjectiveMarker(
                        objective.definition().id(),
                        objective.definition().location().toLocation(),
                        objective.definition().displayName() + " | " + objective.displayState()
                ))
                .toList();
    }

    public String statusSummary() {
        if (objectives.isEmpty()) {
            return "none";
        }
        long completed = objectives.stream().filter(ArenaObjective::completed).count();
        long active = objectives.stream().filter(ArenaObjective::active).count();
        long dormant = objectives.size() - active;
        long revealed = objectives.stream().filter(ArenaObjective::active).filter(ArenaObjective::revealed).filter(objective -> !objective.completed()).count();
        long hidden = active - completed - revealed;
        return "configured=" + objectives.size()
                + " active=" + active
                + " dormant=" + dormant
                + " revealed=" + revealed
                + " hidden=" + hidden
                + " completed=" + completed;
    }

    public String revealedObjectiveIds() {
        List<String> revealed = objectives.stream()
                .filter(ArenaObjective::revealed)
                .filter(ArenaObjective::active)
                .filter(objective -> !objective.completed())
                .map(objective -> objective.definition().displayName())
                .toList();
        return revealed.isEmpty() ? "none" : String.join(", ", revealed);
    }

    public String debugSummary() {
        if (objectives.isEmpty()) {
            return "none";
        }
        return objectives.stream().map(ArenaObjective::describe).toList().toString();
    }

    public String activationSummary() {
        return "highValueMode=" + highValueActivationMode + " activeHighValueCount=" + activeHighValueCount;
    }

    private void selectActiveHighValueObjectives() {
        List<ArenaObjective> highValueObjectives = objectives.stream()
                .filter(objective -> objective.definition().type() == ObjectiveType.HIGH_VALUE)
                .toList();
        int count = switch (highValueActivationMode) {
            case ALL_ACTIVE -> highValueObjectives.size();
            case RANDOM_ONE -> 1;
            case RANDOM_TWO -> 2;
            case RANDOM_COUNT -> activeHighValueCount;
        };
        List<ArenaObjective> shuffled = new ArrayList<>(highValueObjectives);
        Collections.shuffle(shuffled);
        Set<String> activeIds = new HashSet<>();
        shuffled.stream()
                .limit(Math.min(count, shuffled.size()))
                .map(objective -> objective.definition().id())
                .forEach(activeIds::add);
        for (ArenaObjective objective : highValueObjectives) {
            objective.setActive(activeIds.contains(objective.definition().id()));
        }
    }

    private LootBundle rewardFor(ObjectiveDefinition definition) {
        LootBundle reward = definition.rewardBundle().copy();
        if (!definition.rewardTable().isBlank()) {
            WeightedLootTable table = config.namedLootTables().get(definition.rewardTable().toLowerCase(Locale.ROOT));
            if (table != null) {
                reward.addAll(table.roll());
            }
        }
        if (reward.isEmpty()) {
            reward.addAll(WeightedLootTable.fallback().roll());
        }
        return reward;
    }

    private boolean shouldReveal(ObjectiveDefinition definition, int remainingSeconds, boolean finalExtraction) {
        return switch (definition.revealMode()) {
            case ROUND_START -> true;
            case FINAL_EXTRACTION -> finalExtraction;
            case CUSTOM_TIME_REMAINING -> remainingSeconds <= definition.revealSecondsRemaining();
        };
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }
}
