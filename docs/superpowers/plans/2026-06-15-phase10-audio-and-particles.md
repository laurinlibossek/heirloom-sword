# Phase 10 (Plan A) — Audio Triggers & Missing Particles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire every still-missing Phase 10 sound trigger and the two missing particle bursts, using Minecraft vanilla **placeholder** sounds (per design doc §21), without touching any sound that already works.

**Architecture:** All new sounds are centralized as helper methods in `SwordSounds.java` (mirroring the existing `playStuckImpact`/`playDenied` pattern) so the audio pass can later swap each to a custom `SoundEvent` in one place. Triggers are added at the authoritative **server-side** state-transition / damage sites already mapped in `SwordFamiliarEntity` and the packets; `level.playSound(null, …)` broadcasts to nearby clients (owner included). Particles use `ServerLevel.sendParticles`.

**Tech Stack:** NeoForge 1.21.1, Java 21. No new assets, no `sounds.json`, no `DeferredRegister<SoundEvent>` (that is the deferred "final release" audio task, explicitly **out of scope** here — Plan A).

---

## Scope & Guardrails

**KEEP UNCHANGED — do not touch these 7 already-working cues** (user explicitly likes them):
- STUCK embed → `MACE_SMASH_GROUND` (`SwordSounds.playStuckImpact`)
- Block stance **hit** → `SHIELD_BLOCK` (`SwordEventHandler` melee)
- Projectile deflect → `SHIELD_BLOCK` ↑pitch (`SwordEventHandler` projectile)
- Block-slash release → `PLAYER_ATTACK_SWEEP` (`doBlockSlashDamage`)
- Spawn touchdown → `AMETHYST_CLUSTER_BREAK` (`tickArriving`)
- Warp success → `CHORUS_FRUIT_TELEPORT` (`WarpHandler`)
- Action denied → `DISPENSER_FAIL` (`SwordSounds.playDenied`)

**OUT OF SCOPE:** custom `.ogg` files / `SoundEvent` registry (final-release task); the 3 tether sounds (Phase 11); the hovering-loop and charge-loop are implemented as throttled one-shots, **not** true `TickableSoundInstance` loops (a final-audio refinement).

**No automated tests:** sounds/particles are runtime-only and the repo has no game-test harness. Each code task ends with `./gradlew build` (compile gate) + commit; Task 7 is the authoritative in-game verification matrix.

---

## File Structure

| File | Change |
|---|---|
| `src/main/java/com/alucard/heirloomsword/SwordSounds.java` | Add 11 new sound helpers (existing two untouched) |
| `src/main/java/com/alucard/heirloomsword/network/SwordModePacket.java` | Mode enter/exit sounds |
| `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` | Launch, impact (+particle), return-arrival, death-fall, sweep-contact, guard-raised, guard-break, charge-loop, hover-loop, embed particle |
| `src/main/java/com/alucard/heirloomsword/SwordEventHandler.java` | Deflection particle burst |
| `docs/alucard_sword_design_v3.md` | Mark Phase 10 audio/particle triggers complete |

---

### Task 1: Add the new sound helpers (central, all in one file)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordSounds.java`

- [ ] **Step 1: Add the helpers** — insert these methods inside the `SwordSounds` class, after `playDenied(...)` (before the closing `}`). Leave `playStuckImpact` and `playDenied` exactly as they are.

```java
    // === Phase 10 placeholder cues (vanilla sounds; swap to custom SoundEvents in the audio pass) ===

    /** Flying mode entered (F). */
    public static void playModeEnter(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.0f);
    }

    /** Flying mode exited to normal (F toggle). Pitched down vs enter. */
    public static void playModeExit(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 0.7f);
    }

    /** Familiar launch. Charged launch is louder and pitched down. */
    public static void playLaunch(Level level, double x, double y, double z, boolean charged) {
        level.playSound(null, x, y, z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS,
                charged ? 1.2f : 0.9f, charged ? 0.8f : 1.0f);
    }

    /** Familiar strikes an entity (launch / return / quick-fire contact). */
    public static void playImpact(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    /** Familiar reaches the player at the end of RETURNING. */
    public static void playReturnArrival(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.3f);
    }

    /** SWEEPING_HOLD contact with an entity. */
    public static void playSweepContact(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /** Guard raised (entering BLOCKING). Quieter/higher than the block-hit cue. */
    public static void playGuardRaised(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7f, 1.1f);
    }

    /** Guard broken (mana exhausted while BLOCKING). */
    public static void playGuardBreak(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Charge building loop (throttled one-shot). progress 0..1 raises the pitch. */
    public static void playChargeLoop(Level level, double x, double y, double z, float progress) {
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                0.5f, 0.6f + progress * 0.8f);
    }

    /** Hovering ambient (throttled one-shot, very quiet) [TUNE: most likely to annoy — easy to disable]. */
    public static void playHoverAmbient(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.2f, 1.4f);
    }

    /** Familiar death-fall (owner death / despawn). */
    public static void playDeathFall(Level level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.8f, 0.9f);
    }
```

- [ ] **Step 2: Compile** — `./gradlew build`. Expected: `BUILD SUCCESSFUL`. (Validates all 11 `SoundEvents.*` constants resolve against 1.21.1 mappings — if any name is wrong, it fails here.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordSounds.java
git commit -m "feat(audio): add Phase 10 placeholder sound helpers"
```

---

### Task 2: Mode enter / exit sounds

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/network/SwordModePacket.java`

- [ ] **Step 1: Exit sound** — in `handle(...)`, the `current == SwordMode.FLYING` branch, after the despawn call:

```java
                HeirloomSwordItem.setMode(held, SwordMode.NORMAL);
                held.remove(ModDataComponents.FAMILIAR_UUID.get());
                SwordFamiliarEntity.despawnForOwner(level, player.getUUID());
                SwordSounds.playModeExit(level, player.getX(), player.getY(), player.getZ());
```

- [ ] **Step 2: Enter sound** — in the `else` branch, after adding the familiar UUID:

```java
                HeirloomSwordItem.setMode(held, SwordMode.FLYING);
                SwordFamiliarEntity familiar = new SwordFamiliarEntity(level, player);
                level.addFreshEntity(familiar);
                held.set(ModDataComponents.FAMILIAR_UUID.get(), familiar.getUUID());
                SwordSounds.playModeEnter(level, player.getX(), player.getY(), player.getZ());
```

(`SwordSounds` is in the same package root; the file already imports `com.alucard.heirloomsword.*`.)

- [ ] **Step 2: Compile & commit**

```bash
./gradlew build
git add src/main/java/com/alucard/heirloomsword/network/SwordModePacket.java
git commit -m "feat(audio): mode enter/exit cues on F toggle"
```

---

### Task 3: Launch, impact (+particle), return-arrival, quick-fire, death-fall

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

All sites are server-side methods. `ParticleTypes` is already imported in this file. Use the fully-qualified `net.minecraft.server.level.ServerLevel` cast inline (the file already uses FQNs for `net.minecraft.sounds.*`).

- [ ] **Step 1: Launch sound** — in `launch(Vec3 direction, boolean charged)`, replace the final line:

```java
        setState(FamiliarState.LAUNCHING);
```

with:

```java
        setState(FamiliarState.LAUNCHING);
        if (!this.level().isClientSide) {
            SwordSounds.playLaunch(this.level(), getX(), getY(), getZ(), charged);
        }
```

- [ ] **Step 2: Impact sound + particle** — in `damageEntitiesInPath(... double hInflate)`, after the `for` loop that hurts entities (just before the method's closing `}`):

```java
            if (!returning) bloodyOwnerBlade(entity);
        }
        if (!entities.isEmpty() && this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            SwordSounds.playImpact(this.level(), getX(), getY(), getZ());
            sl.sendParticles(ParticleTypes.CRIT,
                    getX(), getY() + getBbHeight() * 0.5, getZ(), 8, 0.2, 0.2, 0.2, 0.0);
        }
    }
```

- [ ] **Step 3: Return-arrival sound** — in `tickReturning(...)`, inside the `if (distance <= PICKUP_RANGE)` block, after `this.targetPosition = computeCandidatePosition(owner, 0);`:

```java
            this.targetPosition = computeCandidatePosition(owner, 0);
            SwordSounds.playReturnArrival(this.level(), getX(), getY(), getZ());
            return;
```

- [ ] **Step 4: Quick-fire impact sound + particle** — in `tickQuickFire(...)`, inside the contact block, after `bloodyOwnerBlade(living);`:

```java
                bloodyOwnerBlade(living);
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    SwordSounds.playImpact(this.level(), getX(), getY(), getZ());
                    sl.sendParticles(ParticleTypes.CRIT,
                            getX(), getY() + getBbHeight() * 0.5, getZ(), 8, 0.2, 0.2, 0.2, 0.0);
                }
```

- [ ] **Step 5: Death-fall sound** — in `exitFlyingMode(...)`, after `setState(FamiliarState.DYING);`:

```java
            setState(FamiliarState.DYING);
            dyingTimer = 0;
            SwordSounds.playDeathFall(this.level(), getX(), getY(), getZ());
```

- [ ] **Step 6: Compile & commit**

```bash
./gradlew build
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat(audio): launch, impact (+crit particle), return-arrival, quick-fire, death-fall cues"
```

---

### Task 4: Sweep-contact, guard-raised, guard-break

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

- [ ] **Step 1: Sweep-contact** — at the end of `sweepDamageEntities(...)` (after the `for` loop; the method already `return`ed early if `entities.isEmpty()`, so a hit is guaranteed here):

```java
            sweepIFrames.put(entity.getId(), SWEEP_IFRAME_TICKS);
        }
        if (!this.level().isClientSide) {
            SwordSounds.playSweepContact(this.level(), getX(), getY(), getZ());
        }
    }
```

- [ ] **Step 2: Guard-raised** — add the same line after `setState(FamiliarState.BLOCKING);` in **all three** entry methods (`startBlocking`, `cancelChargeIntoBlock`, `cancelSweepIntoBlock`). For each, the edit is:

```java
        setState(FamiliarState.BLOCKING);
        if (!this.level().isClientSide) {
            SwordSounds.playGuardRaised(this.level(), getX(), getY(), getZ());
        }
```

(Three separate occurrences — apply individually. `startBlocking` is a one-line body; the other two have cleanup lines before `setState`, but the anchor `setState(FamiliarState.BLOCKING);` line is what each appends after.)

- [ ] **Step 3: Guard-break** — in `guardBreak()`, after `setGuardCooldown(60);`:

```java
        setState(FamiliarState.HOVERING);
        setGuardCooldown(60);
        if (!this.level().isClientSide) {
            SwordSounds.playGuardBreak(this.level(), getX(), getY(), getZ());
        }
```

- [ ] **Step 4: Compile & commit**

```bash
./gradlew build
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat(audio): sweep-contact, guard-raised, guard-break cues"
```

---

### Task 5: Charge-building and hovering ambient loops (throttled one-shots)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

- [ ] **Step 1: Charge loop** — in `tickCharging(...)`, after `chargeTimer++;`:

```java
        chargeTimer++;
        if (!isChargeReady() && chargeTimer % 10 == 0) {
            SwordSounds.playChargeLoop(this.level(), getX(), getY(), getZ(),
                    Math.min(1.0f, (float) chargeTimer / CHARGE_THRESHOLD_TICKS));
        }
```

- [ ] **Step 2: Hover ambient** — in `tickHovering(Player owner)`, add at the end of the method body (after `updateMobAwareness(owner);`):

```java
        updateMobAwareness(owner);
        if (this.tickCount % 80 == 0) {
            SwordSounds.playHoverAmbient(this.level(), getX(), getY(), getZ());
        }
```

- [ ] **Step 3: Compile & commit**

```bash
./gradlew build
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat(audio): throttled charge-building and hovering ambient loops"
```

---

### Task 6: Missing particles — deflection burst & embed burst

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordEventHandler.java`
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

- [ ] **Step 1: Import ParticleTypes in SwordEventHandler** — add with the other imports:

```java
import net.minecraft.core.particles.ParticleTypes;
```

- [ ] **Step 2: Deflection burst** — in `onProjectileImpact(...)`, after `event.setCanceled(true);`:

```java
        event.setCanceled(true);
        player.serverLevel().sendParticles(ParticleTypes.CRIT,
                projectile.getX(), projectile.getY(), projectile.getZ(), 10, 0.1, 0.1, 0.1, 0.2);
```

- [ ] **Step 3: Embed burst** — in `SwordFamiliarEntity.enterStuck()`, inside the existing `if (!this.level().isClientSide)` block, after the `playStuckImpact` call (keep that call as-is):

```java
        if (!this.level().isClientSide) {
            SwordSounds.playStuckImpact(this.level(), this.getX(), this.getY(), this.getZ());
            ((net.minecraft.server.level.ServerLevel) this.level()).sendParticles(ParticleTypes.POOF,
                    getX(), getY() + getBbHeight() * 0.5, getZ(), 12, 0.2, 0.3, 0.2, 0.02);
        }
```

- [ ] **Step 4: Compile & commit**

```bash
./gradlew build
git add src/main/java/com/alucard/heirloomsword/SwordEventHandler.java src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat(fx): deflection and embed particle bursts"
```

---

### Task 7: In-game verification matrix (authoritative — third-person review)

**Files:** none. Runtime behavior can't be unit-tested; this is the acceptance gate.

- [ ] **Step 1: Launch the client** — `./gradlew runClient` (GeckoLib is on the dev classpath; Epic Fight optional). Get the heirloom sword, enter flying mode.

- [ ] **Step 2: Confirm each new cue fires, in third person where visual:**

| Event | Expected sound | Expected particle |
|---|---|---|
| Press F (enter flying) | orb-pickup blip | — |
| Press F (exit to normal) | orb-pickup, pitched down | — |
| Launch (uncharged) | trident throw | END_ROD trail (existing) |
| Launch (charged) | trident throw, lower/louder | END_ROD trail (existing) |
| Launch/return hits a mob | attack-sweep thud | CRIT burst at sword |
| Quick-fire (V) hits a mob | attack-sweep thud | CRIT burst |
| Familiar returns to player | soft high orb-pickup | — |
| Hold sweep through a mob | knockback thwack | — |
| Raise guard (G) | soft shield raise | — |
| Guard breaks (drain mana to 0 while G) | shield break | — |
| Charging (hold to charge) | rising amethyst resonate loop | charge gather (existing) |
| Hovering idle | very faint enchant ambient every ~4s | hand shimmer (existing) |
| Block a projectile | shield (existing, unchanged) | **new** CRIT burst at deflect |
| Familiar embeds in block | mace smash (existing, unchanged) | **new** POOF burst |
| Owner dies while flying | trident hit-ground | death-fall anim (existing) |

- [ ] **Step 3: Confirm the 7 KEPT sounds are unchanged** (STUCK mace-smash, block-hit, block-slash, touchdown, warp, denied still sound exactly as before).

- [ ] **Step 4: Tuning pass** — adjust the volume/pitch constants in `SwordSounds.java` to taste (all are `[TUNE]`). The hover ambient (`playHoverAmbient`) is the most likely to annoy — lower its volume or raise its interval (the `% 80` in `tickHovering`) if so. No commit if only confirming; commit any tuning with `tune(audio): …`.

---

### Task 8: Mark Phase 10 audio/particles done in the design doc

**Files:**
- Modify: `docs/alucard_sword_design_v3.md`

- [ ] **Step 1: Append a status note** to the "Phase 10 — Audio and Polish" entry (Section 24):

```markdown
> **Phase 10 status (2026-06-15): audio triggers + particles DONE (Plan A, placeholders).** All §21
> sound events except the 3 tether sounds (Phase 11) are now wired to vanilla placeholders, centralized
> in `SwordSounds`. Added deflection + embed particle bursts and impact CRIT bursts. The 7 pre-existing
> cues were kept as-is per user preference. **Still deferred to final release:** custom `.ogg` files +
> `DeferredRegister<SoundEvent>` + `sounds.json` (Plan B), and true looping sound instances (hover/charge
> are throttled one-shots). Third-person review pass completed in-game.
```

- [ ] **Step 2: Commit**

```bash
git add docs/alucard_sword_design_v3.md
git commit -m "docs(phase10): audio triggers and particles complete (Plan A placeholders)"
```

---

## Self-Review

**Spec coverage (§21 sound table):** hovering ambient ✅(T5), mode enter ✅(T2), mode exit ✅(T2), launch uncharged/charged ✅(T3), impact on entity ✅(T3), embed ✅(kept), return arrival ✅(T3), sweep contact ✅(T4), block raised ✅(T4), block hit ✅(kept), deflect ✅(kept sound + T6 particle), slash release ✅(kept), guard break ✅(T4), charge building ✅(T5), death fall ✅(T3); tether start/loop/arrival = Phase 11 (out of scope, noted). Particle list (impact ✅T3, deflection ✅T6, embed ✅T6, spawn ✅kept, tether = Phase 11).

**Placeholder scan:** every step shows exact code or exact command; the only "to taste" content is the Task 7 tuning pass, explicitly gated behind in-game review.

**Type/name consistency:** helper names (`playModeEnter/Exit/Launch/Impact/ReturnArrival/SweepContact/GuardRaised/GuardBreak/ChargeLoop/HoverAmbient/DeathFall`) are defined in Task 1 and called identically in Tasks 2–5. All hooks are server-side (`!isClientSide` guarded or inside server tick methods); particles use `ServerLevel.sendParticles`. `CHARGE_THRESHOLD_TICKS`, `SWEEP_IFRAME_TICKS`, `PICKUP_RANGE`, `ParticleTypes` all already exist in the target files.
