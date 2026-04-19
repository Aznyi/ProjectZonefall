package com.zonefall.command;

import com.zonefall.arena.ArenaAuthoringService;
import com.zonefall.match.MatchManager;
import com.zonefall.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single prototype command entry point: /zonefall and /zf.
 */
public final class ZonefallCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "help", "create", "start", "stop", "join", "leave", "status", "extract",
            "stash", "grantloot", "lootstatus", "placeloot", "lootreload", "extractstatus", "arena"
    );

    private final MatchManager matchManager;
    private final ArenaAuthoringService authoringService;

    public ZonefallCommand(JavaPlugin plugin, MatchManager matchManager) {
        this.matchManager = matchManager;
        this.authoringService = new ArenaAuthoringService(plugin, matchManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "help" -> sendHelp(sender);
            case "arena" -> handleArena(sender, label, args);
            case "create" -> matchManager.create(sender);
            case "start" -> matchManager.start(sender);
            case "stop" -> matchManager.stop(sender);
            case "join" -> requirePlayer(sender, matchManager::join);
            case "leave" -> requirePlayer(sender, matchManager::leave);
            case "status" -> matchManager.status(sender);
            case "extract" -> requirePlayer(sender, matchManager::extract);
            case "stash" -> requirePlayer(sender, matchManager::stash);
            case "lootstatus" -> matchManager.lootStatus(sender, args.length >= 2 ? args[1] : null);
            case "grantloot" -> {
                if (args.length < 4) {
                    sender.sendMessage(Messages.error("Usage: /zonefall grantloot <player> <type> <amount>"));
                    return true;
                }
                matchManager.grantLoot(sender, args[1], args[2], args[3]);
            }
            case "placeloot" -> {
                if (args.length < 2) {
                    sender.sendMessage(Messages.error("Usage: /zonefall placeloot <SUPPLY_CRATE|SCRAP_CACHE|ORE_NODE>"));
                    return true;
                }
                requirePlayer(sender, player -> matchManager.placeLoot(player, args[1]));
            }
            case "lootreload" -> matchManager.lootReload(sender);
            case "extractstatus" -> matchManager.extractStatus(sender, args.length >= 2 ? args[1] : null);
            default -> sender.sendMessage(Messages.error("Unknown command. Try /" + label + " help."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String subcommand : SUBCOMMANDS) {
            if (subcommand.startsWith(prefix)) {
                matches.add(subcommand);
            }
        }
        return matches;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Messages.info("Zonefall arena commands:"));
        sender.sendMessage(Messages.info("/zonefall arena list"));
        sender.sendMessage(Messages.info("/zonefall arena info <id>"));
        sender.sendMessage(Messages.info("/zonefall arena join <id>"));
        sender.sendMessage(Messages.info("/zonefall arena leave"));
        sender.sendMessage(Messages.info("/zonefall arena status [id]"));
        sender.sendMessage(Messages.info("/zonefall arena rolls <id>"));
        sender.sendMessage(Messages.info("/zonefall arena debug <id>"));
        sender.sendMessage(Messages.info("/zonefall arena objectives <id>"));
        sender.sendMessage(Messages.info("/zonefall arena validate <id> [detailed]"));
        sender.sendMessage(Messages.info("/zonefall arena setpoint <id> <center|join-spawn>"));
        sender.sendMessage(Messages.info("/zonefall arena setpoint <id> extraction <index>"));
        sender.sendMessage(Messages.info("/zonefall arena setpoint <id> objective <objective-id>"));
        sender.sendMessage(Messages.info("/zonefall arena setpoint <id> loot-source <loot-source-id>"));
        sender.sendMessage(Messages.info("/zonefall arena setpoint <id> join-point <join-point-id>"));
        sender.sendMessage(Messages.info("/zonefall arena setregion <id> <playable|protected> <pos1|pos2>"));
        sender.sendMessage(Messages.info("/zonefall arena reloadconfig"));
        sender.sendMessage(Messages.info("/zonefall arena save"));
        sender.sendMessage(Messages.info("/zonefall arena tp <id> <point> [index|id]"));
        sender.sendMessage(Messages.info("/zonefall arena forcestart <id>"));
        sender.sendMessage(Messages.info("/zonefall arena reset <id>"));
        sender.sendMessage(Messages.info("/zonefall extract - extract when inside an extraction zone"));
        sender.sendMessage(Messages.info("/zonefall stash - show your in-memory stash"));
        sender.sendMessage(Messages.info("/zonefall grantloot <player> <type> <amount> - grant match loot"));
        sender.sendMessage(Messages.info("/zonefall lootstatus [player] - show carried match loot"));
        sender.sendMessage(Messages.info("/zonefall placeloot <type> - place a loot source at your location"));
        sender.sendMessage(Messages.info("/zonefall extractstatus [player] - show extraction hold progress"));
        sender.sendMessage(Messages.info("/zonefall lootreload - flush local loot/stash data"));
        sender.sendMessage(Messages.info("/zonefall status - print match debug state"));
        sender.sendMessage(Messages.info("Legacy aliases still work: join, leave, start, stop, status."));
    }

    private void handleArena(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Usage: /" + label + " arena <list|info|join|leave|status|forcestart|reset>"));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> matchManager.listArenas(sender);
            case "info" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("Usage: /" + label + " arena info <id>"));
                    return;
                }
                matchManager.arenaInfo(sender, args[2]);
            }
            case "join" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("Usage: /" + label + " arena join <id>"));
                    return;
                }
                requirePlayer(sender, player -> matchManager.joinArena(player, args[2]));
            }
            case "leave" -> requirePlayer(sender, matchManager::leaveArena);
            case "status" -> matchManager.arenaStatus(sender, args.length >= 3 ? args[2] : null);
            case "rolls" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("Usage: /" + label + " arena rolls <id>"));
                    return;
                }
                matchManager.arenaRolls(sender, args[2]);
            }
            case "debug" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("Usage: /" + label + " arena debug <id>"));
                    return;
                }
                matchManager.arenaDebug(sender, args[2]);
            }
            case "objectives" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("Usage: /" + label + " arena objectives <id>"));
                    return;
                }
                matchManager.arenaObjectives(sender, args[2]);
            }
            case "validate" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("Usage: /" + label + " arena validate <id> [detailed]"));
                    return;
                }
                matchManager.arenaValidate(sender, args[2], args.length >= 4 && args[3].equalsIgnoreCase("detailed"));
            }
            case "setpoint" -> {
                if (args.length < 4) {
                    sender.sendMessage(Messages.error("Usage: /" + label + " arena setpoint <id> <point> [index|id]"));
                    return;
                }
                requirePlayer(sender, player -> authoringService.setPoint(player, args[2], args[3], args.length >= 5 ? args[4] : ""));
            }
            case "setregion" -> {
                if (args.length < 5) {
                    sender.sendMessage(Messages.error("Usage: /" + label + " arena setregion <id> <playable|protected> <pos1|pos2>"));
                    return;
                }
                requirePlayer(sender, player -> authoringService.setRegion(player, args[2], args[3], args[4]));
            }
            case "reloadconfig" -> authoringService.reloadConfig(sender);
            case "save" -> authoringService.saveConfig(sender);
            case "tp" -> {
                if (args.length < 4) {
                    sender.sendMessage(Messages.error("Usage: /" + label + " arena tp <id> <point> [index|id]"));
                    return;
                }
                requirePlayer(sender, player -> authoringService.teleport(player, args[2], args[3], args.length >= 5 ? args[4] : ""));
            }
            case "forcestart" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("Usage: /" + label + " arena forcestart <id>"));
                    return;
                }
                matchManager.forceStart(sender, args[2]);
            }
            case "reset" -> {
                if (args.length < 3) {
                    sender.sendMessage(Messages.error("Usage: /" + label + " arena reset <id>"));
                    return;
                }
                matchManager.resetArena(sender, args[2]);
            }
            default -> sender.sendMessage(Messages.error("Unknown arena command. Try /" + label + " help."));
        }
    }

    private void requirePlayer(CommandSender sender, PlayerAction action) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("This command must be run by a player."));
            return;
        }
        action.run(player);
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(Player player);
    }
}
