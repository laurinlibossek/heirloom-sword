# Mana, Texture Variants & Warp — Design Spec

**Date:** 2026-06-13
**Status:** Approved design (pre-plan). Tuning values marked **[TUNE]** are starting points, not final.
**Authority:** `docs/alucard_sword_design_v3.md` is the canonical design. This document is an
*addition* to it; where this doc supersedes a v3 decision, that is called out explicitly under
"Resolved conflicts." The actual code is the source of truth where v3 and the code disagree.

This is the feasibility + architecture pass for five feature ideas (from
`heirloom_feature_planning.md`). Each feature gets its own detailed implementation plan
afterward, in the sequencing at the end. Scope here: what is feasible, what conflicts with
code/design built since v3, which files each touches, and the agreed design.

---

## 0. Verified baseline (what the code actually does today)

Confirmed by reading the code on 2026-06-13:

- **No stamina or mana exists anywhere.** `CHARGING`, `SWEEPING_HOLD`, and `BLOCKING` cost
  nothing today — they are pure timers. The v3 "Stamina Model (§1)" was never built. So mana
  is *new behavior*, not a migration of existing drains.
- **`Config.java` is empty** (just an unused `SPEC`). No config infrastructure exists.
- **Texture pipeline:** the familiar entity (`SwordFamiliarModel`) and the held item
  (`HeirloomSwordItemModel`) share the *same* geo (`geo/alucard_sword.geo.json`) and the
  *same* texture (`textures/entity/alucard_sword.png`, 64×64). Both override
  `getTextureResource()` with a hardcoded constant. GeckoLib calls this per-render, so
  state-driven texturing is possible; the renderer already uses the packed-`int color` API
  (`SwordFamiliarGeoRenderer.preRender(..., int packedLight, int packedOverlay, int color)`),
  whose alpha byte enables translucent fading via render layers.
- **Geo bones:** `root / blade / guard / grip / pommel` — matches the texture-rework safety
  claim exactly.
- **`chargeTimer`** increments independently on both client and server (`tickCharging` /
  `tickChargingClient`); `getChargeTimer()` is readable client-side. `CHARGE_THRESHOLD_TICKS = 60`
  (3 s). Charge tier is binary (`DATA_CHARGED`). A charge HUD bar and the charge-complete cue
  already exist.
- **Keybinds:** F = toggle mode, R = recall, G = guard, V = quick-fire; left-click = charge,
  right-click = sweep (both flying-only). In **normal mode every mod keybind and click
  early-returns** (does nothing mod-specific). V is therefore free to overload in normal mode.
- **Awareness target** (`awarenessTarget`, nearest hostile `Monster` within 16 blocks) lives
  on the *familiar* and only exists in flying mode. Normal mode has no target lock.
- **Candidate-position validation:** `computeCandidatePosition(owner, idx)` (5 positions) +
  `isPositionObstructed(pos)` test only a 0.4-block sphere — not a player-sized volume.
- **Network:** 8 play-to-server payloads, all the same record + `STREAM_CODEC` + `handle`
  pattern (`ModNetwork`). Adding a packet is mechanical.
- **Item:** vanilla `SwordItem` (netherite tier, 12 attack dmg). Epic Fight greatsword
  registration is **not** present yet (v3 schedules it for Phase 9).

---

## 1. Resolved conflicts with design doc v3

| v3 says | This spec decides | Why |
|---|---|---|
| §1 Stamina Model: dual provider — EF bar when present, **hidden internal pool mirroring EF** when absent; core code talks to a `StaminaProvider`. | **Dropped.** Mana and EF stamina are **separate systems for separate domains**, not interchangeable. There is no "mirror EF" pool. | User decision 2026-06-13: EF stamina powers normal-mode combat only; mana powers warp + all flying actions. |
| §10 CHARGING/SWEEPING_HOLD/BLOCKING drain **Epic Fight stamina**; §10 charge depletion-stop and guard-break are EF-stamina-driven. | Those flying-mode drains and their depletion behaviors are driven by **mana**. | Same decision. EF is not a hard requirement for any mod mechanic. |
| §23/§25.1 config has a `stamina` section. | Renamed/repurposed to a **`mana`** section (plus `warp`). | Mana replaces stamina as the owned resource. |
| Warp is **not in v3 at all.** | Warp is **net-new**; v3 should gain a normal-mode warp section after this lands. | New Alucard-identity mechanic. Distinct from §10 TETHERING (flying-mode self-pull). |
| Runes / blood / texture variants are **not in v3.** | Net-new cosmetic systems. | New "living weapon" polish. |

EF's surviving role: **normal-mode greatsword combat patterns + its own stamina bar** (Phase 9,
when EF is installed). If EF is absent, normal mode is plain vanilla swings with no stamina.
The mod never depends on EF for any of the five features here.

---

## 2. Feature 1 — Mana system

**Verdict:** Feasible. Implemented as new behavior (no existing drains to migrate).

### Resource domains
- **Epic Fight stamina** → normal-mode greatsword combat only. Untouched by this mod (it is
  EF's system). EF absent → no stamina, vanilla swings.
- **Mana** → **Warp** (normal mode) **+ all flying-mode actions**: CHARGING drain,
  SWEEPING_HOLD drain, BLOCKING drain + guard break. **QUICK_FIRE stays free** (cooldown-gated
  only).

### Storage & access
- Mana is a **`AttachmentType<Float>` on the player** (NeoForge attachment), serialized so it
  persists across logout. Regenerates over time.
- Core state-machine and warp code talk to a thin **`ManaService`** helper (get / spend /
  hasAtLeast / addRegen), never the attachment directly — preserves the v3 intent that game
  logic doesn't know the backend.
- Only ever one sword per player in practice, so per-player mana is functionally equivalent to
  per-sword and simpler to read from both the held item and the familiar.

### Behavior at low/zero mana (decision: gate start + stop mid-action)
- An action **cannot begin** without its start-minimum mana (silent denial + the reusable
  denial sound, see §6).
- A held drain that empties the pool mid-action **stops the action**: CHARGING → HOVERING (no
  launch), SWEEPING_HOLD → ends, BLOCKING → guard break (existing guard-break behavior/cooldown).

### Regen
- Regenerates at a flat rate, **paused briefly after any spend** so you can't channel
  indefinitely. Regen runs whenever the player exists (normal or flying mode).

### HUD
- A **mana bar** appears whenever the player has the sword in hand (normal mode) **or** the
  familiar is present (flying) — i.e. whenever the sword is in use. **Inconspicuous**, styled
  like a conventional mana bar. It coexists with EF's stamina bar in normal mode when EF is
  installed (two bars; **accepted**). The existing charge bar and purple hotbar glow are
  unchanged.

### Tuning (kept cheap — "don't make anything too expensive") **[TUNE]**
| Value | Default |
|---|---|
| Pool max | 100 |
| Regen | 12 / sec |
| Regen pause after spend | 1 s |
| Charge drain | 15 / sec (3 s ≈ 45) |
| Sweep-hold drain | 8 / sec |
| Blocking drain | 10 / sec (guard break at 0) |
| Warp cost | 10 flat |
| Start minimums | charge / sweep / block ≥ 10; warp ≥ 10 |

### Files touched
- New: `ManaService` (logic), `ManaAttachment` registration (`AttachmentType`), mana-bar HUD
  render (in `HeirloomSwordModClient`), a `mana` sync packet S→C if the bar needs the value
  client-side (or read from a synced attachment).
- Edit: `SwordFamiliarEntity` (`tickCharging`, `tickSweepingHold`, `tickBlocking`, charge
  depletion / guard-break paths) to spend/check mana; `Config.java` (mana section); warp code
  (§5) for the warp cost.

---

## 3. Feature 2 — Default texture rework

**Verdict:** Feasible, pure asset work + a 2-line path move. Animation-safe (bones/pivots
unchanged; only cosmetic cube reshaping within bones).

- Introduce folder `assets/heirloomswordmod/textures/entity/alucard_sword/`.
- The reworked default lives at `.../alucard_sword/default.png` (64×64). Update the texture
  constant in **`SwordFamiliarModel`** and **`HeirloomSwordItemModel`** to the new path.
- **UV layout must stay identical** to today's so the rune/blood overlays (§4) register on the
  same blade pixels.
- Applies to both the held item and the familiar automatically (shared texture).
- **Open item:** the separate 16×16 `textures/item/heirloom_sword.png` (likely an inventory
  icon; GeoItemRenderer may not even use it) — out of scope unless you want it reworked too.

---

## 4. Features 3 & 4 — Runes and Blood (unified render-layer system)

**Verdict:** Feasible and cleaner as **two transparent overlay layers with code-controlled
alpha** than as discrete texture swaps. This collapses the variant count to **3 textures total**
and removes the `_half` / `_light` intermediates.

### Texture deliverables (final — folder `textures/entity/alucard_sword/`)
| File | Content | Render |
|---|---|---|
| `default.png` | Opaque reworked base blade (Feature 2). | Normal, the model's base texture. |
| `runes.png` | **Only** the purple runes painted; everything else fully transparent. Same UV layout as base. | Translucent **emissive** (full-bright), alpha faded by charge. |
| `bloodied.png` | **Only** the blood splatter painted (push the red so it reads as blood, not rust); everything else transparent. Same UV layout. | Translucent, normal lighting, alpha faded by the blood value. |

Both overlays are drawn on top of the base via `GeoRenderLayer`s; alpha is set per-frame
through the packed-`int color` the renderer already passes. The "half rune" and "light blood"
appearances are simply these overlays at partial alpha.

### Feature 3 — Runes (charge feedback)
- A `GeoRenderLayer` on `SwordFamiliarGeoRenderer`, active **only when state == CHARGING**.
- Alpha = function of client `chargeTimer` **[TUNE]**: 0 during the first second, ramping
  linearly to full between 1 s and 3 s, held at full with a subtle pulse at ≥ 3 s.
- Drawn **emissive / full-bright** so runes glow regardless of world light; **purple**.
- **Purely cosmetic.** The charge damage tier stays binary (normal < 3 s, charged ≥ 3 s). The
  rune brightness is continuous feedback only and must not imply a third damage tier. Keep the
  existing charge-complete sound as reinforcement; the runes-at-full pulse is the visual half
  of that same cue (this is the "better charge-complete indicator" the feature wanted).
- Does **not** appear on the held item (charging is flying-only; the item renderer gets no rune
  layer).

### Feature 4 — Blood (escalate on hit, decay over time)
- **Purely cosmetic** — never affects damage or any gameplay value.
- Canonical store: a **`DataComponent` on the item** holding a blood value plus the game-time
  of the last hit (e.g. `(float amount, long lastHitTick)` or two components). The current
  displayed alpha is derived from `amount` decayed by elapsed game-time, so no per-tick network
  spam is needed — the component changes only on a hit.
- **Trigger (flying-mode contact only):** a qualifying contact sets `amount → 1.0` and
  `lastHitTick → now`. Qualifying contacts: LAUNCHING outbound entity pierce, QUICK_FIRE
  contact, SWEEPING_HOLD contact damage, RETURNING-path damage. Normal-mode melee does **not**
  bloody the blade. (Hook points already exist where these call damage/`igniteIfUndead`; add a
  `bloodyOwnerSword()` call that locates the owner's flying sword stack — same lookup pattern
  as `findFlyingSword`.)
- **Decay:** `amount` decays continuously toward 0 over **~60 s [TUNE]** of no contact, so the
  blade visibly "dries." Evaluated lazily from `lastHitTick` whenever the item or familiar
  renders/ticks, so tick gaps (chest, ground) don't matter.
- **Shows in both modes:** the held-item renderer reads the item component directly. The
  familiar mirrors the item's blood value into a **synced entity field** (set server-side when
  blood changes) so its render layer can read it without an item lookup at render time.
- A `GeoRenderLayer` (blood) is added to **both** `SwordFamiliarGeoRenderer` and the item
  renderer, alpha = current blood value.

### Files touched (3 & 4)
- New: `RuneGlowLayer`, `BloodLayer` (GeoRenderLayers); blood `DataComponent`(s) in
  `ModDataComponents`; a synced blood field on `SwordFamiliarEntity`.
- Edit: `SwordFamiliarGeoRenderer` (add both layers), `HeirloomSwordItemRenderer` (add blood
  layer), `SwordFamiliarEntity` (blood-on-hit hooks + sync), texture path constants.
- Assets: the 3 PNGs above + move default into the new folder.

---

## 5. Feature 5 — Warp-next-to-target (normal mode)

**Verdict:** Feasible, net-new. Server-authoritative. No invincibility (i-frames cut by design).

### Trigger & keybind
- **Reuse the V (quick-fire) keybind** with a mode branch: in flying mode V → quick-fire
  (unchanged); in **normal mode V → warp**. The current `QUICK_FIRE` handler early-returns in
  normal mode — replace that branch with the warp send. Guard is **normal mode AND holding the
  sword** (the rest is validated server-side).
- New packet **`SwordWarpPacket`** (Client → Server, unit; keypress only). Register in
  `ModNetwork`.

### Server validation & flow
1. Holding `HeirloomSwordItem`, **normal mode**, **off cooldown**, **mana ≥ cost** — else play
   the reusable denial sound (§6) and abort (no mana spent, no cooldown started).
2. **Target:** raycast from the player's eyes along the look vector, clamped at the first solid
   block, out to **~20 blocks [TUNE]**; take the first `LivingEntity` hit. **Never targets
   players** (per §9), regardless of PvP settings. No target → denial sound, abort.
3. **Destination ("next to," not behind):** from the horizontal player→target direction, take
   the perpendicular axis and sample **left** then **right** of the target at
   `target.bbWidth/2 + ~0.6` blocks, with front-left / front-right fallbacks. Validate each:
   **two vertical air blocks for the player + a solid floor** (small ±1 vertical snap). First
   valid wins. No valid spot → denial sound, abort (no cost, no cooldown).
4. **Teleport** the player server-side to the destination. Compute yaw/pitch from the player's
   **new eye position** to the target's **torso/eye height** (not feet, not the destination
   angle, not the enemy's facing). Apply rotation so the client view does **not** snap back
   (teleport with the rotation set, e.g. `ServerPlayer.connection.teleport(...)`).
5. Spend mana, start the cooldown, emit an arrival particle + sound cue (placeholder; real
   asset in the audio pass). **No i-frames.**

### Design intent (sharp purpose)
A **cheap, low-cooldown convenience/style gap-closer**, not a power move: close on something
faster than you (fleeing ranged enemy) or cross terrain you can't walk (ravine, lava). Low MP +
short cooldown keeps it usable; a high price would make players never touch it.

### Tuning **[TUNE]**
| Value | Default |
|---|---|
| Mana cost | 10 |
| Cooldown | 5 s (100 ticks) |
| Target raycast range | 20 blocks |
| Side offset from target | bbWidth/2 + 0.6 |
| Vertical snap search | ±1 block |

### Distinction from TETHERING (§10)
Tether = flying-mode self-pull to your embedded sword, ending at a midpoint. Warp =
normal-mode blink next to a targeted enemy + face it. Different trigger, mode, destination,
intent. They never coincide.

### Files touched
- New: `SwordWarpPacket`, warp logic (a `WarpHandler`/static method), cooldown storage
  (player `AttachmentType<Integer>` or last-warp game-time).
- Edit: `ModKeybinds`/handler branch for V in normal mode (`HeirloomSwordModClient`),
  `ModNetwork` (register packet), `ManaService` (cost), `Config.java` (`warp` section), lang
  (none if silent + sound only).

---

## 6. Shared: reusable "action-denied" cue

A single subtle **sound-only** cue (no text), fired for any blocked action — warp with no
target / no valid spot / on cooldown / insufficient mana, and reusable for future denials.
Registered as one `SoundEvent` (`ModSounds.ACTION_DENIED`) with a placeholder vanilla sound for
now; the real asset comes in the audio/polish pass. Played to the acting player server-side
(or client-side where the denial is detected client-side).

---

## 7. Sequencing

1. **Mana (Feature 1)** — infrastructure everything else costs against. Establish
   `ManaService`, attachment, HUD, and wire the flying-mode drains.
2. **Default texture rework (Feature 2)** — base layer; move default into the new folder, keep
   UVs. Do before overlays so they're painted against the final base.
3. **Runes (Feature 3) + Blood (Feature 4)** — the shared overlay render-layer system; can be
   built together (one layer infra, two layers).
4. **Warp (Feature 5)** — self-contained; depends only on `ManaService` (cost) and the V-key
   branch. Can be planned in parallel once mana exists.

Each becomes its own implementation plan in this order.

### Where this slots into design doc v3's phases
None of these five features require the rest of the mod to be finished first; they do **not**
wait for Phases 10–13.
- **Mana (1)** is the **resource half of Phase 9**. v3's Phase 9 bundled "Epic Fight
  integration **& stamina**"; mana replaces the stamina / `StaminaProvider` portion entirely.
  The EF combat-registration portion of Phase 9 stays separate and can land later. Mana has no
  technical dependency on the Phase 7 leftovers or block piercing (v3 listed those "before
  Phase 9" for completeness, not as a dependency), so it can proceed now.
- **Default texture (2)** completes the Phase 8 visual layer.
- **Runes + Blood (3, 4)** are Phase 8 / Phase 10 visual polish; they need Phase 8 (done) and
  the reworked base (2).
- **Warp (5)** is net-new normal-mode content; needs only mana.

[TUNE] values use hardcoded constants for now (as the rest of the code does) and fold into the
Phase 13 config pass (§25.1) along with everything else — not a blocker.

---

## 8. Open items / playtest flags

- Mana magnitudes, regen, warp cost/cooldown, blood decay time, rune fade curve — all **[TUNE]**.
- Two HUD bars (EF stamina + mana) coexisting in normal mode — **accepted**; mana bar shows
  whenever the sword is in hand, inconspicuous styling.
- Mana-bar exact placement/style.
- Rune intermediate is brightness-only (one `runes.png` faded), not a different glyph pattern —
  confirmed acceptable.
- 16×16 inventory icon rework — out of scope unless requested.
- Add a warp section to design doc v3 once implemented.
