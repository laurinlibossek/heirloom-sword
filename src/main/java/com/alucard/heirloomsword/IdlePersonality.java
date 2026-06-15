package com.alucard.heirloomsword;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Cosmetic idle "personality" for the sword familiar, active only within HOVERING.
 *
 * <p>Server-authoritative: the server runs the timers/selection and writes the synced
 * descriptor ({@code DATA_IDLE_ANIM} id + {@code DATA_CURIOSITY_POS}) that the client reads
 * to reproduce the same {@code targetPosition} offset and pick the matching clip. Purely
 * visual — no damage, no interaction, no new {@link FamiliarState}.
 *
 * <p>All constants are {@code [TUNE]} and bound for the Phase 13 {@code idle} config section.
 */
public final class IdlePersonality {

    // Idle-anim ids — must match the switch in SwordFamiliarEntity#animationPredicate.
    public static final int ANIM_NONE = 0;
    public static final int ANIM_CURIOUS = 1;
    public static final int ANIM_FIGURE_EIGHT = 2;
    public static final int ANIM_GUARD = 3;   // held guard pose toward a trapped chest

    // [TUNE]
    private static final int CURIOSITY_TRIGGER_TICKS = 100;    // 5s idle before curious drift
    private static final double CURIOSITY_RANGE = 4.0;          // block scan radius
    private static final double CURIOSITY_MIN_DRIFT = 1.0;
    private static final double CURIOSITY_MAX_DRIFT = 2.0;
    private static final int CURIOSITY_HOLD_TICKS = 50;         // 2.5s inspection hold
    private static final int GUARD_GESTURE_TICKS = 20;          // 1s guard hold for a trapped chest
    private static final int CURIOSITY_COOLDOWN_TICKS = 600;    // 30s before it cares about any block again
    private static final int FIGURE_EIGHT_TRIGGER_TICKS = 400;  // 20s idle before figure-eight
    private static final double FIGURE_EIGHT_SPEED = 0.04;
    private static final double FIGURE_EIGHT_WIDTH = 0.6;
    private static final double FIGURE_EIGHT_BOB = 0.2;
    private static final double RECOIL_RANGE = 3.0;
    private static final double RECOIL_NUDGE = 0.5;
    private static final double IDLE_MOVE_EPSILON_SQR = 0.003 * 0.003;

    private static final TagKey<Block> CURIOSITIES = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "curiosities"));

    private final SwordFamiliarEntity sword;

    // Server-only timing/selection state.
    private int idleTicks = 0;
    private int curiosityHeldTicks = 0;
    private int gestureTicks = 0;
    private boolean curiosityConsumedThisPeriod = false;
    private Vec3 lastOwnerPos = null;
    private boolean recoilLatched = false;
    private boolean wasRaining = false;
    private long lastTickedAt = Long.MIN_VALUE;
    private long curiosityReadyAt = Long.MIN_VALUE; // game-time gate; survives idle-period resets
    private boolean curiousIsTrappedChest = false;

    public IdlePersonality(SwordFamiliarEntity sword) {
        this.sword = sword;
    }

    /**
     * Called every HOVERING tick from {@link SwordFamiliarEntity#tickHovering}, after the base
     * target anchor is set and before spring physics runs.
     */
    public void tick(Player owner) {
        if (sword.level().isClientSide) {
            applyClientOffset();
        } else {
            tickServer(owner);
        }
    }

    // ---- Client: reproduce the server's offset from synced data (no timers) ----
    private void applyClientOffset() {
        switch (sword.getIdleAnim()) {
            case ANIM_CURIOUS, ANIM_GUARD -> sword.getCuriosityPos().ifPresent(pos ->
                    sword.addIdleOffset(curiosityOffset(Vec3.atCenterOf(pos))));
            case ANIM_FIGURE_EIGHT -> sword.addIdleOffset(figureEightOffset());
            default -> { /* plain idle — no offset */ }
        }
    }

    // ---- Server: authoritative timers + selection ----
    private void tickServer(Player owner) {
        long now = sword.tickCount;
        if (now - lastTickedAt > 1) {
            // (Re)entered HOVERING after a gap — start a fresh idle period.
            reset();
            lastOwnerPos = null;
        }
        lastTickedAt = now;

        if (shouldCancel(owner)) {
            reset();
            lastOwnerPos = owner.position();
            return;
        }
        lastOwnerPos = owner.position();
        idleTicks++;

        // One-shot environmental reactions — independent of the 5s/20s thresholds.
        handleReactions();

        // Curious look-hold and the trapped-chest guard beat both pin the sword at the block.
        int phase = sword.getIdleAnim();
        if (phase == ANIM_CURIOUS) {
            tickCuriousHold();
            return;
        }
        if (phase == ANIM_GUARD) {
            tickGuardHold();
            return;
        }

        // Start a curious inspection: one block, once per idle period, and not during the
        // 30s cooldown that follows any inspection (so it won't fixate on the same spot).
        if (!curiosityConsumedThisPeriod
                && idleTicks >= CURIOSITY_TRIGGER_TICKS
                && sword.level().getGameTime() >= curiosityReadyAt) {
            BlockPos block = findCuriosityBlock();
            if (block != null) {
                curiosityConsumedThisPeriod = true;
                curiosityReadyAt = sword.level().getGameTime() + CURIOSITY_COOLDOWN_TICKS;
                curiousIsTrappedChest = sword.level().getBlockState(block).is(Blocks.TRAPPED_CHEST);
                curiosityHeldTicks = 0;
                sword.setIdleAnim(ANIM_CURIOUS);
                sword.setCuriosityPos(Optional.of(block));
                sword.addIdleOffset(curiosityOffset(Vec3.atCenterOf(block)));
                return;
            }
        }

        // Lazy figure-eight after extended idle.
        if (idleTicks >= FIGURE_EIGHT_TRIGGER_TICKS) {
            if (sword.getIdleAnim() != ANIM_FIGURE_EIGHT) {
                sword.setIdleAnim(ANIM_FIGURE_EIGHT);
            }
            sword.addIdleOffset(figureEightOffset());
        }
    }

    private void tickCuriousHold() {
        curiosityHeldTicks++;
        Optional<BlockPos> target = sword.getCuriosityPos();
        if (target.isEmpty()) {
            endInspection();
            return;
        }
        sword.addIdleOffset(curiosityOffset(Vec3.atCenterOf(target.get())));
        if (curiosityHeldTicks >= CURIOSITY_HOLD_TICKS) {
            if (curiousIsTrappedChest) {
                // Stay pinned at the chest and raise a guard pose for a beat ("don't open that").
                gestureTicks = 0;
                sword.setIdleAnim(ANIM_GUARD);
            } else {
                endInspection();
            }
        }
    }

    /** Trapped-chest guard beat: hold the guard pose at the chest, then drift home. */
    private void tickGuardHold() {
        gestureTicks++;
        sword.getCuriosityPos().ifPresent(pos ->
                sword.addIdleOffset(curiosityOffset(Vec3.atCenterOf(pos))));
        if (gestureTicks >= GUARD_GESTURE_TICKS) {
            endInspection();
        }
    }

    /** End the inspection: clear gesture state and idle anim; the spring eases the sword home. */
    private void endInspection() {
        curiousIsTrappedChest = false;
        sword.setIdleAnim(ANIM_NONE);
        sword.setCuriosityPos(Optional.empty());
    }

    private boolean shouldCancel(Player owner) {
        if (sword.getAwarenessTarget() != null) return true;
        return lastOwnerPos != null
                && owner.position().distanceToSqr(lastOwnerPos) > IDLE_MOVE_EPSILON_SQR;
    }

    private void handleReactions() {
        // Recoil from primed TNT — once per stimulus (re-arm only when none in range).
        AABB box = sword.getBoundingBox().inflate(RECOIL_RANGE);
        List<PrimedTnt> tnt = sword.level().getEntitiesOfClass(PrimedTnt.class, box);
        if (!tnt.isEmpty()) {
            if (!recoilLatched) {
                recoilLatched = true;
                sword.triggerIdleAnim("idle_recoil");
                Vec3 away = sword.position().subtract(tnt.get(0).position());
                if (away.lengthSqr() > 1.0E-4) {
                    sword.addIdleOffset(away.normalize().scale(RECOIL_NUDGE));
                }
            }
        } else {
            recoilLatched = false;
        }

        // Perk on the dry -> raining transition.
        boolean raining = sword.level().isRaining();
        if (raining && !wasRaining) {
            sword.triggerIdleAnim("idle_perk");
        }
        wasRaining = raining;
    }

    private Vec3 curiosityOffset(Vec3 blockCenter) {
        Vec3 dir = blockCenter.subtract(sword.position());
        double dist = dir.length();
        if (dist < 1.0E-4) return Vec3.ZERO;
        double drift = Math.min(Math.max(dist, CURIOSITY_MIN_DRIFT), CURIOSITY_MAX_DRIFT);
        drift = Math.min(drift, dist); // never overshoot the block itself
        return dir.normalize().scale(drift);
    }

    private Vec3 figureEightOffset() {
        double t = sword.level().getGameTime() * FIGURE_EIGHT_SPEED;
        return new Vec3(
                Math.sin(t) * FIGURE_EIGHT_WIDTH,
                Math.sin(t * 0.5) * FIGURE_EIGHT_BOB,
                Math.sin(2.0 * t) * FIGURE_EIGHT_WIDTH);
    }

    private BlockPos findCuriosityBlock() {
        BlockPos center = sword.blockPosition();
        int r = (int) Math.ceil(CURIOSITY_RANGE);
        double bestSqr = CURIOSITY_RANGE * CURIOSITY_RANGE;
        BlockPos best = null;
        Vec3 origin = sword.position();
        Level level = sword.level();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.getBlockState(m).is(CURIOSITIES)) continue;
                    double dsq = Vec3.atCenterOf(m).distanceToSqr(origin);
                    if (dsq <= bestSqr) {
                        bestSqr = dsq;
                        best = m.immutable();
                    }
                }
            }
        }
        return best;
    }

    /**
     * Clear per-period idle state and the synced descriptor. Server-side only (called from
     * tickServer). Note {@code curiosityReadyAt} is deliberately NOT cleared — the 30s
     * curiosity cooldown must survive idle-period resets (moving away and back).
     */
    public void reset() {
        idleTicks = 0;
        curiosityHeldTicks = 0;
        gestureTicks = 0;
        curiosityConsumedThisPeriod = false;
        curiousIsTrappedChest = false;
        recoilLatched = false;
        if (sword.getIdleAnim() != ANIM_NONE) {
            sword.setIdleAnim(ANIM_NONE);
        }
        if (sword.getCuriosityPos().isPresent()) {
            sword.setCuriosityPos(Optional.empty());
        }
    }
}
