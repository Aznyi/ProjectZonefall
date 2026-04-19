package com.zonefall.customblock;

import com.zonefall.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Owns custom light primary blocks and their bounded helper light blocks.
 */
public final class CustomLightBlockManager {
    private static final Material HELPER_MATERIAL = Material.LIGHT;
    private static final int HELPER_LIGHT_LEVEL = 15;

    private final Plugin plugin;
    private final NamespacedKey itemKey;
    private final File file;
    private final File legacyOdinFile;
    private final Map<CustomLightBlockType, NamespacedKey> recipeKeys = new EnumMap<>(CustomLightBlockType.class);
    private final Map<BlockKey, CustomLightBlockType> sources = new LinkedHashMap<>();
    private final Map<BlockKey, Set<BlockKey>> helperOwners = new HashMap<>();

    public CustomLightBlockManager(Plugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
        this.file = new File(plugin.getDataFolder(), "custom-light-blocks.yml");
        this.legacyOdinFile = new File(plugin.getDataFolder(), "odins-light.yml");
        for (CustomLightBlockType type : CustomLightBlockType.values()) {
            recipeKeys.put(type, new NamespacedKey(plugin, type.itemId()));
        }
        load();
    }

    public ItemStack createItem(CustomLightBlockType type, int amount) {
        ItemStack item = new ItemStack(type.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.displayName()));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type.itemId());
        item.setItemMeta(meta);
        return item;
    }

    public Optional<CustomLightBlockType> itemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        CustomLightBlockType type = CustomLightBlockType.fromItemId(data.get(itemKey, PersistentDataType.STRING));
        if (type == null || item.getType() != type.material()) {
            return Optional.empty();
        }
        return Optional.of(type);
    }

    public boolean isSource(Block block) {
        return sources.containsKey(BlockKey.from(block));
    }

    public CustomLightBlockType sourceType(Block block) {
        return sources.get(BlockKey.from(block));
    }

    public void addSource(Block block, CustomLightBlockType type) {
        BlockKey source = BlockKey.from(block);
        block.setType(type.material(), false);
        sources.put(source, type);
        applyHelpers(source, type);
        saveAll();
    }

    public void removeSource(Block block) {
        removeSource(BlockKey.from(block));
    }

    public void removeSource(BlockKey source) {
        CustomLightBlockType type = sources.remove(source);
        if (type == null) {
            return;
        }
        cleanupHelpers(source, type);
        saveAll();
    }

    public void give(Player player, CustomLightBlockType type, int amount) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(createItem(type, amount));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(Messages.ok("Given " + type.displayName() + " x" + amount + "."));
    }

    public void registerRecipes() {
        for (CustomLightBlockType type : CustomLightBlockType.values()) {
            NamespacedKey key = recipeKeys.get(type);
            plugin.getServer().removeRecipe(key);
            ShapedRecipe recipe = new ShapedRecipe(key, createItem(type, 1));
            recipe.shape("PPP", "PGP", "PPP");
            recipe.setIngredient('P', type.material());
            recipe.setIngredient('G', Material.GLOWSTONE);
            plugin.getServer().addRecipe(recipe);
        }
    }

    public void restoreLoadedChunks() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                restoreChunk(chunk);
            }
        }
        saveAll();
    }

    public void restoreChunk(Chunk chunk) {
        List<BlockKey> missingSources = new ArrayList<>();
        for (Map.Entry<BlockKey, CustomLightBlockType> entry : sources.entrySet()) {
            BlockKey source = entry.getKey();
            CustomLightBlockType type = entry.getValue();
            if (source.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
                Optional<Block> block = source.blockIfLoaded();
                if (block.isPresent() && block.get().getType() != type.material()) {
                    missingSources.add(source);
                    continue;
                }
            }
            for (BlockKey helper : helperPositions(source, type)) {
                if (helper.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
                    applyHelper(source, type, helper);
                }
            }
        }
        for (BlockKey missingSource : missingSources) {
            removeSource(missingSource);
        }
        saveAll();
    }

    public void saveAll() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for custom light block persistence.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<BlockKey, CustomLightBlockType> entry : sources.entrySet()) {
            yaml.set("sources." + entry.getKey().pathKey(), entry.getValue().itemId());
        }
        for (Map.Entry<BlockKey, Set<BlockKey>> entry : helperOwners.entrySet()) {
            yaml.set("helpers." + entry.getKey().pathKey(), entry.getValue().stream().map(BlockKey::serialize).toList());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save custom-light-blocks.yml.", ex);
        }
    }

    private void load() {
        loadCurrent();
        loadLegacyOdin();
        plugin.getLogger().info("Loaded " + sources.size() + " custom light source blocks.");
    }

    private void loadCurrent() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.isConfigurationSection("sources")) {
            for (String rawSource : yaml.getConfigurationSection("sources").getKeys(false)) {
                Optional<BlockKey> source = BlockKey.parsePathKey(rawSource);
                CustomLightBlockType type = CustomLightBlockType.fromItemId(yaml.getString("sources." + rawSource, ""));
                if (source.isPresent() && type != null) {
                    sources.put(source.get(), type);
                }
            }
        }
        loadHelpers(yaml);
    }

    private void loadLegacyOdin() {
        if (!legacyOdinFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyOdinFile);
        for (String rawSource : yaml.getStringList("sources")) {
            BlockKey.parse(rawSource).ifPresent(source -> sources.putIfAbsent(source, CustomLightBlockType.ODINS_LIGHT));
        }
        loadHelpers(yaml);
    }

    private void loadHelpers(YamlConfiguration yaml) {
        if (!yaml.isConfigurationSection("helpers")) {
            return;
        }
        for (String rawHelper : yaml.getConfigurationSection("helpers").getKeys(false)) {
            Optional<BlockKey> helper = BlockKey.parsePathKey(rawHelper);
            if (helper.isEmpty()) {
                continue;
            }
            Set<BlockKey> owners = new HashSet<>();
            for (String rawOwner : yaml.getStringList("helpers." + rawHelper)) {
                BlockKey.parse(rawOwner).filter(sources::containsKey).ifPresent(owners::add);
            }
            if (!owners.isEmpty()) {
                helperOwners.computeIfAbsent(helper.get(), ignored -> new HashSet<>()).addAll(owners);
            }
        }
    }

    private void applyHelpers(BlockKey source, CustomLightBlockType type) {
        for (BlockKey helper : helperPositions(source, type)) {
            applyHelper(source, type, helper);
        }
    }

    private void applyHelper(BlockKey source, CustomLightBlockType type, BlockKey helper) {
        Optional<Block> optionalBlock = helper.blockIfLoaded();
        if (optionalBlock.isEmpty()) {
            return;
        }
        Block block = optionalBlock.get();
        Set<BlockKey> owners = helperOwners.get(helper);
        if (block.getType() == HELPER_MATERIAL && owners == null) {
            return;
        }
        if (!block.isEmpty() && block.getType() != HELPER_MATERIAL) {
            removeHelperOwner(helper, source);
            return;
        }
        block.setType(HELPER_MATERIAL, false);
        BlockData data = block.getBlockData();
        if (data instanceof Levelled levelled) {
            levelled.setLevel(Math.min(HELPER_LIGHT_LEVEL, levelled.getMaximumLevel()));
            block.setBlockData(levelled, false);
        }
        helperOwners.computeIfAbsent(helper, ignored -> new HashSet<>()).add(source);
    }

    private void cleanupHelpers(BlockKey source, CustomLightBlockType type) {
        for (BlockKey helper : helperPositions(source, type)) {
            removeHelperOwner(helper, source);
        }
    }

    private void removeHelperOwner(BlockKey helper, BlockKey source) {
        Set<BlockKey> owners = helperOwners.get(helper);
        if (owners == null) {
            return;
        }
        owners.remove(source);
        if (!owners.isEmpty()) {
            return;
        }
        helperOwners.remove(helper);
        helper.blockIfLoaded()
                .filter(block -> block.getType() == HELPER_MATERIAL)
                .ifPresent(block -> block.setType(Material.AIR, false));
    }

    private List<BlockKey> helperPositions(BlockKey source, CustomLightBlockType type) {
        if (type == CustomLightBlockType.BAMBOO_LIGHT) {
            return bambooHelperPositions(source);
        }
        return odinHelperPositions(source);
    }

    private List<BlockKey> odinHelperPositions(BlockKey source) {
        List<BlockKey> positions = new ArrayList<>();
        int[] horizontalOffsets = {-12, -6, 0, 6, 12};
        int[] verticalOffsets = {-3, 1, 5};
        for (int dy : verticalOffsets) {
            for (int dx : horizontalOffsets) {
                for (int dz : horizontalOffsets) {
                    if ((dx * dx) + (dz * dz) > 144 || (dx == 0 && dz == 0 && dy == 1)) {
                        continue;
                    }
                    positions.add(new BlockKey(source.worldName(), source.x() + dx, source.y() + dy, source.z() + dz));
                }
            }
        }
        return positions;
    }

    private List<BlockKey> bambooHelperPositions(BlockKey source) {
        List<BlockKey> positions = new ArrayList<>();
        int[] offsets = {-12, -8, -4, 0, 4, 8, 12};
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx : offsets) {
                for (int dz : offsets) {
                    if (dx == 0 && dz == 0 && dy == 0) {
                        continue;
                    }
                    int distanceSquared = dx * dx + dz * dz;
                    if (distanceSquared > 144 || !includeBambooOffset(dx, dz, distanceSquared)) {
                        continue;
                    }
                    positions.add(new BlockKey(source.worldName(), source.x() + dx, source.y() + dy, source.z() + dz));
                }
            }
        }
        return positions;
    }

    private boolean includeBambooOffset(int dx, int dz, int distanceSquared) {
        if (distanceSquared <= 36) {
            return true;
        }
        if (distanceSquared <= 100) {
            return Math.floorMod(dx + dz, 8) == 0;
        }
        return dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
    }
}
