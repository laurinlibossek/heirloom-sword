package com.alucard.heirloomsword;

/**
 * Client-side cached mana for the local player, written by {@code ManaSyncPacket} and read
 * by the HUD and input prediction. Plain field only (no client-only types) so it links on
 * both sides; only ever mutated on the client.
 */
public class ClientManaState {
    public static float current = ManaService.MAX_MANA;
}
