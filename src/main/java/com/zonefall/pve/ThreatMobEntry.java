package com.zonefall.pve;

import org.bukkit.entity.EntityType;

/**
 * One arena-authored hostile mob entry.
 */
public record ThreatMobEntry(EntityType type, int weight, ThreatPhase minimumPhase) {
    public String describe() {
        return type + "(weight=" + weight + ", min=" + minimumPhase + ")";
    }
}
