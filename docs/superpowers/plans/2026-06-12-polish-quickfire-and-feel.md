# Combat Feel, Smoothness, Sky-Drop Spawn, Quick-Fire & Hand Poses Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tune combat feel (faster spin/launches, lower i-frames and sweep knockback), make every realistic state transition visually smooth, replace the materialize spawn with a sky-drop, add charging/launching particles, add a V-key homing quick-fire attack, and add first-person telekinesis hand poses.

**Architecture:** All gameplay stays server-authoritative in `SwordFamiliarEntity`; new visual machinery (state-transition tracker, spin ramp, slash window, hand poses) is client-side only. Two new states are added to the familiar state machine: `ARRIVING` (sky-drop descent) and `QUICK_FIRE` (homing dart). One new packet (`SwordQuickFirePacket`) and one new keybind (V).

**Tech Stack:** NeoForge 21.1.233 (MC 1.21.1, Java 21), GeckoLib 4.8.4.

---

## Context for the implementer

- **Read `CLAUDE.md` in the repo root first.** Verify any Minecraft/NeoForge signature you're unsure of with the mcmodding-mcp tools (`search_mappings`, `get_class_details`) for MC **1.21.1**. No automated test harness exists — each task verifies via `gradlew.bat build` plus the in-game check listed. Run `graphify update .` after code changes. Commit after each task.
- **This plan builds on the completed 2026-06-11 plan** (`docs/superpowers/plans/2026-06-11-sword-mechanics-and-guard-fixes.md`), all of whose commits are in `main`. Line references are against that state.
- **User decisions already made (do not re-ask):** quick-fire = homing dart that always connects, ~12 dmg, ~1s cooldown, returns immediately after hit; sweep release outbound = spinning throw (sawblade keeps spinning as it flies out; return trip stays hilt-first); hand poses = first-person only (no PlayerAnimator dependency); spawn = sky-drop with materialize fallback when there's no vertical clearance.
- **Task dependency:** Task 3 introduces the client state-transition tracker (`lastClientState`). Tasks 4 and 8 use it. Do Tasks 3 → 4 → 8 in order; everything else is independent.
- **Hotfixes landed after this plan was written:** (1) commit `3d0d0e9` — undead-burn (`igniteIfUndead`/`burnUndeadOnContact` in `SwordFamiliarEntity`, `hurtEnemy` in `HeirloomSwordItem`) and hover tuning (`HOVER_RADIUS` is now **1.65**, anchor multiplier **0.45**); (2) smart-slash gate — `stopBlocking()` only fires the slash when `hasSlashTarget()` (public helper, hostile in the frontal 3-block arc) is true. Code anchors in this plan may sit a few lines off; match on content, not line numbers. Task 1's table intentionally does not change `HOVER_RADIUS`.

### Smoothness audit (what "smooth between every realistic state" maps to)

| Transition | Problem today | Fixed by |
|---|---|---|
| HOVERING ↔ horizontal lock-on | linear 5-tick blend, slightly abrupt | Task 1 (slower rate) + Task 2 (smoothstep easing) |
| BLOCKING → HOVERING (slash) | horizontal blend decays during the slash clip → 90° tip + 1.5-block rise pollute the wind-up | Task 3 (slash window holds the blocking pose until the clip ends) |
| HOVERING → SWEEPING_HOLD | spin starts at full speed with a yaw pop | Task 4 (spin speed ramps in over ~8 ticks) |
| SWEEPING_HOLD → SWEEPING_RELEASE | instant snap from spin to tip-first | Task 4 (outbound keeps spinning — thrown sawblade) |
| RETURNING/SWEEP → HOVERING arrival | `setPos` teleport to hover slot | Task 5 (spring glides in, no snap) |
| CHARGING → LAUNCHING | instant orientation snap | intentional — launches should feel explosive; not changed |
| spawn → HOVERING | grow-in scale | Task 8 (sky-drop with impact ring; fallback keeps materialize) |

---

## File Map

| File | Tasks | Change |
|---|---|---|
| `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java` | 1,3,4,5,6,7,8,9 | tuning constants, transition tracker, slash window, spin ramp/throw, arrival smoothing, charged sync, particles, ARRIVING + QUICK_FIRE states |
| `src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java` | 2,3,4,8 | smoothstep blend, slash-window pose, spinning-throw render path, sky-drop scale suppression |
| `src/main/java/com/alucard/heirloomsword/FamiliarState.java` | 8,9 | ARRIVING(9), QUICK_FIRE(10) |
| `src/main/java/com/alucard/heirloomsword/ModKeybinds.java` | 9 | V keybind |
| `src/main/java/com/alucard/heirloomsword/network/SwordQuickFirePacket.java` (new) | 9 | quick-fire packet |
| `src/main/java/com/alucard/heirloomsword/network/ModNetwork.java` | 9 | register packet |
| `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java` | 9,10 | V key handling, hand-pose hook |
| `src/main/java/com/alucard/heirloomsword/client/TelekinesisHandRenderer.java` (new) | 10 | first-person arm poses |
| `src/main/resources/assets/heirloomswordmod/lang/en_us.json` | 9 | keybind name |

---

### Task 1: Combat & speed tuning constants

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

All single-constant edits, all `[TUNE]`:

- [ ] **Step 1: Apply the new values**

| Constant | Old | New | Why |
|---|---|---|---|
| `LAUNCH_SPEED_NORMAL` | 1.6 | 2.08 | +30% per user |
| `LAUNCH_SPEED_CHARGED` | 3.2 | 4.8 | +50% per user |
| `SWEEP_IFRAME_TICKS` | 10 | 6 | higher hit rate instead of more damage |
| `SWEEP_KNOCKBACK_STRENGTH` | 0.6f | 0.3f | less knockback so mobs stay in the blade |
| `SWEEP_SPIN_DEG_PER_TICK` | 40.0f | 60.0f | faster sawblade |

`RETURN_SPEED` stays 1.8 (user: unchanged).

Also in `updateOrientation()`, slow the horizontal blend (line ~1072):

```java
        horizontalProgress = Mth.approach(horizontalProgress, targetProgress, 0.1f); // [TUNE] was 0.2 — softer lock-on blend
```

- [ ] **Step 2: Compile**

Run: `gradlew.bat build` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "tune: faster launches and spin, lower sweep i-frames and knockback"
```

---

### Task 2: Smoothstep easing on the horizontal blend

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java`

The blend factor is linear; ease it in the renderer so the lock-on tilt accelerates and decelerates gently. (Entity-side `hProgress` stays linear — the hitbox doesn't need easing.)

- [ ] **Step 1: Ease hProgress where it's read**

In `preRender`, right after `float hProgress = animatable.getHorizontalProgress(partialTick);` add:

```java
        // Smoothstep easing: gentle start and settle on the vertical<->horizontal blend
        hProgress = hProgress * hProgress * (3.0f - 2.0f * hProgress);
```

(All later uses of `hProgress` in the method automatically pick up the eased value.)

- [ ] **Step 2: Compile + in-game check**

Run: `gradlew.bat build`, then `runClient`: walk toward/away from a zombie so the sword tilts into and out of alert mode — the tilt should ease in/out rather than start and stop abruptly (now ~10 ticks with soft ends).

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java
git commit -m "feat: smoothstep easing on horizontal blend"
```

---

### Task 3: Client transition tracker + block-slash window fix

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`
- Modify: `src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java`

When G is released, the state flips BLOCKING→HOVERING the same tick the 0.7s `block_slash` clip starts, so the horizontal blend decays mid-clip and the renderer re-applies the 90° tip-forward + 1.5-block translate decay over the clip's wind-up. Fix: a **client-only visual timer** — when the client observes the BLOCKING→HOVERING transition, hold the blocking-style pose for 14 ticks (the clip length) so only the clip's own motion shows. This also introduces the `lastClientState` transition tracker reused by Tasks 4 and 8.

- [ ] **Step 1: Add tracker and slash-window fields to the entity**

Near the spin fields:

```java
    // Client-side state-transition tracking (visual effects only)
    private FamiliarState lastClientState = FamiliarState.HOVERING;
    private int slashVisualTicks = 0;
    private static final int SLASH_VISUAL_TICKS = 14; // matches block_slash clip (0.7s)

    public boolean isSlashing() {
        return slashVisualTicks > 0;
    }
```

- [ ] **Step 2: Drive them at the top of `clientTick()`**

Insert at the very start of `clientTick()` (before the tickCount==1 burst):

```java
        FamiliarState st = getState();
        if (st != lastClientState) {
            onClientStateChange(lastClientState, st);
            lastClientState = st;
        }
        if (slashVisualTicks > 0) slashVisualTicks--;
```

And add the hook method (Tasks 4 and 8 extend it):

```java
    private void onClientStateChange(FamiliarState from, FamiliarState to) {
        if (from == FamiliarState.BLOCKING && to == FamiliarState.HOVERING && hasSlashTarget()) {
            // hasSlashTarget() already exists (smart-slash hotfix): the server only fires
            // block_slash when a hostile is in reach, so mirror that gate here — otherwise
            // this window would hold the guard pose on slash-less releases.
            slashVisualTicks = SLASH_VISUAL_TICKS; // block_slash is playing — hold the guard pose
        }
    }
```

- [ ] **Step 3: Honor the window in `updateOrientation()`**

In the `shouldBeHorizontal` switch, change the HOVERING case:

```java
            case HOVERING -> awarenessTarget != null || slashVisualTicks > 0;
```

And in the orientation chain, insert **before** the first `if (getState() == FamiliarState.HOVERING && awarenessTarget != null)` branch:

```java
        if (currentState == FamiliarState.HOVERING && slashVisualTicks > 0) {
            // Mid block-slash: keep the guard-style facing so only the clip moves the model
            Player slashOwner = getOwner();
            if (slashOwner != null) {
                Vec3 look = slashOwner.getLookAngle();
                targetYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
                targetPitch = 0;
            }
        } else if (getState() == FamiliarState.HOVERING && awarenessTarget != null) {
```

(i.e. the existing first branch becomes the `else if`.)

- [ ] **Step 4: Honor the window in the renderer**

In `preRender`, the BLOCKING check that skips the tip-forward rotation becomes:

```java
            if (animatable.getState() != FamiliarState.BLOCKING && !animatable.isSlashing()) {
                float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
                poseStack.mulPose(Axis.XP.rotationDegrees(hProgress * (90.0f - pitch)));
            }
```

- [ ] **Step 5: Compile + in-game check**

`gradlew.bat build`, then `runClient`: guard with G, release. The sword must stay in the guard position/orientation while the slash clip plays and only blend back to vertical hover **after** the arc completes. No tip-forward lurch, no upward slide during the wind-up.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java
git commit -m "fix: hold guard pose during block_slash clip (client slash window)"
```

---

### Task 4: Spin ramp-in + spinning throw on sweep release

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`
- Modify: `src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java`

Two changes: (a) the sawblade revs up over ~8 ticks instead of starting at full speed; (b) on release, the **outbound** flight keeps the spin (decaying with flight speed) — a thrown sawblade — which also removes the HOLD→RELEASE orientation snap. The return trip stays hilt-first exactly as today.

- [ ] **Step 1: Generalize the spin ticking in the entity**

Add fields + helper next to the existing spin fields:

```java
    private int spinRampTicks = 0;
    private static final int SPIN_RAMP_TICKS = 8; // [TUNE] rev-up time

    private void tickSpinClient(float speedScale) {
        spinAngleO = spinAngle;
        spinAngle += SWEEP_SPIN_DEG_PER_TICK * speedScale;
        if (spinAngleO >= 360.0f) { // wrap both together so the partialTick lerp never jumps
            spinAngle -= 360.0f;
            spinAngleO -= 360.0f;
        }
    }

    public boolean isSweepReturning() {
        return sweepReturning;
    }
```

In `onClientStateChange` (from Task 3) add:

```java
        if (to == FamiliarState.SWEEPING_HOLD) {
            spinRampTicks = 0; // rev the sawblade up from rest
        }
```

- [ ] **Step 2: Use it in both sweep client ticks**

In `tickSweepingHoldClient`, **replace** the existing inline spin-update block (the `spinAngleO = spinAngle; ... }` lines at the top) with:

```java
        spinRampTicks++;
        tickSpinClient(Math.min(1.0f, spinRampTicks / (float) SPIN_RAMP_TICKS));
```

In `tickSweepingReleaseClient`, at the top of the **outbound** path (after the `if (sweepReturning) { ... return; }` block), insert:

```java
        // Thrown sawblade: keep spinning, decaying with flight speed
        tickSpinClient(Math.max(0.35f, (float) (this.sweepVelocity.length() / SWEEP_MAX_SPEED)));
```

- [ ] **Step 3: Stable orientation on outbound release**

In `updateOrientation()`, replace the SWEEPING_RELEASE branch with:

```java
        } else if (getState() == FamiliarState.SWEEPING_RELEASE) {
            if (!sweepReturning) {
                // Spinning throw — renderer shows the spin; keep synced rotation stable
                Player owner = getOwner();
                if (owner != null) targetYaw = owner.getYRot();
                targetPitch = 0;
            } else {
                Vec3 dir = this.sweepVelocity; // stored reversed → hilt leads
                if (dir.lengthSqr() > 0.05) {
                    targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                    double horizDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
                    targetPitch = (float) -Math.toDegrees(Math.atan2(dir.y, horizDist));
                }
            }
```

- [ ] **Step 4: Disc hitbox + wider damage on outbound release**

In `makeBoundingBox()`, widen the SWEEPING_HOLD disc case to:

```java
        if (getState() == FamiliarState.SWEEPING_HOLD
                || (getState() == FamiliarState.SWEEPING_RELEASE && !sweepReturning)) {
```

In `tickSweepingRelease` (server), the outbound damage call currently is `damageEntitiesInPath(currentPos, nextPos, sweepReleaseHitSet, SWEEP_RELEASE_DAMAGE, owner);`. Add a horizontal-inflation overload and use it for the outbound disc:

```java
    private void damageEntitiesInPath(Vec3 from, Vec3 to, Set<Integer> hitSet, float damage, Player owner) {
        damageEntitiesInPath(from, to, hitSet, damage, owner, 0.5);
    }

    private void damageEntitiesInPath(Vec3 from, Vec3 to, Set<Integer> hitSet, float damage, Player owner, double hInflate) {
        AABB sweepBox = new AABB(
                Math.min(from.x, to.x) - hInflate, Math.min(from.y, to.y) - 0.5, Math.min(from.z, to.z) - hInflate,
                Math.max(from.x, to.x) + hInflate, Math.max(from.y, to.y) + 0.5, Math.max(from.z, to.z) + hInflate
        );
        // ... rest identical to the existing method body
    }
```

Outbound call becomes:

```java
        damageEntitiesInPath(currentPos, nextPos, sweepReleaseHitSet, SWEEP_RELEASE_DAMAGE, owner, SWORD_HALF_LENGTH);
```

(Launch/return/sweep-return calls keep the 5-arg form → unchanged 0.5 inflation.)

- [ ] **Step 5: Render the outbound spin**

In `SwordFamiliarGeoRenderer.preRender`, the spin path condition becomes:

```java
        boolean spinning = animatable.getState() == FamiliarState.SWEEPING_HOLD
                || (animatable.getState() == FamiliarState.SWEEPING_RELEASE && !animatable.isSweepReturning());

        if (spinning) {
            // Sawblade: continuous yaw spin, blade flattened into the horizontal plane.
            ...existing spin-path body unchanged...
        }
```

- [ ] **Step 6: Use the sweep_hold clip on outbound**

In `animationPredicate`, change:

```java
            case SWEEPING_RELEASE -> anim = sweepReturning ? "return_hilt" : "sweep_hold";
```

(The `launch` clip's wobble fought the spin; `sweep_hold` is the natural companion.)

- [ ] **Step 7: Compile + in-game check**

`gradlew.bat build`, then `runClient`: (a) starting a sweep revs the blade up smoothly from rest — no yaw pop; (b) flinging it sends a spinning disc flying out, spin slowing as it slows, hitting mobs along the way; (c) when it turns around it flips to hilt-first return exactly as before; (d) F3+B shows the disc hitbox during outbound.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java
git commit -m "feat: sawblade rev-up and spinning throw on sweep release"
```

---

### Task 5: Smooth arrivals — no teleport snap into the hover slot

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

`tickReturning` and `enterHoveringFromSweep` both `setPos(hoverPos)` on arrival — a visible teleport of up to ~1.5 blocks. Let the hover spring glide the last stretch instead.

- [ ] **Step 1: Remove the snaps**

In `tickReturning`, the arrival block becomes:

```java
        if (distance <= PICKUP_RANGE) {
            // Arrived — hand over to the hover spring, which glides it into the slot
            setState(FamiliarState.HOVERING);
            this.velocity = Vec3.ZERO;
            this.smoothedAnchorY = Double.NaN;
            this.targetPosition = computeCandidatePosition(owner, 0);
            return;
        }
```

In `enterHoveringFromSweep`, the `if (owner != null)` block becomes:

```java
        if (owner != null) {
            this.targetPosition = computeCandidatePosition(owner, 0);
        }
```

(`tickReturningClient` already does nothing inside pickup range — no client change needed.)

- [ ] **Step 2: Compile + in-game check**

`gradlew.bat build`, then `runClient`: launch + recall, and sweep + release; in both cases the sword glides into its hover slot on return instead of teleporting the last meter. Confirm it doesn't orbit/oscillate around the slot (the spring is well-damped; if it overshoots, that's a [TUNE] on SPRING_DAMPING — report, don't change).

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "fix: returning sword glides into hover slot instead of teleporting"
```

---

### Task 6: Sync the charged-launch flag (fixes prediction, enables trail tiers)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

`chargedLaunch` is server-only, so the client predicts charged launches at 1.6 instead of 4.8 → rubber-banding (worse now that speeds went up), and Task 7 needs the flag for the trail.

- [ ] **Step 1: Add the synced flag**

Next to `DATA_LAUNCH_DIR`:

```java
    private static final EntityDataAccessor<Boolean> DATA_CHARGED =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.BOOLEAN);
```

In `defineSynchedData`: `builder.define(DATA_CHARGED, false);`

In `launch()`, after setting `this.chargedLaunch = charged;` add:

```java
        this.entityData.set(DATA_CHARGED, charged);
```

Add accessor:

```java
    public boolean isChargedLaunch() {
        return this.entityData.get(DATA_CHARGED);
    }
```

- [ ] **Step 2: Use it client-side**

In `tickLaunchingClient`, replace `chargedLaunch` with `isChargedLaunch()`:

```java
        double speed = isChargedLaunch() ? LAUNCH_SPEED_CHARGED : LAUNCH_SPEED_NORMAL;
```

- [ ] **Step 3: Compile + commit**

`gradlew.bat build` → BUILD SUCCESSFUL.

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "fix: sync charged-launch flag for correct client prediction"
```

---

### Task 7: Charging gather particles + faint launch trail

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

All client-side, all rates `[TUNE]`. Charging: ENCHANT glyphs converging on the blade, ramping with charge; END_ROD sparkles once fully charged. Launching: a very faint trail — denser/dragon-breath for charged.

- [ ] **Step 1: Charging gather in `tickChargingClient`**

Append at the end of `tickChargingClient(Player owner)`:

```java
        // Telekinetic gather — converging glyphs, ramping with charge [TUNE rates]
        int count = 1 + Math.min(chargeTimer / 15, 3);
        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double dist = 0.8 + this.random.nextDouble() * 0.6;
            double px = getX() + Math.cos(angle) * dist;
            double py = getY() + getBbHeight() * 0.5 + (this.random.nextDouble() - 0.5) * 1.2;
            double pz = getZ() + Math.sin(angle) * dist;
            this.level().addParticle(ParticleTypes.ENCHANT, px, py, pz,
                    (getX() - px) * 0.35,
                    (getY() + getBbHeight() * 0.5 - py) * 0.35,
                    (getZ() - pz) * 0.35);
        }
        if (isChargeReady() && this.tickCount % 4 == 0) {
            this.level().addParticle(ParticleTypes.END_ROD,
                    getX(), getY() + getBbHeight() * 0.5, getZ(),
                    (this.random.nextDouble() - 0.5) * 0.1, 0.05, (this.random.nextDouble() - 0.5) * 0.1);
        }
```

- [ ] **Step 2: Faint trail in `tickLaunchingClient`**

Append at the end of `tickLaunchingClient()` (after the `setPos`):

```java
        // Very faint flight trail [TUNE density]
        if (this.random.nextFloat() < (isChargedLaunch() ? 0.9f : 0.6f)) {
            this.level().addParticle(isChargedLaunch() ? ParticleTypes.DRAGON_BREATH : ParticleTypes.WITCH,
                    getX() + (this.random.nextDouble() - 0.5) * 0.3,
                    getY() + getBbHeight() * 0.5 + (this.random.nextDouble() - 0.5) * 0.3,
                    getZ() + (this.random.nextDouble() - 0.5) * 0.3,
                    0, 0, 0);
        }
```

- [ ] **Step 3: Compile + in-game check**

`gradlew.bat build`, then `runClient`: holding left-click shows glyphs streaming into the blade, intensifying, with white sparkles at full charge; launching leaves a subtle trail (purple normal, pink-ish charged) that does **not** obscure the sword. If it reads as too busy, halve the rates — they're [TUNE].

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat: charging gather particles and faint launch trail"
```

---

### Task 8: Sky-drop spawn (ARRIVING state)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/FamiliarState.java`
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`
- Modify: `src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java`

The sword spawns high above the hover slot and plummets to it, arresting with an impact ring. The geo model hovers tip-down ("blade tip at y=0"), so a falling sword is already in its hover orientation — no special pose needed. **Fallback:** if there's less than 6 blocks of clearance (cave/house), spawn at the slot with the existing materialize effect. No spawn-site change needed in `SwordModePacket` — the entity constructor decides.

- [ ] **Step 1: Add the state**

`FamiliarState.java`: add `ARRIVING(9)` to the enum and `case 9 -> ARRIVING;` to `fromId`.

- [ ] **Step 2: Constants + spawn decision in the entity constructor**

Constants:

```java
    private static final double SKY_DROP_HEIGHT = 16.0;  // [TUNE] how high above the slot
    private static final double MIN_SKY_CLEARANCE = 6.0; // below this, fall back to materialize
    private static final double ARRIVE_SPEED = 2.5;      // [TUNE] blocks/tick descent
```

Replace the `(Level, Player)` constructor body:

```java
    public SwordFamiliarEntity(Level level, Player owner) {
        this(ModEntities.SWORD_FAMILIAR.get(), level);
        this.entityData.set(DATA_OWNER_UUID, Optional.of(owner.getUUID()));
        Vec3 hoverPos = computeCandidatePosition(owner, 0);
        this.targetPosition = hoverPos;

        // Sky-drop entrance when there's vertical clearance; materialize otherwise
        BlockHitResult skyHit = level.clip(new ClipContext(
                hoverPos, hoverPos.add(0, SKY_DROP_HEIGHT, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double clearance = skyHit.getType() == HitResult.Type.BLOCK
                ? skyHit.getLocation().y - hoverPos.y
                : SKY_DROP_HEIGHT;
        if (clearance >= MIN_SKY_CLEARANCE) {
            this.setPos(hoverPos.add(0, clearance - 1.0, 0));
            setState(FamiliarState.ARRIVING);
        } else {
            this.setPos(hoverPos);
        }
    }
```

- [ ] **Step 3: Tick the descent**

Add to the `serverTick` switch: `case ARRIVING -> tickArriving(owner);` and to the `clientTick` switch: `case ARRIVING -> tickArrivingClient();`

```java
    private void tickArriving(Player owner) {
        Vec3 hoverPos = computeCandidatePosition(owner, 0);
        Vec3 toTarget = hoverPos.subtract(this.position());
        if (toTarget.length() <= ARRIVE_SPEED) {
            this.setPos(hoverPos);
            this.targetPosition = hoverPos;
            this.velocity = Vec3.ZERO;
            this.smoothedAnchorY = Double.NaN;
            setState(FamiliarState.HOVERING);
            this.level().playSound(null, this.blockPosition(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK, // [TUNE] impact sound
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.7f);
            return;
        }
        this.setPos(this.position().add(toTarget.normalize().scale(ARRIVE_SPEED)));
    }

    private void tickArrivingClient() {
        // Falling streak [TUNE density]
        for (int i = 0; i < 2; i++) {
            this.level().addParticle(ParticleTypes.END_ROD,
                    getX() + (this.random.nextDouble() - 0.5) * 0.3,
                    getY() + this.random.nextDouble() * 2.5,
                    getZ() + (this.random.nextDouble() - 0.5) * 0.3,
                    0, 0.1, 0);
        }
    }
```

- [ ] **Step 4: Impact ring + keep materialize only for fallback**

In `onClientStateChange` (Task 3) add:

```java
        if (from == FamiliarState.ARRIVING && to == FamiliarState.HOVERING) {
            for (int i = 0; i < 24; i++) {
                double angle = (Math.PI * 2 * i) / 24;
                this.level().addParticle(ParticleTypes.WITCH,
                        getX() + Math.cos(angle) * 0.8, getY(), getZ() + Math.sin(angle) * 0.8,
                        Math.cos(angle) * 0.15, 0.05, Math.sin(angle) * 0.15);
            }
        }
```

Gate the existing tickCount==1 materialize burst in `clientTick()`:

```java
        if (this.tickCount == 1 && getState() != FamiliarState.ARRIVING) {
```

Add a sky-drop marker for the renderer (initial entity data arrives with the spawn packet, so the first client tick already sees ARRIVING):

```java
    private boolean skyDropSpawn = false;

    public boolean isSkyDropSpawn() {
        return skyDropSpawn;
    }
```

and inside the gated tickCount==1 block's `else` / before it:

```java
        if (this.tickCount == 1) {
            skyDropSpawn = getState() == FamiliarState.ARRIVING;
        }
```

- [ ] **Step 5: State plumbing**

In `updateOrientation()`'s `shouldBeHorizontal` switch add: `case ARRIVING -> false;` (a dropping sword stays vertical — without this it falls into the horizontal `default -> true`).

In `SwordFamiliarGeoRenderer.preRender`, gate the grow-in scale (it's the materialize effect):

```java
        if (animatable.tickCount < 10 && !animatable.isSkyDropSpawn()) {
```

F is already locked during ARRIVING on both sides (the allowed-state lists in `SwordModePacket`/client only contain HOVERING/SWEEPING_HOLD/BLOCKING) — no change needed; just verify in-game.

- [ ] **Step 6: Compile + in-game check**

`gradlew.bat build`, then `runClient`: (a) outdoors, pressing F makes the sword streak down from ~15 blocks up with a light trail and arrest at the hover slot with a particle ring + chime — no grow-in scale; (b) in a 3-block-high room it materializes exactly as before; (c) pressing F again during the descent does nothing; (d) the descent tracks you if you walk.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/FamiliarState.java src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java src/main/java/com/alucard/heirloomsword/client/SwordFamiliarGeoRenderer.java
git commit -m "feat: sky-drop spawn with materialize fallback (ARRIVING state)"
```

---

### Task 9: V-key quick-fire (QUICK_FIRE homing state)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/FamiliarState.java`
- Modify: `src/main/java/com/alucard/heirloomsword/ModKeybinds.java`
- Create: `src/main/java/com/alucard/heirloomsword/network/SwordQuickFirePacket.java`
- Modify: `src/main/java/com/alucard/heirloomsword/network/ModNetwork.java`
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java`
- Modify: `src/main/resources/assets/heirloomswordmod/lang/en_us.json`

V while HOVERING with a locked-on target (the alert/horizontal pose): the sword darts at the target, re-aiming every tick (homing — always connects), hits for 12 `[TUNE]`, then immediately enters RETURNING. Not chargeable, ~1s cooldown `[TUNE]`. **Anti-cheat note:** the packet carries no target — the server uses its **own** `awarenessTarget`, so clients can't spoof targets.

- [ ] **Step 1: State + keybind + lang**

`FamiliarState.java`: add `QUICK_FIRE(10)` and `case 10 -> QUICK_FIRE;`.

`ModKeybinds.java`:

```java
    public static final KeyMapping QUICK_FIRE = new KeyMapping(
            "key.heirloomswordmod.quick_fire",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );
```

Register it in `HeirloomSwordModClient.ModBusEvents.onRegisterKeyMappings`: `event.register(ModKeybinds.QUICK_FIRE);`

`en_us.json`: add `"key.heirloomswordmod.quick_fire": "Quick Fire (Familiar)"` (match the file's existing key style).

- [ ] **Step 2: Packet**

`SwordQuickFirePacket.java` — same shape as `SwordRecallPacket`:

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

public record SwordQuickFirePacket() implements CustomPacketPayload {
    public static final Type<SwordQuickFirePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_quick_fire"));

    public static final StreamCodec<ByteBuf, SwordQuickFirePacket> STREAM_CODEC =
            StreamCodec.unit(new SwordQuickFirePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordQuickFirePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) return;
            if (!HeirloomSwordItem.isFlying(held)) return;

            ServerLevel level = player.serverLevel();
            SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(level, player.getUUID());
            if (familiar == null) return;
            if (familiar.getState() != FamiliarState.HOVERING) return;

            familiar.quickFire(); // validates target + cooldown internally
        });
    }
}
```

Register in `ModNetwork.register` (same pattern as the others):

```java
        registrar.playToServer(
                SwordQuickFirePacket.TYPE,
                SwordQuickFirePacket.STREAM_CODEC,
                SwordQuickFirePacket::handle
        );
```

- [ ] **Step 3: Entity — state data, entry, tick**

Constants + synced target id + cooldown:

```java
    private static final float QUICK_FIRE_DAMAGE = 12.0f;       // [TUNE]
    private static final int QUICK_FIRE_COOLDOWN_TICKS = 20;    // [TUNE] ~1s
    private static final EntityDataAccessor<Integer> DATA_QUICKFIRE_TARGET =
            SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.INT);
    private int quickFireCooldown = 0;
```

`defineSynchedData`: `builder.define(DATA_QUICKFIRE_TARGET, 0);`

Entry (server only — called from the packet):

```java
    public void quickFire() {
        if (quickFireCooldown > 0) return;
        Entity target = this.awarenessTarget; // server's own lock-on; never client-supplied
        if (target == null || !target.isAlive()) return;
        this.entityData.set(DATA_QUICKFIRE_TARGET, target.getId());
        this.quickFireCooldown = QUICK_FIRE_COOLDOWN_TICKS;
        setState(FamiliarState.QUICK_FIRE);
    }
```

Cooldown tick — first line of `tickHovering`:

```java
        if (quickFireCooldown > 0) quickFireCooldown--;
```

Server tick (add `case QUICK_FIRE -> tickQuickFire(owner);` to the serverTick switch):

```java
    private void tickQuickFire(Player owner) {
        Entity target = this.level().getEntity(this.entityData.get(DATA_QUICKFIRE_TARGET));
        if (target == null || !target.isAlive()
                || this.position().distanceTo(owner.position()) > MAX_LAUNCH_RANGE) {
            enterReturning();
            return;
        }

        Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 toTarget = targetCenter.subtract(this.position());

        // Contact: hit and immediately come home
        if (toTarget.length() <= LAUNCH_SPEED_NORMAL
                || this.getBoundingBox().inflate(0.3).intersects(target.getBoundingBox())) {
            if (target instanceof LivingEntity living) {
                living.hurt(this.level().damageSources().playerAttack(owner), QUICK_FIRE_DAMAGE);
                igniteIfUndead(living); // helper exists in this class (2026-06-12 hotfix: undead burn)
                living.knockback(0.3, this.getX() - living.getX(), this.getZ() - living.getZ());
            }
            enterReturning();
            return;
        }

        // Homing: re-aim every tick
        Vec3 dir = toTarget.normalize();
        Vec3 nextPos = this.position().add(dir.scale(LAUNCH_SPEED_NORMAL));
        BlockHitResult blockHit = this.level().clip(new ClipContext(
                this.position(), nextPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            enterReturning(); // quick-fire never sticks — just comes back
            return;
        }
        this.setPos(nextPos);
    }
```

Client prediction (add `case QUICK_FIRE -> tickQuickFireClient();` to the clientTick switch):

```java
    private void tickQuickFireClient() {
        Entity target = this.level().getEntity(this.entityData.get(DATA_QUICKFIRE_TARGET));
        if (target == null) return;
        Vec3 targetCenter = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 toTarget = targetCenter.subtract(this.position());
        if (toTarget.length() <= LAUNCH_SPEED_NORMAL) return;
        this.setPos(this.position().add(toTarget.normalize().scale(LAUNCH_SPEED_NORMAL)));
    }
```

- [ ] **Step 4: Orientation + animation**

`updateOrientation()` — add after the LAUNCHING branch:

```java
        } else if (getState() == FamiliarState.QUICK_FIRE) {
            Entity target = this.level().getEntity(this.entityData.get(DATA_QUICKFIRE_TARGET));
            if (target != null) {
                Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(this.position());
                targetYaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                double horizDist = Math.sqrt(to.x * to.x + to.z * to.z);
                targetPitch = (float) -Math.toDegrees(Math.atan2(to.y, horizDist));
            }
```

And include it in the snap-lerp list:

```java
        float lerpFactor = (currentState == FamiliarState.LAUNCHING || currentState == FamiliarState.STUCK
                || currentState == FamiliarState.QUICK_FIRE) ? 1.0f : 0.25f;
```

`animationPredicate`: add `case QUICK_FIRE -> anim = "launch";`

(`shouldBeHorizontal` default already yields `true` for QUICK_FIRE — correct, tip-first dart. F stays locked during it since QUICK_FIRE isn't in the allowed exit lists. R/recall is ignored by `recall()`'s state check — fine, the dart resolves in well under a second.)

- [ ] **Step 5: Client key handling**

In `HeirloomSwordModClient.ClientEvents.onClientTick`, after the R-key block:

```java
            // Handle V key (quick fire at the locked-on target)
            while (ModKeybinds.QUICK_FIRE.consumeClick()) {
                if (!HeirloomSwordItem.isFlying(held)) continue;
                SwordFamiliarEntity familiar = findClientFamiliar(player);
                if (familiar == null || familiar.getState() != FamiliarState.HOVERING) continue;
                if (familiar.getAwarenessTarget() == null) continue; // needs a lock-on
                PacketDistributor.sendToServer(new SwordQuickFirePacket());
            }
```

(Import `SwordQuickFirePacket`.)

- [ ] **Step 6: Compile + in-game check**

`gradlew.bat build`, then `runClient`: with a zombie in range (sword tilted horizontal/alert), press V — the sword darts to the mob, hits (~12, small knockback), and flies straight back to hover. Press V repeatedly: fires at most once per second. V with no mob in range: nothing. V can't be held/charged. Dart around a corner: hitting a wall sends it home without sticking. Hit a strafing target: homing tracks it.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/FamiliarState.java src/main/java/com/alucard/heirloomsword/ModKeybinds.java src/main/java/com/alucard/heirloomsword/network/SwordQuickFirePacket.java src/main/java/com/alucard/heirloomsword/network/ModNetwork.java src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java src/main/resources/assets/heirloomswordmod/lang/en_us.json
git commit -m "feat: V-key quick-fire homing dart at locked-on target"
```

---

### Task 10: First-person telekinesis hand poses

**Files:**
- Create: `src/main/java/com/alucard/heirloomsword/client/TelekinesisHandRenderer.java`
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java`

Today `onRenderHand` cancels the main hand entirely in flying mode. Instead: cancel the item, but render the **bare arm** with a state-driven pose (design doc §Hand Gesture System table — intent-based gestures, not tracking gestures). Poses blend smoothly when the state changes. First-person only per user decision.

All pose values are `[TUNE]` starting points — they will need in-game iteration; that's expected.

- [ ] **Step 1: Create the renderer**

```java
package com.alucard.heirloomsword.client;

import com.alucard.heirloomsword.FamiliarState;
import com.alucard.heirloomsword.SwordFamiliarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import javax.annotation.Nullable;

/**
 * Renders the player's bare main arm with state-driven telekinesis poses while
 * flying mode is active (design doc "Hand Gesture System": intent-based gestures).
 * First-person only.
 */
public final class TelekinesisHandRenderer {

    private record HandPose(float x, float y, float z, float rotX, float rotY, float rotZ) {
        static HandPose lerp(HandPose a, HandPose b, float t) {
            return new HandPose(
                    Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y), Mth.lerp(t, a.z, b.z),
                    Mth.lerp(t, a.rotX, b.rotX), Mth.lerp(t, a.rotY, b.rotY), Mth.lerp(t, a.rotZ, b.rotZ));
        }
    }

    // [TUNE] every value below — these are starting points
    private static final HandPose RELAXED = new HandPose(0.45f, -0.50f, -0.60f, 10f,  -8f, 0f); // hovering, calm
    private static final HandPose ALERT   = new HandPose(0.42f, -0.45f, -0.58f, 18f, -10f, 0f); // mob in range
    private static final HandPose CHARGE  = new HandPose(0.40f, -0.38f, -0.52f, 35f, -18f, 5f); // tightened, raised
    private static final HandPose THRUST  = new HandPose(0.30f, -0.40f, -0.85f, 65f, -25f, 0f); // launch/quick-fire flick
    private static final HandPose SWEEP   = new HandPose(0.25f, -0.35f, -0.70f, 55f, -30f, 0f); // tracing the view
    private static final HandPose GUARD   = new HandPose(0.20f, -0.20f, -0.55f, 75f, -35f, 0f); // raised, palm forward
    private static final HandPose RECEIVE = new HandPose(0.45f, -0.45f, -0.65f, 25f, -12f, 0f); // returning

    private static final float BLEND_SPEED = 0.12f; // [TUNE] per-frame pose blend

    private static HandPose current = RELAXED;

    public static void render(RenderHandEvent event, @Nullable SwordFamiliarEntity familiar) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        current = HandPose.lerp(current, poseFor(familiar), BLEND_SPEED);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(current.x(), current.y(), current.z());
        poseStack.mulPose(Axis.XP.rotationDegrees(current.rotX()));
        poseStack.mulPose(Axis.YP.rotationDegrees(current.rotY()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(current.rotZ()));

        // Subtle tremble while charging
        if (familiar != null && familiar.getState() == FamiliarState.CHARGING) {
            float t = (mc.player.tickCount + event.getPartialTick()) * 1.7f;
            poseStack.translate(Math.sin(t * 3.1f) * 0.01f, Math.sin(t * 4.3f) * 0.01f, 0);
        }

        PlayerRenderer playerRenderer =
                (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
        playerRenderer.renderRightHand(poseStack, event.getMultiBufferSource(),
                event.getPackedLight(), mc.player);
        poseStack.popPose();
    }

    private static HandPose poseFor(@Nullable SwordFamiliarEntity familiar) {
        if (familiar == null) return RELAXED;
        return switch (familiar.getState()) {
            case HOVERING -> familiar.getAwarenessTarget() != null ? ALERT : RELAXED;
            case CHARGING -> CHARGE;
            case LAUNCHING, QUICK_FIRE, STUCK -> THRUST;
            case SWEEPING_HOLD, SWEEPING_RELEASE -> SWEEP;
            case BLOCKING -> GUARD;
            case RETURNING -> RECEIVE;
            default -> RELAXED;
        };
    }
}
```

Implementation notes for the engineer:
- `PlayerRenderer.renderRightHand(PoseStack, MultiBufferSource, int, AbstractClientPlayer)` is the vanilla first-person arm render call (used by `ItemInHandRenderer`). Verify the exact 1.21.1 signature via mcmodding-mcp `get_class_details` on `net.minecraft.client.renderer.entity.player.PlayerRenderer` if it doesn't resolve.
- `RenderHandEvent` getters: `getPoseStack()`, `getMultiBufferSource()`, `getPackedLight()`, `getPartialTick()` — verify likewise.
- Left-handed players (`mc.options.mainHand()` = LEFT) will still get the right arm with this baseline. Acceptable for now; note it as a known limitation in the commit message rather than mirroring all transforms.

- [ ] **Step 2: Hook it up**

In `HeirloomSwordModClient.ClientEvents.onRenderHand`, replace the cancel-only body:

```java
        @SubscribeEvent
        public static void onRenderHand(RenderHandEvent event) {
            if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof HeirloomSwordItem && HeirloomSwordItem.isFlying(stack)) {
                event.setCanceled(true);
                com.alucard.heirloomsword.client.TelekinesisHandRenderer.render(
                        event, findClientFamiliar(mc.player));
            }
        }
```

- [ ] **Step 3: Compile + in-game check**

`gradlew.bat build`, then `runClient` in first person: entering flying mode shows your bare arm raised in a relaxed telekinesis pose (with the existing purple hand shimmer around it). The arm shifts pose smoothly when: a mob comes in range (slight tense), charging (raises + trembles), launching/quick-firing (thrust), sweeping (palm forward), guarding (raised high), returning (receiving curl). No pose snaps — every change blends. Third person unchanged.

Expect to iterate the pose constants — report which poses look off rather than spending more than one round of tuning.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/alucard/heirloomsword/client/TelekinesisHandRenderer.java src/main/java/com/alucard/heirloomsword/HeirloomSwordModClient.java
git commit -m "feat: first-person telekinesis hand poses (right-arm baseline)"
```

---

### Task 11: Final end-to-end verification

**Files:** none (verification only)

- [ ] **Step 1: Clean build**

`gradlew.bat clean build` → BUILD SUCCESSFUL.

- [ ] **Step 2: Full pass in `gradlew.bat runClient`**

- Spawn: sky-drop outdoors (trail + impact ring + chime), materialize indoors. No grow-scale on sky-drop.
- Hover ↔ alert tilt eases smoothly both ways (~10 ticks, soft ends).
- Charge: gather particles ramp, sparkles at full charge, hand trembles; launch (both tiers) is noticeably faster with a faint trail and no rubber-banding.
- Sweep: blade revs up, spins fast (60°/tick), mobs take hits every 6 ticks with mild knockback; release throws a spinning disc that decays into the hilt-first return; arrival glides into the slot.
- Guard: slash plays with the guard pose held for its full 0.7s.
- V: homing dart with ~1s cooldown, returns immediately after hit/wall.
- Hand: all seven poses blend; no snaps.
- Regression: stuck-state freeze, launch orientation, F-locks, no-drop rule all still hold.

- [ ] **Step 3: `graphify update .`, report [TUNE] values that felt wrong**
