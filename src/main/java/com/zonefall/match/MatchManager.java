package com.zonefall.match;

import com.zonefall.arena.ArenaController;
import com.zonefall.arena.ArenaManager;
import com.zonefall.loot.LootType;
import com.zonefall.loot.source.LootSourceType;
import com.zonefall.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Compatibility facade for commands/listeners. Runtime now delegates to configured arena controllers.
 */
public final class MatchManager {
    private final ArenaManager arenaManager;
    private final com.zonefall.core.ZonefallServices services;
    private final com.zonefall.core.ZonefallConfig config;

    public MatchManager(ArenaManager arenaManager, com.zonefall.core.ZonefallServices services, com.zonefall.core.ZonefallConfig config) {
        this.arenaManager = arenaManager;
        this.services = services;
        this.config = config;
    }

    public Optional<ArenaController> findMatchFor(UUID playerId) {
        return arenaManager.findForPlayer(playerId);
    }

    public void listArenas(CommandSender sender) {
        sender.sendMessage(Messages.info("Zonefall arenas:"));
        for (ArenaController arena : arenaManager.arenas()) {
            sender.sendMessage(Messages.info("- " + arena.id() + " / " + arena.displayName()
                    + " / " + arena.state()));
        }
    }

    public void arenaInfo(CommandSender sender, String id) {
        ArenaController arena = requireArena(sender, id);
        if (arena == null) {
            return;
        }
        sender.sendMessage(Messages.info(arena.infoLine()));
        sender.sendMessage(Messages.info("World=" + arena.definition().worldName()
                + ", joinable=" + arena.canJoin()));
    }

    public void joinArena(Player player, String id) {
        if (arenaManager.findForPlayer(player.getUniqueId()).isPresent()) {
            player.sendMessage(Messages.error("Leave your current arena before joining another."));
            return;
        }
        ArenaController arena = requireArena(player, id);
        if (arena != null) {
            arena.join(player);
        }
    }

    public void leaveArena(Player player) {
        ArenaController arena = arenaManager.findForPlayer(player.getUniqueId()).orElse(null);
        if (arena == null) {
            player.sendMessage(Messages.error("You are not in an arena."));
            return;
        }
        arena.leave(player);
    }

    public void arenaStatus(CommandSender sender, String id) {
        if (id != null) {
            ArenaController arena = requireArena(sender, id);
            if (arena != null) {
                sender.sendMessage(Messages.info(arena.statusLine()));
            }
            return;
        }
        for (ArenaController arena : arenaManager.arenas()) {
            sender.sendMessage(Messages.info(arena.statusLine()));
        }
    }

    public void forceStart(CommandSender sender, String id) {
        ArenaController arena = requireArena(sender, id);
        if (arena != null) {
            arena.forceStart();
            sender.sendMessage(Messages.ok("Force-started " + arena.id() + "."));
        }
    }

    public void resetArena(CommandSender sender, String id) {
        ArenaController arena = requireArena(sender, id);
        if (arena != null) {
            arena.forceReset();
            sender.sendMessage(Messages.ok("Reset " + arena.id() + "."));
        }
    }

    public void spectate(Player player, String id) {
        if (arenaManager.findForPlayer(player.getUniqueId()).isPresent()) {
            player.sendMessage(Messages.error("Leave your current arena before spectating."));
            return;
        }
        ArenaController arena = requireArena(player, id);
        if (arena == null) {
            return;
        }
        player.teleport(arena.spectatorLocation());
        updateSpectatorState(player);
        player.sendMessage(Messages.ok("Spectating " + arena.displayName() + "."));
    }

    public void stash(Player player) {
        player.sendMessage(Messages.info("Stash: " + services.stashService()
                .getContents(player.getUniqueId()).describe()));
    }

    public void lootStatus(CommandSender sender, String playerName) {
        Player target = resolvePlayer(sender, playerName);
        if (target == null) {
            return;
        }
        ArenaController arena = arenaManager.findForPlayer(target.getUniqueId()).orElse(null);
        if (arena == null) {
            sender.sendMessage(Messages.info(target.getName() + " is not in an arena."));
            return;
        }
        sender.sendMessage(Messages.info(arena.lootStatus(target)));
    }

    public void grantLoot(CommandSender sender, String playerName, String typeName, String amountText) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(Messages.error("Player not found: " + playerName));
            return;
        }
        ArenaController arena = arenaManager.findForPlayer(target.getUniqueId()).orElse(null);
        if (arena == null) {
            sender.sendMessage(Messages.error("Target player is not in an arena."));
            return;
        }
        LootType type;
        int amount;
        try {
            type = LootType.valueOf(typeName.toUpperCase(Locale.ROOT));
            amount = Integer.parseInt(amountText);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Messages.error("Usage: /zonefall grantloot <player> <SCRAP|CLOTH|STONE|IRON_FRAGMENTS|GEMS> <amount>"));
            return;
        }
        if (amount <= 0) {
            sender.sendMessage(Messages.error("Amount must be greater than zero."));
            return;
        }
        if (arena.grantLoot(target, type, amount)) {
            sender.sendMessage(Messages.ok("Granted " + type.displayName() + " x" + amount + " to " + target.getName() + "."));
        }
    }

    public void placeLoot(Player player, String typeName) {
        ArenaController arena = arenaManager.findForPlayer(player.getUniqueId()).orElse(null);
        if (arena == null) {
            player.sendMessage(Messages.error("Join an arena before placing arena loot."));
            return;
        }
        try {
            arena.placeLoot(player, LootSourceType.valueOf(typeName.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Messages.error("Unknown loot source. Try SUPPLY_CRATE, SCRAP_CACHE, or ORE_NODE."));
        }
    }

    public void extract(Player player) {
        ArenaController arena = arenaManager.findForPlayer(player.getUniqueId()).orElse(null);
        if (arena == null) {
            player.sendMessage(Messages.error("You are not in an arena."));
            return;
        }
        arena.tryExtract(player).ifPresent(message -> player.sendMessage(Messages.error(message)));
    }

    public void extractStatus(CommandSender sender, String playerName) {
        Player target = resolvePlayer(sender, playerName);
        if (target == null) {
            return;
        }
        ArenaController arena = arenaManager.findForPlayer(target.getUniqueId()).orElse(null);
        sender.sendMessage(Messages.info(arena == null
                ? target.getName() + " is not in an arena."
                : arena.extractionStatus(target)));
    }

    public void lootReload(CommandSender sender) {
        services.stashService().saveAll();
        sender.sendMessage(Messages.ok("Loot/stash data flushed. Restart to reload arena config."));
    }

    public void handleMove(Player player) {
        if (arenaManager.findForPlayer(player.getUniqueId()).isEmpty()) {
            arenaManager.findJoinPoint(player.getLocation()).ifPresent(arena -> arena.join(player));
        }
        updateSpectatorState(player);
        arenaManager.handleMove(player);
    }

    public void handleDeath(Player player) {
        arenaManager.handleDeath(player);
    }

    public void handleDamage(Player player) {
        arenaManager.findForPlayer(player.getUniqueId()).ifPresent(arena -> arena.cancelExtraction(player, "damage taken"));
    }

    public void handleMobKill(Player player) {
        arenaManager.handleMobKill(player);
    }

    public void handleInteract(Player player, Block block) {
        arenaManager.handleInteract(player, block);
    }

    public Optional<org.bukkit.Location> respawnLocation(Player player) {
        return arenaManager.findForPlayer(player.getUniqueId()).map(ArenaController::hubReturnLocation);
    }

    public boolean isProtected(org.bukkit.Location location) {
        return arenaManager.findProtecting(location).isPresent();
    }

    public boolean canBuild(Player player, org.bukkit.Location location) {
        Optional<ArenaController> arena = arenaManager.findProtecting(location);
        return arena.isEmpty();
    }

    public boolean canSpectatorInteract(Player player, org.bukkit.Location location) {
        Optional<ArenaController> arena = arenaManager.findProtecting(location);
        return arena.isEmpty() || arena.get().isParticipant(player);
    }

    public boolean isArenaParticipant(Player player) {
        return arenaManager.findForPlayer(player.getUniqueId()).isPresent();
    }

    public boolean isSpectating(Player player) {
        return arenaManager.findForPlayer(player.getUniqueId()).isEmpty()
                && arenaManager.findSpectatorRegion(player.getLocation()).isPresent();
    }

    public boolean shouldPreventSpectatorDamage() {
        return config.spectatorPreventDamage();
    }

    public boolean shouldPreventSpectatorHunger() {
        return config.spectatorPreventHunger();
    }

    public void updateSpectatorState(Player player) {
        if (isSpectating(player)) {
            if (config.spectatorFlightEnabled()) {
                player.setAllowFlight(true);
            }
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        } else if (!isArenaParticipant(player) && player.getAllowFlight()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    public void shutdown() {
        arenaManager.shutdown();
    }

    // Legacy aliases kept for short-term compatibility with the original prototype command flow.
    public void create(CommandSender sender) {
        sender.sendMessage(Messages.info("Arena mode is active. Use /zonefall arena list."));
    }

    public void start(CommandSender sender) {
        arenaManager.arenas().stream().findFirst().ifPresent(arena -> {
            arena.forceStart();
            sender.sendMessage(Messages.ok("Force-started " + arena.id() + "."));
        });
    }

    public void stop(CommandSender sender) {
        arenaManager.arenas().forEach(ArenaController::forceReset);
        sender.sendMessage(Messages.ok("Reset all arenas."));
    }

    public void join(Player player) {
        arenaManager.arenas().stream().findFirst().ifPresent(arena -> arena.join(player));
    }

    public void leave(Player player) {
        leaveArena(player);
    }

    public void status(CommandSender sender) {
        arenaStatus(sender, null);
    }

    private ArenaController requireArena(CommandSender sender, String id) {
        if (id == null || id.isBlank()) {
            sender.sendMessage(Messages.error("Arena id is required."));
            return null;
        }
        ArenaController arena = arenaManager.find(id).orElse(null);
        if (arena == null) {
            sender.sendMessage(Messages.error("Unknown arena: " + id));
        }
        return arena;
    }

    private Player resolvePlayer(CommandSender sender, String playerName) {
        if (playerName == null) {
            if (sender instanceof Player player) {
                return player;
            }
            sender.sendMessage(Messages.error("Console must specify a player."));
            return null;
        }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(Messages.error("Player not found: " + playerName));
        }
        return target;
    }
}
