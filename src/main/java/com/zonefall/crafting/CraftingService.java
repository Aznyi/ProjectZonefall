package com.zonefall.crafting;

import java.util.UUID;

public interface CraftingService {
    boolean canCraftPrototypeItem(UUID playerId, String recipeId);
}

