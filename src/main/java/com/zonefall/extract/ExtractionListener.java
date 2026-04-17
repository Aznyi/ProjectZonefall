package com.zonefall.extract;

import com.zonefall.match.MatchManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Checks extraction entry on block movement.
 */
public final class ExtractionListener implements Listener {
    private final MatchManager matchManager;

    public ExtractionListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        matchManager.handleMove(event.getPlayer());
    }
}

