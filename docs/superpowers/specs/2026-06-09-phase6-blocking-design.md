# Phase 6 — BLOCKING State Design
**Date:** 2026-06-09
**Mod:** HeirloomSword (NeoForge 1.21.1)
**Phase:** 6 of 12

---

## Scope

Implements the BLOCKING state per `docs/alucard_sword_design_v3.md` Section 10. Only what is listed in **In Scope** below is implemented. Everything else is deferred to its designated phase.

### In Scope

| Feature | Notes |
|---|---|
| `BLOCKING(7)` added to `FamiliarState` enum | ID 7, `fromId()` updated |
| G held from HOVERING → BLOCKING | New `SwordGuardPacket(boolean held)` |
| Sword repositions to 1.5 blocks in front of player, tracks look direction | Spring physics each server tick |
| Hitbox stays horizontal (same as all non-HOVERING states) | Visual X-across-chest is Phase 8 |
| G released with stamina remaining → HOVERING | `block_slash` animation skipped (Phase 8) |
| Stamina depleted → HOVERING + 3-second G cooldown | Stamina drain itself is Phase 9; cooldown infrastructure wired but dormant |
| F key exits flying mode from BLOCKING | Add `BLOCKING` to allowed-exit set in `SwordModePacket::handle` |
| Both `serverTick()` and `clientTick()` switches get `case BLOCKING` | — |

### Explicitly Deferred

| Feature | Phase |
|---|---|
| G during CHARGING cancels charge → BLOCKING | 7 |
| G during SWEEPING_HOLD cancels sweep → BLOCKING | 7 |
| Actual stamina drain during BLOCKING | 9 |
| Damage reduction (frontal, vanilla-shield equivalent) | 9 |
| Projectile interception and geometric deflection | 10 |
| `block_slash` animation on G release | 8 |
| `guard_break` animation on stamina depletion | 8 |
| `removeChargeSlowdown()` call when entering from CHARGING | 7 (deferred with that entry path) |

---

## New Files

### `network/SwordGuardPacket.java`

```java
public record SwordGuardPacket(boolean held) implements CustomPacketPayload {
    public static final Type<SwordGuardPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_guard"));

    public static final StreamCodec<ByteBuf, SwordGuardPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, SwordGuardPacket::held,
            SwordGuardPacket::new
        );

    public static void handle(SwordGuardPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) return;
            if (!HeirloomSwordItem.isFlying(held)) return;

            SwordFamiliarEntity familiar =
                SwordFamiliarEntity.findForOwner(player.serverLevel(), player.getUUID());
            if (familiar == null) return;

            if (packet.held()) {
                // Press: only valid from HOVERING, only if not on cooldown
                if (familiar.getState() != FamiliarState.HOVERING) return;
                if (familiar.getGuardCooldown() > 0) return;
                familiar.startBlocking();
            } else {
                // Release: only meaningful if currently BLOCKING
                if (familiar.getState() != FamiliarState.BLOCKING) return;
                familiar.stopBlocking();
            }
        });
    }
}
```

**Codec note:** `SwordGuardPacket` carries one `boolean` field, so it uses `StreamCodec.composite` (not `StreamCodec.unit`). This is the first packet with a payload field — see `SwordLaunchPacket` for a Vec3 + boolean composite example.

---

## Modified Files

### `FamiliarState.java`

Add `BLOCKING(7)` to the enum. Update `fromId()` switch:

```java
case 7 -> BLOCKING;
```

### `ModKeybinds.java`

Add:
```java
public static final KeyMapping GUARD = new KeyMapping(
    "key.heirloomswordmod.guard",
    KeyConflictContext.IN_GAME,
    InputConstants.Type.KEYSYM,
    GLFW.GLFW_KEY_G,
    CATEGORY
);
```

Register in `HeirloomSwordModClient.ModBusEvents.onRegisterKeyMappings`:
```java
event.register(ModKeybinds.GUARD);
```

### `ModNetwork.java`

Add inside `registerPayloadHandlers`:
```java
registrar.playToServer(
    SwordGuardPacket.TYPE,
    SwordGuardPacket.STREAM_CODEC,
    SwordGuardPacket::handle
);
```

### `HeirloomSwordModClient.java` — `ClientEvents.onClientTick`

Add a `isBlocking` boolean field (mirrors `isCharging` / `isSweeping` pattern):

```java
private static boolean isBlocking = false;

private static void resetBlockState() {
    isBlocking = false;
}
```

In `onClientTick`, after the sweep section, add:

```java
// --- GUARD (G key held/released) ---
if (!isBlocking) {
    // Not currently blocking — check for press
    if (ModKeybinds.GUARD.isDown()) {
        if (HeirloomSwordItem.isFlying(held)) {
            SwordFamiliarEntity familiar = findClientFamiliar(player);
            boolean onCooldown = familiar != null && familiar.getGuardCooldown() > 0;
            FamiliarState state = familiar != null ? familiar.getState() : null;
            if (!onCooldown && state == FamiliarState.HOVERING) {
                PacketDistributor.sendToServer(new SwordGuardPacket(true));
                isBlocking = true;
            }
        }
    }
} else {
    // Currently blocking — check for release
    if (!HeirloomSwordItem.isFlying(held)) {
        resetBlockState();
        return;
    }
    if (!ModKeybinds.GUARD.isDown()) {
        PacketDistributor.sendToServer(new SwordGuardPacket(false));
        resetBlockState();
    }
}
```

**Why `.isDown()` not `.consumeClick()`:** G is a hold-down key, not a tap. The same pattern as `keyAttack.isDown()` / `keyUse.isDown()` used for charge and sweep.

Early-exit cleanup: add `if (isBlocking) resetBlockState();` to the early-return guards at the top of `onClientTick` (matching the pattern for `isCharging` and `isSweeping`).

### `SwordModePacket.java` — `handle`

The handler already checks state before allowing F-key exit. Add `FamiliarState.BLOCKING` to the allowed-exit set alongside `FamiliarState.HOVERING` and `FamiliarState.SWEEPING_HOLD`. Exact line: wherever the existing check reads something like:

```java
if (state == FamiliarState.HOVERING || state == FamiliarState.SWEEPING_HOLD) {
```

Change to:

```java
if (state == FamiliarState.HOVERING
        || state == FamiliarState.SWEEPING_HOLD
        || state == FamiliarState.BLOCKING) {
```

### `SwordFamiliarEntity.java`

#### New fields

```java
// BLOCKING state
private int guardCooldownTicks = 0;
```

`guardCooldownTicks` is server-side only (not synced as EntityData — the client only needs it for UX smoothing via the cooldown getter which reads from the synced state or a separate sync mechanism). For Phase 6, sync via a new `DATA_GUARD_COOLDOWN` EntityDataAccessor so the client can suppress the G keybind on cooldown.

```java
private static final EntityDataAccessor<Integer> DATA_GUARD_COOLDOWN =
    SynchedEntityData.defineId(SwordFamiliarEntity.class, EntityDataSerializers.INT);
```

Register in `defineSynchedData`:
```java
builder.define(DATA_GUARD_COOLDOWN, 0);
```

Add getter:
```java
public int getGuardCooldown() {
    return this.entityData.get(DATA_GUARD_COOLDOWN);
}
private void setGuardCooldown(int ticks) {
    this.entityData.set(DATA_GUARD_COOLDOWN, ticks);
}
```

#### New state methods

```java
public void startBlocking() {
    setState(FamiliarState.BLOCKING);
}

public void stopBlocking() {
    // Called on G release with stamina remaining.
    // block_slash animation fires here in Phase 8.
    setState(FamiliarState.HOVERING);
}

public void guardBreak() {
    // Called from Phase 9 stamina hook when stamina depletes during BLOCKING.
    // guard_break animation fires here in Phase 8.
    setState(FamiliarState.HOVERING);
    setGuardCooldown(60); // 3 seconds
}
```

#### Tick dispatchers

In `serverTick()` switch:
```java
case BLOCKING -> tickBlocking(owner);
```

In `clientTick()` switch:
```java
case BLOCKING -> tickBlockingClient(owner);
```

#### `tickBlocking(ServerPlayer owner)`

```java
private void tickBlocking(ServerPlayer owner) {
    // Decrement G cooldown (also runs while in other states — move to pre-state tick
    // or guard here; cooldown only matters at HOVERING entry, so decrement here is fine
    // because guard break immediately exits to HOVERING).

    // Reposition: 1.5 blocks directly in front of player eyes, tracking look direction.
    Vec3 target = owner.getEyePosition().add(owner.getLookAngle().scale(1.5));
    // Apply spring physics toward target (same approach as tickHovering spring follow).
    applySpringToward(target);
}
```

#### `tickBlockingClient(LocalPlayer owner)`

```java
private void tickBlockingClient(LocalPlayer owner) {
    // Client-side position prediction mirrors server tickBlocking.
    Vec3 target = owner.getEyePosition().add(owner.getLookAngle().scale(1.5));
    applySpringToward(target);
}
```

#### Cooldown decrement

The cooldown must tick down even while in HOVERING (so the player can enter BLOCKING again after the 3s window). Add to `tickHovering`:

```java
if (getGuardCooldown() > 0) setGuardCooldown(getGuardCooldown() - 1);
```

#### NBT persistence

Add to `addAdditionalSaveData`:
```java
tag.putInt("guardCooldown", getGuardCooldown());
```

Add to `readAdditionalSaveData`:
```java
setGuardCooldown(tag.getInt("guardCooldown"));
```

---

## State Transition Diagram (this phase)

```
HOVERING ──[G held, no cooldown]──► BLOCKING
BLOCKING ──[G released]──────────► HOVERING  (slash deferred to Phase 8)
BLOCKING ──[stamina depleted]────► HOVERING  (guard_break deferred, cooldown=60 ticks)
BLOCKING ──[F pressed]───────────► flying mode exit (instant despawn)
```

All other inputs from BLOCKING (left click, right click, R) are ignored per design doc.

---

## What Is Wired But Dormant

- `guardBreak()` exists and sets the cooldown. It will never fire until Phase 9 calls it from the stamina hook.
- `DATA_GUARD_COOLDOWN` is synced and the client reads it to suppress the G press — but since guard break never fires in Phase 6, the cooldown will always be 0 in play.
- The `block_slash` / `guard_break` animation hooks are noted in `stopBlocking()` / `guardBreak()` but no GeckoLib call is made yet.

---

## Testing Checklist

- [ ] G held in HOVERING → sword snaps to front-of-player position and tracks turning
- [ ] G released from BLOCKING → sword returns to hover, state is HOVERING
- [ ] F pressed from BLOCKING → flying mode exits, familiar despawns
- [ ] G during LAUNCHING/STUCK/RETURNING → no effect, state unchanged
- [ ] G during SWEEPING_HOLD → no effect (Phase 7 scope)
- [ ] G during CHARGING → no effect (Phase 7 scope)
- [ ] `BLOCKING(7)` survives NBT round-trip (save/reload)
- [ ] `fromId(7)` returns BLOCKING
- [ ] Client `isBlocking` resets cleanly on menu open, item switch, mode exit

---

*Spec self-review: No TBDs. No contradictions with design doc. Scope is contained to Phase 6 per prompt. All deferred items are explicit. Cooldown is wired but dormant — documented.*
