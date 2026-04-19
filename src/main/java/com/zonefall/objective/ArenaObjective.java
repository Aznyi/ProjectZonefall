package com.zonefall.objective;

import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Mutable per-round state for an authored objective.
 */
public final class ArenaObjective {
    private final ObjectiveDefinition definition;
    private boolean active;
    private boolean revealed;
    private boolean completed;
    private int progress;
    private Material originalMaterial;

    public ArenaObjective(ObjectiveDefinition definition) {
        this.definition = definition;
    }

    public ObjectiveDefinition definition() {
        return definition;
    }

    public void resetForRound() {
        active = true;
        revealed = definition.revealMode() == ObjectiveRevealMode.ROUND_START;
        completed = false;
        progress = 0;
        originalMaterial = null;
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            revealed = false;
        }
    }

    public boolean revealed() {
        return revealed;
    }

    public boolean completed() {
        return completed;
    }

    public int progress() {
        return progress;
    }

    public void reveal() {
        revealed = true;
        applyPresentation();
    }

    public void incrementProgress() {
        progress++;
    }

    public void complete() {
        completed = true;
    }

    public void applyPresentation() {
        if (definition.type() != ObjectiveType.HIGH_VALUE || !active || !revealed) {
            return;
        }
        Block block = definition.location().toLocation().getBlock();
        if (originalMaterial == null) {
            originalMaterial = block.getType();
        }
        block.setType(definition.markerMaterial());
    }

    public void clearPresentation() {
        if (originalMaterial == null) {
            return;
        }
        definition.location().toLocation().getBlock().setType(originalMaterial);
        originalMaterial = null;
    }

    public String displayState() {
        if (!active) {
            return "DORMANT";
        }
        if (completed) {
            return "COMPLETED";
        }
        if (revealed) {
            return "ACTIVE";
        }
        return "HIDDEN";
    }

    public String describe() {
        String target = definition.type() == ObjectiveType.SCAVENGE
                ? " progress=" + Math.min(progress, definition.progressTarget()) + "/" + definition.progressTarget()
                : "";
        return definition.id()
                + " name=\"" + definition.displayName() + "\""
                + " type=" + definition.type()
                + " active=" + active
                + " reveal=" + definition.revealMode()
                + " state=" + displayState()
                + target
                + " marker=" + definition.markerMaterial()
                + " rewardTable=" + (definition.rewardTable().isBlank() ? "none" : definition.rewardTable())
                + " reward=" + definition.rewardBundle().describe()
                + " extractBonusMultiplier=" + definition.extractBonusMultiplier()
                + " effects=" + definition.effects();
    }
}
