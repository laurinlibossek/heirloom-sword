# Phase 11 — Tether Pull Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the `TETHERING` state — a fresh Shift press while the familiar is `STUCK` yanks the player (riptide-style single impulse) toward the **midpoint** between player and embedded sword; on arrival/timeout/geometry-block the sword wrenches free into `RETURNING`.

**Architecture:** Server-authoritative. The familiar entity owns the state machine and applies the yank to the owner with `owner.setDeltaMovement(impulse)` + `owner.hurtMarked = true` — the exact vanilla mechanism knockback and explosions use to push a client-authoritative player and sync the velocity to their client. **One impulse, then monitor** (riptide model, not a per-tick reel). The sword does not move during the pull; the player collides with terrain normally and can still take damage. Client sends a fresh-press `SwordTetherPacket`; the server validates `state == STUCK` so a stray packet is inert. Tether sounds reuse the Plan A vanilla-placeholder convention in `SwordSounds`.

**Tech Stack:** NeoForge 1.21.1, Java 21, GeckoLib 4.x. No new assets, no `sounds.json`, no `DeferredRegister<SoundEvent>` (deferred to the final-release audio pass, same as Phase 10).

---

## Scope & Guardrails

**In scope (design doc §State:TETHERING L610-664, §Phase 11 L1259-1266):**
- `TETHERING` state, `SwordTetherPacket`, fresh-Shift client detection, midpoint yank, arrival/timeout/geometry exits → `RETURNING`, 3 placeholder tether sounds, arrival particle burst.

**Out of scope (deliberate, deferred to later passes):**
- Custom `.ogg` files / `SoundEvent` registry (final-release audio task).
- A bespoke `tether_pull` GeckoLib clip with intensified vibration + emissive glow. **No such animation asset exists yet** — the `tether_pull` name would crash GeckoLib with "animation not found". This plan aliases `TETHERING` to the existing `stuck` clip; authoring the real clip + glow is an art/GeckoLib-pass task (note added in Task 2, Step 7).
- Config migration of the tether `[TUNE]` constants — those move to the Phase 13 config section (`docs/.../alucard_sword_design_v3.md` §25.1, `tether` section). Phase 11 uses named `private static final` constants now, exactly as the other states were built.

**Testing approach (codebase reality):** This repo has **no game-test harness** for entity runtime behavior, and tether mechanics (velocity sync, collision, arrival) are runtime-only — they cannot be unit-tested. Per the established pattern (see `docs/superpowers/plans/2026-06-15-phase10-audio-and-particles.md`), every code task ends with `./gradlew build` as the compile gate + a commit, and **Task 5 is the authoritative in-game verification matrix**. The three exhaustive `switch` statements over `FamiliarState` act as a compiler-enforced completeness check — omitting the `TETHERING` case fails the build.

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `src/main/java/com/alucard/heirloomsword/SwordSounds.java` | Centralized cue helpers | Add 3 tether placeholder helpers |
| `src/main/java/com/alucard/heirloomsword/FamiliarState.java` | State enum + id mapping | Add `TETHERING(11)` + `fromId` case |
| `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` | State machine + physics | Constants, fields, `startTether`/`enterTether`/`tickTethering`/`endTether`, 3 switch cases |
| `src/main/java/com/alucard/heirloomsword/network/SwordTetherPacket.java` | Client→Server tether request | New file (clone of `SwordRecallPacket`) |
| `src/main/java/com/alucard/heirloomsword/network/ModNetwork.java` | Packet registration | Register `SwordTetherPacket` |
| `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java` | Client input | Fresh-Shift-press detection → send packet |

**Dependency order:** SwordSounds (standalone) → enum+entity (introduces `startTether`, `TETHERING`) → packet (calls `startTether`) → client (sends packet). Each task compiles on its own.

---

## Task 1: Tether sound helpers

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordSounds.java` (append after `playDeathFall`, before the closing brace at line 89-90)

- [ ] **Step 1: Add the three tether placeholder helpers**

Insert immediately after the `playDeathFall` method (currently ends at line 89), before the final `}`:

```java

    // === Phase 11 tether placeholder cues (vanilla sounds; swap to custom SoundEvents in the audio pass) ===

    /** Tether yank begins — the chain snaps taut as the player is pulled. */
    public static void playTetherStart(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    /** Tether pull loop (throttled one-shot while TETHERING). Pitched up reel-in whir. */
    public static void playTetherLoop(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    /** Tether ends — sword wrenches free into RETURNING. Quieter, pitched-up teleport-like pop. */
    public static void playTetherArrival(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.3f);
    }
```

- [ ] **Step 2: Build (compile gate)**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. (`SoundEvents.CHAIN_BREAK`, `FISHING_BOBBER_RETRIEVE`, and `ENDERMAN_TELEPORT` all exist in 1.21.1 `net.minecraft.sounds.SoundEvents`.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordSounds.java
git commit -m "feat(phase11): add tether start/loop/arrival placeholder sounds"
```

---

## Task 2: TETHERING state + yank physics

This is the core task. All sub-steps land in two files that must change together so the three exhaustive `switch` statements still compile.

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/FamiliarState.java`
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` (constants ~L83, fields ~L142-150, `serverTick` switch L310-322, `clientTick` switch L352-364, `tickStuck`/`enterReturning` L712-730, `animationPredicate` switch L1592-1604)

- [ ] **Step 1: Add `TETHERING(11)` to the state enum**

In `FamiliarState.java`, add the constant at the end of the enum list (after `QUICK_FIRE(10)`):

```java
    QUICK_FIRE(10),
    TETHERING(11);
```

And add the `fromId` case (after `case 10 -> QUICK_FIRE;`):

```java
            case 10 -> QUICK_FIRE;
            case 11 -> TETHERING;
```

- [ ] **Step 2: Add tether constants**

In `SwordFamiliarEntity.java`, after the `STUCK_TIMEOUT_TICKS` constant (line 83), add:

```java
    // TETHERING constants — riptide-style single-impulse yank to the midpoint [all TUNE → Phase 13 config]
    private static final double TETHER_IMPULSE_PER_BLOCK = 0.30; // impulse magnitude per block of distance
    private static final double TETHER_IMPULSE_MIN = 0.7;        // floor so short tethers still feel like a yank
    private static final double TETHER_IMPULSE_MAX = 2.6;        // cap so long tethers don't fling absurdly
    private static final double TETHER_VERTICAL_BOOST = 0.32;    // added upward so the player arcs, not skids
    private static final double TETHER_ARRIVAL_RANGE = 2.0;      // within this of the midpoint → done
    private static final int TETHER_TIMEOUT_TICKS = 40;          // 2 seconds
    private static final int TETHER_GEOMETRY_BLOCK_TICKS = 10;   // ticks of near-zero horizontal travel → done
    private static final double TETHER_GEOMETRY_MOVE_SQR = 0.01; // (~0.1 block/tick)^2 horizontal-movement floor
```

- [ ] **Step 3: Add tether state fields**

In `SwordFamiliarEntity.java`, after the STUCK fields (line 142-143):

```java
    // STUCK state fields
    private int stuckTimer = 0;

    // TETHERING state fields
    private boolean tetherPending = false;
    private Vec3 tetherMidpoint = Vec3.ZERO;
    private int tetherTimer = 0;
    private int tetherGeometryTicks = 0;
    private Vec3 tetherLastPos = Vec3.ZERO;
```

- [ ] **Step 4: Add the public request entrypoint + wire `tickStuck`**

Replace the existing `tickStuck` method (lines 712-723) with the version below — it adds the `tetherPending` branch. The `recallPending` (R-key) branch keeps priority and clears a stray `tetherPending`:

```java
    private void tickStuck(Player owner) {
        if (recallPending) {
            recallPending = false;
            tetherPending = false;
            enterReturning();
            return;
        }

        if (tetherPending) {
            tetherPending = false;
            enterTether(owner);
            return;
        }

        stuckTimer++;
        if (stuckTimer >= STUCK_TIMEOUT_TICKS) {
            enterReturning();
        }
    }
```

Then add a public `startTether()` next to the existing `recall()` method (which ends at line 736). Insert after `recall()`'s closing brace:

```java
    public void startTether() {
        if (getState() == FamiliarState.STUCK) {
            this.tetherPending = true;
        }
    }
```

- [ ] **Step 5: Add `enterTether` / `tickTethering` / `endTether`**

Insert these three methods immediately after `enterReturning()` (which ends at line 730) — i.e. right before `public void recall()`:

```java
    // === TETHERING ===

    private void enterTether(Player owner) {
        Vec3 a = owner.position();               // player feet
        Vec3 b = this.position();                // embedded sword
        tetherMidpoint = a.add(b).scale(0.5);
        tetherTimer = 0;
        tetherGeometryTicks = 0;
        tetherLastPos = a;
        setState(FamiliarState.TETHERING);

        // Riptide-style single impulse toward the midpoint. Magnitude scales with distance
        // (clamped), plus a fixed upward boost so the player arcs over terrain instead of
        // skidding into it. Applied server-side; hurtMarked forces the velocity packet to the
        // client (same path vanilla knockback uses for a client-authoritative player).
        Vec3 dir = tetherMidpoint.subtract(a);
        double dist = dir.length();
        if (dist > 1.0E-4) {
            double mag = Math.min(Math.max(dist * TETHER_IMPULSE_PER_BLOCK, TETHER_IMPULSE_MIN), TETHER_IMPULSE_MAX);
            Vec3 impulse = dir.normalize().scale(mag).add(0.0, TETHER_VERTICAL_BOOST, 0.0);
            owner.setDeltaMovement(impulse);
            owner.hurtMarked = true;
            // Fall damage is intentionally NOT reset here — the player takes it naturally on
            // landing, exactly like a riptide launch (design L629: "can still take damage during
            // the pull"). Do not add owner.fallDistance = 0.
        }

        if (!this.level().isClientSide) {
            SwordSounds.playTetherStart(this.level(), getX(), getY(), getZ());
        }
    }

    private void tickTethering(Player owner) {
        tetherTimer++;

        // Throttled reel-in loop (one-shot every 4 ticks while pulling).
        if (!this.level().isClientSide && tetherTimer % 4 == 0) {
            SwordSounds.playTetherLoop(this.level(), getX(), getY(), getZ());
        }

        // Arrival: player within range of the snapshot midpoint.
        if (owner.position().distanceToSqr(tetherMidpoint) <= TETHER_ARRIVAL_RANGE * TETHER_ARRIVAL_RANGE) {
            endTether();
            return;
        }

        // Timeout.
        if (tetherTimer >= TETHER_TIMEOUT_TICKS) {
            endTether();
            return;
        }

        // Geometry block: player's horizontal travel stalls (wall/ceiling). Measured from
        // position deltas — more reliable than getDeltaMovement() for a client-auth player.
        Vec3 nowPos = owner.position();
        double dx = nowPos.x - tetherLastPos.x;
        double dz = nowPos.z - tetherLastPos.z;
        if (dx * dx + dz * dz < TETHER_GEOMETRY_MOVE_SQR) {
            tetherGeometryTicks++;
            if (tetherGeometryTicks >= TETHER_GEOMETRY_BLOCK_TICKS) {
                endTether();
                return;
            }
        } else {
            tetherGeometryTicks = 0;
        }
        tetherLastPos = nowPos;
    }

    private void endTether() {
        if (!this.level().isClientSide) {
            SwordSounds.playTetherArrival(this.level(), getX(), getY(), getZ());
            ((net.minecraft.server.level.ServerLevel) this.level()).sendParticles(ParticleTypes.POOF,
                    getX(), getY() + getBbHeight() * 0.5, getZ(), 16, 0.25, 0.3, 0.25, 0.03);
        }
        enterReturning();
    }
```

> Note: the sword stays embedded for the whole pull — `tickTethering` never moves the entity, exactly as `tickStuck` doesn't. `endTether` → `enterReturning()` clears `returnHitSet` and the normal RETURNING travel carries the sword to the player's **new** post-tether position, applying its 8-damage return arc there (design L633-635).

- [ ] **Step 6: Add the `TETHERING` case to both tick switches**

In `serverTick`'s switch (after `case QUICK_FIRE -> tickQuickFire(owner);`, line 321):

```java
            case QUICK_FIRE -> tickQuickFire(owner);
            case TETHERING -> tickTethering(owner);
```

In `clientTick`'s switch (after `case QUICK_FIRE -> tickQuickFireClient();`, line 363) — the client does nothing; the player's own vanilla movement renders the yank:

```java
            case QUICK_FIRE -> tickQuickFireClient();
            case TETHERING -> {}
```

- [ ] **Step 7: Add the `TETHERING` animation case**

In `animationPredicate`'s switch (after `case QUICK_FIRE -> anim = "launch";`, line 1601), alias to the existing `stuck` clip:

```java
            case QUICK_FIRE -> anim = "launch";
            case TETHERING -> anim = "stuck"; // PLACEHOLDER: real `tether_pull` clip + glow is an art/GeckoLib-pass task
```

- [ ] **Step 8: Build (compile gate)**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. If any of the three switches still errors with "switch ... does not cover all values of FamiliarState", a `case TETHERING` is missing — add it.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/FamiliarState.java src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat(phase11): TETHERING state with riptide-style midpoint yank"
```

---

## Task 3: SwordTetherPacket + registration

**Files:**
- Create: `src/main/java/com/alucard/heirloomsword/network/SwordTetherPacket.java`
- Modify: `src/main/java/com/alucard/heirloomsword/network/ModNetwork.java` (add a `playToServer` block alongside the others, L9-58)

- [ ] **Step 1: Create the packet (clone of `SwordRecallPacket`, calls `startTether`)**

Create `src/main/java/com/alucard/heirloomsword/network/SwordTetherPacket.java`:

```java
package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwordTetherPacket() implements CustomPacketPayload {
    public static final Type<SwordTetherPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_tether"));

    public static final StreamCodec<ByteBuf, SwordTetherPacket> STREAM_CODEC =
            StreamCodec.unit(new SwordTetherPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordTetherPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) return;
            if (!HeirloomSwordItem.isFlying(held)) return;

            if (ManaService.isLockedOut(player)) return; // inputs locked during depletion punishment

            ServerLevel level = player.serverLevel();
            SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(level, player.getUUID());
            if (familiar == null) return;

            // Tether only triggers from STUCK (server is authoritative — a stray packet is inert).
            if (familiar.getState() != FamiliarState.STUCK) return;

            familiar.startTether();
        });
    }
}
```

- [ ] **Step 2: Register the packet**

In `ModNetwork.java`, add after the `SwordCancelChargePacket` block (line 54-58, before the `playToClient` ManaSync block):

```java
        registrar.playToServer(
                SwordTetherPacket.TYPE,
                SwordTetherPacket.STREAM_CODEC,
                SwordTetherPacket::handle
        );
```

- [ ] **Step 3: Build (compile gate)**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. (`familiar.startTether()` and `FamiliarState.TETHERING` exist from Task 2; `findForOwner`, `ManaService.isLockedOut`, `HeirloomSwordItem.isFlying` are the same calls `SwordRecallPacket` uses.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/network/SwordTetherPacket.java src/main/java/com/alucard/heirloomsword/network/ModNetwork.java
git commit -m "feat(phase11): SwordTetherPacket (client->server tether request)"
```

---

## Task 4: Fresh-Shift-press client detection

The tether uses the **vanilla sneak key** (`mc.options.keyShift`), not a new keybind, and must NOT consume it (the player still needs to sneak). Detect a rising edge (down now, not down last tick) — a player who held Shift into STUCK produces no edge until they release and re-press, satisfying the "fresh press" rule (design L618-621).

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java` (field block ~L72-85; tick handler — insert after the RECALL block, L141-148; import at top, ~L7-16)

- [ ] **Step 1: Add the import**

In the import block (alongside the other `network.*` imports, L7-16), add:

```java
import com.alucard.heirloomsword.network.SwordTetherPacket;
```

- [ ] **Step 2: Add the edge-tracking field**

In `ClientEvents`, after the `isBlocking` field (line 81):

```java
        private static boolean isBlocking = false;

        // Tether uses the vanilla sneak key; track its edge so a fresh press (not a held key)
        // triggers the pull during STUCK. Not consumed — the player still sneaks normally.
        private static boolean wasSneaking = false;
```

- [ ] **Step 3: Add the fresh-press detection block**

In `onClientTick`, insert immediately after the RECALL `while` block (which ends at line 148, before the QUICK_FIRE block at line 150):

```java
            // Tether: a FRESH sneak press while the familiar is STUCK yanks the player to the
            // midpoint. Rising-edge only, so holding Shift into STUCK does nothing until released
            // and re-pressed. The STUCK gate (here + server-side) makes any stray packet inert.
            boolean sneakingNow = mc.options.keyShift.isDown();
            if (sneakingNow && !wasSneaking && HeirloomSwordItem.isFlying(held)
                    && (isManaExempt(player) || ClientManaState.lockoutTicks <= 0)) {
                SwordFamiliarEntity tetherFamiliar = findClientFamiliar(player);
                if (tetherFamiliar != null && tetherFamiliar.getState() == FamiliarState.STUCK) {
                    PacketDistributor.sendToServer(new SwordTetherPacket());
                }
            }
            wasSneaking = sneakingNow;
```

- [ ] **Step 4: Build (compile gate)**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. (`mc.options.keyShift` is the sneak `KeyMapping` in 1.21.1; `isManaExempt`, `ClientManaState.lockoutTicks`, `findClientFamiliar` are already used in the surrounding RECALL/QUICK_FIRE blocks.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java
git commit -m "feat(phase11): fresh sneak-press detection sends tether packet during STUCK"
```

---

## Task 5: In-game verification matrix (authoritative QA)

No code. Launch the client (`./gradlew runClient`), enter flying mode, and walk this matrix. This is the real test — runtime velocity/collision/sync cannot be unit-tested in this repo.

- [ ] **Step 1: Happy path — yank**

Run: `./gradlew runClient`
- Launch the sword into a wall across a gap so it goes STUCK.
- Press Shift (fresh press).
- Expected: chain-snap sound; player is yanked through the air toward the **halfway point** between where they stood and the sword; reel-in whir loops briefly; on arrival the sword pops free (teleport pop + POOF burst) and flies back tip-forward to the player → HOVERING.

- [ ] **Step 2: Fresh-press rule**

- Launch into a wall while **holding Shift** (sneaking) the whole time.
- Expected: NO tether while Shift stays held. Release Shift, press again → tether fires.

- [ ] **Step 3: Midpoint, not the sword**

- Stand far from where the sword embeds. Tether.
- Expected: player stops roughly halfway, not at the sword.

- [ ] **Step 4: Timeout fallback**

- Tether toward a midpoint behind a wall the player can't reach.
- Expected: after ~2s the pull gives up and the sword returns. Player is not left stuck mid-air indefinitely.

- [ ] **Step 5: Geometry-block fallback**

- Tether such that the player slams into a ceiling/wall and stalls before arriving.
- Expected: after ~0.5s of no horizontal progress, the sword returns. No hanging.

- [ ] **Step 6: R still works from STUCK**

- Embed, press R (recall) instead of Shift.
- Expected: normal recall → RETURNING. Tether did not interfere.

- [ ] **Step 7: Collision + damage**

- Tether across terrain with a mob in the flight path.
- Expected: player collides with blocks normally (no phasing); fall/contact damage still applies (design allows it); the RETURNING arc after arrival deals its 8-damage hit to entities on the path.

- [ ] **Step 8: Tune pass**

- If the yank under/overshoots or feels weak/violent, adjust the `TETHER_*` constants in `SwordFamiliarEntity.java` (Task 2 Step 2) — most likely `TETHER_IMPULSE_PER_BLOCK`, `TETHER_IMPULSE_MAX`, and `TETHER_VERTICAL_BOOST` — rebuild, re-test. These are the `[TUNE]` values that move to the Phase 13 config.

- [ ] **Step 9: Update design doc + memory**

- In `docs/alucard_sword_design_v3.md`, mark Phase 11 (L1259) status DONE (mirror the Phase 10 status-note style at L1252), noting the `tether_pull` animation + glow remain a deferred art-pass item.
- Run `graphify update .` to refresh the knowledge graph.
- Commit:

```bash
git add docs/alucard_sword_design_v3.md
git commit -m "docs(phase11): mark tether pull implemented; note art-pass deferral"
```

---

## Self-Review (completed during authoring)

- **Spec coverage (design L610-664, L1259-1266):** fresh-Shift-during-STUCK (Task 4) ✓; midpoint calc (Task 2 Step 5) ✓; player velocity toward midpoint (riptide single impulse per user decision — Task 2 Step 5) ✓; normal block collision (vanilla movement, no entity phasing — Task 2 note) ✓; arrival → RETURNING (Task 2 `endTether`) ✓; timeout → RETURNING ✓; geometry-block → RETURNING ✓; no stamina cost (no mana deduction added) ✓; `SwordTetherPacket` (Task 3) ✓; arrival particle burst (Task 2 `endTether` POOF) ✓; start/loop/arrival sounds (Task 1 + wired in Task 2) ✓; `tether_pull` clip + intensified glow — **explicitly deferred** to art pass with placeholder alias (documented, not silently dropped) ✓.
- **Type consistency:** method names `startTether` / `enterTether` / `tickTethering` / `endTether` and fields `tetherPending` / `tetherMidpoint` / `tetherTimer` / `tetherGeometryTicks` / `tetherLastPos` are used identically across Tasks 2-4. Packet type `SwordTetherPacket` matches across Tasks 3-4. Sound helpers `playTetherStart` / `playTetherLoop` / `playTetherArrival` match between Tasks 1 and 2.
- **Placeholder scan:** no TBD/TODO/"handle edge cases" — every code step shows complete code. The single intentional placeholder (the `stuck` animation alias) is called out explicitly as a deferred art task, not a gap.
