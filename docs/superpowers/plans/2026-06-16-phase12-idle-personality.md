# Phase 12 — Idle Personality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the sword familiar cosmetic idle personality (curious drift, lazy figure-eight, primed-TNT recoil, rain perk) inside the existing HOVERING state, with zero gameplay effect.

**Architecture:** A new server-authoritative `IdlePersonality` helper owns all idle state/logic and is ticked from `SwordFamiliarEntity#tickHovering`. It applies a position offset to the existing `targetPosition` (the spring physics carries the sword there and back) and writes a compact synced descriptor (`DATA_IDLE_ANIM` int + `DATA_CURIOSITY_POS` `Optional<BlockPos>`) so the client reproduces the identical offset and picks the matching GeckoLib clip. One-shots (recoil/perk) reuse the existing `triggerAnim("action", …)` controller.

**Tech Stack:** NeoForge 1.21.1, Java 21, GeckoLib 4.x, `SynchedEntityData`, block tags.

**Spec:** `docs/superpowers/specs/2026-06-16-phase12-idle-personality-design.md`

---

## Testing note (read first)

This codebase has **no unit-test harness** for entity behavior — Phases 10 and 11 were verified by *compile + in-game matrix*, and this plan follows that established pattern. The per-task gate is therefore **"`./gradlew build` succeeds"** (the build compiles all Java and validates resources), and the behavioral test is the manual in-game matrix in Task 5. Do not invent a JUnit/GameTest harness; it would be inconsistent with the project. On Windows use `./gradlew` from Git Bash (works) or `gradlew.bat` from cmd.

All four animation clips (`idle_curious`, `idle_figure_eight`, `idle_recoil`, `idle_perk`) already exist in `src/main/resources/assets/heirloomswordmod/animations/alucard_sword.animation.json` — no animation authoring is required.

---

## Scope & Guardrails

- **Purely cosmetic.** No damage, no interaction, no new `FamiliarState`. Everything lives inside HOVERING.
- **Server authoritative.** Timers/selection/offset computed server-side; client only reproduces offset + picks clip from synced data.
- **Recoil = primed TNT only** (fire/lava removed — the entity/item are fire-immune). As-built deviation from design doc L447–449.
- **Position-only cancellation.** WASD movement / mob-in-range / combat input cancel idle; camera rotation does not.
- All numeric constants are `[TUNE]` and live as named constants in `IdlePersonality` — **no config wiring** (that is Phase 13).

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `src/main/resources/data/heirloomswordmod/tags/block/curiosities.json` | Datapack tag of "notable" blocks the sword inspects | **Create** |
| `src/main/java/com/alucard/heirloomsword/IdlePersonality.java` | All idle state + logic (timers, selection, offset math, one-shots) | **Create** |
| `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` | Synced fields + accessors, idle field, `tickHovering` call, anim predicate, controllers | **Modify** |

---

## Task 1: Curiosities block tag

**Files:**
- Create: `src/main/resources/data/heirloomswordmod/tags/block/curiosities.json`

- [ ] **Step 1: Create the tag file**

Mirror the existing `pierceable.json` format (no `replace` key; defaults to false so datapacks can extend).

```json
{
  "values": [
    "minecraft:chest",
    "minecraft:trapped_chest",
    "minecraft:crafting_table",
    "minecraft:brewing_stand",
    "minecraft:enchanting_table",
    "minecraft:bookshelf"
  ]
}
```

- [ ] **Step 2: Build to confirm resources are valid**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. (The tag is read at runtime; this step just confirms nothing else broke.)

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/data/heirloomswordmod/tags/block/curiosities.json
git commit -m "feat(phase12): add curiosities block tag for idle curious-drift"
```

---

## Task 2: Entity scaffolding (synced data, accessors, hooks, predicate, controllers)

Adds everything the `IdlePersonality` helper will call, plus the animation wiring — but does **not** yet construct or tick the helper, so this task compiles on its own. Until Task 4 wires the tick, `DATA_IDLE_ANIM` stays 0 and the predicate always picks `idle` (no behavior change).

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

- [ ] **Step 1: Declare the two synced data accessors**

After the `DATA_QUICKFIRE_TARGET` declaration (currently `SwordFamiliarEntity.java:65-66`), add:

```java
    private static final EntityDataAccessor<Integer> DATA_IDLE_ANIM =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_CURIOSITY_POS =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
```

(`INT` is used for the idle-anim id to match the codebase's existing `DATA_STATE`/`DATA_QUICKFIRE_TARGET` convention; it stores only 0/1/2. `Optional` and `BlockPos` are already imported.)

- [ ] **Step 2: Register defaults in `defineSynchedData`**

In `defineSynchedData` (currently `SwordFamiliarEntity.java:252-259`), add to the builder block:

```java
        builder.define(DATA_IDLE_ANIM, 0);
        builder.define(DATA_CURIOSITY_POS, Optional.empty());
```

- [ ] **Step 3: Add the accessor + hook methods**

Place these next to the other accessors (e.g. just after `getAwarenessTarget()`, currently `SwordFamiliarEntity.java:1413-1416`):

```java
    // === Idle personality hooks (used by IdlePersonality) ===

    public int getIdleAnim() {
        return this.entityData.get(DATA_IDLE_ANIM);
    }

    public void setIdleAnim(int id) {
        this.entityData.set(DATA_IDLE_ANIM, id);
    }

    public Optional<BlockPos> getCuriosityPos() {
        return this.entityData.get(DATA_CURIOSITY_POS);
    }

    public void setCuriosityPos(Optional<BlockPos> pos) {
        this.entityData.set(DATA_CURIOSITY_POS, pos);
    }

    /** Nudge the hover target this tick; the spring physics carries the sword there and back. */
    public void addIdleOffset(Vec3 offset) {
        this.targetPosition = this.targetPosition.add(offset);
    }

    /** Fire a one-shot idle reaction clip on the shared "action" controller. */
    public void triggerIdleAnim(String clip) {
        triggerAnim("action", ANIM_PREFIX + clip);
    }
```

- [ ] **Step 4: Extend the HOVERING branch of `animationPredicate`**

Replace the HOVERING case (currently `SwordFamiliarEntity.java:1710`):

```java
            case HOVERING -> anim = awarenessTarget != null ? "alert" : "idle";
```

with:

```java
            case HOVERING -> {
                if (awarenessTarget != null) {
                    anim = "alert";
                } else {
                    anim = switch (getIdleAnim()) {
                        case 1 -> "idle_curious";
                        case 2 -> "idle_figure_eight";
                        default -> "idle";
                    };
                }
            }
```

- [ ] **Step 5: Register the two one-shot reaction clips**

In `registerControllers`, extend the `"action"` controller chain (currently ends `SwordFamiliarEntity.java:1702`) by adding two `triggerableAnim` calls before the closing `)`:

```java
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim(ANIM_PREFIX + "block_slash", RawAnimation.begin().thenPlay(ANIM_PREFIX + "block_slash"))
                .triggerableAnim(ANIM_PREFIX + "guard_break", RawAnimation.begin().thenPlay(ANIM_PREFIX + "guard_break"))
                .triggerableAnim(ANIM_PREFIX + "death_fall", RawAnimation.begin().thenPlay(ANIM_PREFIX + "death_fall"))
                .triggerableAnim(ANIM_PREFIX + "idle_recoil", RawAnimation.begin().thenPlay(ANIM_PREFIX + "idle_recoil"))
                .triggerableAnim(ANIM_PREFIX + "idle_perk", RawAnimation.begin().thenPlay(ANIM_PREFIX + "idle_perk")));
```

- [ ] **Step 6: Build to confirm it compiles**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. (Behavior is unchanged — nothing sets `DATA_IDLE_ANIM` yet.)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat(phase12): entity scaffolding for idle personality (synced data, hooks, anim wiring)"
```

---

## Task 3: `IdlePersonality` helper class

The full idle subsystem. References only the accessors added in Task 2, so it compiles against the current entity.

**Files:**
- Create: `src/main/java/com/alucard/heirloomsword/IdlePersonality.java`

- [ ] **Step 1: Create the helper class**

```java
package com.alucard.heirloomsword;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

    // [TUNE]
    private static final int CURIOSITY_TRIGGER_TICKS = 100;    // 5s idle before curious drift
    private static final double CURIOSITY_RANGE = 4.0;          // block scan radius
    private static final double CURIOSITY_MIN_DRIFT = 1.0;
    private static final double CURIOSITY_MAX_DRIFT = 2.0;
    private static final int CURIOSITY_HOLD_TICKS = 50;         // 2.5s inspection hold
    private static final int FIGURE_EIGHT_TRIGGER_TICKS = 200;  // 10s idle before figure-eight
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
    private boolean curiosityConsumedThisPeriod = false;
    private Vec3 lastOwnerPos = null;
    private boolean recoilLatched = false;
    private boolean wasRaining = false;
    private long lastTickedAt = Long.MIN_VALUE;

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
            case ANIM_CURIOUS -> sword.getCuriosityPos().ifPresent(pos ->
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

        // One-shot environmental reactions — independent of the 5s/10s thresholds.
        handleReactions();

        // Curious drift holds its block for the inspection window.
        if (sword.getIdleAnim() == ANIM_CURIOUS) {
            tickCuriousHold();
            return;
        }

        // Start a curious inspection: one block, once per idle period.
        if (!curiosityConsumedThisPeriod && idleTicks >= CURIOSITY_TRIGGER_TICKS) {
            BlockPos block = findCuriosityBlock();
            if (block != null) {
                curiosityConsumedThisPeriod = true;
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
        if (target.isEmpty() || curiosityHeldTicks >= CURIOSITY_HOLD_TICKS) {
            // Inspection over — drift back (spring eases home); stay idle so figure-eight can follow.
            sword.setIdleAnim(ANIM_NONE);
            sword.setCuriosityPos(Optional.empty());
            return;
        }
        sword.addIdleOffset(curiosityOffset(Vec3.atCenterOf(target.get())));
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

    /** Clear all idle state and the synced descriptor. Server-side only (called from tickServer). */
    public void reset() {
        idleTicks = 0;
        curiosityHeldTicks = 0;
        curiosityConsumedThisPeriod = false;
        recoilLatched = false;
        if (sword.getIdleAnim() != ANIM_NONE) {
            sword.setIdleAnim(ANIM_NONE);
        }
        if (sword.getCuriosityPos().isPresent()) {
            sword.setCuriosityPos(Optional.empty());
        }
    }
}
```

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. (The class compiles but is not yet referenced — Task 4 wires it in.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/IdlePersonality.java
git commit -m "feat(phase12): IdlePersonality helper (curious drift, figure-eight, TNT recoil, rain perk)"
```

---

## Task 4: Wire the helper into the entity

Connects everything: constructs the helper and ticks it inside HOVERING. After this task the feature is live.

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

- [ ] **Step 1: Add the helper field**

Next to the hover/physics instance fields (just after `private Vec3 targetPosition = Vec3.ZERO;`, currently `SwordFamiliarEntity.java:138`), add:

```java
    private final IdlePersonality idle = new IdlePersonality(this);
```

- [ ] **Step 2: Tick the helper in `tickHovering`**

Replace the body of `tickHovering` (currently `SwordFamiliarEntity.java:416-425`):

```java
    private void tickHovering(Player owner) {
        if (quickFireCooldown > 0) quickFireCooldown--;
        if (getGuardCooldown() > 0) setGuardCooldown(getGuardCooldown() - 1);
        updateTargetPosition(owner);
        idle.tick(owner);          // adds idle offset to targetPosition (no-op when not idle)
        applySpringPhysics();
        updateMobAwareness(owner);
        if (this.tickCount % 80 == 0) {
            SwordSounds.playHoverAmbient(this.level(), getX(), getY(), getZ());
        }
    }
```

(The idle offset is applied *after* the base anchor is computed and *before* the spring runs, so the spring smoothly carries the sword to the offset position and back.)

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat(phase12): tick IdlePersonality within HOVERING"
```

---

## Task 5: In-game verification & documentation

No code in this task — it is the behavioral test (the project's substitute for automated entity tests) plus doc/graph upkeep. Run `./gradlew runClient` and work the matrix.

- [ ] **Step 1: Run the client**

Run: `./gradlew runClient`
Get a sword, enter flying mode (F), and let the familiar settle into HOVERING.

- [ ] **Step 2: Verify the matrix**

1. **Curious drift:** Stand still ~5s next to a chest → sword drifts toward it, tilts (`idle_curious`), holds ~2.5s, drifts back. With a chest *and* a crafting table adjacent, only **one** is inspected (no ping-pong).
2. **Figure-eight:** Stand still ~10s with no curiosity block in range → smooth figure-eight (`idle_figure_eight`).
3. **Recoil:** Place + ignite TNT within 3 blocks → a single `idle_recoil` flinch away; it does **not** repeat while that TNT keeps burning.
4. **Perk:** Trigger rain (`/weather rain`) → a single `idle_perk`.
5. **Immunity:** Light fire / place lava within range → **no** reaction.
6. **Cancel — move:** During any idle behavior, press W → snaps back to hover instantly. Rotating the camera only (no movement) → idle continues.
7. **Cancel — mob:** Spawn a hostile in range mid-idle → `alert` overrides immediately and idle resets.
8. **Combat intact:** Charge, quick-fire, block, and recall all still work normally from HOVERING.
9. **Multiplayer (if feasible):** On a second client, idle motion is smooth and matches (no jitter) — confirms the sync model.

- [ ] **Step 3: Tune if needed**

Adjust the `[TUNE]` constants at the top of `IdlePersonality.java` (drift distances, timers, figure-eight amplitude/speed) and rebuild until the feel is right. Commit any changes:

```bash
git add src/main/java/com/alucard/heirloomsword/IdlePersonality.java
git commit -m "tune(phase12): idle personality feel pass"
```

- [ ] **Step 4: Mark Phase 12 DONE in the design doc**

In `docs/alucard_sword_design_v3.md`, mark the Phase 12 summary (L1283–1290) as DONE and add an as-built note: recoil triggers on **primed TNT only** (fire/lava removed — entity/item fire-immune); curious-block set is the `#heirloomswordmod:curiosities` tag; idle is server-authoritative via `DATA_IDLE_ANIM` + `DATA_CURIOSITY_POS`.

```bash
git add docs/alucard_sword_design_v3.md
git commit -m "docs(phase12): mark idle personality DONE with as-built notes"
```

- [ ] **Step 5: Update the knowledge graph**

Run: `graphify update .`
(AST-only, no API cost — keeps `graphify-out/` current per project rules.)

---

## Self-Review

**1. Spec coverage** — every spec section maps to a task:
- Architecture (Approach C helper, server-authoritative) → Task 3 + Task 4.
- Sync model (`DATA_IDLE_ANIM`, `DATA_CURIOSITY_POS`, game-time figure-eight, `triggerAnim` one-shots) → Task 2 (fields/accessors/controllers) + Task 3 (usage).
- Idle timer & position-only cancellation → `tickServer`/`shouldCancel` (Task 3).
- Curious drift (5s, nearest tagged block, 1–2 block clamp, 2.5s hold, one-per-period latch) → `tickServer`/`tickCuriousHold`/`findCuriosityBlock`/`curiosityOffset` (Task 3) + tag (Task 1).
- Figure-eight (10s, game-time Lissajous + bob) → `figureEightOffset` (Task 3).
- Recoil (primed TNT, 3 blocks, once-per-stimulus, fire/lava excluded) → `handleReactions` (Task 3).
- Perk (rain rising edge, once-per-transition) → `handleReactions` (Task 3).
- Animation predicate + controllers → Task 2.
- Block tag (`tags/block/curiosities.json`, no `replace`) → Task 1.
- `[TUNE]` constants, no config wiring → constants block in Task 3; Phase 13 noted out of scope.
- Verification matrix → Task 5.

**2. Placeholder scan** — no TBD/TODO/"handle edge cases"/"similar to". Every code step shows complete code.

**3. Type consistency** — accessor names match between definer (Task 2) and caller (Task 3): `getIdleAnim()`/`setIdleAnim(int)`, `getCuriosityPos()`/`setCuriosityPos(Optional<BlockPos>)`, `addIdleOffset(Vec3)`, `triggerIdleAnim(String)`, `getAwarenessTarget()` (pre-existing). Anim ids `ANIM_NONE/CURIOUS/FIGURE_EIGHT` = 0/1/2 match the predicate `switch` cases `1`/`2`/default (Task 2 Step 4). `DATA_IDLE_ANIM` is `INT` in both the declaration and the `int`-typed accessors. `DATA_CURIOSITY_POS` is `Optional<BlockPos>` consistently. `idle` field type `IdlePersonality` matches the class created in Task 3.
