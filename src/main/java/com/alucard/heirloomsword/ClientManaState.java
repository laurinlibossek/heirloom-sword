package com.alucard.heirloomsword;

/**
 * Client-side cached mana for the local player, written by {@code ManaSyncPacket} and read
 * by the HUD and input prediction. Plain field only (no client-only types) so it links on
 * both sides; only ever mutated on the client.
 */
public class ClientManaState {
    // Literal (not ManaService.maxMana()) so class init can never touch config before it loads;
    // the server syncs the real value within a tick of the sword mattering anyway.
    public static float current = 100f;
    // Depletion-lockout ticks remaining; mirrors the server timer and is also counted down
    // locally each client tick. While > 0, all sword inputs except the mode toggle are blocked.
    public static int lockoutTicks = 0;
    // Mode-switch cooldown ticks remaining; gates re-entry INTO flying mode only. Fully
    // client-predicted: set when the client predicts an exit and counted down locally, mirroring
    // the server timer in SwordModePacket so re-entry isn't mispredicted (which would stick, since
    // the server never re-syncs an unchanged slot). Exiting flying mode is never gated.
    public static int modeSwitchCooldownTicks = 0;
}
