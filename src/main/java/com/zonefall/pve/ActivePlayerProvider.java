package com.zonefall.pve;

import java.util.Set;
import java.util.UUID;

/**
 * Supplies active combatants for arena-scoped PvE pressure.
 */
public interface ActivePlayerProvider {
    Set<UUID> participants();

    boolean isActiveParticipant(UUID playerId);
}

