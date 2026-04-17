package com.zonefall.match;

import com.zonefall.arena.Arena;
import com.zonefall.core.ZonefallConfig;
import com.zonefall.core.ZonefallServices;
import com.zonefall.extract.ExtractionHoldResult;
import com.zonefall.extract.ExtractionHoldTracker;
import com.zonefall.extract.ExtractionManager;
import com.zonefall.loot.DroppedLootContainer;
import com.zonefall.loot.LootBundle;
import com.zonefall.loot.LootType;
import com.zonefall.loot.MatchLootTracker;
import com.zonefall.loot.source.LootSource;
import com.zonefall.loot.source.LootSourceManager;
import com.zonefall.loot.source.LootSourceType;
import com.zonefall.pve.ActivePlayerProvider;
import com.zonefall.pve.PvePressureManager;
import com.zonefall.util.Messages;
import com.zonefall.zone.ZoneController;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns one playable match session and coordinates the state machine.
 * @deprecated ArenaController is the runtime model for hub-based Zonefall arenas.
 */
@Deprecated(forRemoval = false)
public final class Match implements ActivePlayerProvider {
    private final UUID id = UUID.randomUUID();
    private final Plugin plugin;
    private final ZonefallConfig config;
    private final ZonefallServices services;
    private final Arena arena;
    private final ZoneController zoneController;
    private final ExtractionManager extractionManager;
    private final ExtractionHoldTracker extractionHoldTracker;
    private final PvePressureManager pvePressureManager;
    private final MatchLootTracker lootTracker;
    private final LootSourceManager lootSourceManager;
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
        this.extractionHoldTracker = new ExtractionHoldTracker();
        this.pvePressureManager = new PvePressureManager(plugin, config, this);
        this.lootTracker = new MatchLootTracker(plugin);
        this.lootSourceManager = new LootSourceManager(plugin, config, arena);
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
        lootTracker.ensurePlayer(player.getUniqueId());
        boolean added = participants.add(player.getUniqueId());
        if (added) {
            player.sendMessage(Messages.ok("Joined Zonefall match " + shortId() + "."));
            broadcast(player.getName() + " joined the match.");
        }
        return added;
    }

    public boolean removePlayer(Player player) {
        boolean removed = participants.remove(player.getUniqueId());
        lootTracker.removePlayer(player.getUniqueId());
        extractionHoldTracker.cancel(player.getUniqueId());
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
        Optional<DroppedLootContainer> dropped = lootTracker.dropCarriedLoot(player.getUniqueId(), player.getLocation());
        extractionHoldTracker.cancel(player.getUniqueId());
        eliminatedPlayers.add(player.getUniqueId());
        if (dropped.isPresent()) {
            broadcast(player.getName() + " was eliminated and dropped match loot: "
                    + dropped.get().contents().describe() + ".");
        } else {
            broadcast(player.getName() + " was eliminated with no carried match loot.");
        }
        checkCompletion();
    }

    public void handleMobKill(Player player) {
        if (state != MatchState.ACTIVE || !isActiveParticipant(player.getUniqueId())) {
            return;
        }
        LootBundle reward = config.mobKillReward().copy();
        if (reward.isEmpty()) {
            return;
        }
        lootTracker.grant(player.getUniqueId(), reward);
        player.sendMessage(Messages.ok("Recovered match loot: " + reward.describe() + "."));
        if (config.debug()) {
            plugin.getLogger().info(player.getName() + " earned mob loot: " + reward.describe());
        }
    }

    public boolean grantLoot(Player player, LootType type, int amount) {
        if (state != MatchState.ACTIVE && state != MatchState.COUNTDOWN && state != MatchState.CREATED) {
            player.sendMessage(Messages.error("Cannot grant loot after the match has ended."));
            return false;
        }
        if (!participants.contains(player.getUniqueId())) {
            player.sendMessage(Messages.error("Target player is not in the current match."));
            return false;
        }
        LootBundle loot = LootBundle.single(type, amount);
        lootTracker.grant(player.getUniqueId(), loot);
        player.sendMessage(Messages.ok("Granted match loot: " + loot.describe() + "."));
        return true;
    }

    public void tryPickupDroppedLoot(Player player) {
        if (state != MatchState.ACTIVE || !isActiveParticipant(player.getUniqueId())) {
            return;
        }
        List<DroppedLootContainer> pickedUp = lootTracker.pickupNearby(
                player.getUniqueId(),
                player.getLocation(),
                config.deathDropPickupRadius()
        );
        for (DroppedLootContainer container : pickedUp) {
            player.sendMessage(Messages.ok("Recovered dropped match loot: "
                    + container.contents().describe() + "."));
            if (config.debug()) {
                plugin.getLogger().info(player.getName() + " picked up dropped loot "
                        + container.contents().describe() + " from " + container.id());
            }
        }
    }

    public String lootStatus(Player player) {
        if (!participants.contains(player.getUniqueId())) {
            return player.getName() + " is not in the current match.";
        }
        return player.getName() + " carried loot: " + lootTracker.describeCarried(player.getUniqueId());
    }

    public void handleLootSourceInteract(Player player, Block block) {
        if (state != MatchState.ACTIVE || !isActiveParticipant(player.getUniqueId())) {
            return;
        }
        lootSourceManager.handleInteract(player, block).ifPresent(loot -> {
            lootTracker.grant(player.getUniqueId(), loot);
            if (config.debug()) {
                plugin.getLogger().info(player.getName() + " looted source reward: " + loot.describe());
            }
        });
    }

    public boolean placeLootSource(Player player, LootSourceType type) {
        if (state == MatchState.ENDED || state == MatchState.ENDING) {
            player.sendMessage(Messages.error("Cannot place loot after the match has ended."));
            return false;
        }
        LootSource source = lootSourceManager.addSource(type, player.getLocation());
        player.sendMessage(Messages.ok("Placed " + type.displayName() + " at "
                + source.location().getBlockX() + ","
                + source.location().getBlockY() + ","
                + source.location().getBlockZ() + "."));
        return true;
    }

    public String extractionStatus(Player player) {
        return player.getName() + " extraction hold: " + extractionHoldTracker.describe(player.getUniqueId());
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
        if (config.extractionHoldSeconds() > 0) {
            player.sendMessage(Messages.info("Stay inside the extraction zone for "
                    + config.extractionHoldSeconds() + " seconds to extract."));
            return Optional.empty();
        }
        return completeExtraction(player);
    }

    private Optional<MatchSummary> completeExtraction(Player player) {
        extractedPlayers.add(player.getUniqueId());
        LootBundle extracted = lootTracker.extract(player.getUniqueId());
        services.stashService().deposit(player.getUniqueId(), extracted);
        broadcast(player.getName() + " extracted successfully with loot: " + extracted.describe() + ".");
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
                + " drops=" + lootTracker.describeDrops()
                + " lootSources=" + lootSourceManager.openedCount() + "/" + lootSourceManager.sourceCount()
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
        lootSourceManager.registerDefaults();
        broadcast("Zonefall match is active. Extract before the timer expires.");
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMatch, 20L, 20L);
    }

    private void tickMatch() {
        elapsedSeconds++;
        zoneController.tick(elapsedSeconds);
        pvePressureManager.tick(elapsedSeconds);
        lootTracker.drawDropMarkers();
        lootSourceManager.drawMarkers();
        tickExtractionHolds();

        if (elapsedSeconds >= config.matchDurationSeconds()) {
            end(MatchEndReason.TIMER_EXPIRED);
        }
    }

    private void tickExtractionHolds() {
        for (UUID playerId : Set.copyOf(participants)) {
            if (!isActiveParticipant(playerId)) {
                extractionHoldTracker.cancel(playerId);
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            boolean inside = extractionManager.isInsideExtraction(player.getLocation());
            ExtractionHoldResult result = extractionHoldTracker.tick(player, inside, config.extractionHoldSeconds());
            if (result.complete()) {
                completeExtraction(player);
            }
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
        lootSourceManager.stop();
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
                Set.copyOf(eliminatedPlayers),
                describeLootMap(lootTracker.extractedLootSnapshot()),
                describeLootMap(lootTracker.lostOnDeathLootSnapshot()),
                lootSourceManager.openedCount(),
                lootSourceManager.sourceCount()
        );
    }

    private Map<UUID, String> describeLootMap(Map<UUID, LootBundle> lootByPlayer) {
        Map<UUID, String> descriptions = new LinkedHashMap<>();
        lootByPlayer.forEach((playerId, loot) -> descriptions.put(playerId, loot.describe()));
        return Map.copyOf(descriptions);
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
