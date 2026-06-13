# Runes & Blood Overlay System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two cosmetic blade overlays that fade in over the reworked base texture: **runes** (purple, full-bright, fade in with charge during CHARGING) and **blood** (splatter, fades in on flying-mode hits and dries over ~60 s). Both purely visual — no gameplay effect.

**Architecture:** A single reusable `FadingOverlayLayer` (a GeckoLib `GeoRenderLayer`) re-renders the existing baked model with a transparent overlay texture at a code-controlled alpha. The entity renderer gets two instances (blood + runes); the item renderer gets one (blood). Blood is the canonical state: a `Float` `DataComponent` on the item, set to 1.0 by the familiar on qualifying hits and decayed only in normal mode by the item's `inventoryTick`. Both renderers read that one component (familiar reads the owner's stack), so blood is consistent on the held blade and the familiar. Runes read the familiar's client-side `chargeTimer` directly — no new state.

**Tech Stack:** NeoForge 1.21.1, GeckoLib 4.8.4. Verified API (from the GeckoLib 4.8.4 sources): `GeoRenderLayer.render(...)`, `GeoRenderer.reRender(model, poseStack, bufferSource, animatable, renderType, buffer, partialTick, packedLight, packedOverlay, int colourARGB)`, `addRenderLayer(...)` in the renderer constructor, `GeoItemRenderer.getCurrentItemStack()`.

> **Depends on:** nothing — the base (`textures/entity/alucard_sword.png`) and the overlay PNGs (`alucard_sword_bloodied.png`, `alucard_sword_runes.png`) already exist in assets (transparent overlays, 64×64, same UV layout as the base).
> Build command: `./gradlew build` (PowerShell: `.\gradlew.bat build`). Expected: `BUILD SUCCESSFUL`. Commits carry no `Co-Authored-By` trailer. Magnitudes are **[TUNE]**.

---

## File Structure

**New files:**
- `client/FadingOverlayLayer.java` — generic overlay render layer (alpha-faded re-render). One responsibility: draw one overlay texture at a given alpha.
- `client/SwordTextures.java` — the overlay `ResourceLocation`s (`BLOOD`, `RUNES`).

**Assets (already present in `textures/entity/`, transparent 64×64, same UV layout as the base):**
- `alucard_sword_bloodied.png` — only the blood splatter painted.
- `alucard_sword_runes.png` — only the purple runes painted.

**Modified:**
- `ModDataComponents.java` — add the `BLOOD` float component.
- `HeirloomSwordItem.java` — blood get/set/find helpers + `inventoryTick` decay.
- `SwordFamiliarEntity.java` — `bloodyOwnerBlade()` + calls at the three flying-mode hit sites.
- `client/SwordFamiliarGeoRenderer.java` — add blood + runes layers.
- `client/HeirloomSwordItemRenderer.java` — add blood layer.

---

## Task 1: Add the blood DataComponent

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/ModDataComponents.java`

- [ ] **Step 1: Add imports**

At the top of `ModDataComponents.java`, add:

```java
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
```

- [ ] **Step 2: Register the component**

After the `FAMILIAR_UUID` registration (before the closing brace of the class), add:

```java
    // Cosmetic blood level 0.0–1.0. Persists on the stack and syncs to clients for rendering.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> BLOOD =
            DATA_COMPONENTS.register("blood", () -> DataComponentType.<Float>builder()
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT)
                    .build());
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/ModDataComponents.java
git commit -m "feat(blood): add cosmetic blood DataComponent"
```

---

## Task 2: Blood accessors + decay on the item

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordItem.java`

- [ ] **Step 1: Add imports**

With the other imports at the top of `HeirloomSwordItem.java`, add:

```java
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
```

- [ ] **Step 2: Add blood constants, accessors, and inventory lookup**

After the existing `isFlying(ItemStack)` method (before the class closing brace), add:

```java
    // Cosmetic blood decay [TUNE]: a fresh 1.0 dries to 0.0 over ~60s while in normal mode.
    // Decays in steps to bound component-sync traffic (writes only every interval).
    public static final long BLOOD_DECAY_INTERVAL = 6L;   // ticks between decay steps
    public static final float BLOOD_DECAY_STEP = 0.005f;  // per step -> ~1.0 over 1200 ticks

    public static float getBlood(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.BLOOD.get(), 0f);
    }

    public static void setBlood(ItemStack stack, float value) {
        stack.set(ModDataComponents.BLOOD.get(), Mth.clamp(value, 0f, 1f));
    }

    /** First Heirloom Sword stack in the player's inventory, or {@link ItemStack#EMPTY}. */
    public static ItemStack findInInventory(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof HeirloomSwordItem) return s;
        }
        return ItemStack.EMPTY;
    }
```

- [ ] **Step 3: Add the decay tick**

Add this `inventoryTick` override next to the other overrides (e.g. after `hurtEnemy`):

```java
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide()) return;
        // While flying, the familiar holds/sets blood; only dry it out in normal mode.
        if (isFlying(stack)) return;
        if (level.getGameTime() % BLOOD_DECAY_INTERVAL != 0L) return;
        float blood = getBlood(stack);
        if (blood <= 0f) return;
        setBlood(stack, Math.max(0f, blood - BLOOD_DECAY_STEP));
    }
```

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/HeirloomSwordItem.java
git commit -m "feat(blood): item blood accessors and normal-mode decay"
```

---

## Task 3: Bloody the blade on flying-mode hits

The three flying-mode blade-contact damage methods each get one call. (Block-slash and the ARRIVING landing shockwave are intentionally excluded per the spec.)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

- [ ] **Step 1: Add the `bloodyOwnerBlade` helper**

Add this method near the other private helpers (e.g. just below `igniteIfUndead`):

```java
    /** Cosmetic: mark the owner's blade freshly bloodied (server-side). */
    private void bloodyOwnerBlade() {
        Player owner = getOwner();
        if (owner == null) return;
        ItemStack stack = HeirloomSwordItem.findInInventory(owner);
        if (!stack.isEmpty()) {
            HeirloomSwordItem.setBlood(stack, 1.0f);
        }
    }
```

(`ItemStack` is already imported and used in this file; `Player` and `HeirloomSwordItem` are in scope.)

- [ ] **Step 2: Hook the sweep-contact path (`sweepDamageEntities`)**

Find:

```java
        if (entities.isEmpty()) return;

        Vec3 travelDir = this.sweepVelocity.length() > 0.01 ? this.sweepVelocity.normalize() : owner.getLookAngle();
```

Replace with:

```java
        if (entities.isEmpty()) return;
        bloodyOwnerBlade();

        Vec3 travelDir = this.sweepVelocity.length() > 0.01 ? this.sweepVelocity.normalize() : owner.getLookAngle();
```

- [ ] **Step 3: Hook the quick-fire contact**

Find:

```java
            if (target instanceof LivingEntity living) {
                living.hurt(this.level().damageSources().playerAttack(owner), QUICK_FIRE_DAMAGE);
                igniteIfUndead(living);
                living.knockback(0.3, this.getX() - living.getX(), this.getZ() - living.getZ());
            }
```

Replace with:

```java
            if (target instanceof LivingEntity living) {
                living.hurt(this.level().damageSources().playerAttack(owner), QUICK_FIRE_DAMAGE);
                igniteIfUndead(living);
                living.knockback(0.3, this.getX() - living.getX(), this.getZ() - living.getZ());
                bloodyOwnerBlade();
            }
```

- [ ] **Step 4: Hook the launch/return/sweep-release path (`damageEntitiesInPath`)**

Find (the 6-argument overload, ~line 1003):

```java
        DamageSource source = this.level().damageSources().playerAttack(owner);
        for (LivingEntity entity : entities) {
            hitSet.add(entity.getId());
            entity.hurt(source, damage);
            igniteIfUndead(entity);
        }
```

Replace with:

```java
        DamageSource source = this.level().damageSources().playerAttack(owner);
        if (!entities.isEmpty()) bloodyOwnerBlade();
        for (LivingEntity entity : entities) {
            hitSet.add(entity.getId());
            entity.hurt(source, damage);
            igniteIfUndead(entity);
        }
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat(blood): familiar bloodies the blade on launch/quick-fire/sweep hits"
```

---

## Task 4: Overlay texture refs + the fading render layer

**Files:**
- Create: `src/main/java/com/alucard/heirloomsword/client/SwordTextures.java`
- Create: `src/main/java/com/alucard/heirloomsword/client/FadingOverlayLayer.java`

- [ ] **Step 1: Create `SwordTextures.java`**

```java
package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.HeirloomSwordMod;
import net.minecraft.resources.ResourceLocation;

/** Overlay textures composited over the base blade (same UV layout as default.png). */
public final class SwordTextures {
    private SwordTextures() {}

    public static final ResourceLocation BLOOD = rl("alucard_sword_bloodied");
    public static final ResourceLocation RUNES = rl("alucard_sword_runes");

    private static ResourceLocation rl(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                HeirloomSwordMod.MODID, "textures/entity/" + name + ".png");
    }
}
```

- [ ] **Step 2: Create `FadingOverlayLayer.java`**

```java
package com.alucard.heirloomsword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Re-renders the model with a transparent overlay texture at a code-controlled alpha.
 * Used for the blood splatter (normal lighting) and the charge runes (full-bright emissive).
 * Alpha is supplied per-frame; an alpha &lt;= 0 skips the pass entirely.
 */
public class FadingOverlayLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    @FunctionalInterface
    public interface AlphaFn<T> {
        /** @return overlay opacity 0..1 for this frame. */
        float alpha(T animatable, float partialTick);
    }

    private final ResourceLocation overlayTexture;
    private final boolean emissive;
    private final AlphaFn<T> alphaFn;

    public FadingOverlayLayer(GeoRenderer<T> renderer, ResourceLocation overlayTexture,
                              boolean emissive, AlphaFn<T> alphaFn) {
        super(renderer);
        this.overlayTexture = overlayTexture;
        this.emissive = emissive;
        this.alphaFn = alphaFn;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
                       MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        float a = alphaFn.alpha(animatable, partialTick);
        if (a <= 0.001f) return;

        int alpha255 = Mth.clamp((int) (a * 255f), 0, 255);
        int colour = (alpha255 << 24) | 0x00FFFFFF; // white tint at the chosen alpha

        RenderType overlay = RenderType.entityTranslucent(overlayTexture);
        int light = emissive ? LightTexture.FULL_BRIGHT : packedLight;

        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, overlay,
                bufferSource.getBuffer(overlay), partialTick, light, packedOverlay, colour);
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/client/SwordTextures.java \
        src/main/java/com/alucard/heirloomsword/client/FadingOverlayLayer.java
git commit -m "feat(overlay): generic fading overlay render layer + texture refs"
```

---

## Task 5: Wire the layers into the renderers

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java`
- Modify: `src/main/java/com/alucard/heirloomsword/client/HeirloomSwordItemRenderer.java`

- [ ] **Step 1: Imports for the familiar renderer**

At the top of `SwordFamiliarGeoRenderer.java`, add:

```java
import com.alucard.heirloomsword.HeirloomSwordItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
```

(`FamiliarState` and `Mth` are already imported in this file.)

- [ ] **Step 2: Add the two layers in the familiar renderer constructor**

Find:

```java
    public SwordFamiliarGeoRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SwordFamiliarModel());
    }
```

Replace with:

```java
    public SwordFamiliarGeoRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new SwordFamiliarModel());

        // Blood: normal lighting, alpha = the owner blade's blood level.
        addRenderLayer(new FadingOverlayLayer<>(this, SwordTextures.BLOOD, false,
                (entity, partialTick) -> bloodAlpha(entity)));
        // Runes: full-bright emissive, alpha ramps with charge during CHARGING.
        addRenderLayer(new FadingOverlayLayer<>(this, SwordTextures.RUNES, true,
                SwordFamiliarGeoRenderer::runeAlpha));
    }

    private static float bloodAlpha(SwordFamiliarEntity entity) {
        Player owner = entity.getOwner();
        if (owner == null) return 0f;
        ItemStack stack = HeirloomSwordItem.findInInventory(owner);
        return stack.isEmpty() ? 0f : HeirloomSwordItem.getBlood(stack);
    }

    private static float runeAlpha(SwordFamiliarEntity entity, float partialTick) {
        if (entity.getState() != FamiliarState.CHARGING) return 0f;
        float t = entity.getChargeTimer() + partialTick;
        if (t < 20f) return 0f;                 // first second: no runes
        if (t < 60f) return (t - 20f) / 40f;    // 1s -> 3s: ramp half -> full
        // Held at full charge: gentle pulse (doubles as the charge-complete cue).
        return 0.85f + 0.15f * Mth.sin((entity.tickCount + partialTick) * 0.3f);
    }
```

- [ ] **Step 3: Imports for the item renderer**

At the top of `HeirloomSwordItemRenderer.java`, add:

```java
import com.alucard.heirloomsword.HeirloomSwordItem;
```

- [ ] **Step 4: Add the blood layer in the item renderer constructor**

Find:

```java
    public HeirloomSwordItemRenderer() {
        super(new HeirloomSwordItemModel());
    }
```

Replace with:

```java
    public HeirloomSwordItemRenderer() {
        super(new HeirloomSwordItemModel());

        // Blood on the held / inventory blade, read from the stack currently being rendered.
        addRenderLayer(new FadingOverlayLayer<>(this, SwordTextures.BLOOD, false, (item, partialTick) -> {
            ItemStack stack = getCurrentItemStack();
            return stack == null || stack.isEmpty() ? 0f : HeirloomSwordItem.getBlood(stack);
        }));
    }
```

(`ItemStack` is already imported in this file.)

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java \
        src/main/java/com/alucard/heirloomsword/client/HeirloomSwordItemRenderer.java
git commit -m "feat(overlay): blood + rune layers on familiar, blood on held item"
```

---

## Task 6: Assets + in-game verification

- [ ] **Step 1: Confirm the overlay PNGs exist**

These already exist (transparent overlays, supplied by the artist):
- `src/main/resources/assets/heirloomswordmod/textures/entity/alucard_sword_bloodied.png` (blood splatter only)
- `src/main/resources/assets/heirloomswordmod/textures/entity/alucard_sword_runes.png` (purple runes only)

Run: `ls src/main/resources/assets/heirloomswordmod/textures/entity/`
Expected: `alucard_sword.png`, `alucard_sword_bloodied.png`, `alucard_sword_runes.png` all present.

- [ ] **Step 2: Commit the assets**

```bash
git add src/main/resources/assets/heirloomswordmod/textures/entity/alucard_sword_bloodied.png \
        src/main/resources/assets/heirloomswordmod/textures/entity/alucard_sword_runes.png
git commit -m "assets(overlay): blood and rune overlay textures"
```

- [ ] **Step 3: Launch and verify runes**

Run: `./gradlew runClient`
- [ ] Enter flying mode and hold left-click to charge: no runes in the first second; runes fade in and brighten from ~1 s to 3 s; at full charge they glow steadily with a subtle pulse.
- [ ] Runes glow full-bright (visible in a dark area), and disappear the instant charging ends.
- [ ] Charging still produces only the binary damage tier (runes are cosmetic — confirm no damage change).

- [ ] **Step 4: Verify blood**

- [ ] Launch/quick-fire/sweep into a mob: the blade visibly gains blood (familiar and, after returning to normal mode, the held blade).
- [ ] With no further contact, blood fades out over ~60 s in normal mode.
- [ ] Blood reads as blood (not rust) against the base — note for the artist if the red needs pushing.
- [ ] Blood shows on the held blade and the inventory icon.

- [ ] **Step 5: Record outcome**

Note any [TUNE] adjustments (rune ramp timing/pulse, blood decay rate) for later. Do not block completion on tuning.

---

## Self-Review (coverage against the spec)

- **3 textures total (default + bloodied + runes), overlays not full variants** → base unchanged; `alucard_sword_bloodied.png`/`alucard_sword_runes.png` are transparent overlays already in assets (Task 4/6). ✓
- **Runes: emissive, fade with charge, only in CHARGING, cosmetic, charge-complete cue** → `runeAlpha` + emissive layer (Task 5); damage tier untouched. ✓
- **Blood: flying-mode contact only, continuous decay ~60s, shows on item + familiar, cosmetic** → hits at the three flying paths only (Task 3); decay in `inventoryTick` (Task 2); both renderers read the one component (Task 5). ✓
- **Fade via the packed `int color` alpha the renderer already passes** → `FadingOverlayLayer` builds an ARGB colour (Task 4). ✓
- **Removes `_half`/`_light` intermediates** → single overlay each, alpha-driven. ✓

**Type/name consistency:** `ModDataComponents.BLOOD`, `HeirloomSwordItem.getBlood/setBlood/findInInventory`, `SwordFamiliarEntity.bloodyOwnerBlade`, `SwordTextures.BLOOD/RUNES`, `FadingOverlayLayer.AlphaFn`, `SwordFamiliarGeoRenderer.bloodAlpha/runeAlpha` — used consistently. `reRender(...)` call matches the GeckoLib 4.8.4 signature (10 args, ARGB `colour` last).

**No placeholders:** every code step is complete and runnable. ✓
