package com.zonefall.customblock;

import org.bukkit.Material;

public enum CustomLightBlockType {
    ODINS_LIGHT("zonefall:odins_light", "odins_light", "Odin's Light", Material.TINTED_GLASS),
    BAMBOO_LIGHT("zonefall:bamboo_light", "bamboo_light", "Bamboo Light", Material.BAMBOO_PLANKS);

    private final String internalId;
    private final String itemId;
    private final String displayName;
    private final Material material;

    CustomLightBlockType(String internalId, String itemId, String displayName, Material material) {
        this.internalId = internalId;
        this.itemId = itemId;
        this.displayName = displayName;
        this.material = material;
    }

    public String internalId() {
        return internalId;
    }

    public String itemId() {
        return itemId;
    }

    public String displayName() {
        return displayName;
    }

    public Material material() {
        return material;
    }

    public static CustomLightBlockType fromItemId(String itemId) {
        for (CustomLightBlockType type : values()) {
            if (type.itemId.equals(itemId)) {
                return type;
            }
        }
        return null;
    }
}
