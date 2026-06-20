package com.alucard.heirloomsword;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of which players are currently displaying a sheathed sword on their back,
 * written by {@code BackSheathSyncPacket} and read by {@code BackSheathLayer}. Plain data (no
 * client-only types) so it links on both sides; only ever mutated on the client.
 *
 * <p>The server is authoritative: it computes the full render decision (display preference is on,
 * the player owns a NORMAL-mode sword, and isn't holding it) and syncs a single boolean per player.
 * The client cannot see other players' inventories, so this synced flag is the only signal the
 * layer can rely on.
 */
public final class BackSheathClientState {
    private BackSheathClientState() {}

    private static final Set<UUID> WEARING = ConcurrentHashMap.newKeySet();

    public static void set(UUID id, boolean wearing) {
        if (wearing) {
            WEARING.add(id);
        } else {
            WEARING.remove(id);
        }
    }

    public static boolean isWearing(UUID id) {
        return WEARING.contains(id);
    }
}
