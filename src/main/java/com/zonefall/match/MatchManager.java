package com.zonefall.match;

import com.zonefall.arena.Arena;
import com.zonefall.arena.ArenaManager;
import com.zonefall.core.ZonefallConfig;
import com.zonefall.core.ZonefallServices;
import com.zonefall.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates match creation and lookup. Phase 1 intentionally supports one local active match.
 */
public final class MatchManager {
    private final Plugin plugin;
    private final ZonefallConfig config;
    private final ArenaManager arenaManager;
    private final ZonefallServices services;
    private Match currentMatch;

    public MatchManager(Plugin plugin, ZonefallConfig config, ArenaManager arenaManager, ZonefallServices services) {
        this.plugin = plugin;
        this.config = config;
        this.arenaManager = arenaManager;
        this.services = services;
    }

    public Optional<Match> currentMatch() {
        return Optional.ofNullable(currentMatch).filter(match -> match.state() != MatchState.ENDED);
    }

    public Optional<Match> findMatchFor(UUID playerId) {
        return currentMatch().filter(match -> match.contains(playerId));
    }

    public void create(CommandSender sender) {
        if (currentMatch().isPresent()) {
            sender.sendMessage(Messages.error("A local test match already exists. Stop it before creating another."));
            return;
        }

        Arena arena = sender instanceof Player player
                ? arenaManager.createLocalArena(player)
                : arenaManager.createLocalArenaFromDefaultWorld();
        currentMatch = new Match(plugin, config, services, arena);
        sender.sendMessage(Messages.ok("Created local Zonefall match in world " + arena.world().getName() + "."));
        sender.sendMessage(Messages.info("Extraction zone: "
                + arena.extractionLocation().getBlockX() + ", "
                + arena.extractionLocation().getBlockY() + ", "
                + arena.extractionLocation().getBlockZ()));
        plugin.getLogger().info(currentMatch.debugStatus());
    }

    public void join(Player player) {
        Match match = requireMatch(player);
        if (match != null) {
            match.addPlayer(player);
        }
    }

    public void leave(Player player) {
        Match match = findMatchFor(player.getUniqueId()).orElse(null);
        if (match == null) {
            player.sendMessage(Messages.error("You are not in a Zonefall match."));
            return;
        }
        match.removePlayer(player);
        player.sendMessage(Messages.ok("Left the Zonefall match."));
    }

    public void start(CommandSender sender) {
        Match match = requireMatch(sender);
        if (match == null) {
            return;
        }
        try {
            match.startCountdown();
            sender.sendMessage(Messages.ok("Started match countdown."));
        } catch (IllegalStateException ex) {
            sender.sendMessage(Messages.error(ex.getMessage()));
        }
    }

    public void stop(CommandSender sender) {
        Match match = requireMatch(sender);
        if (match == null) {
            return;
        }
        match.forceEnd(MatchEndReason.ADMIN_STOP);
        currentMatch = null;
        sender.sendMessage(Messages.ok("Stopped current match."));
    }

    public void status(CommandSender sender) {
        Match match = currentMatch().orElse(null);
        if (match == null) {
            sender.sendMessage(Messages.info("No active local test match."));
            return;
        }
        sender.sendMessage(Messages.info(match.debugStatus()));
    }

    public void extract(Player player) {
        Match match = findMatchFor(player.getUniqueId()).orElse(null);
        if (match == null) {
            player.sendMessage(Messages.error("You are not in a Zonefall match."));
            return;
        }
        match.tryExtract(player);
        clearEndedMatch(match);
    }

    public void handleMove(Player player) {
        Match match = findMatchFor(player.getUniqueId()).orElse(null);
        if (match == null || match.state() != MatchState.ACTIVE) {
            return;
        }
        if (match.extractionManager().isInsideExtraction(player.getLocation())) {
            match.tryExtract(player);
            clearEndedMatch(match);
        }
    }

    public void handleDeath(Player player) {
        Match match = findMatchFor(player.getUniqueId()).orElse(null);
        if (match == null) {
            return;
        }
        match.handlePlayerDeath(player);
        clearEndedMatch(match);
    }

    public void shutdown() {
        if (currentMatch != null) {
            currentMatch.shutdown();
            currentMatch = null;
        }
    }

    private Match requireMatch(CommandSender sender) {
        Match match = currentMatch().orElse(null);
        if (match == null) {
            sender.sendMessage(Messages.error("No local test match exists. Run /zonefall create first."));
        }
        return match;
    }

    private void clearEndedMatch(Match match) {
        if (match.state() == MatchState.ENDED && currentMatch == match) {
            currentMatch = null;
        }
    }
}

