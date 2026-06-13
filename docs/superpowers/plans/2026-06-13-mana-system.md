# Mana System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a player-owned, regenerating **mana** pool that powers warp and every flying-mode action (CHARGING / SWEEPING_HOLD / BLOCKING drains + guard break), shown as an inconspicuous HUD bar, while Epic Fight stamina stays untouched for normal-mode combat only.

**Architecture:** Mana is a server-authoritative `AttachmentType<Float>` on the player, manipulated only through a small static `ManaService` (constants + get/spend/drain/regen). The current value is mirrored to the owning client via a `ManaSyncPacket` (S→C) into a plain `ClientManaState` holder, which the HUD and client-side input prediction read. Flying-mode state entry is gated on a minimum (server validates; client predicts), and held drains stop the action when the pool empties (charge → hover, sweep → release, block → guard break). A shared `SwordSounds.playDenied` cue fires on any blocked action.

**Tech Stack:** NeoForge 1.21.1 (Java 21), NeoForge attachments + `CustomPacketPayload` networking, GeckoLib (unaffected). No unit-test harness exists in this project; every task is verified by a compile-clean `./gradlew build` and the manual in-game checklist in the final task — the same model every prior phase used.

> **Conventions used below**
> - Build command (Git Bash / the agent's shell): `./gradlew build`. PowerShell equivalent: `.\gradlew.bat build`. Expected on success: `BUILD SUCCESSFUL`.
> - Commit messages must **not** include a `Co-Authored-By` trailer (project preference — attribution lives in the README).
> - Tuning numbers are deliberate **[TUNE]** placeholders kept cheap; they fold into the Phase 13 config pass later.

---

## File Structure

**New files (all package `com.alucard.heirloomsword` unless noted):**
- `ManaService.java` — the only place mana logic lives: constants, `get`/`hasAtLeast`/`spend`/`trySpend`/`drain`/`tickRegen`, and client sync. One responsibility: own the pool.
- `ManaAttachments.java` — registers the two `AttachmentType`s (`MANA`, `REGEN_DELAY`).
- `ClientManaState.java` — a plain client-side cache (one `float`) updated by the sync packet and read by the HUD. Common class, no client-only types, so it links on both sides.
- `SwordSounds.java` — shared player-feedback cues (`playDenied`). Reused by warp later.
- `network/ManaSyncPacket.java` — S→C float payload updating `ClientManaState`.

**Modified files:**
- `HeirloomSwordMod.java` — register `ManaAttachments.ATTACHMENT_TYPES` on the mod event bus.
- `network/ModNetwork.java` — register `ManaSyncPacket` as `playToClient`.
- `SwordEventHandler.java` — regen tick + a `playerHasSword` helper.
- `SwordFamiliarEntity.java` — drains in `tickCharging`, `tickSweepingHold`, `tickBlocking`.
- `network/SwordChargePacket.java`, `SwordSweepPacket.java`, `SwordGuardPacket.java` — server entry gates.
- `HeirloomSwordModClient.java` — mana HUD bar + client-side prediction gates + client denial cue.

---

## Task 1: Mana core spine (service, attachments, client cache, sync packet)

These four classes reference each other cyclically (service → packet → client cache → service constant), so they are created together and built as a unit.

**Files:**
- Create: `src/main/java/com/alucard/heirloomsword/ManaService.java`
- Create: `src/main/java/com/alucard/heirloomsword/ManaAttachments.java`
- Create: `src/main/java/com/alucard/heirloomsword/ClientManaState.java`
- Create: `src/main/java/com/alucard/heirloomsword/network/ManaSyncPacket.java`
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordMod.java`
- Modify: `src/main/java/com/alucard/heirloomsword/network/ModNetwork.java`

- [ ] **Step 1: Create `ManaService.java`**

```java
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

    public static float get(Player player) {
        return player.getData(ManaAttachments.MANA.get());
    }

    public static boolean hasAtLeast(Player player, float amount) {
        return get(player) >= amount;
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
        setMana(player, remaining);
        player.setData(ManaAttachments.REGEN_DELAY.get(), REGEN_PAUSE_TICKS);
        return remaining > 0f;
    }

    /** Called every tick while the player possesses the sword. Handles the regen pause. */
    public static void tickRegen(Player player) {
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
            PacketDistributor.sendToPlayer(sp, new ManaSyncPacket(clamped));
        }
    }

    /** Bound packet traffic: sync on whole-unit changes and on the empty/full boundaries. */
    private static boolean shouldSync(float oldV, float newV) {
        if (oldV == newV) return false;
        return Mth.floor(oldV) != Mth.floor(newV) || newV <= 0f || newV >= MAX_MANA;
    }
}
```

- [ ] **Step 2: Create `ManaAttachments.java`**

```java
package com.alucard.heirloomsword;

import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ManaAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, HeirloomSwordMod.MODID);

    // Current mana. Persists across logout; a freshly-respawned player gets the default (full).
    public static final Supplier<AttachmentType<Float>> MANA =
            ATTACHMENT_TYPES.register("mana", () ->
                    AttachmentType.<Float>builder(() -> ManaService.MAX_MANA)
                            .serialize(Codec.FLOAT)
                            .build());

    // Ticks remaining before regen resumes after a spend. Transient (not serialized).
    public static final Supplier<AttachmentType<Integer>> REGEN_DELAY =
            ATTACHMENT_TYPES.register("mana_regen_delay", () ->
                    AttachmentType.<Integer>builder(() -> 0)
                            .build());
}
```

- [ ] **Step 3: Create `ClientManaState.java`**

```java
package com.alucard.heirloomsword;

/**
 * Client-side cached mana for the local player, written by {@code ManaSyncPacket} and read
 * by the HUD and input prediction. Plain field only (no client-only types) so it links on
 * both sides; only ever mutated on the client.
 */
public class ClientManaState {
    public static float current = ManaService.MAX_MANA;
}
```

- [ ] **Step 4: Create `network/ManaSyncPacket.java`**

```java
package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.ClientManaState;
import com.alucard.heirloomsword.HeirloomSwordMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client: updates the local player's cached mana value. */
public record ManaSyncPacket(float amount) implements CustomPacketPayload {
    public static final Type<ManaSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "mana_sync"));

    public static final StreamCodec<ByteBuf, ManaSyncPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.FLOAT, ManaSyncPacket::amount, ManaSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ManaSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientManaState.current = packet.amount());
    }
}
```

- [ ] **Step 5: Register the attachments — modify `HeirloomSwordMod.java`**

In the constructor, alongside the other `*.register(modEventBus)` calls (after `ModEntities.ENTITY_TYPES.register(modEventBus);`), add:

```java
        ManaAttachments.ATTACHMENT_TYPES.register(modEventBus);
```

- [ ] **Step 6: Register the sync packet — modify `network/ModNetwork.java`**

Inside `register(...)`, after the last `registrar.playToServer(...)` block (the `SwordQuickFirePacket` one), add:

```java
        registrar.playToClient(
                ManaSyncPacket.TYPE,
                ManaSyncPacket.STREAM_CODEC,
                ManaSyncPacket::handle
        );
```

- [ ] **Step 7: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. (Fixes if it fails: confirm `NeoForgeRegistries.Keys.ATTACHMENT_TYPES` import resolves and `AttachmentType.<Float>builder(...)` type witnesses are present.)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/ManaService.java \
        src/main/java/com/alucard/heirloomsword/ManaAttachments.java \
        src/main/java/com/alucard/heirloomsword/ClientManaState.java \
        src/main/java/com/alucard/heirloomsword/network/ManaSyncPacket.java \
        src/main/java/com/alucard/heirloomsword/HeirloomSwordMod.java \
        src/main/java/com/alucard/heirloomsword/network/ModNetwork.java
git commit -m "feat(mana): core mana service, attachments, and client sync"
```

---

## Task 2: Shared "action-denied" cue

**Files:**
- Create: `src/main/java/com/alucard/heirloomsword/SwordSounds.java`

- [ ] **Step 1: Create `SwordSounds.java`**

```java
package com.alucard.heirloomsword;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Shared player-feedback cues. */
public final class SwordSounds {
    private SwordSounds() {}

    /**
     * Subtle "you can't do that right now" cue. Reused for any blocked action (insufficient
     * mana, on cooldown, no valid target, …). Placeholder vanilla sound — swapped for a custom
     * sound in the audio pass.
     */
    public static void playDenied(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5f, 1.2f);
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordSounds.java
git commit -m "feat(mana): shared action-denied feedback cue"
```

---

## Task 3: Mana regeneration tick

Regen runs whenever the player has the sword anywhere in their inventory (normal or flying).

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordEventHandler.java`

- [ ] **Step 1: Add regen at the top of `onPlayerTick`**

Find the start of `onPlayerTick`:

```java
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack swordStack = findFlyingSword(player);
        if (swordStack == null) return;
```

Replace it with (inserts the regen call before the flying-sword early-return):

```java
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (playerHasSword(player)) {
            ManaService.tickRegen(player);
        }

        ItemStack swordStack = findFlyingSword(player);
        if (swordStack == null) return;
```

- [ ] **Step 2: Add the `playerHasSword` helper**

Next to the existing `findFlyingSword` helper at the bottom of the class, add:

```java
    private boolean playerHasSword(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof HeirloomSwordItem) {
                return true;
            }
        }
        return false;
    }
```

(`Player` and `ManaService` are already in package `com.alucard.heirloomsword`; `Player` is imported as `net.minecraft.world.entity.player.Player` — confirm the import is present, it is used by `findFlyingSword`.)

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordEventHandler.java
git commit -m "feat(mana): regenerate mana while holding the sword"
```

---

## Task 4: CHARGING — drain + entry gate

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` (`tickCharging`, ~line 503)
- Modify: `src/main/java/com/alucard/heirloomsword/network/SwordChargePacket.java`

- [ ] **Step 1: Drain in `tickCharging`**

Find:

```java
    private void tickCharging(Player owner) {
        chargeTimer++;
```

Replace with:

```java
    private void tickCharging(Player owner) {
        if (!ManaService.drain(owner, ManaService.CHARGE_DRAIN_PER_TICK)) {
            // Mana exhausted mid-charge — stop, no launch (mirrors the design's depletion-stop).
            removeChargeSlowdown();
            chargeTimer = 0;
            setState(FamiliarState.HOVERING);
            return;
        }
        chargeTimer++;
```

(`ManaService` is in the same package — no import needed.)

- [ ] **Step 2: Entry gate in `SwordChargePacket.handle`**

Find:

```java
            if (familiar.getState() != FamiliarState.HOVERING) return;

            familiar.startCharging();
```

Replace with:

```java
            if (familiar.getState() != FamiliarState.HOVERING) return;

            if (!ManaService.hasAtLeast(player, ManaService.MIN_CHARGE)) {
                SwordSounds.playDenied(player);
                return;
            }

            familiar.startCharging();
```

(The wildcard import `import com.alucard.heirloomsword.*;` already in the packet file covers `ManaService` and `SwordSounds`.)

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java \
        src/main/java/com/alucard/heirloomsword/network/SwordChargePacket.java
git commit -m "feat(mana): charging drains mana and is gated on a minimum"
```

---

## Task 5: SWEEPING_HOLD — drain + entry gate

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` (`tickSweepingHold`, ~line 745)
- Modify: `src/main/java/com/alucard/heirloomsword/network/SwordSweepPacket.java`

- [ ] **Step 1: Drain in `tickSweepingHold`**

Find:

```java
    private void tickSweepingHold(Player owner) {
        Vec3 lookDir = owner.getLookAngle();
```

Replace with:

```java
    private void tickSweepingHold(Player owner) {
        if (!ManaService.drain(owner, ManaService.SWEEP_DRAIN_PER_TICK)) {
            // Mana exhausted mid-sweep — end it (transitions to SWEEPING_RELEASE / HOVERING).
            releaseSweep();
            return;
        }
        Vec3 lookDir = owner.getLookAngle();
```

- [ ] **Step 2: Entry gate in `SwordSweepPacket.handle`**

Find:

```java
            if (familiar.getState() != FamiliarState.HOVERING) return;

            familiar.startSweeping();
```

Replace with:

```java
            if (familiar.getState() != FamiliarState.HOVERING) return;

            if (!ManaService.hasAtLeast(player, ManaService.MIN_SWEEP)) {
                SwordSounds.playDenied(player);
                return;
            }

            familiar.startSweeping();
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java \
        src/main/java/com/alucard/heirloomsword/network/SwordSweepPacket.java
git commit -m "feat(mana): sweep-hold drains mana and is gated on a minimum"
```

---

## Task 6: BLOCKING — drain + guard break on empty + entry gate

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` (`tickBlocking`, ~line 466)
- Modify: `src/main/java/com/alucard/heirloomsword/network/SwordGuardPacket.java`

- [ ] **Step 1: Drain in `tickBlocking`**

Find:

```java
    private void tickBlocking(Player owner) {
        Vec3 target = owner.getEyePosition().add(owner.getLookAngle().scale(1.5));
        targetPosition = target;
        applySpringPhysics();
    }
```

Replace with:

```java
    private void tickBlocking(Player owner) {
        if (!ManaService.drain(owner, ManaService.BLOCK_DRAIN_PER_TICK)) {
            // Mana exhausted while guarding — guard break (existing 3s cooldown applies).
            guardBreak();
            return;
        }
        Vec3 target = owner.getEyePosition().add(owner.getLookAngle().scale(1.5));
        targetPosition = target;
        applySpringPhysics();
    }
```

- [ ] **Step 2: Entry gate in `SwordGuardPacket.handle`**

Find:

```java
            if (packet.held()) {
                if (familiar.getGuardCooldown() > 0) return;
                switch (familiar.getState()) {
```

Replace with:

```java
            if (packet.held()) {
                if (familiar.getGuardCooldown() > 0) return;
                if (!ManaService.hasAtLeast(player, ManaService.MIN_BLOCK)) {
                    SwordSounds.playDenied(player);
                    return;
                }
                switch (familiar.getState()) {
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java \
        src/main/java/com/alucard/heirloomsword/network/SwordGuardPacket.java
git commit -m "feat(mana): blocking drains mana, breaks guard on empty, gated on a minimum"
```

---

## Task 7: Mana HUD bar

Inconspicuous bar shown whenever the sword is in hand or the familiar is present. Added to the existing GUI overlay alongside (but independent of) the flying-only purple glow / charge bar.

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java`

- [ ] **Step 1: Add imports**

At the top of the file with the other imports, add:

```java
import com.alucard.heirloomsword.ClientManaState;
import com.alucard.heirloomsword.ManaService;
import net.minecraft.util.Mth;
```

- [ ] **Step 2: Render the mana bar at the top of `onRenderGuiPost`**

Find the start of `onRenderGuiPost`:

```java
        @SubscribeEvent
        public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            LocalPlayer player = mc.player;
            int selectedSlot = player.getInventory().selected;
            ItemStack stack = player.getInventory().getItem(selectedSlot);

            if (!(stack.getItem() instanceof HeirloomSwordItem) || !HeirloomSwordItem.isFlying(stack)) {
                return;
            }
```

Replace with (adds the mana bar before the flying-only early return):

```java
        @SubscribeEvent
        public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            LocalPlayer player = mc.player;

            // Mana bar: shown whenever the sword is in hand (normal or flying) or the
            // familiar is present. Independent of the flying-only HUD below.
            boolean showMana = player.getMainHandItem().getItem() instanceof HeirloomSwordItem
                    || findClientFamiliar(player) != null;
            if (showMana) {
                renderManaBar(event.getGuiGraphics(),
                        mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            }

            int selectedSlot = player.getInventory().selected;
            ItemStack stack = player.getInventory().getItem(selectedSlot);

            if (!(stack.getItem() instanceof HeirloomSwordItem) || !HeirloomSwordItem.isFlying(stack)) {
                return;
            }
```

- [ ] **Step 3: Add the `renderManaBar` method**

Next to the existing `renderChargeBar` method, add:

```java
        private static void renderManaBar(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
            float ratio = Mth.clamp(ClientManaState.current / ManaService.MAX_MANA, 0f, 1f);

            int barWidth = 80;          // [TUNE] placement/size — inconspicuous, above the hotbar
            int barHeight = 4;
            int barX = screenWidth / 2 - barWidth / 2;
            int barY = screenHeight - 34;
            int fillWidth = (int) (barWidth * ratio);

            // Dark backing + a conventional mana-blue fill.
            guiGraphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0x88000000);
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF3A7BD5);
        }
```

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java
git commit -m "feat(mana): inconspicuous mana HUD bar while sword is in use"
```

---

## Task 8: Client-side prediction gates + local denial cue

Prevents the client from optimistically charging/sweeping/blocking when it knows mana is too low (avoids a charge-bar-with-no-effect desync), and gives instant feedback with a local denial sound.

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java`

- [ ] **Step 1: Add a client denial-sound helper**

Next to the `resetChargeState` helper, add:

```java
        private static void playDeniedClient(LocalPlayer player) {
            // Mirror of SwordSounds.playDenied, played locally for client-predicted denials.
            player.playSound(net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, 0.5f, 1.2f);
        }
```

- [ ] **Step 2: Gate charge start (left click) in `onMouseClick`**

Find (inside `if (event.isAttack())`):

```java
                SwordFamiliarEntity familiar = findClientFamiliar(player);
                if (familiar != null && familiar.getState() == FamiliarState.HOVERING) {
                    PacketDistributor.sendToServer(new SwordChargePacket());
                    isCharging = true;
                    clientChargeTimer = 0;
                    event.setCanceled(true);
                    event.setSwingHand(false);
                } else {
```

Replace with:

```java
                SwordFamiliarEntity familiar = findClientFamiliar(player);
                if (familiar != null && familiar.getState() == FamiliarState.HOVERING) {
                    if (ClientManaState.current < ManaService.MIN_CHARGE) {
                        playDeniedClient(player);
                        event.setCanceled(true);
                        event.setSwingHand(false);
                        return;
                    }
                    PacketDistributor.sendToServer(new SwordChargePacket());
                    isCharging = true;
                    clientChargeTimer = 0;
                    event.setCanceled(true);
                    event.setSwingHand(false);
                } else {
```

- [ ] **Step 3: Gate sweep start (right click) in `onMouseClick`**

Find (inside `if (event.isUseItem())`):

```java
                SwordFamiliarEntity familiar = findClientFamiliar(player);
                if (familiar != null && familiar.getState() == FamiliarState.HOVERING) {
                    PacketDistributor.sendToServer(new SwordSweepPacket());
                    isSweeping = true;
                    lastYaw = player.getYRot();
                    lastPitch = player.getXRot();
                    event.setCanceled(true);
                    event.setSwingHand(false);
                } else {
```

Replace with:

```java
                SwordFamiliarEntity familiar = findClientFamiliar(player);
                if (familiar != null && familiar.getState() == FamiliarState.HOVERING) {
                    if (ClientManaState.current < ManaService.MIN_SWEEP) {
                        playDeniedClient(player);
                        event.setCanceled(true);
                        event.setSwingHand(false);
                        return;
                    }
                    PacketDistributor.sendToServer(new SwordSweepPacket());
                    isSweeping = true;
                    lastYaw = player.getYRot();
                    lastPitch = player.getXRot();
                    event.setCanceled(true);
                    event.setSwingHand(false);
                } else {
```

- [ ] **Step 4: Gate block start (G) in `onClientTick`**

Find:

```java
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
```

Replace with:

```java
                    if (familiar != null && familiar.getGuardCooldown() == 0) {
                        FamiliarState s = familiar.getState();
                        if (s == FamiliarState.HOVERING
                                || s == FamiliarState.CHARGING
                                || s == FamiliarState.SWEEPING_HOLD) {
                            if (ClientManaState.current < ManaService.MIN_BLOCK) {
                                playDeniedClient(player);
                            } else {
                                if (isCharging) resetChargeState();   // G cancels the charge — no launch packet
                                if (isSweeping) resetSweepState();    // G arrests the sweep — no release packet
                                PacketDistributor.sendToServer(new SwordGuardPacket(true));
                                isBlocking = true;
                            }
                        }
                    }
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java
git commit -m "feat(mana): client-side mana gates with local denial cue"
```

---

## Task 9: In-game verification

No automated harness exists; verify behavior in the running client. This is the acceptance test for the whole feature.

- [ ] **Step 1: Launch the client**

Run: `./gradlew runClient`
Expected: client launches with no crash; reach a world with the Heirloom Sword in hand (creative or `/give`).

- [ ] **Step 2: HUD presence**

- [ ] In normal mode holding the sword: the thin mana bar appears (full) above the hotbar.
- [ ] Switch to flying mode (F): the mana bar still shows; the purple hotbar glow and (on charge) the charge bar are unaffected.
- [ ] Put the sword away (switch hotbar slot, no familiar): the mana bar disappears.

- [ ] **Step 3: Drains**

- [ ] Hold left-click to charge: mana bar visibly drops while charging and pauses ~1s before refilling after release.
- [ ] Right-click sweep: mana drops while held.
- [ ] Hold G to block: mana drops while guarding.

- [ ] **Step 4: Depletion behavior**

- [ ] Charge until mana empties: the charge stops and the sword returns to HOVERING with no launch.
- [ ] Block until mana empties: guard breaks (guard_break animation + the existing 3s cooldown).
- [ ] Sweep until mana empties: the sweep ends (sword returns).

- [ ] **Step 5: Entry gates + denial cue**

- [ ] With mana below the minimum, attempt to charge / sweep / block: the action does not start and the denial click plays.
- [ ] Confirm the action becomes available again once mana regenerates above the minimum.

- [ ] **Step 6: Persistence sanity**

- [ ] Spend some mana, then disconnect/reconnect (or `/reload` + relog in singleplayer): mana is not silently full-reset mid-session beyond expected regen (it persists via the attachment).

- [ ] **Step 7: Record outcome**

If everything passes, the mana feature is complete. Note any [TUNE] values that felt off (regen rate, drain rates, minimums, bar placement) for the later config pass — do not block completion on tuning.

---

## Self-Review (coverage against the spec)

- **Mana on player, via `ManaService`** → Tasks 1, 3 (attachment + service + regen). ✓
- **Powers warp + all flying actions; quick-fire free; EF untouched** → drains wired into charge/sweep/block only (Tasks 4–6); quick-fire and normal combat are not modified. Warp's cost is consumed in the separate Warp plan via `ManaService.trySpend`/`WARP_COST` (defined here). ✓
- **Gate start + stop mid-action** → entry gates (Tasks 4–6) + `drain()` returning false stops the action (charge→hover, sweep→release, block→guard break). ✓
- **HUD shown whenever sword in hand or familiar present, inconspicuous** → Task 7. ✓
- **Regen with pause after spend** → `REGEN_DELAY` + `tickRegen` (Tasks 1, 3). ✓
- **Reusable denial cue (sound only, no text)** → `SwordSounds.playDenied` (server) + `playDeniedClient` (Tasks 2, 8). ✓
- **[TUNE] constants, cheap, fold into Phase 13 config** → all magnitudes are constants in `ManaService` (Task 1). ✓
- **No EF dependency** → nothing here references Epic Fight. ✓

**Type/name consistency:** `ManaService.get/hasAtLeast/spend/trySpend/drain/tickRegen`, `ManaAttachments.MANA/REGEN_DELAY`, `ClientManaState.current`, `ManaSyncPacket(float amount)`, `SwordSounds.playDenied(ServerPlayer)`, `playDeniedClient(LocalPlayer)` — used consistently across tasks. `MIN_CHARGE/MIN_SWEEP/MIN_BLOCK/WARP_COST` referenced by the gates and (later) the warp plan.

**No placeholders:** every code step contains complete, runnable code. ✓
