package com.zonefall.objective;

import com.zonefall.extract.ExtractionManager;
import com.zonefall.loot.source.LootSourceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes objective completion effects against arena systems.
 */
public final class ObjectiveEffectExecutor {
    private final ExtractionManager extractionManager;
    private final LootSourceManager lootSourceManager;
    private final List<String> triggeredEffects = new ArrayList<>();
    private double extractBonusMultiplier = 1.0;

    public ObjectiveEffectExecutor(ExtractionManager extractionManager, LootSourceManager lootSourceManager) {
        this.extractionManager = extractionManager;
        this.lootSourceManager = lootSourceManager;
    }

    public List<String> apply(ObjectiveDefinition definition) {
        List<String> messages = new ArrayList<>();
        for (ObjectiveCompletionEffect effect : definition.effects()) {
            switch (effect) {
                case REVEAL_EXTRA_EXTRACTION -> extractionManager.activateAdditionalZone().ifPresent(description -> {
                    String message = "Extra extraction route revealed by " + definition.displayName() + ": " + description + ".";
                    triggeredEffects.add(message);
                    messages.add(message);
                });
                case ACTIVATE_BONUS_LOOT -> lootSourceManager.activateBonusSource().ifPresent(description -> {
                    String message = "Bonus loot source activated by " + definition.displayName() + ": " + description + ".";
                    triggeredEffects.add(message);
                    messages.add(message);
                });
                case EXTRACT_BONUS_MULTIPLIER -> {
                    extractBonusMultiplier = Math.max(extractBonusMultiplier, definition.extractBonusMultiplier());
                    String message = "Extraction bonus increased by " + definition.displayName()
                            + " to x" + String.format(java.util.Locale.ROOT, "%.2f", extractBonusMultiplier) + ".";
                    triggeredEffects.add(message);
                    messages.add(message);
                }
            }
        }
        return messages;
    }

    public void reset() {
        triggeredEffects.clear();
        extractBonusMultiplier = 1.0;
    }

    public double extractBonusMultiplier() {
        return extractBonusMultiplier;
    }

    public String debugSummary() {
        return "extractBonusMultiplier=" + String.format(java.util.Locale.ROOT, "%.2f", extractBonusMultiplier)
                + " triggered=" + (triggeredEffects.isEmpty() ? "none" : triggeredEffects.toString());
    }
}
