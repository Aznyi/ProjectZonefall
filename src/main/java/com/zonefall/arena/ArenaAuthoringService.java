package com.zonefall.arena;

import com.zonefall.core.ZonefallConfig;
import com.zonefall.match.MatchManager;
import com.zonefall.util.Messages;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin-only in-game tools for tuning authored arena coordinates and regions.
 */
public final class ArenaAuthoringService {
    private final JavaPlugin plugin;
    private final MatchManager matchManager;
    private final Map<UUID, Map<String, Location>> regionSelections = new HashMap<>();

    public ArenaAuthoringService(JavaPlugin plugin, MatchManager matchManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
    }

    public void setPoint(Player player, String arenaId, String pointType, String selector) {
        if (!canAuthor(player)) {
            return;
        }
        String arenaPath = arenaPath(arenaId);
        FileConfiguration config = plugin.getConfig();
        if (!config.isConfigurationSection(arenaPath)) {
            player.sendMessage(Messages.error("Unknown arena: " + arenaId));
            return;
        }

        String validationLabel = pointType.toLowerCase(Locale.ROOT);
        boolean updated = switch (pointType.toLowerCase(Locale.ROOT)) {
            case "center", "join-spawn", "spectator-point" -> {
                writeLocation(config, arenaPath + "." + pointType.toLowerCase(Locale.ROOT), player.getLocation(), true);
                yield true;
            }
            case "extraction" -> {
                validationLabel = setExtraction(player, config, arenaPath, selector);
                yield validationLabel != null;
            }
            case "objective" -> {
                validationLabel = setListLocation(player, config, arenaPath + ".objectives", selector, "objective");
                yield validationLabel != null;
            }
            case "loot-source" -> {
                validationLabel = setListLocation(player, config, arenaPath + ".loot-sources", selector, "loot");
                yield validationLabel != null;
            }
            case "join-point" -> {
                validationLabel = setJoinPoint(player, config, arenaPath, selector);
                yield validationLabel != null;
            }
            default -> {
                player.sendMessage(Messages.error("Unknown point type. Try center, join-spawn, spectator-point, extraction, objective, loot-source."));
                yield false;
            }
        };

        if (!updated) {
            return;
        }
        saveAndReload();
        player.sendMessage(Messages.ok("Updated " + arenaId + " " + pointType + describeSelector(selector)
                + " to " + formatLocation(player.getLocation()) + "."));
        sendValidation(player, arenaId, validationLabel);
    }

    public void setRegion(Player player, String arenaId, String regionType, String positionName) {
        if (!canAuthor(player)) {
            return;
        }
        String normalizedRegion = regionType.toLowerCase(Locale.ROOT);
        if (!normalizedRegion.equals("playable") && !normalizedRegion.equals("spectator")) {
            player.sendMessage(Messages.error("Region must be playable or spectator."));
            return;
        }
        String normalizedPosition = positionName.toLowerCase(Locale.ROOT);
        if (!normalizedPosition.equals("pos1") && !normalizedPosition.equals("pos2")) {
            player.sendMessage(Messages.error("Position must be pos1 or pos2."));
            return;
        }
        String arenaPath = arenaPath(arenaId);
        FileConfiguration config = plugin.getConfig();
        if (!config.isConfigurationSection(arenaPath)) {
            player.sendMessage(Messages.error("Unknown arena: " + arenaId));
            return;
        }

        String selectionKey = arenaId.toLowerCase(Locale.ROOT) + ":" + normalizedRegion;
        Map<String, Location> selections = regionSelections.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        selections.put(selectionKey + ":" + normalizedPosition, player.getLocation().clone());
        player.sendMessage(Messages.ok("Set " + arenaId + " " + normalizedRegion + " " + normalizedPosition
                + " to " + formatLocation(player.getLocation()) + "."));

        Location pos1 = selections.get(selectionKey + ":pos1");
        Location pos2 = selections.get(selectionKey + ":pos2");
        if (pos1 == null || pos2 == null) {
            player.sendMessage(Messages.info("Set the other position to save the region."));
            return;
        }
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            player.sendMessage(Messages.error("Region positions must be in the same world."));
            return;
        }

        String regionPath = arenaPath + "." + normalizedRegion + "-region";
        writeRegion(config, regionPath, pos1, pos2);
        saveAndReload();
        player.sendMessage(Messages.ok("Saved " + arenaId + " " + normalizedRegion + " region: "
                + boundsSummary(pos1, pos2) + "."));
        sendValidation(player, arenaId, normalizedRegion + "-region");
    }

    public void reloadConfig(CommandSender sender) {
        if (!canAuthor(sender)) {
            return;
        }
        plugin.reloadConfig();
        matchManager.reloadConfig(ZonefallConfig.from(plugin.getConfig()));
        sender.sendMessage(Messages.ok("Reloaded Zonefall arena config."));
    }

    public void saveConfig(CommandSender sender) {
        if (!canAuthor(sender)) {
            return;
        }
        plugin.saveConfig();
        sender.sendMessage(Messages.ok("Saved active Zonefall config."));
    }

    public void teleport(Player player, String arenaId, String pointName, String selector) {
        if (!canAuthor(player)) {
            return;
        }
        Optional<Location> target = resolvePoint(arenaId, pointName, selector);
        if (target.isEmpty()) {
            player.sendMessage(Messages.error("Could not resolve point. Try center, join-spawn, spectator-point, extraction <index>, objective <id>, loot-source <id>."));
            return;
        }
        player.teleport(target.get());
        player.sendMessage(Messages.ok("Teleported to " + arenaId + " " + pointName + describeSelector(selector) + "."));
    }

    private boolean canAuthor(CommandSender sender) {
        if (!sender.isOp() && !sender.hasPermission("zonefall.admin")) {
            sender.sendMessage(Messages.error("Arena authoring requires operator/admin permission."));
            return false;
        }
        return true;
    }

    private boolean canAuthor(Player player) {
        return canAuthor((CommandSender) player);
    }

    private String setExtraction(Player player, FileConfiguration config, String arenaPath, String selector) {
        int index;
        try {
            index = Integer.parseInt(selector);
        } catch (NumberFormatException ex) {
            player.sendMessage(Messages.error("Usage: /zonefall arena setpoint <id> extraction <index>"));
            return null;
        }
        if (index < 1) {
            player.sendMessage(Messages.error("Extraction index is 1-based."));
            return null;
        }
        List<Map<?, ?>> rawExtractions = config.getMapList(arenaPath + ".extractions");
        if (rawExtractions.isEmpty()) {
            player.sendMessage(Messages.error("Arena has no configured extractions."));
            return null;
        }
        if (index > rawExtractions.size()) {
            player.sendMessage(Messages.error("Extraction " + index + " does not exist. Configured: " + rawExtractions.size()));
            return null;
        }
        List<Map<String, Object>> extractions = copyMapList(rawExtractions);
        extractions.set(index - 1, locationMap(player.getLocation(), true));
        config.set(arenaPath + ".extractions", extractions);
        if (index == 1) {
            writeLocation(config, arenaPath + ".extraction", player.getLocation(), true);
        }
        return "extraction-" + index;
    }

    private String setListLocation(Player player, FileConfiguration config, String listPath, String id, String validationPrefix) {
        if (id == null || id.isBlank()) {
            player.sendMessage(Messages.error("Missing " + validationPrefix + " id."));
            return null;
        }
        List<Map<String, Object>> entries = copyMapList(config.getMapList(listPath));
        for (Map<String, Object> entry : entries) {
            if (id.equalsIgnoreCase(String.valueOf(entry.get("id")))) {
                entry.put("location", locationMap(player.getLocation(), true));
                config.set(listPath, entries);
                return validationPrefix + "-" + id;
            }
        }
        player.sendMessage(Messages.error("No " + validationPrefix + " found with id: " + id));
        return null;
    }

    private String setJoinPoint(Player player, FileConfiguration config, String arenaPath, String id) {
        if (id == null || id.isBlank()) {
            player.sendMessage(Messages.error("Usage: /zonefall arena setpoint <id> join-point <join-point-id>"));
            return null;
        }
        String path = arenaPath + ".join-points." + id;
        if (!config.isConfigurationSection(path)) {
            player.sendMessage(Messages.error("No join point found with id: " + id));
            return null;
        }
        writeLocation(config, path, player.getLocation(), true);
        return "join-point-" + id;
    }

    private void writeLocation(FileConfiguration config, String path, Location location, boolean includeFacing) {
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", round(location.getX()));
        config.set(path + ".y", round(location.getY()));
        config.set(path + ".z", round(location.getZ()));
        if (includeFacing) {
            config.set(path + ".yaw", round(location.getYaw()));
            config.set(path + ".pitch", round(location.getPitch()));
        }
    }

    private Map<String, Object> locationMap(Location location, boolean includeFacing) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("world", location.getWorld().getName());
        values.put("x", round(location.getX()));
        values.put("y", round(location.getY()));
        values.put("z", round(location.getZ()));
        if (includeFacing) {
            values.put("yaw", round(location.getYaw()));
            values.put("pitch", round(location.getPitch()));
        }
        return values;
    }

    private void writeRegion(FileConfiguration config, String path, Location pos1, Location pos2) {
        config.set(path + ".world", pos1.getWorld().getName());
        config.set(path + ".min.x", Math.min(pos1.getBlockX(), pos2.getBlockX()));
        config.set(path + ".min.y", Math.min(pos1.getBlockY(), pos2.getBlockY()));
        config.set(path + ".min.z", Math.min(pos1.getBlockZ(), pos2.getBlockZ()));
        config.set(path + ".max.x", Math.max(pos1.getBlockX(), pos2.getBlockX()));
        config.set(path + ".max.y", Math.max(pos1.getBlockY(), pos2.getBlockY()));
        config.set(path + ".max.z", Math.max(pos1.getBlockZ(), pos2.getBlockZ()));
    }

    private void saveAndReload() {
        plugin.saveConfig();
        plugin.reloadConfig();
        matchManager.reloadConfig(ZonefallConfig.from(plugin.getConfig()));
    }

    private void sendValidation(CommandSender sender, String arenaId, String entryLabel) {
        ZonefallConfig runtimeConfig = ZonefallConfig.from(plugin.getConfig());
        ArenaDefinition arena = runtimeConfig.arenas().stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(arenaId))
                .findFirst()
                .orElse(null);
        if (arena == null) {
            return;
        }
        ArenaValidationReport report = ArenaValidator.report(runtimeConfig, arena);
        report.entries().stream()
                .filter(entry -> entry.name().equalsIgnoreCase(entryLabel))
                .findFirst()
                .ifPresent(entry -> sender.sendMessage(Messages.info("Validation: " + entry.format(false))));
        sender.sendMessage(Messages.info("Arena validation summary: " + report.summary()
                + ". Run /zonefall arena validate " + arenaId + " detailed for the full report."));
    }

    private Optional<Location> resolvePoint(String arenaId, String pointName, String selector) {
        ZonefallConfig runtimeConfig = ZonefallConfig.from(plugin.getConfig());
        ArenaDefinition arena = runtimeConfig.arenas().stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(arenaId))
                .findFirst()
                .orElse(null);
        if (arena == null) {
            return Optional.empty();
        }
        return switch (pointName.toLowerCase(Locale.ROOT)) {
            case "center" -> Optional.of(arena.center().toLocation());
            case "join-spawn" -> Optional.of(arena.joinSpawn().toLocation());
            case "spectator-point" -> Optional.of(arena.spectatorPoint().toLocation());
            case "extraction" -> {
                int index = parsePositiveIndex(selector).orElse(-1);
                yield index >= 1 && index <= arena.extractions().size()
                        ? Optional.of(arena.extractions().get(index - 1).toLocation())
                        : Optional.empty();
            }
            case "objective" -> arena.objectives().stream()
                    .filter(objective -> objective.id().equalsIgnoreCase(selector))
                    .map(objective -> objective.location().toLocation())
                    .findFirst();
            case "loot-source" -> arena.lootSources().stream()
                    .filter(source -> source.id().equalsIgnoreCase(selector))
                    .map(source -> source.location().toLocation())
                    .findFirst();
            case "join-point" -> arena.joinPoints().stream()
                    .filter(point -> point.id().equalsIgnoreCase(selector))
                    .map(point -> point.location().toLocation())
                    .findFirst();
            default -> Optional.empty();
        };
    }

    private Optional<Integer> parsePositiveIndex(String text) {
        try {
            int value = Integer.parseInt(text);
            return value > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private List<Map<String, Object>> copyMapList(List<Map<?, ?>> rawList) {
        return rawList.stream()
                .map(raw -> {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    raw.forEach((key, value) -> copy.put(String.valueOf(key), value));
                    return copy;
                })
                .toList();
    }

    private String arenaPath(String arenaId) {
        return "arenas." + arenaId.toLowerCase(Locale.ROOT);
    }

    private String describeSelector(String selector) {
        return selector == null || selector.isBlank() ? "" : " " + selector;
    }

    private String formatLocation(Location location) {
        World world = location.getWorld();
        return world.getName() + " "
                + round(location.getX()) + " "
                + round(location.getY()) + " "
                + round(location.getZ());
    }

    private String boundsSummary(Location pos1, Location pos2) {
        return pos1.getWorld().getName()
                + " min=" + Math.min(pos1.getBlockX(), pos2.getBlockX())
                + "," + Math.min(pos1.getBlockY(), pos2.getBlockY())
                + "," + Math.min(pos1.getBlockZ(), pos2.getBlockZ())
                + " max=" + Math.max(pos1.getBlockX(), pos2.getBlockX())
                + "," + Math.max(pos1.getBlockY(), pos2.getBlockY())
                + "," + Math.max(pos1.getBlockZ(), pos2.getBlockZ());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
