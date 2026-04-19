package com.zonefall.customblock;

import com.zonefall.util.Messages;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Owns Odin's Light primary blocks and their bounded helper light blocks.
 */
public final class OdinLightManager {
    public static final String ODINS_LIGHT_ID = "zonefall:odins_light";
    private static final String ITEM_ID_VALUE = "odins_light";
    private static final Material PRIMARY_MATERIAL = Material.TINTED_GLASS;
    private static final Material HELPER_MATERIAL = Material.LIGHT;
    private static final int HELPER_LIGHT_LEVEL = 15;

    private final Plugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;
    private final File file;
    private final Set<BlockKey> sources = new LinkedHashSet<>();
    private final Map<BlockKey, Set<BlockKey>> helperOwners = new HashMap<>();

    public OdinLightManager(Plugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
        this.recipeKey = new NamespacedKey(plugin, "odins_light");
        this.file = new File(plugin.getDataFolder(), "odins-light.yml");
        load();
    }

    public ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(PRIMARY_MATERIAL, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Odin's Light");
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, ITEM_ID_VALUE);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isOdinLightItem(ItemStack item) {
        if (item == null || item.getType() != PRIMARY_MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return ITEM_ID_VALUE.equals(data.get(itemKey, PersistentDataType.STRING));
    }

    public boolean isSource(Block block) {
        return sources.contains(BlockKey.from(block));
    }

    public void addSource(Block block) {
        BlockKey source = BlockKey.from(block);
        block.setType(PRIMARY_MATERIAL, false);
        sources.add(source);
        applyHelpers(source);
        saveAll();
    }

    public void removeSource(Block block) {
        removeSource(BlockKey.from(block));
    }

    public void removeSource(BlockKey source) {
        if (!sources.remove(source)) {
            return;
        }
        cleanupHelpers(source);
        saveAll();
    }

    public void give(Player player, int amount) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(createItem(amount));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(Messages.ok("Given Odin's Light x" + amount + "."));
    }

    public void registerRecipe() {
        plugin.getServer().removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createItem(1));
        recipe.shape("GGG", "GSG", "GGG");
        recipe.setIngredient('G', Material.TINTED_GLASS);
        recipe.setIngredient('S', Material.GLOWSTONE);
        plugin.getServer().addRecipe(recipe);
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
        for (BlockKey source : sources) {
            if (source.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
                Optional<Block> block = source.blockIfLoaded();
                if (block.isPresent() && block.get().getType() != PRIMARY_MATERIAL) {
                    missingSources.add(source);
                    continue;
                }
            }
            for (BlockKey helper : helperPositions(source)) {
                if (helper.isInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
                    applyHelper(source, helper);
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
            plugin.getLogger().warning("Could not create plugin data folder for Odin's Light persistence.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("sources", sources.stream().map(BlockKey::serialize).toList());
        for (Map.Entry<BlockKey, Set<BlockKey>> entry : helperOwners.entrySet()) {
            yaml.set("helpers." + entry.getKey().pathKey(), entry.getValue().stream().map(BlockKey::serialize).toList());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save odins-light.yml.", ex);
        }
    }

    public int sourceCount() {
        return sources.size();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String rawSource : yaml.getStringList("sources")) {
            BlockKey.parse(rawSource).ifPresent(sources::add);
        }
        if (yaml.isConfigurationSection("helpers")) {
            for (String rawHelper : yaml.getConfigurationSection("helpers").getKeys(false)) {
                Optional<BlockKey> helper = BlockKey.parsePathKey(rawHelper);
                if (helper.isEmpty()) {
                    continue;
                }
                Set<BlockKey> owners = new HashSet<>();
                for (String rawOwner : yaml.getStringList("helpers." + rawHelper)) {
                    BlockKey.parse(rawOwner).filter(sources::contains).ifPresent(owners::add);
                }
                if (!owners.isEmpty()) {
                    helperOwners.put(helper.get(), owners);
                }
            }
        }
        plugin.getLogger().info("Loaded " + sources.size() + " Odin's Light source blocks.");
    }

    private void applyHelpers(BlockKey source) {
        for (BlockKey helper : helperPositions(source)) {
            applyHelper(source, helper);
        }
    }

    private void applyHelper(BlockKey source, BlockKey helper) {
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

    private void cleanupHelpers(BlockKey source) {
        for (BlockKey helper : helperPositions(source)) {
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

    private List<BlockKey> helperPositions(BlockKey source) {
        List<BlockKey> positions = new ArrayList<>();
        int[] horizontalOffsets = {-12, -6, 0, 6, 12};
        int[] verticalOffsets = {-3, 1, 5};
        for (int dy : verticalOffsets) {
            for (int dx : horizontalOffsets) {
                for (int dz : horizontalOffsets) {
                    // Bounded disc layers: 38 helpers maximum, close enough for broad light without chunk spam.
                    if ((dx * dx) + (dz * dz) > 144 || (dx == 0 && dz == 0 && dy == 1)) {
                        continue;
                    }
                    positions.add(new BlockKey(source.worldName(), source.x() + dx, source.y() + dy, source.z() + dz));
                }
            }
        }
        return positions;
    }
}
