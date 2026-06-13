package com.alucard.heirloomsword;

import com.alucard.heirloomsword.network.ManaSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Owns the player's mana pool — the mod's single resource for warp and all flying-mode
 * actions (CHARGING / SWEEPING_HOLD / BLOCKING drains + guard break). Epic Fight stamina is
 * a separate system used only for normal-mode greatsword combat and is never touched here.
 *
 * Server-authoritative. The current value is mirrored to the owning client via
 * {@link ManaSyncPacket} (for the HUD and client-side prediction); {@link ClientManaState}
 * holds that client copy.
 */
public final class ManaService {
    private ManaService() {}

    // All [TUNE] — kept cheap. Folded into the Phase 13 config pass later.
    public static final float MAX_MANA = 100f;
    public static final float REGEN_PER_TICK = 0.6f;        // 12 / sec
    public static final int   REGEN_PAUSE_TICKS = 20;        // 1 s pause after any spend
    public static final float CHARGE_DRAIN_PER_TICK = 0.75f; // 15 / sec
    public static final float SWEEP_DRAIN_PER_TICK  = 0.40f; // 8 / sec
    public static final float BLOCK_DRAIN_PER_TICK  = 0.50f; // 10 / sec
    public static final float WARP_COST = 10f;
    public static final float MIN_CHARGE = 10f;
    public static final float MIN_SWEEP  = 10f;
    public static final float MIN_BLOCK  = 10f;
    public static final int   LOCKOUT_TICKS = 40;            // 2 s punishment after running dry

    public static float get(Player player) {
        return player.getData(ManaAttachments.MANA.get());
    }

    public static boolean hasAtLeast(Player player, float amount) {
        return get(player) >= amount;
    }

    /** True while the depletion lockout is active — all sword inputs (except mode toggle) are rejected. */
    public static boolean isLockedOut(Player player) {
        return getLockout(player) > 0;
    }

    public static int getLockout(Player player) {
        return player.getData(ManaAttachments.LOCKOUT.get());
    }

    /** Deduct {@code amount} (clamped at 0) and pause regen. */
    public static void spend(Player player, float amount) {
        setMana(player, get(player) - amount);
        player.setData(ManaAttachments.REGEN_DELAY.get(), REGEN_PAUSE_TICKS);
    }

    /** Spend the full amount only if available. Returns true if spent. */
    public static boolean trySpend(Player player, float amount) {
        if (!hasAtLeast(player, amount)) return false;
        spend(player, amount);
        return true;
    }

    /**
     * Per-tick drain for a held action. Returns true if mana remains (the action may
     * continue), false if the pool is now empty (the caller stops the action).
     */
    public static boolean drain(Player player, float perTick) {
        float remaining = get(player) - perTick;
        boolean depleted = remaining <= 0f;
        if (depleted) {
            // Punish running dry: freeze at 0, hold regen, lock out inputs. Lockout supersedes
            // the normal regen pause, so clear REGEN_DELAY and let LOCKOUT be the sole gate.
            player.setData(ManaAttachments.LOCKOUT.get(), LOCKOUT_TICKS);
            player.setData(ManaAttachments.REGEN_DELAY.get(), 0);
        } else {
            player.setData(ManaAttachments.REGEN_DELAY.get(), REGEN_PAUSE_TICKS);
        }
        setMana(player, remaining); // setMana carries the (now-updated) lockout to the client
        return !depleted;
    }

    /** Called every tick while the player possesses the sword. Handles the lockout and regen pause. */
    public static void tickRegen(Player player) {
        int lockout = getLockout(player);
        if (lockout > 0) {
            int next = lockout - 1;
            player.setData(ManaAttachments.LOCKOUT.get(), next);
            // On the final lockout tick, re-sync so the client clears its lockout even though
            // mana is still 0 (no whole-unit change would otherwise trigger a sync).
            if (next == 0 && player instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new ManaSyncPacket(get(player), 0));
            }
            return; // mana frozen at 0 during the punishment
        }
        int delay = player.getData(ManaAttachments.REGEN_DELAY.get());
        if (delay > 0) {
            player.setData(ManaAttachments.REGEN_DELAY.get(), delay - 1);
            return;
        }
        float current = get(player);
        if (current < MAX_MANA) {
            setMana(player, current + REGEN_PER_TICK);
        }
    }

    private static void setMana(Player player, float value) {
        float clamped = Mth.clamp(value, 0f, MAX_MANA);
        float old = get(player);
        player.setData(ManaAttachments.MANA.get(), clamped);
        if (player instanceof ServerPlayer sp && shouldSync(old, clamped)) {
            PacketDistributor.sendToPlayer(sp, new ManaSyncPacket(clamped, getLockout(player)));
        }
    }

    /** Bound packet traffic: sync on whole-unit changes and on the empty/full boundaries. */
    private static boolean shouldSync(float oldV, float newV) {
        if (oldV == newV) return false;
        return Mth.floor(oldV) != Mth.floor(newV) || newV <= 0f || newV >= MAX_MANA;
    }
}
