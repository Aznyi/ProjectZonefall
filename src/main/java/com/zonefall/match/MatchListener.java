package com.zonefall.match;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        matchManager.findMatchFor(player.getUniqueId()).ifPresent(match -> match.removePlayer(player));
    }
}

