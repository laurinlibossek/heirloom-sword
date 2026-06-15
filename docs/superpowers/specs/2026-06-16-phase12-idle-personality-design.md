# Phase 12 — Idle Personality: Design Spec

**Status:** Approved design, pre-implementation
**Date:** 2026-06-16
**Design doc:** `docs/alucard_sword_design_v3.md` §"Idle Personality Behaviors" (L426–454), tuning table L1122–1126, Phase 12 summary L1283–1290

---

## 1. Goal

Give the sword familiar contextual *personality* while it hovers idle — curious drift toward nearby blocks, a lazy figure-eight, a flinch from primed TNT, and an alert perk-up when rain starts. Purely cosmetic: no new `FamiliarState`, no damage, no interaction, no gameplay effect. All behavior lives **inside** the existing HOVERING state and is pre-empted instantly by anything combat-relevant.

The four GeckoLib clips already exist in `alucard_sword.animation.json`: `idle_curious`, `idle_figure_eight`, `idle_recoil`, `idle_perk` (plus `idle` and `alert`). No art-pass dependency.

## 2. Architecture

**Approach C — extracted `IdlePersonality` helper, server-authoritative.**

A new `IdlePersonality.java` owns all idle state and logic. `SwordFamiliarEntity` holds one instance (`private final IdlePersonality idle = new IdlePersonality(this);`) and calls `idle.tick(owner)` from `tickHovering`. This keeps the ~1700-line entity readable and matches CLAUDE.md's "composition over inheritance" / "one class per concern" guidance. Idle is sub-behavior, not a state, so it does not get a `FamiliarState` entry.

**Server is authoritative.** Curious drift and figure-eight are real position moves (the familiar's hitbox actually approaches the block), so timers, behavior selection, and the position offset are computed server-side. `tickHovering` runs on **both** client and server (the client runs spring physics locally for smooth rendering), so the client reproduces the *same* offset from synced data to keep prediction matched. This mirrors how the entity already drives looping anims via the synced `DATA_STATE` int and one-shots via `triggerAnim`.

### 2.1 Sync model

| Concern | Mechanism |
|---|---|
| Which looping idle clip is active | New synced byte `DATA_IDLE_ANIM` (0 = plain `idle`, 1 = `idle_curious`, 2 = `idle_figure_eight`). Drives the animation predicate **and** tells the client which position offset to apply. |
| Curious drift target | New synced `Optional<BlockPos>` `DATA_CURIOSITY_POS`. Server writes the chosen block; client drifts toward the identical block so offsets match. Cleared (empty) when not inspecting. |
| Figure-eight path | No sync. Path is a pure function of `level().getGameTime()`, identical on both sides. |
| Recoil / perk one-shots | Existing `triggerAnim("action", ANIM_PREFIX + "idle_recoil" / "idle_perk")` (same controller as `block_slash` / `guard_break`; GeckoLib auto-syncs to tracking clients). |

### 2.2 Server vs client split inside `IdlePersonality.tick(owner)`

- **Server (`!level.isClientSide`):** authoritative. Runs the idle timer, cancellation checks, behavior selection, environmental scans. Writes `DATA_IDLE_ANIM` / `DATA_CURIOSITY_POS`, fires `triggerAnim` one-shots, and applies the position offset to the entity's `targetPosition`.
- **Client:** reads `DATA_IDLE_ANIM` + `DATA_CURIOSITY_POS` and applies the matching offset to its local `targetPosition` so predicted motion matches the server. Runs **no** timers, makes **no** selections, fires **no** triggers.

## 3. Behavior model

### 3.1 Idle timer & cancellation (server-side)

An `idleTicks` counter increments each server HOVERING tick. It **resets to 0** — ending the idle period — when any cancel condition holds:

- **Owner moved positionally:** compare `owner.position()` against the previous tick's stored position; horizontal+vertical delta `> IDLE_MOVE_EPSILON` (≈0.003) → reset. Camera rotation does **not** reset (per design decision: the familiar may daydream while the player looks around).
- **Mob in awareness range:** `getAwarenessTarget() != null` (already maintained by `updateMobAwareness`, every 5 ticks).
- **Combat input:** any charge/quick-fire/block/recall already transitions the entity out of HOVERING, so `IdlePersonality` only runs while HOVERING — no extra check needed.

On reset: `idleTicks = 0`, `DATA_IDLE_ANIM = 0`, `DATA_CURIOSITY_POS = empty`, `curiosityConsumedThisPeriod = false`, and any active offset is dropped (the spring eases the sword back to its anchor naturally — no snap).

### 3.2 Curious drift

- **Trigger:** `idleTicks >= CURIOSITY_TRIGGER_TICKS` (100t / 5s) **and** `!curiosityConsumedThisPeriod` **and** a block tagged `#heirloomswordmod:curiosities` is within `CURIOSITY_RANGE` (4 blocks) of the familiar.
- **Selection:** scan the 4-block cube around the familiar; pick the **nearest** tagged block. Set `DATA_CURIOSITY_POS` to it, `DATA_IDLE_ANIM = 1`, `curiosityConsumedThisPeriod = true`. **One inspection per idle period** — the latch prevents re-scanning and ping-ponging between equidistant blocks.
- **Drift:** offset = direction from the hover anchor to the block center, clamped to `[CURIOSITY_MIN_DRIFT, CURIOSITY_MAX_DRIFT]` (1–2 blocks). Added to `targetPosition`; the existing spring eases the sword over. The `idle_curious` clip supplies the inquisitive tilt (animation, not position).
- **Hold:** maintain for `CURIOSITY_HOLD_TICKS` (50t / 2.5s).
- **Return:** clear `DATA_CURIOSITY_POS`, set `DATA_IDLE_ANIM = 0`. The spring eases the sword home. `idleTicks` keeps counting, so figure-eight can follow if the player stays idle.

### 3.3 Lazy figure-eight

- **Trigger:** `idleTicks >= FIGURE_EIGHT_TRIGGER_TICKS` (200t / 10s) and not currently in a curious inspection. Set `DATA_IDLE_ANIM = 2`.
- **Path:** horizontal-plane figure-eight (Lissajous) plus a gentle vertical bob, parameterised by world game time so both sides agree:
  ```
  double t = level.getGameTime() * FIGURE_EIGHT_SPEED;
  offset = new Vec3(
      Math.sin(t)       * FIGURE_EIGHT_WIDTH,   // X
      Math.sin(t * 0.5) * FIGURE_EIGHT_BOB,     // Y gentle bob
      Math.sin(2 * t)   * FIGURE_EIGHT_WIDTH    // Z (2× frequency → figure-eight)
  );
  ```
  Added to `targetPosition`; the spring keeps it smooth.
- **Persistence:** continues until a cancel condition ends the idle period.

### 3.4 Environmental reactions (one-shots)

Fire independently of drift/figure-eight; do not require the 5s/10s idle threshold (they can punctuate any HOVERING moment), but still respect the awareness/movement cancels via the normal flow.

- **Recoil (primed TNT only):** if a `PrimedTnt` entity is within `RECOIL_RANGE` (3 blocks) and no recoil is currently latched → `triggerAnim("action", ANIM_PREFIX + "idle_recoil")` + a brief backward `targetPosition` nudge (~0.5 block away from the TNT). Latched so it fires **once per stimulus**: re-arm only after no primed TNT is in range.
  - *Rationale (as-built deviation from design L447–449):* the original spec also recoiled from fire/lava. Removed — the familiar entity is already fire-immune and the sword item will gain netherite-esque fire immunity; an indestructible artifact has no reason to flinch from flame. TNT stays because the sword still avoids being *hit* by the blast.
- **Perk (rain start):** detect a dry→raining transition (`level.isRaining()` rising edge, tracked via `wasRaining`) → `triggerAnim("action", ANIM_PREFIX + "idle_perk")` + a brief upward tilt pause. Fires once per transition.

## 4. Animation wiring

Extend the HOVERING branch of `animationPredicate` (currently L1710, `case HOVERING -> anim = awarenessTarget != null ? "alert" : "idle";`):

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

`alert` (mob in range) always wins over idle clips — combat readiness is never gated by personality. Recoil/perk play on the separate `"action"` controller (registered in `registerControllers`, L1696–1702) layered over the base idle, so no predicate change is needed for them.

## 5. Block tag

`src/main/resources/data/heirloomswordmod/tags/block/curiosities.json` (NeoForge 1.21.1 tag dir is `tags/block/`, matching the existing pierceable tag), seeded from design L436–437:

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

No `replace` key (defaults to false, so datapacks can extend it) — matching the existing `pierceable.json` exactly. Created by hand, no datagen wiring required. The tag is read via `TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MODID, "curiosities"))`, mirroring `PIERCEABLE_BLOCKS` (`SwordFamiliarEntity.java:95`).

## 6. Files touched

| File | Change |
|---|---|
| `IdlePersonality.java` | **Create.** Helper class: idle state fields, `tick(Player)`, server/client split, behavior selection, offset math, one-shot triggers. |
| `SwordFamiliarEntity.java` | Add `DATA_IDLE_ANIM` byte + `DATA_CURIOSITY_POS` `Optional<BlockPos>` synched accessors (define in `defineSynchedData` L252); add `idle` field; call `idle.tick(owner)` in `tickHovering` (L416) after `updateTargetPosition`/`updateMobAwareness`, before `applySpringPhysics`; extend `animationPredicate` HOVERING branch (L1710); add package-private accessors the helper needs (`targetPosition` get/add-offset, `getAwarenessTarget` exists). |
| `data/heirloomswordmod/tags/block/curiosities.json` | **Create.** Block tag (§5). |

No new packets (idle is server-internal, never client-triggered). No new `FamiliarState`. No new animation controller. No config wiring (Phase 13).

## 7. Tuning constants (all `[TUNE]` → Phase 13 `idle` config section)

| Constant | Value | Meaning |
|---|---|---|
| `CURIOSITY_TRIGGER_TICKS` | 100 (5s) | Idle before curious drift |
| `CURIOSITY_RANGE` | 4.0 | Block scan radius |
| `CURIOSITY_MIN_DRIFT` / `CURIOSITY_MAX_DRIFT` | 1.0 / 2.0 | Drift distance clamp toward block |
| `CURIOSITY_HOLD_TICKS` | 50 (2.5s) | Inspection hold before drift-back |
| `FIGURE_EIGHT_TRIGGER_TICKS` | 200 (10s) | Idle before figure-eight |
| `FIGURE_EIGHT_SPEED` | 0.04 | Game-time multiplier (path speed) |
| `FIGURE_EIGHT_WIDTH` | 0.6 | Horizontal amplitude |
| `FIGURE_EIGHT_BOB` | 0.2 | Vertical bob amplitude |
| `RECOIL_RANGE` | 3.0 | Primed-TNT detection radius |
| `IDLE_MOVE_EPSILON` | 0.003 | Owner position delta that counts as "moving" |

## 8. Verification (manual, in-game — Task in the plan)

1. Stand still 5s near a chest → sword drifts toward it, tilts (`idle_curious`), holds ~2.5s, drifts back. Only one block inspected even with two adjacent.
2. Stand still 10s with no curiosity block → figure-eight traces smoothly; matches between two clients (multiplayer) / no jitter.
3. Place + ignite TNT within 3 blocks → single `idle_recoil` flinch away; does not repeat while the same TNT burns.
4. Start of rain → single `idle_perk`.
5. Light fire / lava bucket within range → **no** reaction (immunity).
6. During any idle behavior: move (WASD) → snaps back instantly; rotate camera only → idle continues.
7. Spawn a hostile in range mid-idle → `alert` immediately overrides; idle resets.
8. Confirm no behavior change when a combat input is given (charge/quick-fire/block/recall still work normally from HOVERING).
9. Tune pass on the constants; then update `docs/alucard_sword_design_v3.md` (mark Phase 12 DONE + recoil deviation note) and run `graphify update .`.

## 9. Out of scope

- Config wiring (Phase 13 `idle` section consumes these `[TUNE]` constants).
- Any gameplay effect from idle behaviors — strictly cosmetic.
- Sounds for idle behaviors (none specified; the audio pass can add them later if desired).
- Fire/lava recoil (deliberately removed, §3.4).
