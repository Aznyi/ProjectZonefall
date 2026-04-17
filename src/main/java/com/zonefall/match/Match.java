package com.zonefall.match;

import com.zonefall.arena.Arena;
import com.zonefall.core.ZonefallConfig;
import com.zonefall.core.ZonefallServices;
import com.zonefall.extract.ExtractionManager;
import com.zonefall.pve.PvePressureManager;
import com.zonefall.util.Messages;
import com.zonefall.zone.ZoneController;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns one playable match session and coordinates the state machine.
 */
public final class Match {
    private final UUID id = UUID.randomUUID();
    private final Plugin plugin;
    private final ZonefallConfig config;
    private final ZonefallServices services;
    private final Arena arena;
    private final ZoneController zoneController;
    private final ExtractionManager extractionManager;
    private final PvePressureManager pvePressureManager;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> extractedPlayers = new LinkedHashSet<>();
    private final Set<UUID> eliminatedPlayers = new LinkedHashSet<>();

    private MatchState state = MatchState.CREATED;
    private BukkitTask countdownTask;
    private BukkitTask tickTask;
    private int countdownRemaining;
    private int elapsedSeconds;

    public Match(Plugin plugin, ZonefallConfig config, ZonefallServices services, Arena arena) {
        this.plugin = plugin;
        this.config = config;
        this.services = services;
        this.arena = arena;
        this.zoneController = new ZoneController(plugin, config, arena);
        this.extractionManager = new ExtractionManager(plugin, config, arena);
        this.pvePressureManager = new PvePressureManager(plugin, config, this);
    }

    public MatchState state() {
        return state;
    }

    public int elapsedSeconds() {
        return elapsedSeconds;
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    public ExtractionManager extractionManager() {
        return extractionManager;
    }

    public boolean addPlayer(Player player) {
        if (state != MatchState.CREATED && state != MatchState.COUNTDOWN) {
            player.sendMessage(Messages.error("You can only join before the match is active."));
            return false;
        }
        services.profileService().loadProfile(player.getUniqueId());
        boolean added = participants.add(player.getUniqueId());
        if (added) {
            player.sendMessage(Messages.ok("Joined Zonefall match " + shortId() + "."));
            broadcast(player.getName() + " joined the match.");
        }
        return added;
    }

    public boolean removePlayer(Player player) {
        boolean removed = participants.remove(player.getUniqueId());
        extractedPlayers.remove(player.getUniqueId());
        eliminatedPlayers.remove(player.getUniqueId());
        if (removed) {
            broadcast(player.getName() + " left the match.");
            checkCompletion();
        }
        return removed;
    }

    public boolean contains(UUID playerId) {
        return participants.contains(playerId);
    }

    public boolean isActiveParticipant(UUID playerId) {
        return participants.contains(playerId)
                && !extractedPlayers.contains(playerId)
                && !eliminatedPlayers.contains(playerId);
    }

    public void startCountdown() {
        if (state != MatchState.CREATED) {
            throw new IllegalStateException("Cannot start countdown from " + state + ".");
        }
        if (participants.isEmpty()) {
            throw new IllegalStateException("Cannot start match without players.");
        }
        transitionTo(MatchState.COUNTDOWN);
        countdownRemaining = config.countdownSeconds();
        broadcast("Match starts in " + countdownRemaining + " seconds.");
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCountdown, 20L, 20L);
    }

    public void forceEnd(MatchEndReason reason) {
        end(reason);
    }

    public void handlePlayerDeath(Player player) {
        if (!isActiveParticipant(player.getUniqueId())) {
            return;
        }
        eliminatedPlayers.add(player.getUniqueId());
        broadcast(player.getName() + " was eliminated and dropped their carried inventory.");
        checkCompletion();
    }

    public Optional<MatchSummary> tryExtract(Player player) {
        if (state != MatchState.ACTIVE || !isActiveParticipant(player.getUniqueId())) {
            player.sendMessage(Messages.error("You are not an active player in this match."));
            return Optional.empty();
        }
        if (!extractionManager.isInsideExtraction(player.getLocation())) {
            player.sendMessage(Messages.error("You must be inside an extraction zone."));
            return Optional.empty();
        }
        extractedPlayers.add(player.getUniqueId());
        // TODO: Transfer carried match loot into persistent stash once stash data exists.
        services.stashService().recordPrototypeExtraction(player.getUniqueId());
        broadcast(player.getName() + " extracted successfully.");
        return checkCompletion();
    }

    public void shutdown() {
        end(MatchEndReason.SERVER_SHUTDOWN);
    }

    public String debugStatus() {
        return "Match " + shortId()
                + " state=" + state
                + " world=" + arena.world().getName()
                + " players=" + participants.size()
                + " extracted=" + extractedPlayers.size()
                + " eliminated=" + eliminatedPlayers.size()
                + " elapsed=" + elapsedSeconds + "/" + config.matchDurationSeconds()
                + " extraction=" + extractionManager.describeZones();
    }

    private void tickCountdown() {
        countdownRemaining--;
        if (countdownRemaining <= 0) {
            cancelCountdownTask();
            activate();
            return;
        }
        if (countdownRemaining <= 5 || countdownRemaining % 5 == 0) {
            broadcast("Match starts in " + countdownRemaining + " seconds.");
        }
    }

    private void activate() {
        transitionTo(MatchState.ACTIVE);
        elapsedSeconds = 0;
        zoneController.start();
        extractionManager.start();
        pvePressureManager.start();
        broadcast("Zonefall match is active. Extract before the timer expires.");
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMatch, 20L, 20L);
    }

    private void tickMatch() {
        elapsedSeconds++;
        zoneController.tick(elapsedSeconds);
        pvePressureManager.tick(elapsedSeconds);

        if (elapsedSeconds >= config.matchDurationSeconds()) {
            end(MatchEndReason.TIMER_EXPIRED);
        }
    }

    private Optional<MatchSummary> checkCompletion() {
        if (state != MatchState.ACTIVE) {
            return Optional.empty();
        }
        long unresolved = participants.stream().filter(this::isActiveParticipant).count();
        if (unresolved == 0) {
            return Optional.of(end(MatchEndReason.ALL_PLAYERS_RESOLVED));
        }
        return Optional.empty();
    }

    private MatchSummary end(MatchEndReason reason) {
        if (state == MatchState.ENDED || state == MatchState.ENDING) {
            return summary(reason);
        }
        transitionTo(MatchState.ENDING);
        cancelCountdownTask();
        cancelTickTask();
        pvePressureManager.stop();
        extractionManager.stop();
        zoneController.stop();

        MatchSummary summary = summary(reason);
        broadcast("Match ended: " + reason + ". Extracted " + extractedPlayers.size()
                + "/" + participants.size() + " players.");
        plugin.getLogger().info("Zonefall result: " + summary);
        transitionTo(MatchState.ENDED);
        return summary;
    }

    private MatchSummary summary(MatchEndReason reason) {
        return new MatchSummary(
                id,
                reason,
                Duration.ofSeconds(elapsedSeconds),
                Set.copyOf(participants),
                Set.copyOf(extractedPlayers),
                Set.copyOf(eliminatedPlayers)
        );
    }

    private void transitionTo(MatchState next) {
        MatchState previous = state;
        state = next;
        plugin.getLogger().info("Zonefall match " + shortId() + " state " + previous + " -> " + next);
    }

    private void broadcast(String message) {
        String formatted = Messages.info(message);
        plugin.getLogger().info("[Match " + shortId() + "] " + message);
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(formatted);
            }
        }
    }

    private void cancelCountdownTask() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void cancelTickTask() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private String shortId() {
        return id.toString().substring(0, 8);
    }
}

