package com.zonefall.pve;

import com.zonefall.arena.ArenaDefinition;
import com.zonefall.arena.CuboidRegion;
import com.zonefall.arena.SafePlacementValidator;
import com.zonefall.core.ZonefallConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arena-scoped PvE director with phase-based hostile pressure and cleanup.
 */
public final class ArenaThreatDirector {
    private static final int SPAWN_ATTEMPTS = 24;

    private final Plugin plugin;
    private final ZonefallConfig config;
    private final ArenaDefinition arena;
    private final ActivePlayerProvider activePlayers;
    private final Random random = new Random();
    private final Set<UUID> spawnedMobs = ConcurrentHashMap.newKeySet();

    private ZonefallConfig.ThreatPhaseConfig currentPhase;

    public ArenaThreatDirector(Plugin plugin, ZonefallConfig config, ArenaDefinition arena, ActivePlayerProvider activePlayers) {
        this.plugin = plugin;
        this.config = config;
        this.arena = arena;
        this.activePlayers = activePlayers;
    }

    public void start() {
        currentPhase = config.threatPhases().isEmpty() ? null : config.threatPhases().get(0);
        plugin.getLogger().info("Threat director enabled for " + arena.id() + ".");
    }

    public void tick(int elapsedSeconds) {
        cleanupInvalidMobs();
        ZonefallConfig.ThreatPhaseConfig phase = phaseFor(elapsedSeconds);
        currentPhase = phase;
        if (phase == null || elapsedSeconds <= 0 || elapsedSeconds % phase.spawnIntervalSeconds() != 0) {
            return;
        }
        int activeCount = (int) activePlayers.participants().stream().filter(activePlayers::isActiveParticipant).count();
        if (activeCount <= 0 || spawnedMobs.size() >= phase.maxAliveMobs()) {
            return;
        }
        int desired = Math.min(phase.groupSize() * activeCount, phase.maxAliveMobs() - spawnedMobs.size());
        for (int i = 0; i < desired; i++) {
            findSpawnLocation().ifPresent(location -> spawnMob(location, phase));
        }
    }

    public void stop() {
        for (UUID mobId : Set.copyOf(spawnedMobs)) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null) {
                entity.remove();
            }
        }
        spawnedMobs.clear();
        plugin.getLogger().info("Threat director stopped for " + arena.id() + ".");
    }

    public String debugSummary() {
        ZonefallConfig.ThreatPhaseConfig phase = currentPhase;
        if (phase == null) {
            return "phase=none activeMobs=" + spawnedMobs.size();
        }
        return "phase=" + phase.id()
                + " interval=" + phase.spawnIntervalSeconds() + "s"
                + " groupSize=" + phase.groupSize()
                + " healthMultiplier=" + phase.healthMultiplier()
                + " maxAlive=" + phase.maxAliveMobs()
                + " activeMobs=" + spawnedMobs.size()
                + " pool=" + describePool()
                + " eligible=" + describeEligible(currentThreatPhase());
    }

    private Optional<Location> findSpawnLocation() {
        World world = Bukkit.getWorld(arena.worldName());
        if (world == null) {
            return Optional.empty();
        }
        CuboidRegion region = arena.playableRegion();
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            int x = random.nextInt(region.maxX() - region.minX() + 1) + region.minX();
            int z = random.nextInt(region.maxZ() - region.minZ() + 1) + region.minZ();
            int y = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            Location location = new Location(world, x + 0.5, y, z + 0.5);
            if (isValidSpawn(location)) {
                return Optional.of(location);
            }
        }
        return Optional.empty();
    }

    private boolean isValidSpawn(Location location) {
        if (!arena.playableRegion().contains(location)) {
            return false;
        }
        if (arena.protectedRegion().contains(location) && !arena.playableRegion().contains(location)) {
            return false;
        }
        if (isNearActivePlayer(location)) {
            return false;
        }
        return SafePlacementValidator.validate(location).safe();
    }

    private boolean isNearActivePlayer(Location location) {
        double minDistanceSquared = config.spawnMinDistance() * config.spawnMinDistance();
        for (UUID playerId : activePlayers.participants()) {
            if (!activePlayers.isActiveParticipant(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null
                    && player.getWorld().equals(location.getWorld())
                    && player.getLocation().distanceSquared(location) < minDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private void spawnMob(Location location, ZonefallConfig.ThreatPhaseConfig phase) {
        EntityType type = pickMob(currentThreatPhase());
        Entity entity = location.getWorld().spawnEntity(location, type);
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return;
        }
        living.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        scaleHealth(living, phase.healthMultiplier());
        spawnedMobs.add(living.getUniqueId());
        if (config.debug()) {
            plugin.getLogger().info("Threat spawned " + type + " for " + arena.id()
                    + " at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ()
                    + " phase=" + phase.id());
        }
    }

    private void scaleHealth(LivingEntity living, double multiplier) {
        if (multiplier <= 1.0) {
            return;
        }
        AttributeInstance attribute = living.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double maxHealth = attribute.getBaseValue() * multiplier;
        attribute.setBaseValue(maxHealth);
        living.setHealth(maxHealth);
    }

    private EntityType pickMob(ThreatPhase phase) {
        Map<EntityType, Integer> weights = weightedEligiblePool(phase);
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (total <= 0) {
            return EntityType.ZOMBIE;
        }
        int roll = random.nextInt(total);
        int cursor = 0;
        for (Map.Entry<EntityType, Integer> entry : weights.entrySet()) {
            cursor += entry.getValue();
            if (roll < cursor) {
                return entry.getKey();
            }
        }
        return EntityType.ZOMBIE;
    }

    private Map<EntityType, Integer> weightedEligiblePool(ThreatPhase phase) {
        Map<EntityType, Integer> weights = new java.util.LinkedHashMap<>();
        for (ThreatMobEntry entry : arena.hostileMobPool()) {
            if (entry.minimumPhase().ordinal() > phase.ordinal()) {
                continue;
            }
            weights.merge(entry.type(), biasedWeight(entry, phase), Integer::sum);
        }
        if (weights.isEmpty()) {
            return Map.of(EntityType.ZOMBIE, 1);
        }
        return weights;
    }

    private int biasedWeight(ThreatMobEntry entry, ThreatPhase phase) {
        double multiplier = 1.0;
        if (phase == ThreatPhase.MID && entry.minimumPhase() == ThreatPhase.MID) {
            multiplier = 1.4;
        } else if (phase == ThreatPhase.LATE && entry.minimumPhase() == ThreatPhase.LATE) {
            multiplier = 2.0;
        } else if (phase == ThreatPhase.LATE && entry.minimumPhase() == ThreatPhase.MID) {
            multiplier = 1.25;
        } else if (phase == ThreatPhase.LATE && entry.minimumPhase() == ThreatPhase.EARLY) {
            multiplier = 0.75;
        }
        return Math.max(1, (int) Math.round(entry.weight() * multiplier));
    }

    private ThreatPhase currentThreatPhase() {
        if (currentPhase == null) {
            return ThreatPhase.EARLY;
        }
        return ThreatPhase.fromConfig(currentPhase.id(), ThreatPhase.EARLY);
    }

    private String describePool() {
        return arena.hostileMobPool().stream().map(ThreatMobEntry::describe).toList().toString();
    }

    private String describeEligible(ThreatPhase phase) {
        return weightedEligiblePool(phase).toString();
    }

    private ZonefallConfig.ThreatPhaseConfig phaseFor(int elapsedSeconds) {
        double progress = Math.max(0.0, Math.min(1.0, elapsedSeconds / (double) arena.roundDurationSeconds()));
        ZonefallConfig.ThreatPhaseConfig selected = null;
        for (ZonefallConfig.ThreatPhaseConfig phase : config.threatPhases()) {
            if (progress >= phase.startProgress()) {
                selected = phase;
            }
        }
        return selected;
    }

    private void cleanupInvalidMobs() {
        for (UUID mobId : Set.copyOf(spawnedMobs)) {
            Entity entity = Bukkit.getEntity(mobId);
            if (!(entity instanceof Monster) || entity.isDead() || !entity.isValid()) {
                spawnedMobs.remove(mobId);
            }
        }
    }
}
