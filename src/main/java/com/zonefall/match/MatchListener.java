package com.zonefall.match;

import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

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
        Player killer = event.getEntity().getKiller();
        if (killer != null && event.getEntity() instanceof Monster) {
            matchManager.handleMobKill(killer);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        if (!matchManager.canSpectatorInteract(event.getPlayer(), event.getClickedBlock().getLocation())) {
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
        if (attacker != null && !matchManager.canSpectatorInteract(attacker, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (matchManager.shouldPreventSpectatorDamage() && matchManager.isSpectating(player)) {
            event.setCancelled(true);
            return;
        }
        if (!event.isCancelled()) {
            matchManager.handleDamage(player);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (matchManager.shouldPreventSpectatorHunger()
                && event.getEntity() instanceof Player player
                && matchManager.isSpectating(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
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
