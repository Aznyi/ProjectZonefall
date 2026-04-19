package com.zonefall.arena;

import com.zonefall.core.ZonefallConfig;
import com.zonefall.core.ZonefallServices;
import com.zonefall.extract.ExtractionHoldResult;
import com.zonefall.extract.ExtractionHoldTracker;
import com.zonefall.extract.ExtractionManager;
import com.zonefall.extract.ExtractionZone;
import com.zonefall.loot.DroppedLootContainer;
import com.zonefall.loot.LootBundle;
import com.zonefall.loot.LootType;
import com.zonefall.loot.MatchLootTracker;
import com.zonefall.loot.source.LootSource;
import com.zonefall.loot.source.LootSourceManager;
import com.zonefall.loot.source.LootSourceType;
import com.zonefall.objective.ArenaObjectiveManager;
import com.zonefall.objective.ObjectiveEffectExecutor;
import com.zonefall.objective.ObjectiveMarker;
import com.zonefall.pve.ActivePlayerProvider;
import com.zonefall.pve.ArenaThreatDirector;
import com.zonefall.util.Messages;
import com.zonefall.zone.ZoneController;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs one prebuilt hub arena as an independent timed extraction cycle.
 */
public final class ArenaController implements ActivePlayerProvider {
    private final Plugin plugin;
    private final ZonefallConfig config;
    private final ZonefallServices services;
    private final ArenaAnnouncementService announcements;
    private final ArenaDefinition definition;
    private final Arena runtimeArena;
    private final ZoneController zoneController;
    private final ExtractionManager extractionManager;
    private final ExtractionHoldTracker extractionHoldTracker = new ExtractionHoldTracker();
    private final ArenaThreatDirector threatDirector;
    private final LootSourceManager lootSourceManager;
    private final ArenaObjectiveManager objectiveManager;
    private final ObjectiveEffectExecutor objectiveEffectExecutor;
    private MatchLootTracker lootTracker;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> extractedPlayers = new LinkedHashSet<>();
    private final Set<UUID> eliminatedPlayers = new LinkedHashSet<>();

    private ArenaState state = ArenaState.RESETTING;
    private BukkitTask tickTask;
    private int countdownRemaining;
    private int elapsedSeconds;

    public ArenaController(Plugin plugin, ZonefallConfig config, ZonefallServices services,
                           ArenaDefinition definition, ArenaAnnouncementService announcements) {
        this.plugin = plugin;
        this.config = config;
        this.services = services;
        this.announcements = announcements;
        this.definition = definition;
        Location center = definition.center().toLocation();
        this.runtimeArena = new Arena(definition.id(), center.getWorld(), center, definition.extraction().toLocation());
        this.zoneController = new ZoneController(
                plugin,
                config,
                runtimeArena,
                definition.borderStartSize(),
                definition.borderEndSize(),
                definition.shrinkStartDelaySeconds(),
                definition.shrinkDurationSeconds()
        );
        this.extractionManager = new ExtractionManager(
                plugin,
                config,
                runtimeArena,
                definition.extractions().stream().map(LocationSpec::toLocation).toList(),
                definition.extractionActivationMode(),
                definition.activeExtractionCount(),
                definition.extractionRevealMode(),
                definition.extractionRevealSecondsRemaining()
        );
        this.threatDirector = new ArenaThreatDirector(plugin, config, definition, this);
        this.lootSourceManager = new LootSourceManager(plugin, config, runtimeArena);
        this.objectiveManager = new ArenaObjectiveManager(
                plugin,
                config,
                runtimeArena,
                definition.objectives(),
                definition.objectiveActivationMode(),
                definition.activeHighValueObjectiveCount()
        );
        this.objectiveEffectExecutor = new ObjectiveEffectExecutor(extractionManager, lootSourceManager);
        this.lootTracker = new MatchLootTracker(plugin);
        open();
    }

    public String id() {
        return definition.id();
    }

    public String displayName() {
        return definition.displayName();
    }

    public ArenaState state() {
        return state;
    }

    public ArenaDefinition definition() {
        return definition;
    }

    @Override
    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    @Override
    public boolean isActiveParticipant(UUID playerId) {
        return participants.contains(playerId)
                && !extractedPlayers.contains(playerId)
                && !eliminatedPlayers.contains(playerId);
    }

    public boolean contains(UUID playerId) {
        return participants.contains(playerId);
    }

    public boolean canJoin() {
        if (state == ArenaState.OPEN || state == ArenaState.COUNTDOWN) {
            return true;
        }
        return state == ArenaState.ACTIVE
                && definition.allowLateJoin()
                && elapsedSeconds <= definition.joinWindowSeconds();
    }

    public int activePlayerCount() {
        return (int) participants.stream().filter(this::isActiveParticipant).count();
    }

    public int remainingSeconds() {
        return switch (state) {
            case COUNTDOWN -> countdownRemaining;
            case ACTIVE, FINAL_EXTRACTION -> Math.max(0, definition.roundDurationSeconds() - elapsedSeconds);
            default -> 0;
        };
    }

    public boolean joinWindowOpen() {
        return canJoin();
    }

    public boolean containsJoinPoint(Location location) {
        return definition.joinPoints().stream().anyMatch(point -> point.contains(location));
    }

    public void drawJoinPointMarkers() {
        for (ArenaJoinPoint joinPoint : definition.joinPoints()) {
            Location location = joinPoint.location().toLocation();
            location.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0, 0.5, 0), 8, joinPoint.radius(), 0.25, joinPoint.radius(), 0.0);
            location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0, 1.1, 0), 6, 0.45, 0.35, 0.45, 0.0);
        }
    }

    public boolean join(Player player) {
        if (!canJoin()) {
            player.sendMessage(Messages.error(displayName() + " is closed to new runners."));
            return false;
        }
        participants.add(player.getUniqueId());
        extractedPlayers.remove(player.getUniqueId());
        eliminatedPlayers.remove(player.getUniqueId());
        lootTracker.ensurePlayer(player.getUniqueId());
        services.profileService().loadProfile(player.getUniqueId());
        Location joinLocation = SafePointResolver.resolve(definition.joinSpawn().toLocation(), definition.playableRegion())
                .orElse(null);
        if (joinLocation == null) {
            participants.remove(player.getUniqueId());
            lootTracker.removePlayer(player.getUniqueId());
            player.sendMessage(Messages.error(displayName() + " join spawn is unsafe. Run /zonefall arena validate " + id() + " detailed."));
            plugin.getLogger().warning("Refused join for " + player.getName() + " in " + id() + ": no safe join spawn.");
            return false;
        }
        player.teleport(joinLocation);
        if (!player.isOp()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
        }
        player.sendMessage(Messages.ok("Entered " + displayName() + ". Extract before the timer ends."));
        if (state == ArenaState.OPEN) {
            startCountdown();
        }
        return true;
    }

    public void leave(Player player) {
        if (!participants.remove(player.getUniqueId())) {
            player.sendMessage(Messages.error("You are not in an arena."));
            return;
        }
        lootTracker.removePlayer(player.getUniqueId());
        extractionHoldTracker.cancel(player.getUniqueId());
        extractedPlayers.remove(player.getUniqueId());
        eliminatedPlayers.remove(player.getUniqueId());
        player.teleport(definition.hubReturn().toLocation());
        player.sendMessage(Messages.ok("Returned to hub."));
        checkEmptyReset();
    }

    public void forceStart() {
        if (state == ArenaState.OPEN) {
            startCountdown();
        } else if (state == ArenaState.COUNTDOWN) {
            countdownRemaining = 1;
        }
    }

    public void forceReset() {
        closeAndReset(false);
    }

    public void shutdown() {
        cancelTick();
        lootSourceManager.stop();
        extractionManager.stop();
        zoneController.stop();
    }

    public void tick() {
        switch (state) {
            case COUNTDOWN -> tickCountdown();
            case ACTIVE, FINAL_EXTRACTION -> tickRound();
            default -> {
            }
        }
    }

    public void handleMove(Player player) {
        if (!isActiveParticipant(player.getUniqueId())) {
            return;
        }
        if (player.getWorld() != runtimeArena.world()) {
            SafePointResolver.resolve(definition.joinSpawn().toLocation(), definition.playableRegion())
                    .ifPresentOrElse(player::teleport, () -> dropAndEliminate(player, true));
            player.sendMessage(Messages.error("Arena world boundary enforced."));
            return;
        }
        ZoneController.BarrierStatus barrierStatus = zoneController.barrierStatus(player.getLocation());
        if (barrierStatus == ZoneController.BarrierStatus.OUTSIDE) {
            if (zoneController.isShrinking() && config.barrierInstantDeath()) {
                zoneController.recordAction(ZoneController.BarrierAction.INSTANT_DEATH, player.getLocation(), "shrinking");
                player.sendMessage(Messages.error("You crossed the shrinking arena barrier."));
                dropAndEliminate(player, true);
                checkEmptyReset();
            } else {
                enforceSolidStationaryBarrier(player);
            }
            return;
        }
        if (barrierStatus == ZoneController.BarrierStatus.NEAR_EDGE) {
            if (!zoneController.isShrinking()) {
                enforceSolidStationaryBarrier(player);
                return;
            }
            Location desired = zoneController.safeInside(player.getLocation());
            SafePointResolver.resolve(desired, definition.playableRegion(), runtimeArena.center(), 12)
                    .ifPresentOrElse(safeLocation -> {
                        zoneController.recordAction(ZoneController.BarrierAction.TELEPORT_PROTECTION, safeLocation, "safe-fallback");
                        player.teleport(safeLocation);
                        player.sendMessage(Messages.error("Barrier edge enforced."));
                    }, () -> {
                        zoneController.recordAction(ZoneController.BarrierAction.UNSAFE_FALLBACK_DEATH, player.getLocation(), "no-safe-fallback");
                        player.sendMessage(Messages.error("Barrier fallback was unsafe."));
                        dropAndEliminate(player, true);
                        checkEmptyReset();
                    });
            return;
        }
        pickupDrops(player);
    }

    private void enforceSolidStationaryBarrier(Player player) {
        Location desired = zoneController.safeInside(player.getLocation());
        SafePointResolver.resolve(desired, definition.playableRegion(), runtimeArena.center(), 12)
                .ifPresentOrElse(safeLocation -> {
                    zoneController.recordAction(ZoneController.BarrierAction.SOLID_WALL_PROTECTION, safeLocation, "stationary");
                    player.teleport(safeLocation);
                    player.sendMessage(Messages.error("Arena barrier is closed."));
                }, () -> {
                    zoneController.recordAction(ZoneController.BarrierAction.UNSAFE_FALLBACK_DEATH, player.getLocation(), "stationary-no-safe-fallback");
                    player.sendMessage(Messages.error("Barrier fallback was unsafe."));
                    dropAndEliminate(player, true);
                    checkEmptyReset();
                });
    }

    public void handleMobKill(Player player) {
        if (!isActiveParticipant(player.getUniqueId())) {
            return;
        }
        LootBundle reward = config.mobKillReward().copy();
        lootTracker.grant(player.getUniqueId(), reward);
        player.sendMessage(Messages.ok("Recovered match loot: " + reward.describe() + "."));
    }

    public void handleDeath(Player player) {
        if (!isActiveParticipant(player.getUniqueId())) {
            return;
        }
        dropAndEliminate(player, false);
        checkEmptyReset();
    }

    public void handleInteract(Player player, Block block) {
        if (!isActiveParticipant(player.getUniqueId())) {
            return;
        }
        if (objectiveManager.handleInteract(player, block, this::announceObjective, this::applyObjectiveEffects)
                .map(loot -> {
                    lootTracker.grant(player.getUniqueId(), loot);
                    return true;
                })
                .orElse(false)) {
            return;
        }
        lootSourceManager.handleInteract(player, block).ifPresent(loot -> {
            lootTracker.grant(player.getUniqueId(), loot);
            objectiveManager.onLootSourceOpened(
                    player,
                    reward -> lootTracker.grant(player.getUniqueId(), reward),
                    this::announceObjective,
                    this::applyObjectiveEffects
            );
        });
    }

    public boolean placeLoot(Player player, LootSourceType type) {
        LootSource source = lootSourceManager.addSource(type, player.getLocation());
        player.sendMessage(Messages.ok("Placed " + type.displayName() + " at "
                + source.location().getBlockX() + ","
                + source.location().getBlockY() + ","
                + source.location().getBlockZ() + "."));
        return true;
    }

    public boolean grantLoot(Player player, LootType type, int amount) {
        if (!participants.contains(player.getUniqueId())) {
            player.sendMessage(Messages.error("Target player is not in this arena."));
            return false;
        }
        lootTracker.grant(player.getUniqueId(), LootBundle.single(type, amount));
        return true;
    }

    public Optional<String> tryExtract(Player player) {
        if (!isActiveParticipant(player.getUniqueId())) {
            return Optional.of("You are not active in this arena.");
        }
        if (!extractionManager.isInsideExtraction(player.getLocation())) {
            return Optional.of("You must be inside the extraction zone.");
        }
        if (config.extractionHoldSeconds() > 0) {
            return Optional.of("Stay in the extraction zone to complete the hold.");
        }
        completeExtraction(player);
        return Optional.empty();
    }

    public String lootStatus(Player player) {
        return player.getName() + " carried loot: " + lootTracker.describeCarried(player.getUniqueId());
    }

    public String extractionStatus(Player player) {
        return player.getName() + " extraction hold: " + extractionHoldTracker.describe(player.getUniqueId());
    }

    public void cancelExtraction(Player player, String reason) {
        if (extractionHoldTracker.cancel(player.getUniqueId())) {
            player.sendMessage(Messages.error("Extraction canceled: " + reason + "."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
        }
    }

    public boolean protects(Location location) {
        return definition.protectedRegion().contains(location) || definition.playableRegion().contains(location);
    }

    public boolean isParticipant(Player player) {
        return participants.contains(player.getUniqueId());
    }

    public Location hubReturnLocation() {
        return definition.hubReturn().toLocation();
    }

    public List<ExtractionZone> activeExtractionZones() {
        return extractionManager.activeZones();
    }

    public List<ObjectiveMarker> objectiveMarkers() {
        return objectiveManager.markers();
    }

    public String statusLine() {
        return id() + " " + state
                + " players=" + participants.size()
                + " active=" + activePlayerCount()
                + " extracted=" + extractedPlayers.size()
                + " eliminated=" + eliminatedPlayers.size()
                + " remaining=" + remainingSeconds() + "s"
                + " arenaSize=" + definition.playableRegion().describeSize()
                + " movementBounds=world-border"
                + " boundaryGrace=" + config.playableBoundaryGraceDistance()
                + " join=" + (joinWindowOpen() ? "open" : "closed")
                + " extractionReveal=" + extractionManager.revealState()
                + " extracts=" + extractionManager.describeActiveZones()
                + " loot=" + lootSourceManager.activeCount() + " active/" + lootSourceManager.inactiveCount() + " inactive"
                + " objectives=" + objectiveManager.statusSummary()
                + " drops=" + lootTracker.describeDrops();
    }

    public String playerStatusLine(Player player) {
        String extraction = extractionHoldTracker.describeForUi(player.getUniqueId());
        return displayName() + " | " + state
                + " | " + remainingSeconds() + "s"
                + " | join " + (joinWindowOpen() ? "open" : "closed")
                + " | " + activePlayerCount() + " active"
                + (extraction.equals("none") ? "" : " | extract " + extraction);
    }

    public String infoLine() {
        String extractions = definition.extractions().stream()
                .map(LocationSpec::toLocation)
                .map(location -> location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ())
                .toList()
                .toString();
        String joinPoints = definition.joinPoints().isEmpty()
                ? "none"
                : definition.joinPoints().stream().map(ArenaJoinPoint::describe).toList().toString();
        return statusLine()
                + " world=" + definition.worldName()
                + " dimension=" + runtimeArena.world().getEnvironment()
                + " extractions=" + extractions
                + " activeExtractions=" + extractionManager.describeActiveZones()
                + " inactiveExtractions=" + extractionManager.describeInactiveZones()
                + " extractionMode=" + definition.extractionActivationMode()
                + " reveal=" + extractionManager.revealState()
                + " timing=round:" + definition.roundDurationSeconds() + "s shrinkDelay:"
                + definition.shrinkStartDelaySeconds() + "s shrinkDuration:" + definition.shrinkDurationSeconds() + "s"
                + " zone=" + zoneController.debugSummary()
                + " joinPoints=" + joinPoints
                + " lootPlacements=" + definition.lootSources().size()
                + " activeLoot=" + lootSourceManager.activeCount()
                + " inactiveLoot=" + lootSourceManager.inactiveCount()
                + " lootMode=" + definition.lootActivationMode()
                + " objectiveMode=" + objectiveManager.activationSummary()
                + " objectives=" + objectiveManager.statusSummary()
                + " validation=" + validationSummary();
    }

    public String debugLine() {
        return infoLine()
                + " lootDebug=" + lootSourceManager.debugSummary()
                + " objectiveDebug=" + objectiveManager.debugSummary()
                + " objectiveEffects=" + objectiveEffectExecutor.debugSummary()
                + " zoneDebug=" + zoneController.debugSummary()
                + " threatDebug=" + threatDirector.debugSummary()
                + " extractionDebug=" + extractionManager.describeZones();
    }

    public String objectivesLine() {
        return id() + " objectives=" + objectiveManager.debugSummary();
    }

    public ArenaValidationReport validationReport() {
        return ArenaValidator.report(config, definition);
    }

    public String validationSummary() {
        return validationReport().summary();
    }

    public Map<UUID, String> extractedLootSummary() {
        Map<UUID, String> result = new LinkedHashMap<>();
        lootTracker.extractedLootSnapshot().forEach((playerId, loot) -> result.put(playerId, loot.describe()));
        return result;
    }

    public boolean canParticipantBuild(Player player, Location location) {
        return isActiveParticipant(player.getUniqueId()) && definition.playableRegion().contains(location);
    }

    public boolean allowsArenaMobSpawn(Location location) {
        return (state == ArenaState.ACTIVE || state == ArenaState.FINAL_EXTRACTION)
                && definition.playableRegion().contains(location);
    }

    private void open() {
        state = ArenaState.OPEN;
        elapsedSeconds = 0;
        countdownRemaining = definition.countdownSeconds();
        plugin.getLogger().info("Arena " + id() + " is OPEN.");
        announcements.announce(this, "Open for runners.");
    }

    private void startCountdown() {
        state = ArenaState.COUNTDOWN;
        countdownRemaining = definition.countdownSeconds();
        ensureTicker();
        broadcast("Arena starts in " + countdownRemaining + " seconds.");
        announcements.announce(this, "Countdown started.");
    }

    private void activate() {
        state = ArenaState.ACTIVE;
        elapsedSeconds = 0;
        zoneController.start();
        extractionManager.start();
        threatDirector.start();
        lootSourceManager.registerPlacements(
                definition.lootSources(),
                definition.lootActivationMode(),
                definition.activeLootSourceCount()
        );
        objectiveEffectExecutor.reset();
        objectiveManager.startRound();
        broadcast(displayName() + " is active. Early join window: " + definition.joinWindowSeconds() + "s.");
        announcements.announce(this, "Round active.");
        if (extractionManager.revealed()) {
            announcements.announce(this, "Extraction routes revealed.");
        }
        if (!objectiveManager.revealedObjectiveIds().equals("none")) {
            announcements.announce(this, "Objectives revealed: " + objectiveManager.revealedObjectiveIds() + ".");
        }
    }

    private void tickCountdown() {
        countdownRemaining--;
        if (countdownRemaining <= 0) {
            activate();
            return;
        }
        if (countdownRemaining <= 5 || countdownRemaining % 5 == 0) {
            broadcast("Arena starts in " + countdownRemaining + " seconds.");
        }
    }

    private void tickRound() {
        elapsedSeconds++;
        zoneController.tick(elapsedSeconds);
        threatDirector.tick(elapsedSeconds);
        lootTracker.drawDropMarkers();
        lootSourceManager.drawMarkers();
        objectiveManager.drawMarkers();
        tickExtractionHolds();
        revealExtractionIfReady();
        tickObjectives();

        if (state == ArenaState.ACTIVE
                && definition.roundDurationSeconds() - elapsedSeconds <= config.finalExtractionSeconds()) {
            state = ArenaState.FINAL_EXTRACTION;
            revealExtractionIfReady();
            broadcast("Final extraction window. Get out now.");
            announcements.announce(this, "Final extraction started.");
            for (UUID playerId : participants) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.8f);
                }
            }
        }
        if (elapsedSeconds >= definition.roundDurationSeconds()) {
            closeAndReset(true);
        }
    }

    private void revealExtractionIfReady() {
        boolean finalPhase = state == ArenaState.FINAL_EXTRACTION
                || definition.roundDurationSeconds() - elapsedSeconds <= config.finalExtractionSeconds();
        if (extractionManager.shouldReveal(remainingSeconds(), finalPhase) && extractionManager.revealNow()) {
            broadcast("Extraction routes revealed: " + extractionManager.describeActiveZones() + ".");
            announcements.announce(this, "Extraction routes revealed.");
        }
    }

    private void tickObjectives() {
        boolean finalPhase = state == ArenaState.FINAL_EXTRACTION
                || definition.roundDurationSeconds() - elapsedSeconds <= config.finalExtractionSeconds();
        objectiveManager.tick(remainingSeconds(), finalPhase, this::announceObjective);
    }

    private void tickExtractionHolds() {
        for (UUID playerId : Set.copyOf(participants)) {
            if (!isActiveParticipant(playerId)) {
                extractionHoldTracker.cancel(playerId);
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            boolean inside = extractionManager.isInsideExtraction(player.getLocation());
            ExtractionHoldResult result = extractionHoldTracker.tick(player, inside, config.extractionHoldSeconds());
            if (result.complete()) {
                completeExtraction(player);
            }
        }
    }

    private void completeExtraction(Player player) {
        extractedPlayers.add(player.getUniqueId());
        applyExtractionBonus(player);
        LootBundle extracted = lootTracker.extract(player.getUniqueId());
        services.stashService().deposit(player.getUniqueId(), extracted);
        player.teleport(definition.hubReturn().toLocation());
        broadcast(player.getName() + " extracted with loot: " + extracted.describe() + ".");
        checkEmptyReset();
    }

    private void applyExtractionBonus(Player player) {
        double multiplier = objectiveEffectExecutor.extractBonusMultiplier();
        if (multiplier <= 1.0) {
            return;
        }
        LootBundle bonus = lootTracker.carriedLoot(player.getUniqueId()).bonusForMultiplier(multiplier);
        if (bonus.isEmpty()) {
            return;
        }
        lootTracker.grant(player.getUniqueId(), bonus);
        player.sendMessage(Messages.ok("Objective extraction bonus x"
                + String.format(java.util.Locale.ROOT, "%.2f", multiplier)
                + " added: " + bonus.describe() + "."));
    }

    private void pickupDrops(Player player) {
        List<DroppedLootContainer> containers = lootTracker.pickupNearby(
                player.getUniqueId(),
                player.getLocation(),
                config.deathDropPickupRadius()
        );
        for (DroppedLootContainer container : containers) {
            player.sendMessage(Messages.ok("Recovered dropped loot: " + container.contents().describe() + "."));
        }
    }

    private void dropAndEliminate(Player player, boolean killPlayer) {
        Optional<DroppedLootContainer> dropped = lootTracker.dropCarriedLoot(player.getUniqueId(), player.getLocation());
        eliminatedPlayers.add(player.getUniqueId());
        extractionHoldTracker.cancel(player.getUniqueId());
        if (killPlayer && player.isOnline() && !player.isDead()) {
            player.setHealth(0.0);
        }
        dropped.ifPresent(container -> broadcast(player.getName() + " dropped loot: " + container.contents().describe() + "."));
    }

    private void closeAndReset(boolean killUnresolved) {
        state = ArenaState.CLOSING;
        if (killUnresolved) {
            for (UUID playerId : Set.copyOf(participants)) {
                if (!isActiveParticipant(playerId)) {
                    continue;
                }
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    dropAndEliminate(player, true);
                }
            }
        }
        plugin.getLogger().info("Arena " + id() + " closing. Extracted loot=" + extractedLootSummary());
        announcements.announce(this, "Round closed. Resetting.");
        resetRound();
    }

    private void resetRound() {
        state = ArenaState.RESETTING;
        cancelTick();
        threatDirector.stop();
        objectiveManager.stop();
        objectiveEffectExecutor.reset();
        extractionManager.stop();
        lootSourceManager.stop();
        zoneController.stop();
        clearArenaMobs();
        for (UUID playerId : Set.copyOf(participants)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && !extractedPlayers.contains(playerId)) {
                player.teleport(definition.hubReturn().toLocation());
            }
        }
        participants.clear();
        extractedPlayers.clear();
        eliminatedPlayers.clear();
        lootTracker.clearAll();
        lootTracker = new MatchLootTracker(plugin);
        open();
    }

    private void checkEmptyReset() {
        long unresolved = participants.stream().filter(this::isActiveParticipant).count();
        if ((state == ArenaState.ACTIVE || state == ArenaState.FINAL_EXTRACTION) && unresolved == 0) {
            closeAndReset(false);
        }
    }

    private void clearArenaMobs() {
        World world = runtimeArena.world();
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Monster && definition.playableRegion().contains(entity.getLocation())) {
                entity.remove();
            }
        }
    }

    private void ensureTicker() {
        if (tickTask == null) {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        }
    }

    private void cancelTick() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void broadcast(String message) {
        String formatted = Messages.info("[" + displayName() + "] " + message);
        plugin.getLogger().info("[" + id() + "] " + message);
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(formatted);
            }
        }
    }

    private void announceObjective(String message) {
        broadcast(message);
        announcements.announce(this, message);
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.3f);
            }
        }
    }

    private void applyObjectiveEffects(com.zonefall.objective.ObjectiveDefinition definition) {
        for (String message : objectiveEffectExecutor.apply(definition)) {
            broadcast(message);
            announcements.announce(this, message);
        }
    }
}
