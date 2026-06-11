# Sword Mechanics, State Glitches & Functional Guarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Heirloom Sword a real weapon (vanilla sword mechanics, 12 damage, netherite cooldown), fix the four familiar-entity glitches (launch orientation, stuck-state rotation, sweep-hold jitter via sawblade spin, collision-avoidance stickiness), and implement functional guarding (frontal damage negation, projectile deflection, damaging block slash) per design doc v3 §BLOCKING — minus Epic Fight stamina, which is Phase 9.

**Architecture:** All gameplay changes are server-authoritative in `SwordFamiliarEntity` / `SwordEventHandler`; client gets new synced data (launch direction) and pure-visual state (sawblade spin angle). The item becomes a `SwordItem` subclass so vanilla attack/sweep/cooldown logic applies for free. No new entities (the arrow-glue idea is rejected — the launch glitch is just an unsynced field).

**Tech Stack:** NeoForge 21.1.233 (MC 1.21.1, Java 21), GeckoLib 4.8.4.

---

## Context for the implementer

- **⚠️ PRECONDITION — commit the existing working tree FIRST.** As of plan creation (2026-06-11) the entire Phase 8 GeckoLib implementation (renderers, geo model, animations, textures, entity changes — `git status` shows ~10 modified + ~8 untracked paths) is **uncommitted**. The plan's file/line references describe this uncommitted state, not HEAD. Before starting Task 1, create a baseline commit of everything currently in the tree, e.g.:
  ```powershell
  git add -A
  git commit -m "Phase 8 WIP: GeckoLib model, renderers, animations, render alignment fixes"
  ```
  Never use `git checkout --`/`git restore` on these files before that baseline exists — you would destroy unrecoverable Phase 8 work. (`.continue/` is IDE config; include or exclude at your discretion.)
- **Project rules:** Read `CLAUDE.md` in the repo root. Per those rules, verify any Minecraft/NeoForge signature you are unsure about with the mcmodding-mcp tools (`search_mappings`, `get_class_details`, `search_mod_examples`) for MC **1.21.1** before writing code. The code in this plan was written against 1.21.1 Mojang/Parchment mappings but signatures must be confirmed if compilation fails.
- **No automated test harness exists** in this project (Minecraft client behavior). Each task verifies via `gradlew.bat build` (compile gate) plus a targeted in-game check listed in the task. Run the game with `gradlew.bat runClient`. Commit after each task passes its checks.
- **After each task's code change**, run `graphify update .` (project rule, AST-only, cheap).
- **Design doc:** `docs/alucard_sword_design_v3.md`. The BLOCKING spec is at the `### State: BLOCKING` section (~line 651). Values marked `[TUNE]` are free to adjust later; everything else is final.
- **User decisions already made (do not re-ask):**
  - Guarding scope: full v3 spec **minus stamina/guard-break-by-stamina** (that needs Epic Fight, Phase 9). So: frontal damage negation, projectile interception + geometric deflection, block_slash deals damage on release.
  - `block_slash` animation: redesign from scratch (Task 7 contains the new keyframes).
  - Sawblade spin for SWEEPING_HOLD: approved.

### Root-cause notes (already diagnosed — trust these, don't re-derive)

1. **Launch orientation glitch:** `launchDirection` is a plain field set only in server-side `launch()`. The client's copy stays `Vec3.ZERO`, so the client's `updateOrientation()` computes `atan2(-0, 0) = 0` → yaw 0 → blade points at +Z regardless of launch direction. Fix = sync the vector via `SynchedEntityData` (Task 4). The "spawn an arrow and glue the sword to it" idea is explicitly rejected: extra entity, despawn edge cases, no benefit.
2. **Stuck rotation glitch:** `FamiliarState.STUCK` has no branch in `updateOrientation()`, so it falls through to the `else` branch which copies `owner.getYRot()` every tick — the embedded sword turns with the player's head. Fix = freeze orientation in STUCK (Task 5).
3. **Sweep-hold jitter:** orientation in SWEEPING_HOLD is derived from the noisy per-tick `sweepVelocity` vector, which flips direction constantly under the spring physics. Fix = stop deriving orientation from velocity in HOLD; render a continuous flat sawblade spin instead (Task 6). SWEEPING_RELEASE keeps velocity-based orientation (velocity is committed and smooth there).
4. **Collision avoidance stickiness:** `updateTargetPosition()` scans candidates starting at `currentCandidateIndex`, so once the sword lands on a fallback spot (e.g. top), and that spot stays free, it never re-tests the preferred spots. Fix = recurring re-check with hysteresis (Task 3).
5. **Old block_slash "doesn't flow":** the clip ends with keyframe `"0.5": [0,0,0]` coming from `"0.42": [0,-170,90]` — GeckoLib interpolates yaw 170° **backwards** in 0.08 s (violent reverse spin), and the position arc reaches ±47 model units (≈3 blocks of teleporting). Task 7 replaces it with a forward-completing 360° arc and a ≤9-unit position arc.

---

## File Map

| File | Tasks | Responsibility of change |
|---|---|---|
| `src/main/java/com/alucard/heirloomsword/HeirloomSwordItem.java` | 1 | Become a `SwordItem` (vanilla weapon behavior, 12 dmg, netherite speed, unbreakable) |
| `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` | 2,3,4,5,6,10,11 | Hover distance, candidate re-check, launch-dir sync, STUCK freeze, spin state + disc hitbox, block-slash damage, guard-entry helpers |
| `src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java` | 6 | Sawblade spin rendering for SWEEPING_HOLD |
| `src/main/resources/assets/heirloomswordmod/animations/alucard_sword.animation.json` | 7 | New `block_slash` keyframes |
| `src/main/java/com/alucard/heirloomsword/SwordEventHandler.java` | 8,9 | Frontal damage negation + projectile deflection while BLOCKING |
| `src/main/java/com/alucard/heirloomsword/network/SwordGuardPacket.java` | 11 | Allow guard entry from CHARGING / SWEEPING_HOLD |
| `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java` | 11 | Client-side guard entry from CHARGING / SWEEPING_HOLD |

Tasks 1–7 are independent bug fixes/features; Tasks 8–11 build the guard feature and should run in order. Task 12 is the final end-to-end verification.

---

### Task 1: Vanilla sword mechanics — 12 damage, netherite cooldown, sweep

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordItem.java`

The item currently extends plain `Item`, so it has no attack damage, no attack-speed cooldown, and never triggers vanilla sweep attacks. Extending `SwordItem` fixes all three at once: vanilla's `Player.attack()` sweep check and NeoForge's `ItemAbilities.SWORD_SWEEP` both key off `SwordItem`, and the cooldown comes from the `ATTACK_SPEED` attribute.

Damage math: total displayed attack damage = player base (1.0) + modifier from `SwordItem.createAttributes(tier, damage, speed)` where the modifier is `damage + tier.getAttackDamageBonus()`. Netherite's bonus is 4.0, so pass **7** → 7 + 4 + 1 = **12**. Attack speed **-2.4f** gives final attack speed 1.6 — identical cooldown to a netherite sword.

Durability: `TieredItem`'s constructor applies `properties.durability(tier.getUses())`, which would give the item a durability bar. The project rule is **no durability**, so add the `UNBREAKABLE` data component (with `showInTooltip = false`) — `ItemStack.isDamageableItem()` returns false when that component is present, so the item never takes damage and shows no bar.

- [ ] **Step 1: Change the class declaration and constructor**

Replace the class declaration and constructor in `HeirloomSwordItem.java` (currently lines 18–27):

```java
public class HeirloomSwordItem extends SwordItem implements GeoItem {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public HeirloomSwordItem() {
        super(Tiers.NETHERITE, new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant()
                // 7 + netherite bonus 4 + player base 1 = 12 attack damage.
                // -2.4f attack speed = 1.6 final, identical cooldown to a netherite sword.
                .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 7, -2.4f))
                // TieredItem force-applies netherite durability; UNBREAKABLE suppresses it
                // entirely (no damage taken, no bar). false = no "Unbreakable" tooltip line.
                .component(DataComponents.UNBREAKABLE, new Unbreakable(false))
                .component(ModDataComponents.SWORD_MODE.get(), SwordMode.NORMAL));
    }
```

Add imports:

```java
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.Unbreakable;
```

The rest of the class (GeoItem plumbing, enchantment blocks, mode helpers) stays exactly as is. `HeirloomSwordMod.HEIRLOOM_SWORD` registration (`ITEMS.register("heirloom_sword", HeirloomSwordItem::new)`) is unchanged — the constructor signature didn't change.

- [ ] **Step 2: Compile**

Run: `gradlew.bat build`
Expected: BUILD SUCCESSFUL. If `createAttributes` or `Unbreakable` don't resolve, verify the 1.21.1 signatures via mcmodding-mcp `get_class_details` on `net.minecraft.world.item.SwordItem` and `net.minecraft.world.item.component.Unbreakable` and adjust.

- [ ] **Step 3: In-game check**

Run: `gradlew.bat runClient`
- Tooltip shows "12 Attack Damage" and "1.6 Attack Speed" under "When in Main Hand".
- No durability bar appears after hitting mobs; item never breaks.
- Attacking while standing still + full cooldown on a group of mobs triggers the vanilla sweep particle/AoE.
- Cooldown indicator behaves like a netherite sword (compare side by side in creative).
- Flying mode still toggles with F and left/right-click are still suppressed while flying.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/HeirloomSwordItem.java
git commit -m "feat: vanilla sword mechanics - 12 damage, netherite cooldown, sweep"
```

---

### Task 2: Move the hover position slightly further from the player

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` (line 55)

The hitbox follows the entity position automatically (`makeBoundingBox()` is position-relative), so only the radius constant changes.

- [ ] **Step 1: Bump the radius**

```java
    private static final double HOVER_RADIUS = 1.8; // [TUNE] was 1.5 — sword felt too close
```

- [ ] **Step 2: Compile**

Run: `gradlew.bat build` → BUILD SUCCESSFUL.

- [ ] **Step 3: In-game check**

The hovering sword sits noticeably (but only slightly) further out at the player's right; F3+B shows its hitbox moved with it. Charging side-position also uses the same candidates, so it moves out equally — that is intended.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "tune: hover radius 1.5 -> 1.8"
```

---

### Task 3: Collision avoidance — recurring return to the preferred spot

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` (`updateTargetPosition`, ~line 727)

Candidate priority is 0=right, 1=left, 2=back, 3=top, 4=front. The existing scan starts at `currentCandidateIndex`, so the sword never returns to a freed-up better spot. Add a recurring check: while parked on a non-preferred candidate, watch for any **lower-index** candidate to be unobstructed; once one has stayed clear for 10 consecutive ticks (hysteresis — prevents flip-flopping when strafing along a wall), move back to it.

- [ ] **Step 1: Add the hysteresis fields**

Next to `private int currentCandidateIndex = 0;` (line 96):

```java
    private int preferredFreeTicks = 0;
    private static final int PREFERRED_RETURN_DELAY_TICKS = 10; // [TUNE]
```

- [ ] **Step 2: Add the re-check at the top of `updateTargetPosition`**

Replace the method:

```java
    private void updateTargetPosition(Player owner) {
        // Recurring check: drift back to the most-preferred free candidate once it has
        // stayed unobstructed for a short while (hysteresis avoids flip-flopping).
        if (currentCandidateIndex != 0) {
            int best = -1;
            for (int i = 0; i < currentCandidateIndex; i++) {
                if (!isPositionObstructed(computeCandidatePosition(owner, i))) {
                    best = i;
                    break;
                }
            }
            if (best >= 0) {
                preferredFreeTicks++;
                if (preferredFreeTicks >= PREFERRED_RETURN_DELAY_TICKS) {
                    currentCandidateIndex = best;
                    preferredFreeTicks = 0;
                }
            } else {
                preferredFreeTicks = 0;
            }
        }

        for (int i = 0; i < 5; i++) {
            int candidateIdx = (currentCandidateIndex + i) % 5;
            Vec3 candidate = computeCandidatePosition(owner, candidateIdx);
            if (!isPositionObstructed(candidate)) {
                if (candidateIdx != currentCandidateIndex) {
                    currentCandidateIndex = candidateIdx;
                }
                targetPosition = candidate;
                return;
            }
        }

        if (!this.level().isClientSide()) {
            exitFlyingMode(owner);
        }
    }
```

Note: `tickHovering` runs on both server and client, and these are per-side instance fields, so both sides converge on the same candidate independently — same as the existing behavior.

- [ ] **Step 3: Compile**

Run: `gradlew.bat build` → BUILD SUCCESSFUL.

- [ ] **Step 4: In-game check**

Reproduce the user's exact repro: stand so the sword collides right → left → back → top in sequence (e.g. back into a corner, turn). Sword ends on top. Then step away so the right side is clear: within ~half a second the sword glides back to the right-side spot. Verify it does **not** vibrate between spots when standing right at a wall edge.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "fix: familiar returns to preferred hover spot when it frees up"
```

---

### Task 4: Launch orientation — sync the launch vector to the client

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

Sync `launchDirection` via `SynchedEntityData` (`EntityDataSerializers.VECTOR3`, JOML `Vector3f` — the serializer used by display entities). All reads (client tick prediction + orientation on both sides) go through a getter that reads the synced value, so client and server can never disagree. Entity-data writes batch into the same sync packet as the state change, so the client sees the direction and the LAUNCHING state atomically.

- [ ] **Step 1: Add the synced accessor**

Next to the existing accessors (lines 48–53):

```java
    private static final EntityDataAccessor<Vector3f> DATA_LAUNCH_DIR =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.VECTOR3);
```

Import: `import org.joml.Vector3f;`

In `defineSynchedData` (line 149) add:

```java
        builder.define(DATA_LAUNCH_DIR, new Vector3f());
```

- [ ] **Step 2: Write it in `launch()` and snap the rotation immediately**

Replace `launch()` (lines 364–371):

```java
    public void launch(Vec3 direction, boolean charged) {
        removeChargeSlowdown();
        this.launchDirection = direction.normalize();
        this.entityData.set(DATA_LAUNCH_DIR, this.launchDirection.toVector3f());
        this.launchOrigin = this.position();
        this.chargedLaunch = charged;
        this.outboundHitSet.clear();
        // Snap orientation now so the first rotation the client receives already matches
        this.setYRot((float) Math.toDegrees(Math.atan2(-launchDirection.x, launchDirection.z)));
        double horizDist = Math.sqrt(launchDirection.x * launchDirection.x + launchDirection.z * launchDirection.z);
        this.setXRot((float) -Math.toDegrees(Math.atan2(launchDirection.y, horizDist)));
        setState(FamiliarState.LAUNCHING);
    }

    public Vec3 getLaunchDirection() {
        Vector3f v = this.entityData.get(DATA_LAUNCH_DIR);
        return new Vec3(v.x(), v.y(), v.z());
    }
```

- [ ] **Step 3: Read the synced value everywhere the client needs it**

In `tickLaunchingClient()` (lines 420–424) — this method previously moved by `launchDirection` which was always zero on the client (client prediction silently never worked; only server position sync moved it):

```java
    private void tickLaunchingClient() {
        Vec3 dir = getLaunchDirection();
        if (dir.lengthSqr() < 1.0e-4) return; // data not synced yet this tick
        double speed = chargedLaunch ? LAUNCH_SPEED_CHARGED : LAUNCH_SPEED_NORMAL;
        this.setPos(this.position().add(dir.scale(speed)));
    }
```

In `updateOrientation()`, replace the LAUNCHING branch (lines 985–988):

```java
        } else if (getState() == FamiliarState.LAUNCHING) {
            Vec3 dir = getLaunchDirection();
            if (dir.lengthSqr() > 1.0e-4) {
                targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                double horizDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                targetPitch = (float) -Math.toDegrees(Math.atan2(dir.y, horizDist));
            }
```

(If the vector is still zero — one-tick race on the client — keep the current rotation instead of snapping to +Z.)

- [ ] **Step 4: Compile**

Run: `gradlew.bat build` → BUILD SUCCESSFUL. If `EntityDataSerializers.VECTOR3` or `Vec3.toVector3f()` don't resolve, verify via mcmodding-mcp `get_class_details` on `net.minecraft.network.syncher.EntityDataSerializers` and `net.minecraft.world.phys.Vec3`.

- [ ] **Step 5: In-game check**

Launch the sword (charged and uncharged) toward **negative Z**, **positive X**, **negative X**, and steeply up/down. In every case the blade tip leads in the flight direction the whole way — no mid-air flip, no snap to +Z. Also confirm the flight looks smoother than before (client prediction now actually works).

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "fix: sync launch direction to client; sword keeps launch orientation"
```

---

### Task 5: Freeze orientation while STUCK

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` (`updateOrientation`, ~line 960)

- [ ] **Step 1: Early-return for STUCK**

In `updateOrientation()`, immediately after the `horizontalProgress` update block (after the line `horizontalProgress = Mth.approach(horizontalProgress, targetProgress, 0.2f);`), insert:

```java
        if (currentState == FamiliarState.STUCK) {
            // Embedded in a block — keep exactly the orientation it had on impact.
            if (horizontal) {
                this.setBoundingBox(makeBoundingBox());
            }
            return;
        }
```

(The bounding-box refresh is kept so the rotated AABB stays valid; yaw/pitch are simply never written.)

- [ ] **Step 2: Compile**

Run: `gradlew.bat build` → BUILD SUCCESSFUL.

- [ ] **Step 3: In-game check**

Launch the sword into a wall so it sticks. Spin the camera in circles, walk around the sword. The stuck sword must not move or rotate at all until the 3 s timeout (or R recall) pulls it back. Verify recall and timeout both still work and the return flight orients hilt-first as before.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "fix: stuck sword no longer rotates with player view"
```

---

### Task 6: Sweep hold — sawblade spin instead of jittery velocity orientation

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`
- Modify: `src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java`

Concept: while SWEEPING_HOLD, the sword lies flat (blade in the horizontal plane) and spins continuously around the vertical axis like a sawblade. The spin is **pure client visual state** (a float angle ticked client-side and lerped with partialTick); the entity's synced yaw stays pinned to the owner's yaw so nothing jitters. The hitbox becomes a flat disc covering the spin circle. The existing per-tick contact damage (`sweepDamageEntities`) already fits a sawblade — its hit box just widens to the blade radius. SWEEPING_RELEASE is untouched: on release the velocity is committed and smooth, so tip-first velocity orientation stays correct there.

- [ ] **Step 1: Add spin state to the entity**

Next to the SWEEPING state fields (~line 115):

```java
    // Sawblade spin while SWEEPING_HOLD — client-side visual only
    private float spinAngle = 0.0f;
    private float spinAngleO = 0.0f;
    private static final float SWEEP_SPIN_DEG_PER_TICK = 40.0f; // [TUNE] ~2.2 rev/s

    public float getSpinAngle(float partialTick) {
        return spinAngleO + (spinAngle - spinAngleO) * partialTick;
    }
```

- [ ] **Step 2: Tick the spin on the client**

At the top of `tickSweepingHoldClient(Player owner)` (line 562), insert:

```java
        spinAngleO = spinAngle;
        spinAngle += SWEEP_SPIN_DEG_PER_TICK;
        if (spinAngleO >= 360.0f) { // wrap both together so the partialTick lerp never jumps
            spinAngle -= 360.0f;
            spinAngleO -= 360.0f;
        }
```

- [ ] **Step 3: Stop deriving HOLD orientation from sweepVelocity**

In `updateOrientation()`, the combined branch currently reads:

```java
        } else if (getState() == FamiliarState.SWEEPING_HOLD || getState() == FamiliarState.SWEEPING_RELEASE) {
```

Split it — SWEEPING_HOLD gets a stable orientation, SWEEPING_RELEASE keeps the existing velocity logic:

```java
        } else if (getState() == FamiliarState.SWEEPING_HOLD) {
            // Sawblade spin (renderer-side) is the visual; keep synced rotation stable.
            Player owner = getOwner();
            if (owner != null) targetYaw = owner.getYRot();
            targetPitch = 0;
        } else if (getState() == FamiliarState.SWEEPING_RELEASE) {
            Vec3 dir = this.sweepVelocity.lengthSqr() > 0.05 ? this.sweepVelocity : null;
            if (dir == null) {
                Player owner = getOwner();
                if (owner != null) dir = owner.getLookAngle();
            }
            if (dir != null) {
                // Tip points along the travel direction. During the release return phase
                // sweepVelocity is stored reversed, so the hilt leads automatically.
                targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                double horizDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                targetPitch = (float) -Math.toDegrees(Math.atan2(dir.y, horizDist));
            }
```

- [ ] **Step 4: Disc hitbox for SWEEPING_HOLD**

In `makeBoundingBox()` (line 923), after the BLOCKING special case, add:

```java
        if (getState() == FamiliarState.SWEEPING_HOLD) {
            // Spinning sawblade: flat disc covering the blade's spin circle.
            return new AABB(
                    pos.x - SWORD_HALF_LENGTH, pos.y - SWORD_HALF_THICKNESS, pos.z - SWORD_HALF_LENGTH,
                    pos.x + SWORD_HALF_LENGTH, pos.y + SWORD_HALF_THICKNESS, pos.z + SWORD_HALF_LENGTH);
        }
```

- [ ] **Step 5: Widen contact damage to the blade radius**

In `sweepDamageEntities()` (line 581), change the box inflation from 0.5 on all axes to blade-radius horizontally:

```java
        AABB sweepBox = new AABB(
                Math.min(from.x, to.x) - SWORD_HALF_LENGTH, Math.min(from.y, to.y) - 0.5, Math.min(from.z, to.z) - SWORD_HALF_LENGTH,
                Math.max(from.x, to.x) + SWORD_HALF_LENGTH, Math.max(from.y, to.y) + 0.5, Math.max(from.z, to.z) + SWORD_HALF_LENGTH
        );
```

- [ ] **Step 6: Render the spin**

In `SwordFamiliarGeoRenderer.preRender()`, after the spawn-scale block and **before** the existing yaw `mulPose`, add a dedicated SWEEPING_HOLD path that replaces the yaw/pitch logic entirely:

```java
        float hProgress = animatable.getHorizontalProgress(partialTick);

        if (animatable.getState() == FamiliarState.SWEEPING_HOLD) {
            // Sawblade: continuous yaw spin, blade flattened into the horizontal plane.
            poseStack.mulPose(Axis.YP.rotationDegrees(animatable.getSpinAngle(partialTick)));
            poseStack.mulPose(Axis.XP.rotationDegrees(hProgress * 90.0f));
            poseStack.translate(0, -MODEL_CENTER_Y * hProgress, 0);
            return;
        }

        float yaw = Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));

        if (hProgress > 0.0f) {
            if (animatable.getState() != FamiliarState.BLOCKING) {
                float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
                poseStack.mulPose(Axis.XP.rotationDegrees(hProgress * (90.0f - pitch)));
            }

            poseStack.translate(0, -MODEL_CENTER_Y * hProgress, 0);
        }
```

(The original `hProgress` declaration further down must be removed since it moves up — the rest of the method is unchanged.)

- [ ] **Step 7: Compile**

Run: `gradlew.bat build` → BUILD SUCCESSFUL.

- [ ] **Step 8: In-game check**

Hold right-click: the sword flattens and spins like a sawblade in front of you — smooth continuous rotation, **no jitter or flipping**, even while flicking the mouse to feed it momentum. Mobs touching the disc take repeated contact damage with knockback. F3+B shows the flat disc hitbox. Release right-click: the sword flies out tip-first along the flung direction and returns hilt-first as before (release behavior unchanged). The brief orientation snap at the HOLD→RELEASE transition is acceptable (it reads as "the blade flies off the spin").

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java
git commit -m "feat: sawblade spin for sweep hold; fixes orientation jitter"
```

---

### Task 7: Redesign the block_slash animation

**Files:**
- Modify: `src/main/resources/assets/heirloomswordmod/animations/alucard_sword.animation.json` (the `animation.alucard_sword.block_slash` entry)

Why the old clip felt bad (two concrete defects):
1. Yaw goes 0 → -170 across the slash, then the final keyframe `"0.5": [0,0,0]` makes GeckoLib interpolate **+170° backwards in 0.08 s** — a violent reverse spin right at the end.
2. The position arc reaches **±47 model units ≈ 3 blocks** of root translation in linear lerp — the sword teleports around rather than sweeping.

New design: anticipation wind-up → wide horizontal arc with the blade flat → follow-through that completes a full forward 360° (ending at `[0, -360, 0]` ≡ identity, so the rotation never reverses and the final pose blends seamlessly into the vertical hover idle). Position arc stays ≤ 9 units (~0.55 blocks) and uses catmull-rom smoothing.

- [ ] **Step 1: Replace the clip**

Replace the entire `"animation.alucard_sword.block_slash"` object with:

```json
    "animation.alucard_sword.block_slash": {
      "loop": false,
      "animation_length": 0.7,
      "bones": {
        "root": {
          "rotation": {
            "0.0": [0, 0, 45],
            "0.14": { "post": [-8, 30, 85], "lerp_mode": "catmullrom" },
            "0.22": { "post": [0, -30, 90], "lerp_mode": "catmullrom" },
            "0.3": { "post": [0, -110, 90], "lerp_mode": "catmullrom" },
            "0.38": { "post": [0, -190, 90], "lerp_mode": "catmullrom" },
            "0.5": { "post": [0, -250, 70], "lerp_mode": "catmullrom" },
            "0.7": [0, -360, 0]
          },
          "position": {
            "0.0": [0, 0, 0],
            "0.14": { "post": [5, 1, -3], "lerp_mode": "catmullrom" },
            "0.22": { "post": [7, 0, 4], "lerp_mode": "catmullrom" },
            "0.3": { "post": [0, 0, 9], "lerp_mode": "catmullrom" },
            "0.38": { "post": [-7, 0, 4], "lerp_mode": "catmullrom" },
            "0.5": { "post": [-4, 0, 1], "lerp_mode": "catmullrom" },
            "0.7": [0, 0, 0]
          }
        }
      }
    }
```

Beat breakdown (for tuning): 0–0.14 s anticipation (cock to the right, +30 yaw, roll flattening 45→85); 0.14–0.38 s the slash (yaw +30 → -190, blade flat at roll 90, position arcs right→front→left); 0.38–0.7 s follow-through (yaw completes to -360 = home, roll unwinds 90→0, drifts back to center). All `[TUNE]` — keep the "end at -360, never reverse" invariant if retiming.

- [ ] **Step 2: Compile/resource check**

Run: `gradlew.bat build` → BUILD SUCCESSFUL (catches JSON syntax errors via processResources).

- [ ] **Step 3: In-game check**

Enter flying mode, hold G, release G. The sword winds up briefly, sweeps a wide flat arc across the front, and settles back into the vertical hover pose in one continuous motion — no reverse spin, no teleporting. Trigger it ~5 times from different camera angles. If the arc clips through the player visually, reduce the `"0.3"` position Z from 9 toward 6 `[TUNE]`.

- [ ] **Step 4: Commit**

```powershell
git add src/main/resources/assets/heirloomswordmod/animations/alucard_sword.animation.json
git commit -m "feat: redesigned block_slash animation - continuous forward arc"
```

---

### Task 8: Functional guarding — frontal damage negation

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordEventHandler.java`

Design doc: "Damage reduction: equivalent to a vanilla Minecraft shield. Frontal damage only. Does not block explosions. Does not block magic or area-of-effect damage." A vanilla shield fully negates blockable damage, so: cancel the damage event when the owner's familiar is BLOCKING, the source has a frontal position, and the damage type is physical. The frontal-cone test mirrors vanilla `Player#isDamageSourceBlocked`.

NeoForge 1.21.1 damage pipeline: use **`LivingIncomingDamageEvent`** (`net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent`) — it is cancellable and fires before armor/absorption. Verify the exact class via mcmodding-mcp if it doesn't resolve.

- [ ] **Step 1: Add the event handler**

Add to `SwordEventHandler` (it is already registered on `NeoForge.EVENT_BUS`):

```java
    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(player.serverLevel(), player.getUUID());
        if (familiar == null || familiar.getState() != FamiliarState.BLOCKING) return;

        DamageSource source = event.getSource();
        // Shield-equivalent: frontal physical damage only — no explosions, magic, or AoE.
        if (source.is(DamageTypeTags.BYPASSES_SHIELD)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }

        Vec3 sourcePos = source.getSourcePosition();
        if (sourcePos == null) return;

        // Frontal cone test, mirroring vanilla Player#isDamageSourceBlocked
        Vec3 toPlayer = sourcePos.vectorTo(player.position());
        toPlayer = new Vec3(toPlayer.x, 0.0, toPlayer.z).normalize();
        Vec3 look = player.getViewVector(1.0f);
        look = new Vec3(look.x, 0.0, look.z).normalize();
        if (toPlayer.dot(look) >= 0.0) return; // attack came from the side/behind

        event.setCanceled(true);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.9f);
    }
```

Imports to add:

```java
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
```

- [ ] **Step 2: Compile**

Run: `gradlew.bat build` → BUILD SUCCESSFUL.

- [ ] **Step 3: In-game check**

In flying mode, hold G while a zombie attacks you from the front: zero damage, shield-block sound plays. Let it hit you from behind while still guarding: damage goes through. Creeper explosion while guarding: damage goes through (not blocked). Release G / re-test without guarding: normal damage.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordEventHandler.java
git commit -m "feat: BLOCKING negates frontal physical damage (shield-equivalent)"
```

---

### Task 9: Projectile interception with geometric deflection

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordEventHandler.java`

Design doc: physical projectiles (arrows, thrown tridents, fireballs) aimed at the guarding player bounce off the blade's plane at reduced speed. The blade in `block_stance` faces the player's look direction, so the plane normal is the look vector; reflect with `v' = (v - 2(v·n)n) * 0.4`.

Use **`ProjectileImpactEvent`** (`net.neoforged.neoforge.event.entity.ProjectileImpactEvent`) — cancelling it makes the projectile continue flying with whatever velocity we set.

- [ ] **Step 1: Add the event handler**

```java
    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof ServerPlayer player)) return;

        Projectile projectile = event.getProjectile();
        // Physical projectiles only (spec): arrows + tridents (AbstractArrow), fireballs.
        // Magic projectiles, explosions, and area effects are not intercepted.
        boolean physical = projectile instanceof AbstractArrow || projectile instanceof Fireball;
        if (!physical) return;

        SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(player.serverLevel(), player.getUUID());
        if (familiar == null || familiar.getState() != FamiliarState.BLOCKING) return;

        // Same frontal cone as melee blocking
        Vec3 toPlayer = projectile.position().vectorTo(player.position());
        toPlayer = new Vec3(toPlayer.x, 0.0, toPlayer.z).normalize();
        Vec3 look = player.getViewVector(1.0f);
        look = new Vec3(look.x, 0.0, look.z).normalize();
        if (toPlayer.dot(look) >= 0.0) return;

        // Geometric deflection off the blade plane (normal = player look) at reduced speed.
        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 normal = player.getLookAngle();
        Vec3 reflected = velocity.subtract(normal.scale(2.0 * velocity.dot(normal))).scale(0.4); // [TUNE] speed factor

        event.setCanceled(true);
        projectile.setDeltaMovement(reflected);
        projectile.hurtMarked = true; // force velocity sync to clients
        if (reflected.lengthSqr() > 1.0e-4) {
            // Nudge out of the player's hitbox so it can't re-collide next tick
            projectile.setPos(projectile.position().add(reflected.normalize().scale(0.5)));
        }
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setOwner(player); // a deflected arrow belongs to the blocker now
        }

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.3f);
    }
```

Imports to add:

```java
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
```

- [ ] **Step 2: Compile**

Run: `gradlew.bat build` → BUILD SUCCESSFUL. If `getRayTraceResult()` doesn't exist on the 1.21.1 event, check the method name via mcmodding-mcp (`get_class_details` on `ProjectileImpactEvent`) — it has been `getRayTraceResult()` historically.

- [ ] **Step 3: In-game check**

Have a skeleton shoot you while guarding and facing it: arrows ping off at an angle (visibly slower) instead of hitting; no damage. Stop guarding: arrows hit normally. Face away while guarding: arrows from behind hit normally. A ghast/blaze fireball from the front deflects (blaze fireballs are `Fireball` subclasses — small fireballs count).

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordEventHandler.java
git commit -m "feat: BLOCKING deflects physical projectiles off the blade plane"
```

---

### Task 10: Block slash deals damage on G release

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` (`stopBlocking`, ~line 280)

Design doc: on G release with the guard intact, the wide horizontal slash hits everything in a frontal arc for a fixed 12–14 damage (we use 13 `[TUNE]`), then the sword returns to HOVERING. `stopBlocking()` is only invoked server-side (from `SwordGuardPacket`), so the damage goes there.

- [ ] **Step 1: Add constants and the damage method**

Constants next to the other damage values (~line 76):

```java
    private static final float BLOCK_SLASH_DAMAGE = 13.0f;  // [TUNE 12-14 per design doc]
    private static final double BLOCK_SLASH_RANGE = 3.0;    // [TUNE]
```

Replace `stopBlocking()` and add the helper:

```java
    public void stopBlocking() {
        triggerAnim("action", ANIM_PREFIX + "block_slash");
        doBlockSlashDamage();
        setState(FamiliarState.HOVERING);
    }

    private void doBlockSlashDamage() {
        Player owner = getOwner();
        if (owner == null || this.level().isClientSide()) return;

        Vec3 center = owner.getEyePosition().add(owner.getLookAngle().scale(1.5));
        AABB arc = new AABB(center, center).inflate(BLOCK_SLASH_RANGE, 1.2, BLOCK_SLASH_RANGE);
        Vec3 lookFlat = new Vec3(owner.getLookAngle().x, 0, owner.getLookAngle().z).normalize();

        DamageSource source = this.level().damageSources().playerAttack(owner);
        for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class, arc,
                e -> e.isAlive() && e != owner)) {
            Vec3 toEntity = entity.position().subtract(owner.position());
            Vec3 toEntityFlat = new Vec3(toEntity.x, 0, toEntity.z).normalize();
            if (toEntityFlat.dot(lookFlat) <= 0.1) continue; // frontal ~180° arc only
            entity.hurt(source, BLOCK_SLASH_DAMAGE);
            entity.knockback(0.4, owner.getX() - entity.getX(), owner.getZ() - entity.getZ());
        }

        this.level().playSound(null, owner.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
    }
```

(`LivingEntity#knockback(double strength, double x, double z)` pushes the target away from the given attacker-minus-target delta — same call vanilla melee uses. Verify the exact signature via mcmodding-mcp if it doesn't resolve.)

- [ ] **Step 2: Compile**

Run: `gradlew.bat build` → BUILD SUCCESSFUL.

- [ ] **Step 3: In-game check**

Guard with G near 2–3 zombies in front and one behind. Release G: the front zombies take a single big hit (13) with knockback as the slash animation plays + sweep sound; the one behind is untouched. Releasing G with nothing nearby just plays the animation.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat: block slash deals frontal arc damage on guard release"
```

---

### Task 11: Guard entry from CHARGING and SWEEPING_HOLD (spec completion)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`
- Modify: `src/main/java/com/alucard/heirloomsword/network/SwordGuardPacket.java`
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java`

Design doc: G pressed during CHARGING cancels the charge (no launch) into BLOCKING; G during SWEEPING_HOLD arrests the sweep into BLOCKING. Currently both client and server only allow guard entry from HOVERING.

- [ ] **Step 1: Add cancel-into-block transitions to the entity**

Next to `startBlocking()`:

```java
    public void cancelChargeIntoBlock() {
        removeChargeSlowdown();
        chargeTimer = 0;
        setState(FamiliarState.BLOCKING);
    }

    public void cancelSweepIntoBlock() {
        this.sweepVelocity = Vec3.ZERO;
        this.sweepIFrames.clear();
        setState(FamiliarState.BLOCKING);
    }
```

- [ ] **Step 2: Widen server-side packet validation**

In `SwordGuardPacket.handle`, replace the `if (packet.held())` branch:

```java
            if (packet.held()) {
                if (familiar.getGuardCooldown() > 0) return;
                switch (familiar.getState()) {
                    case HOVERING -> familiar.startBlocking();
                    case CHARGING -> familiar.cancelChargeIntoBlock();      // cancels charge, no launch
                    case SWEEPING_HOLD -> familiar.cancelSweepIntoBlock();  // arrests sweep momentum
                    default -> { } // invalid source state — silently discard (design doc §22)
                }
            } else {
```

- [ ] **Step 3: Widen client-side entry and clear local input state**

In `HeirloomSwordModClient.ClientEvents.onClientTick`, replace the guard-entry block (the `if (!isBlocking) { ... }` section, ~line 147):

```java
            // Track guard hold state (G key)
            if (!isBlocking) {
                if (ModKeybinds.GUARD.isDown() && HeirloomSwordItem.isFlying(held)) {
                    SwordFamiliarEntity familiar = findClientFamiliar(player);
                    if (familiar != null && familiar.getGuardCooldown() == 0) {
                        FamiliarState s = familiar.getState();
                        if (s == FamiliarState.HOVERING
                                || s == FamiliarState.CHARGING
                                || s == FamiliarState.SWEEPING_HOLD) {
                            if (isCharging) resetChargeState();   // G cancels the charge — no launch packet
                            if (isSweeping) resetSweepState();    // G arrests the sweep — no release packet
                            PacketDistributor.sendToServer(new SwordGuardPacket(true));
                            isBlocking = true;
                        }
                    }
                }
            } else {
                if (!HeirloomSwordItem.isFlying(held) || !ModKeybinds.GUARD.isDown()) {
                    cancelBlocking();
                }
            }
```

Ordering note: this block runs **after** the charge-release block in the same tick. Resetting `isCharging` here means the launch-on-release branch can never fire afterward for this charge — exactly the "cancel charge, no launch" behavior the doc requires. The server transitions CHARGING→BLOCKING itself; any stray `SwordLaunchPacket` would be rejected by its own state validation.

- [ ] **Step 4: Compile**

Run: `gradlew.bat build` → BUILD SUCCESSFUL.

- [ ] **Step 5: In-game check**

(a) Hold left-click to charge, then press G mid-charge: no launch happens, sword snaps to the guard position, movement slowdown ends. (b) Hold right-click sweeping, press G: the spin stops and the sword guards; releasing G later does the block slash. (c) G during LAUNCHING/STUCK/RETURNING still does nothing.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java src/main/java/com/alucard/heirloomsword/network/SwordGuardPacket.java src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java
git commit -m "feat: G cancels CHARGING/SWEEPING_HOLD into BLOCKING per spec"
```

---

### Task 12: Final end-to-end verification

**Files:** none (verification only)

- [ ] **Step 1: Clean build**

Run: `gradlew.bat clean build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Full regression pass in `gradlew.bat runClient`**

Normal mode:
- 12 attack damage, netherite-speed cooldown, sweep attack, no durability bar, no enchanting (anvil + book must refuse).

Flying mode, per state:
- HOVERING: sword sits at 1.8 radius; force it through right→left→back→top obstruction, then free the right side — it returns there after ~0.5 s without flickering.
- CHARGING/LAUNCHING: launch toward -Z, +X, -X, up: tip always leads, no flip.
- STUCK: camera movement does not rotate the embedded sword; timeout and R recall still work.
- SWEEPING_HOLD: smooth sawblade spin, no jitter, contact damage; release flies tip-first, returns hilt-first.
- BLOCKING: frontal melee fully blocked w/ shield sound; rear melee and explosions go through; frontal arrows/fireballs deflect at reduced speed; G release fires the new fluid block slash (forward 360°, no reverse snap) dealing ~13 to frontal mobs only; G from CHARGING and SWEEPING_HOLD enters guard.
- DYING/exit: F exits cleanly from HOVERING/SWEEPING_HOLD/BLOCKING.

- [ ] **Step 3: Update the knowledge graph**

Run: `graphify update .`

- [ ] **Step 4: Final commit (if any stragglers) and report**

Report any `[TUNE]` values that felt wrong during verification rather than silently changing them.
