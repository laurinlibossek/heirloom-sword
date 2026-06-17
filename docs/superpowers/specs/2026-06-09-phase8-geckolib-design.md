# Phase 8 — GeckoLib Integration Design Spec

**Date:** 2026-06-09
**Phase:** 8 of 12
**Scope:** Replace debug cube renderer with full GeckoLib animated model, hand gesture suppression, telekinetic shimmer particles, spawn fade-in effect.

---

## 1. GeckoLib Dependency

Add GeckoLib 4.x for NeoForge 1.21.1 to `build.gradle`:
- Repository: `maven { url 'https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/' }`
- Dependency: `implementation "software.bernie.geckolib:geckolib-neoforge-1.21.1:<version>"`
- Register GeckoLib in mod constructor: `GeckoLib.initialize()`

---

## 2. Entity Changes — GeoEntity Interface

`SwordFamiliarEntity` implements `GeoEntity`:

```java
public class SwordFamiliarEntity extends Entity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animationPredicate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
```

The `animationPredicate` method reads `getState()` and returns the appropriate `PlayState` with the matching animation:

| FamiliarState | Animation RawAnimation | Loop Type |
|---|---|---|
| HOVERING (no awareness target) | `idle` | LOOP |
| HOVERING (awareness target present) | `alert` | LOOP |
| CHARGING | `charge_spin` | LOOP |
| LAUNCHING | `launch` | LOOP |
| STUCK | `stuck` | LOOP |
| RETURNING | `return` | LOOP |
| SWEEPING_HOLD | `sweep_hold` | LOOP |
| SWEEPING_RELEASE (outbound) | `launch` | LOOP |
| SWEEPING_RELEASE (returning) | `return_hilt` | LOOP |
| BLOCKING | `block_stance` | LOOP |

Transition animations (`block_slash`, `guard_break`, `death_fall`) are triggered via `triggerAnim()` from the relevant state-change methods. These play once and then the controller returns to the destination state's loop animation.

---

## 3. Renderer — GeoEntityRenderer

New class: `SwordFamiliarGeoRenderer extends GeoEntityRenderer<SwordFamiliarEntity>`

Responsibilities:
- Standard GeckoLib geo rendering pipeline
- **Spawn fade-in:** Override `getRenderColor()` or apply alpha in `preRender()` based on `entity.tickCount < 10` — interpolate alpha from 0.0 to 1.0 over 10 ticks
- **Orientation:** The model's facing is driven by the entity's `yRot` (already computed by `updateOrientation()`). Pitch tilt for LAUNCHING/RETURNING states applied via `preRender()` pose manipulation.

The old `SwordFamiliarRenderer` is deleted entirely.

---

## 4. Model — Placeholder Geometry

File: `assets/heirloomswordmod/geo/sword_familiar.geo.json`
- Simple sword shape: rectangular blade (0.2 x 0.2 x 2.5 blocks), crossguard (0.6 x 0.1 x 0.1), handle (0.15 x 0.15 x 0.6)
- Origin at blade center so rotation pivots correctly
- Bones: `root` > `blade`, `crossguard`, `handle`

File: `assets/heirloomswordmod/animations/sword_familiar.animation.json`
- All clips from Section 6 of the design doc as minimal keyframes:
  - `idle`: gentle Y-axis bob (0.1 block amplitude, 2s period)
  - `alert`: 15° X-axis tilt
  - `charge_spin`: 360°/s Z-axis rotation
  - `launch`: static (model orientation driven by entity yaw/pitch)
  - `stuck`: rapid small-amplitude vibration (X/Z ±0.02 blocks, 4Hz)
  - `return`: static (same as launch, orientation-driven)
  - `return_hilt`: 180° Y-flip of return
  - `sweep_hold`: slight forward tilt
  - `block_stance`: 45° Z-axis diagonal
  - `block_slash`: single-frame horizontal sweep (0.3s)
  - `guard_break`: wobble (X ±10°, 0.4s, ease-out)
  - `death_fall`: tip-down rotation + Y drop

File: `assets/heirloomswordmod/textures/entity/sword_familiar.png`
- 16x16 placeholder texture: dark steel blade, purple accents on crossguard

---

## 5. Hand Gesture Suppression

Subscribe to `RenderHandEvent` (NeoForge game bus, client-side):
- If player's main-hand item is `HeirloomSwordItem` AND `isFlying(stack)` is true → cancel the event (`event.setCanceled(true)`)
- This removes the held item and hand model from first-person view

No actual gesture posing in this phase — the design doc's gesture system (curl, flick, palm-forward) requires custom hand model work that can be a follow-up. Phase 8 implements the "empty hand" baseline.

---

## 6. Telekinetic Shimmer Particles

Client-side particle spawner in `ClientTickEvent.Post`:
- Condition: player holding heirloom sword in flying mode
- Spawn 1-2 `ParticleTypes.WITCH` particles per tick at the player's main hand position
- Offset: small random spread (±0.1 blocks)
- Color override not needed — WITCH particles are already purple/violet

First-person position: use `player.getHandPos()` approximation (eye position + slight downward offset).
Third-person: same logic, vanilla handles the world-space position.

---

## 7. Spawn Fade-In

In `SwordFamiliarGeoRenderer.preRender()`:
- If `entity.tickCount < 10`: set render alpha to `entity.tickCount / 10.0f`
- Achieved via `poseStack` scaling from 0→1 or render color alpha channel
- GeckoLib's `getPackedOverlay()` and render color methods support this

Particle burst on spawn: spawn 10-15 `WITCH` particles in a sphere around the entity position on the first client tick (tickCount == 1).

---

## 8. Hotbar Purple Glow

Already implemented as `renderPurpleGlow()` in `HeirloomSwordModClient.ClientEvents`. No changes needed — it already renders a purple overlay on the hotbar slot when flying mode is active.

---

## 9. File Inventory

| File | Action |
|---|---|
| `build.gradle` | Add GeckoLib repository + dependency |
| `gradle.properties` | Add `geckolib_version` property |
| `HeirloomSwordMod.java` | Add `GeckoLib.initialize()` in constructor |
| `SwordFamiliarEntity.java` | Implement `GeoEntity`, add animation controller |
| `client/SwordFamiliarGeoRenderer.java` | New file — replaces old renderer |
| `client/SwordFamiliarModel.java` | New file — `GeoModel<SwordFamiliarEntity>` |
| `client/SwordFamiliarRenderer.java` | Delete |
| `HeirloomSwordModClient.java` | Update renderer registration, add `RenderHandEvent` handler, add shimmer particles |
| `assets/.../geo/sword_familiar.geo.json` | New — placeholder model |
| `assets/.../animations/sword_familiar.animation.json` | New — all animation clips |
| `assets/.../textures/entity/sword_familiar.png` | New — placeholder texture |

---

## 10. Out of Scope

- Full hand gesture posing (custom hand model with finger bones) — future work
- Idle personality animations (Phase 12)
- Tether pull animation (Phase 11)
- Custom particles (using vanilla WITCH for now)
- Third-person hand gesture validation
