# Render Alignment & Polish Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the sword familiar's rendered model exactly match its hitbox in every state, fix the pitch sign convention, simplify the orientation math, center/enlarge the item in the GUI slot, and tone down the hand particles and hotbar glow.

**Architecture:** All entity orientation values (`yRot`/`xRot`) stay server-authoritative in `SwordFamiliarEntity.updateOrientation()` using vanilla Minecraft conventions (yaw: 0 = +Z; pitch: positive = looking down). The renderer (`SwordFamiliarGeoRenderer.preRender`) is the *single* place that converts those conventions into model-space transforms. Animations no longer bake whole-body orientation (the 90°/180° X rotations are removed from the animation JSON); they only add secondary motion (wobble, bob, slash arcs).

**Tech Stack:** NeoForge 21.1.233, GeckoLib 4.8.4, Java 21. No unit-test infrastructure exists for client rendering — verification is `gradlew.bat build` after each task plus a structured in-game checklist (Task 9). This is the accepted deviation from TDD for this plan.

---

## Verified facts this plan is built on (do not re-derive)

These were confirmed by reading the **GeckoLib 4.8.4 sources** (`geckolib-neoforge-1.21.1-4.8.4-sources.jar` in the Gradle cache):

1. **`GeoEntityRenderer.actuallyRender` calls `applyRotations(...)` with `lerpBodyRot = 0` for any non-`LivingEntity`**, which applies a constant `Axis.YP.rotationDegrees(180f - 0)` — i.e. every frame our entity gets an *inner* fixed 180° Y rotation that we cannot remove without overriding `applyRotations` (we deliberately don't; the math below accounts for it).
2. **`preRender` transforms are applied *outside* (after, in vertex order: before) `applyRotations` and the bone animations.** Vertex pipeline, innermost → outermost: `bone anims → YP(180) → [preRender calls in reverse order] → dispatcher translate to entity pos`.
3. **`GeoItemRenderer.preRender` translates `(0.5, 0.51, 0.5)`**, so the geo model's origin (blade tip, y=0) sits at the *center* of the standard item display box. Our 48-unit-tall (3.0 block) model extends 0→3 *upward from the box center* before display-transform scaling.
4. The geo model (`alucard_sword.geo.json`): blade tip at y=0, pommel top at y=48 units → **3.0 blocks tall, model center at y=1.5 blocks**. Root bone pivot `[0,24,0]` = the model center.
5. Entity render origin = `entity.position()`. For the **vertical** hitbox (`EntityDimensions.fixed(0.4f, 3.0f)`, default `makeBoundingBox`) that is the **bottom center** of the box. For the **horizontal** custom `makeBoundingBox()` it is the **box center** (the AABB is built ± around `position()`).

### The orientation math (single source of truth)

With preRender call order `YP(180−yaw)`, `XP(90−pitch)`, `translate(0,−1.5,0)`, the model's tip direction (local −Y) ends up at:

```
tip = (−sin(yaw)·cos(pitch), −sin(pitch), cos(yaw)·cos(pitch))
```

which is **exactly Minecraft's look vector** for that yaw/pitch. (The inner `YP(180)` from fact #1 only spins the near-symmetric blade about its own axis — visually irrelevant.) The model center `(0, 1.5, 0)` maps onto the entity position, matching the horizontal hitbox center.

Two bugs this fixes, for context:
- **Pitch was inverted**: the old `XP(90 + pitch)` produced tip-y = `+sin(pitch)`, the opposite of MC convention. All `updateOrientation()` pitch values already follow MC convention (`targetPitch = −atan2(dy, horiz)`), so the renderer is the only place that changes.
- **Horizontal model floated 1.5 blocks above its hitbox** (clearly visible in the sweep screenshot: hitbox wireframe on the ground, sword in the air). The old `translate(0, +1.5); XP(...); translate(0, −1.5−0.6)` net-translated the model center to `(0, +1.5, −0.6-along-blade)` relative to the hitbox center, and net `−0.6` straight down in vertical states (tip poking out the box bottom).

---

## File Structure

| File | Action | Responsibility after this plan |
|---|---|---|
| `src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java` | Rewrite `preRender` | Sole converter from entity yaw/pitch → model transforms; hitbox alignment |
| `src/main/resources/assets/heirloomswordmod/animations/alucard_sword.animation.json` | Edit 2 animations | Secondary motion only — no baked whole-body orientation |
| `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` | Edit 3 spots | Orientation targets (MC conventions), charging height, BLOCKING hitbox |
| `src/main/resources/assets/heirloomswordmod/models/item/heirloom_sword.json` | Edit `gui` display | Centered, larger sword icon in slots |
| `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java` | Edit 2 spots | ~3 particles/sec at hand; subtle 1px hotbar outline |
| `src/main/java/com/alucard/heirloomsword/client/HeirloomSwordItemRenderer.java` | **No change** | In-hand flip/offset works per screenshot — do not touch |

---

### Task 1: Rewrite `SwordFamiliarGeoRenderer.preRender`

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java` (replace whole file)

- [ ] **Step 1: Replace the file contents with:**

```java
package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.FamiliarState;
import com.alucard.heirloomsword.SwordFamiliarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SwordFamiliarGeoRenderer extends GeoEntityRenderer<SwordFamiliarEntity> {

    // Geo model: blade tip at y=0, pommel at y=48 units -> 3.0 blocks tall, center at 1.5
    private static final float MODEL_CENTER_Y = 1.5f;

    public SwordFamiliarGeoRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SwordFamiliarModel());
    }

    @Override
    public void preRender(PoseStack poseStack, SwordFamiliarEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer vertexConsumer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int color) {
        super.preRender(poseStack, animatable, model, bufferSource, vertexConsumer, isReRender,
                partialTick, packedLight, packedOverlay, color);

        if (animatable.tickCount < 10) {
            float scale = (animatable.tickCount + partialTick) / 10.0f;
            poseStack.scale(scale, scale, scale);
        }

        // GeckoLib only auto-applies yaw for LivingEntity (applyRotations gets lerpBodyRot=0
        // for plain entities, leaving a constant inner YP(180)). YP(180 - yaw) on top of that
        // gives the correct world heading for the entity's yRot.
        float yaw = Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));

        if (animatable.isHorizontal()) {
            if (animatable.getState() != FamiliarState.BLOCKING) {
                // XP(90 - pitch) maps MC pitch convention (positive = down) onto the vertical
                // model so the blade tip tracks the entity's look vector exactly.
                float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f - pitch));
            }
            // BLOCKING keeps the model upright; block_stance/block_slash/guard_break pose it.

            // The horizontal hitbox is centered on the entity position; move the model center
            // onto it. Must be the LAST call so it runs in model space, inside the rotations.
            poseStack.translate(0, -MODEL_CENTER_Y, 0);
        }
        // Vertical states: geo model (0..3) already matches the hitbox (feet..feet+3) - no offset.
    }
}
```

- [ ] **Step 2: Build**

Run: `gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java
git commit -m "fix: align familiar model with hitbox, correct pitch sign in renderer"
```

---

### Task 2: Remove baked whole-body rotations from animations

The renderer now owns orientation, so animations must not also rotate the whole sword:
- `sweep_hold` bakes `x: 90` (would now double-rotate on top of the renderer's `XP(90−pitch)`).
- `return_hilt` bakes `x: 180` — but `tickSweepingRelease` already stores **reversed** `sweepVelocity` so `updateOrientation()` points the hilt forward. The baked 180 flipped it *back* to tip-first (a real bug).

**Files:**
- Modify: `src/main/resources/assets/heirloomswordmod/animations/alucard_sword.animation.json`

- [ ] **Step 1: In `animation.alucard_sword.sweep_hold`, zero the X component of all three rotation keyframes**

Replace the `sweep_hold` bones block so the keyframes read:

```json
"animation.alucard_sword.sweep_hold": {
  "loop": true,
  "animation_length": 2.0,
  "bones": {
    "root": {
      "rotation": {
        "0.0": [0, 0, 0],
        "1.0": [0, 0, 3],
        "2.0": [0, 0, 0]
      }
    }
  }
}
```

(Keyframes were `[90,0,0]`, `[90,0,3]`, `[90,0,0]` — only the gentle z-wobble remains.)

- [ ] **Step 2: In `animation.alucard_sword.return_hilt`, zero the X component of all three rotation keyframes**

```json
"animation.alucard_sword.return_hilt": {
  "loop": true,
  "animation_length": 0.8,
  "bones": {
    "root": {
      "rotation": {
        "0.0": [0, 0, 0],
        "0.4": [0, 0, -5],
        "0.8": [0, 0, 0]
      }
    }
  }
}
```

(Keyframes were `[180,0,0]`, `[180,0,-5]`, `[180,0,0]`.)

- [ ] **Step 3: Validate the JSON parses**

Run: `python -c "import json; json.load(open('src/main/resources/assets/heirloomswordmod/animations/alucard_sword.animation.json'))" `
Expected: no output, exit 0. (Or any JSON validator available.)

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/assets/heirloomswordmod/animations/alucard_sword.animation.json
git commit -m "fix: stop baking whole-body orientation into sweep_hold/return_hilt anims"
```

---

### Task 3: Simplify SWEEPING_HOLD yaw math in `updateOrientation`

The current expression `atan2(dir.x, -dir.z) + 180f` is mathematically *identical* to the standard forward yaw `atan2(-dir.x, dir.z)` (negating both atan2 args adds 180°, then +180 wraps to 0). The two branches are now the same — collapse them and delete the misleading "invert to face hilt" comments.

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` (the `SWEEPING_HOLD || SWEEPING_RELEASE` branch of `updateOrientation()`, currently around lines 977-994)

- [ ] **Step 1: Replace the branch body**

Old code:

```java
            if (dir != null) {
                                // Invert sweep hold direction to face hilt, keep release facing forward
                if (getState() == FamiliarState.SWEEPING_HOLD) {
                    targetYaw = (float) Math.toDegrees(Math.atan2(dir.x, -dir.z)) + 180f; // Add 180 degrees to flip yaw entirely
                    double horizDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                    targetPitch = (float) -Math.toDegrees(Math.atan2(dir.y, horizDist)); // Don't invert pitch, just flip yaw
                } else {
                    targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                    double horizDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                    targetPitch = (float) -Math.toDegrees(Math.atan2(dir.y, horizDist));
                }
            }
```

New code:

```java
            if (dir != null) {
                // Tip points along the travel direction. During the release return phase
                // sweepVelocity is stored reversed, so the hilt leads automatically.
                targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                double horizDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                targetPitch = (float) -Math.toDegrees(Math.atan2(dir.y, horizDist));
            }
```

- [ ] **Step 2: Build**

Run: `gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "refactor: collapse sweep yaw branches (they were mathematically identical)"
```

---

### Task 4: Retune charging height

The old `+1.5` compensated for the renderer's now-removed offsets (visual used to float 1.5 above the hitbox; Gemini's tweak was tuned against that broken renderer). Now visual == hitbox, so `+1.5` would put the sword ~1.5 above the torso anchor — too high.

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` (`updateChargingPosition`, currently lines 347-356)

- [ ] **Step 1: Change both offsets from 1.5 to 1.0**

New method body:

```java
        private void updateChargingPosition(Player owner) {
            // Prefer right side (candidate 0), fall back to left (candidate 1).
            // +1.0 raises the charge pose to roughly head height; the renderer now puts the
            // model exactly on the hitbox, so this offset is the real visual height. [TUNE]
            Vec3 rightSide = computeCandidatePosition(owner, 0).add(0, 1.0, 0);
            if (!isPositionObstructed(rightSide)) {
                targetPosition = rightSide;
            } else {
                Vec3 leftSide = computeCandidatePosition(owner, 1).add(0, 1.0, 0);
                targetPosition = leftSide;
            }
        }
```

- [ ] **Step 2: Build**

Run: `gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "tune: charging hover height now that visual matches hitbox"
```

---

### Task 5: BLOCKING-specific bounding box

During BLOCKING the blade stays upright and `block_stance` rolls it 45° across the player's view — wide and tall, thin along the look axis. The current horizontal box runs *along* the look axis (90° wrong). No gameplay code reads the familiar's AABB for guard checks (verified by grep — `SwordGuardPacket` only toggles state), so this is visual/pickable only.

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` (`makeBoundingBox`, currently lines 918-936)

- [ ] **Step 1: Replace `makeBoundingBox()` with:**

```java
    @Override
    protected AABB makeBoundingBox() {
        if (!horizontal) return super.makeBoundingBox();

        double yawRad = Math.toRadians(this.getYRot());
        double sinYaw = Math.abs(Math.sin(yawRad));
        double cosYaw = Math.abs(Math.cos(yawRad));
        Vec3 pos = this.position();

        if (getState() == FamiliarState.BLOCKING) {
            // Blade is held upright and rolled 45 deg across the view (block_stance):
            // wide across the look direction and tall, thin along the look axis.
            double halfAcross = 1.1;
            double halfX = cosYaw * halfAcross + sinYaw * SWORD_HALF_THICKNESS;
            double halfZ = sinYaw * halfAcross + cosYaw * SWORD_HALF_THICKNESS;
            return new AABB(
                    pos.x - halfX, pos.y - halfAcross, pos.z - halfZ,
                    pos.x + halfX, pos.y + halfAcross, pos.z + halfZ
            );
        }

        // Tight AABB around the rotated blade: 3 long, 0.4 thick, oriented along getYRot().
        double halfX = sinYaw * SWORD_HALF_LENGTH + cosYaw * SWORD_HALF_THICKNESS;
        double halfZ = cosYaw * SWORD_HALF_LENGTH + sinYaw * SWORD_HALF_THICKNESS;
        return new AABB(
                pos.x - halfX, pos.y - SWORD_HALF_THICKNESS, pos.z - halfZ,
                pos.x + halfX, pos.y + SWORD_HALF_THICKNESS, pos.z + halfZ
        );
    }
```

Note: `import com.alucard.heirloomsword.FamiliarState` is not needed — same package. `FamiliarState` is already referenced throughout the file.

- [ ] **Step 2: Build**

Run: `gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "fix: BLOCKING hitbox spans across the view instead of along it"
```

---

### Task 6: Center and enlarge the GUI item icon

Per fact #3, the geo model extends 0→3 blocks *upward from the slot center*; after `scale` it must be pulled back down by `1.5 × scale × cos(30°) × 16` JSON units to center it. With scale 0.3 that is ≈ 6.25. (Vanilla applies display transforms as translate→rotate→scale on the stack, so scale hits vertices first.)

**Files:**
- Modify: `src/main/resources/assets/heirloomswordmod/models/item/heirloom_sword.json` (the `gui` entry only — hand/ground/fixed/head stay as they are; the in-hand look is handled in `HeirloomSwordItemRenderer` and is correct per the screenshot)

- [ ] **Step 1: Replace the `gui` block**

```json
    "gui": {
      "rotation": [30, 45, 0],
      "translation": [0, -6.25, 0],
      "scale": [0.3, 0.3, 0.3]
    },
```

(Old values: translation `[0, -2, 0]`, scale `0.25`.) `[TUNE]` — verify in Task 9; if the sword still sits low, decrease translation y further (more negative = lower model = sword appears more centered… adjust in steps of ±1).

- [ ] **Step 2: Validate the JSON parses**

Run: `python -c "import json; json.load(open('src/main/resources/assets/heirloomswordmod/models/item/heirloom_sword.json'))" `
Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/assets/heirloomswordmod/models/item/heirloom_sword.json
git commit -m "fix: center and enlarge heirloom sword in GUI slots"
```

---

### Task 7: Reduce hand shimmer particles

Currently 1–2 WITCH particles **every client tick** (~30/sec). Target: ~3/sec.

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java` (the "telekinetic shimmer" block at the end of `onClientTick`, currently lines 196-217)

- [ ] **Step 1: Replace the block**

Old: `for (int i = 0; i < (player.getRandom().nextDouble() < 0.5 ? 1 : 2); i++) { ... }`

New:

```java
                        // Telekinetic shimmer at the hand while flying mode is active
            if (HeirloomSwordItem.isFlying(held) && mc.level != null
                    && player.getRandom().nextFloat() < 0.15f) {
                double dx = (player.getRandom().nextDouble() - 0.5) * 0.2;
                double dy = (player.getRandom().nextDouble() - 0.5) * 0.2;
                double dz = (player.getRandom().nextDouble() - 0.5) * 0.2;

                // Approximate hand position based on camera
                Vec3 handPos;
                if (mc.options.getCameraType().isFirstPerson()) {
                    handPos = player.getEyePosition().add(player.getLookAngle().scale(0.5)).add(0, -0.3, 0);
                } else {
                    float yRot = player.yBodyRot * ((float) Math.PI / 180F);
                    double handOffsetZ = Math.cos(yRot) * 0.4;
                    double handOffsetX = Math.sin(yRot) * 0.4;
                    handPos = new Vec3(player.getX() - handOffsetX,
                            player.getY() + player.getBbHeight() * 0.5, player.getZ() + handOffsetZ);
                }

                mc.level.addParticle(ParticleTypes.WITCH, handPos.x + dx, handPos.y + dy, handPos.z + dz, 0, 0, 0);
            }
```

(The outer `if (HeirloomSwordItem.isFlying(held) && mc.level != null)` wrapper is replaced by this block — the only changes are the `nextFloat() < 0.15f` gate and the removal of the `for` loop.)

- [ ] **Step 2: Build**

Run: `gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java
git commit -m "tune: reduce flying-mode hand particles to ~3/sec"
```

---

### Task 8: Subtle hotbar glow

Replace the full-slot purple fill (plus the shader-color tint that double-darkened everything) with a thin 1px outline around the item area.

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java` (`renderPurpleGlow`, currently lines 353-362; plus the now-unused import)

- [ ] **Step 1: Replace `renderPurpleGlow`**

```java
        private static void renderPurpleGlow(GuiGraphics guiGraphics, int x, int y) {
            // Subtle 1px purple outline around the slot's item area
            guiGraphics.renderOutline(x + 2, y + 2, 18, 18, 0x669933FF);
        }
```

- [ ] **Step 2: Remove the now-unused import**

Delete line: `import com.mojang.blaze3d.systems.RenderSystem;`
(Verify nothing else in the file uses `RenderSystem` — the charge bar uses plain `guiGraphics.fill`.)

- [ ] **Step 3: Build**

Run: `gradlew.bat build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java
git commit -m "tune: replace hotbar slot fill with subtle purple outline"
```

---

### Task 9: In-game verification checklist

- [ ] **Step 1: Launch the client**

Run: `gradlew.bat runClient`

- [ ] **Step 2: In a test world (creative, flat), give the sword, press F3+B to show hitboxes, and verify each row of this table.** Toggle flying mode with F.

| State | How to trigger | Expected (hitboxes visible) |
|---|---|---|
| HOVERING (idle) | Toggle flying mode, no mobs nearby | Sword **vertical**, fully inside its 0.4×3 box, tip at box bottom, pommel at box top, gently bobbing |
| HOVERING (alert) | Spawn a zombie within 16 blocks | Sword **horizontal, centered in its box**, tip pointing at the zombie — including **up/down** when the zombie is above/below (pitch sign check) |
| CHARGING | Hold left-click | Horizontal at the player's side ~head height, drill-spinning, tip pointing where the player looks — **tip dips when looking down, rises when looking up** |
| LAUNCHING | Release left-click | Tip leads along the flight path, including steep up/down launches |
| STUCK | Launch into a wall | Embedded in the wall, jittering, orientation frozen |
| RETURNING | Press R while stuck | **Hilt** leads toward the player, model centered in its moving box |
| SWEEPING_HOLD | Hold right-click and swing the view | Horizontal, tip follows the swing direction, model centered in its box (box no longer trails 1.5 blocks below the model) |
| SWEEPING_RELEASE (out) | Flick and release right-click | Tip-first along the throw |
| SWEEPING_RELEASE (return) | Wait for it to turn around | **Hilt-first** toward the player (the old double-flip made it tip-first) |
| BLOCKING | Hold G while hovering | Blade upright, rolled 45° across the view at eye height; box is wide/tall across the view, thin toward the player |
| DYING | Toggle flying mode in a 1-block-high tunnel (all candidates obstructed) | Sword drops and despawns, stays vertical |
| Hotbar | Flying mode on | Thin purple outline around the slot only — no solid fill |
| GUI icon | Open inventory | Sword roughly centered in the slot, larger than before |
| Hand particles | Flying mode on, stand still | Occasional single sparkle near the hand (~3/sec), not a stream |

- [ ] **Step 3: Tune the `[TUNE]` values if needed** — charging height (Task 4, `1.0`) and GUI translation (Task 6, `-6.25`). One edit + rebuild each; keep changes minimal.

- [ ] **Step 4: Final commit if tuning changed anything**

```bash
git add -A src/main
git commit -m "tune: post-verification adjustments to charge height / GUI transform"
```

---

## Explicitly out of scope (do not "fix" these)

- `HeirloomSwordItemRenderer` hand flip (`XP(180)` + `translate(0,-2.5,0)`) — correct per the third-person screenshot.
- The 15-particle spawn burst in `SwordFamiliarEntity.clientTick` (one-time poof, fine).
- The inner constant `YP(180)` from GeckoLib's `applyRotations` — accounted for in the math; overriding it would break the yaw formula everywhere.
- `ground`/`fixed`/`head` display transforms.
