package com.zonefall.command;

import com.zonefall.match.MatchManager;
import com.zonefall.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single prototype command entry point: /zonefall and /zf.
 */
public final class ZonefallCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "help", "create", "start", "stop", "join", "leave", "status", "extract"
    );

    private final MatchManager matchManager;

    public ZonefallCommand(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "help" -> sendHelp(sender);
            case "create" -> matchManager.create(sender);
            case "start" -> matchManager.start(sender);
            case "stop" -> matchManager.stop(sender);
            case "join" -> requirePlayer(sender, matchManager::join);
            case "leave" -> requirePlayer(sender, matchManager::leave);
            case "status" -> matchManager.status(sender);
            case "extract" -> requirePlayer(sender, matchManager::extract);
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
        sender.sendMessage(Messages.info("Zonefall prototype commands:"));
        sender.sendMessage(Messages.info("/zonefall create - create a local test match"));
        sender.sendMessage(Messages.info("/zonefall join - join the current match"));
        sender.sendMessage(Messages.info("/zonefall start - start countdown and match"));
        sender.sendMessage(Messages.info("/zonefall extract - extract when inside an extraction zone"));
        sender.sendMessage(Messages.info("/zonefall status - print match debug state"));
        sender.sendMessage(Messages.info("/zonefall leave - leave the current match"));
        sender.sendMessage(Messages.info("/zonefall stop - force end the current match"));
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

