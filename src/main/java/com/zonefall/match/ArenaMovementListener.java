package com.zonefall.match;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;

/**
 * Handles fine-grained arena movement rules that need sub-block precision.
 */
public final class ArenaMovementListener implements Listener {
    private final MatchManager matchManager;

    public ArenaMovementListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Optional<Location> corrected = matchManager.stationaryBarrierCorrection(event.getPlayer(), event.getFrom(), to);
        corrected.ifPresent(event::setTo);
    }
}
