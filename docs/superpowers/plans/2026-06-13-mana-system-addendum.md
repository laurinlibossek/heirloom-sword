# Mana System — Completion Report & Unplanned Changes

**Date:** 2026-06-14
**Branch:** `polish-quickfire-and-feel`
**Base plan:** [`2026-06-13-mana-system.md`](./2026-06-13-mana-system.md)
**Status:** ✅ Complete — all 9 plan tasks done, plus the unplanned changes below.

This document records every change made beyond the original 9-task mana plan during the
implementation session. Each item emerged from in-game testing feedback, a UX polish pass, or a
follow-up feature request — none were in the approved plan.

---

## 1. Planned work (for reference)

The base plan's Tasks 1–8 landed as one commit each, each built clean (`BUILD SUCCESSFUL`) and
committed before the next:

| Task | Commit | Summary |
|------|--------|---------|
| 1 | `9d100c2` | Core `ManaService`, `ManaAttachments`, `ClientManaState`, `ManaSyncPacket` |
| 2 | `ac77b18` | Shared action-denied feedback cue (`SwordSounds.playDenied`) |
| 3 | `1d59620` | Regenerate mana while holding the sword |
| 4 | `8fb260d` | Charging drains mana, gated on a minimum |
| 5 | `ce280fc` | Sweep-hold drains mana, gated on a minimum |
| 6 | `6b3fa18` | Blocking drains mana, breaks guard on empty, gated on a minimum |
| 7 | `2cddc96` | Inconspicuous mana HUD bar while the sword is in use |
| 8 | `d286f52` | Client-side mana gates with a local denial cue |

**Task 9** (manual in-game verification) was performed by the user during the session — see
§4 below.

---

## 2. Unplanned changes

### 2a. Bug fixes (from in-game testing)

| Commit | Fix | Root cause |
|--------|-----|------------|
| `955cb2b` | Charge drain stops at full charge; sweep "horizontal limbo"; launch-direction guard; mana bar repositioned over the hunger bar | (1) Charge kept billing mana after `isChargeReady()`. (2) Client kept `isSweeping` after the server left `SWEEPING_HOLD`, sending a stale `SwordLaunchPacket(Vec3.ZERO)` that the HOVERING server read as a zero-direction launch and corrupted state. Fixed both sides: client resets on state-exit; server skips launch when `direction.lengthSqr() ≤ 1e-6`. |
| `abfe0e8` | Only reset charge/sweep **after** the server confirms the state | The first desync fix reset the action immediately on the click, before the server's state transition round-tripped back (still HOVERING for a few ticks), killing charge before it began. Added `chargeConfirmed` / `sweepConfirmed` flags — a state-exit is only treated as a server-drop after the state-entry was observed at least once. |
| `7e833c9` | Cancel charge → HOVERING on pause instead of stranding it in CHARGING | Opening a screen / pausing only cleared the client flag; the server stayed `CHARGING` forever (creative never drains out). Added `SwordFamiliarEntity.cancelCharge()` + `SwordCancelChargePacket` (client→server) + client `cancelCharging()` wired into the pause path. |

### 2b. Polish / UX (requested mid-session)

| Commit | Change | Notes |
|--------|--------|-------|
| `e9edf95` | Mana + charge bar outlines recolored from black to inventory-slot gray (`0xFF8B8B8B`) | "Minecraft-y" hotbar look. |
| `69ee221` | **Mana-depletion lockout** — running dry freezes mana at 0, holds regen, and blocks all sword inputs except the F mode-toggle for `LOCKOUT_TICKS = 40` (2 s) | User chose the "punish" option over instant regen. Threshold synced to the client so prediction matches; re-syncs on the final lockout tick. `[TUNE]` value. |
| `d1e7a05` | **Creative mode** — infinite mana, HUD bar hidden | Server-authoritative via `ManaService.isExempt` (`player.getAbilities().instabuild`); every cost, gate, and the lockout short-circuit. Client mirrors the exemption so prediction and the survival→creative switch stay aligned. |

### 2c. New feature requests (STUCK feedback + ambient FX)

| Commit | Change | Notes |
|--------|--------|-------|
| `6b38b6c` | **STUCK impact feedback** — `SoundEvents.MACE_SMASH_GROUND` (`minecraft:item.mace.smash_ground`) plays positionally at the embed point (server, `player=null` so the owner hears it too); a red alert ring nests just inside the purple hotbar ring while STUCK; GUI icon sinks while STUCK | Trident-style "thunk" + at-a-glance hotbar cue, since the world sound falls off with distance. |
| `8e6b99b` | GUI sword icon kept **permanently** lowered in the slot (sink no longer gated on STUCK) | Per follow-up: always-lowered framing. Removed the now-unused stuck-detection helper from the renderer; the red ring stays STUCK-only. |
| `552ef60` | Flying-mode hand shimmer throttled from ~3 particles/s (15 %/tick) to **one particle per 50 ticks (2.5 s)** | Cooldown counts down only while flying. `HAND_PARTICLE_INTERVAL = 50` is a one-line `[TUNE]`. |

---

## 3. Files touched by the unplanned work

- `ManaService.java` — creative exemption, depletion lockout, lockout-aware regen/sync
- `ManaAttachments.java` — `LOCKOUT` attachment
- `ClientManaState.java` / `network/ManaSyncPacket.java` — lockout field carried to the client
- `SwordFamiliarEntity.java` — charge-full drain gate, `cancelCharge()`, STUCK impact sound
- `SwordSounds.java` — `playStuckImpact(...)`
- `network/SwordCancelChargePacket.java` (new), `network/ModNetwork.java`
- `network/SwordLaunchPacket.java` — zero-direction guard
- `network/SwordRecallPacket.java` / `SwordQuickFirePacket.java` — lockout checks
- `HeirloomSwordModClient.java` — confirmation gates, lockout countdown, creative mirror, red ring, particle throttle
- `client/HeirloomSwordItemRenderer.java` — permanent GUI sink

---

## 4. Verification

- **Build:** every commit verified with `./gradlew build` → `BUILD SUCCESSFUL`. No test harness exists in the project; the build is the gate.
- **In-game (user-confirmed this session):** launch, charging (stops draining at full), blocking/guarding, quick-fire, creative exemption, charge-on-pause recovery, the STUCK sound + ring + GUI sink, and the reduced particle rate all confirmed working. Mana described as "perfect."

## 5. Known deferred items

- **Sweeping-hold residual jitter** — minor jitter during a held sweep. Explicitly deferred by the
  user as "fine tune later — doesn't have to be done now."
- **`[TUNE]` dials** to confirm/iterate in-game: `LOCKOUT_TICKS` (2 s), `HAND_PARTICLE_INTERVAL`
  (2.5 s), `GUI_SINK` (~2 px), the red-ring shade (`0xCCFF2A2A`), and all `ManaService` drain/regen
  constants (slated for the Phase 13 config pass).
