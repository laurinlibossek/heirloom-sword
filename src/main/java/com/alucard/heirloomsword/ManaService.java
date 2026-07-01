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

    // Config-backed (mana section). Read per-use, like the combat values in SwordFamiliarEntity,
    // so a config edit on world reload applies without a restart.
    public static float maxMana()            { return (float) Config.MAX_MANA.getAsDouble(); }
    public static float regenPerTick()       { return (float) Config.REGEN_PER_TICK.getAsDouble(); }
    public static int   regenPauseTicks()    { return Config.REGEN_PAUSE_TICKS.getAsInt(); }
    public static float chargeDrainPerTick() { return (float) Config.CHARGE_DRAIN_PER_TICK.getAsDouble(); }
    public static float sweepDrainPerTick()  { return (float) Config.SWEEP_DRAIN_PER_TICK.getAsDouble(); }
    public static float blockDrainPerTick()  { return (float) Config.BLOCK_DRAIN_PER_TICK.getAsDouble(); }
    public static float warpCost()           { return (float) Config.WARP_COST.getAsDouble(); }
    public static float launchCost()         { return (float) Config.LAUNCH_COST.getAsDouble(); }
    public static float tetherCost()         { return (float) Config.TETHER_COST.getAsDouble(); }
    public static float recallCost()         { return (float) Config.RECALL_COST.getAsDouble(); }
    public static float minCharge()          { return (float) Config.MIN_CHARGE.getAsDouble(); }
    public static float minSweep()           { return (float) Config.MIN_SWEEP.getAsDouble(); }
    public static float minBlock()           { return (float) Config.MIN_BLOCK.getAsDouble(); }
    public static int   lockoutTicks()       { return Config.LOCKOUT_TICKS.getAsInt(); }

    /**
     * True when mana costs do not apply to this player: either {@code combat.consumeMana=false}
     * (mana disabled server-wide) or the player is in creative. Bypasses every cost, gate, and
     * the depletion lockout.
     */
    public static boolean isExempt(Player player) {
        return !Config.CONSUME_MANA.getAsBoolean() || player.getAbilities().instabuild || player.isSpectator();
    }

    public static float get(Player player) {
        return player.getData(ManaAttachments.MANA.get());
    }

    public static boolean hasAtLeast(Player player, float amount) {
        return isExempt(player) || get(player) >= amount;
    }

    /** True while the depletion lockout is active — all sword inputs (except mode toggle) are rejected. */
    public static boolean isLockedOut(Player player) {
        return !isExempt(player) && getLockout(player) > 0;
    }

    public static int getLockout(Player player) {
        return player.getData(ManaAttachments.LOCKOUT.get());
    }

    /** Deduct {@code amount} (clamped at 0) and pause regen. */
    public static void spend(Player player, float amount) {
        if (isExempt(player)) return; // creative — no cost
        setMana(player, get(player) - amount);
        player.setData(ManaAttachments.REGEN_DELAY.get(), regenPauseTicks());
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
        if (isExempt(player)) return true; // creative — never drains, never depletes
        float remaining = get(player) - perTick;
        boolean depleted = remaining <= 0f;
        if (depleted) {
            // Punish running dry: freeze at 0, hold regen, lock out inputs. Lockout supersedes
            // the normal regen pause, so clear REGEN_DELAY and let LOCKOUT be the sole gate.
            player.setData(ManaAttachments.LOCKOUT.get(), lockoutTicks());
            player.setData(ManaAttachments.REGEN_DELAY.get(), 0);
        } else {
            player.setData(ManaAttachments.REGEN_DELAY.get(), regenPauseTicks());
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
        if (current < maxMana()) {
            setMana(player, current + regenPerTick());
        }
    }

    private static void setMana(Player player, float value) {
        float clamped = Mth.clamp(value, 0f, maxMana());
        float old = get(player);
        player.setData(ManaAttachments.MANA.get(), clamped);
        if (player instanceof ServerPlayer sp && shouldSync(old, clamped)) {
            PacketDistributor.sendToPlayer(sp, new ManaSyncPacket(clamped, getLockout(player)));
        }
    }

    /** Bound packet traffic: sync on whole-unit changes and on the empty/full boundaries. */
    private static boolean shouldSync(float oldV, float newV) {
        if (oldV == newV) return false;
        return Mth.floor(oldV) != Mth.floor(newV) || newV <= 0f || newV >= maxMana();
    }
}
