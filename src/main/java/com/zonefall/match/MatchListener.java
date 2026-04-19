package com.zonefall.match;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;

/**
 * Bridges Paper player lifecycle events into active matches.
 */
public final class MatchListener implements Listener {
    private final MatchManager matchManager;

    public MatchListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        matchManager.handleDeath(event.getEntity());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.ENDER_DRAGON
                && matchManager.isProtected(event.getEntity().getLocation())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null && event.getEntity() instanceof Monster) {
            matchManager.handleMobKill(killer);
        }
    }

    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        boolean protectedPortal = event.getBlocks().stream()
                .anyMatch(block -> matchManager.isProtected(block.getLocation()));
        if (protectedPortal) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                && matchManager.isProtected(event.getFrom())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(com.zonefall.util.Messages.error("End portal travel is disabled inside Zonefall arenas."));
        }
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Monster
                && matchManager.canNaturalHostileSpawn(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Monster)) {
            return;
        }
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        if (!matchManager.canNaturalHostileSpawn(entity.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        matchManager.handleFirstJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        if (!matchManager.canArenaInteract(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        matchManager.handleInteract(event.getPlayer(), event.getClickedBlock());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!matchManager.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!matchManager.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = event.getDamager() instanceof Player player ? player : null;
        if (event.getEntity() instanceof Player victim
                && matchManager.isProtected(victim.getLocation())
                && !matchManager.isArenaParticipant(victim)) {
            event.setCancelled(true);
            return;
        }
        if (attacker != null && !matchManager.canArenaInteract(attacker, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!event.isCancelled()) {
            matchManager.handleDamage(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        matchManager.handleDeath(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        matchManager.respawnLocation(event.getPlayer()).ifPresent(event::setRespawnLocation);
    }
}
