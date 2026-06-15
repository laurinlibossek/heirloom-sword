# Alucard's Sword — Mod Design Document
**Platform:** NeoForge 1.21.1
**Status:** Pre-implementation reference. All decisions final unless marked [TUNE].

---

## 1. Platform & Libraries

| Dependency | Role | Type |
|---|---|---|
| NeoForge 1.21.1 | Modloader | Required |
| GeckoLib 4.x | Entity and item rendering, keyframe animation | Required |
| Epic Fight (latest NeoForge 1.21.1 build) | Normal mode combat patterns, stamina bar | Soft optional |

Epic Fight integration is loaded only if Epic Fight is present at runtime, via a dedicated
`EpicFightCompat` class guarded by `ModList.get().isLoaded("epicfight")` in
`FMLCommonSetupEvent`. The mod functions fully without Epic Fight; normal mode simply uses
vanilla sword behavior as a fallback.

### Resource Model (superseded 2026-06-13 — replaces the 2026-06-12 "Stamina Model")

> **READ THIS BEFORE IMPLEMENTING PHASE 9.** The original 2026-06-12 "Stamina Model"
> described a single `StaminaProvider` abstraction backing *both* normal-mode combat and all
> flying-mode actions, with a hidden internal pool when Epic Fight was absent. **That model
> is dropped.** It is replaced by the two-resource split below, already designed in
> `docs/superpowers/specs/2026-06-13-mana-textures-warp-design.md` and implemented by
> `docs/superpowers/plans/2026-06-13-mana-system.md` (built *before* Phase 9).

The mod uses **two independent resources**:

- **Mana** — a server-authoritative `AttachmentType<Float>` on the player, owned entirely by
  `ManaService`, shown as its own inconspicuous HUD bar. Mana powers **every flying-mode
  action**: CHARGING drain + depletion-stop, SWEEPING_HOLD drain, BLOCKING drain + guard
  break, plus the normal-mode **Warp** gap-closer (Section 27). QUICK_FIRE stays free. Mana
  works **with or without Epic Fight** — it is the mod's own resource and never touches Epic
  Fight, so there is **no hidden-pool fallback**.
- **Epic Fight stamina** — used **only** for normal-mode greatsword combat, through Epic
  Fight's own native system. The mod never reads, writes, mirrors, or abstracts it. With Epic
  Fight absent, normal mode is plain vanilla swings with no stamina at all.

**Reading the rest of this document:** wherever the sections below say "stamina" for a
**flying-mode** behavior (CHARGING / SWEEPING_HOLD / BLOCKING drains and guard break in
Sections 10–14, the cost rows in Section 20, the animation triggers in Section 19), read it
as **mana**, and treat that drain as **already implemented** by the mana plan — not as Phase
9 work. "Stamina" still means literal Epic Fight stamina only where it refers to **normal
mode** combat (Section 5).

**Consequences for Phase 9:** do **not** build a `StaminaProvider` interface or a hidden
stamina pool — both are obsolete. Phase 9 is now *only* the Epic Fight normal-mode combat
integration.

---

## 2. The Item

### Identity
A single legendary magical sword. One item, one mod. No crafting recipe exists.

### Acquisition
The sword spawns exclusively in **Ancient City** loot chests with a **5% chance per chest**.
This is implemented as a loot table injection into
`minecraft:chests/ancient_city` via NeoForge's `LootTableLoadEvent` or a datapack-style
loot table modifier. The sword does not spawn anywhere else — no mob drops, no crafting, no
trading, no other structure loot. Finding it requires venturing into the Deep Dark.

### Durability & Enchantments
The sword has no durability bar. It cannot be damaged, repaired, or enchanted. It does not
accept any enchantment at an enchanting table or anvil. It is a magical artifact and behaves
as one.

### Hotbar Behavior
The item always remains in the player's hotbar, including while the familiar is in flying
mode. The hotbar slot displays a faint purplish glow on the item icon at all times while
flying mode is active. No glow in normal mode. No other HUD indicators are added.

### Drop Prevention
The item cannot be dropped via the Q key while in flying mode. Attempting to do so produces
a hotbar message: *"The sword refuses to leave your side."* No item is dropped and no
familiar state is changed.

---

## 3. Mode System

### Toggle
**Keybind: F** (configurable). Switching is instant. No cost, no cooldown.

### Lock Condition
Mode switching via F is available in the following states:

- **HOVERING:** Normal toggle. Exits flying mode, despawns familiar.
- **SWEEPING_HOLD:** Emergency exit. Instant despawn, sword returns to inventory.
- **BLOCKING:** Emergency exit. Instant despawn, sword returns to inventory.

In all other active states (LAUNCHING, SWEEPING_RELEASE, CHARGING, STUCK, TETHERING,
RETURNING), the F keybind is locked and does nothing. The sword is committed and must
complete its current action before mode switching becomes available again.

### Normal → Flying Transition
The familiar entity is spawned at the player's side. The item remains in the hotbar. The
player's hand rendering switches to the gesture system.

### Flying → Normal Transition
The familiar despawns instantly — fast enough that no despawn animation is needed. The
player's hand rendering returns to holding the sword normally.

---

## 4. Normal Mode

Standard Epic Fight greatsword behavior.

- **Base damage:** 10
- **Skill tree:** Mimics whichever Epic Fight greatsword has the most satisfying combo
  pattern. Developer's pick, subject to playtesting override. No custom skills added.
- **Registration:** The item is registered with Epic Fight's weapon type system as a
  greatsword archetype via `EpicFightCompat`. If Epic Fight is absent, vanilla sword
  behavior applies.

No throwing in normal mode. No special right-click behavior. All special mechanics are
exclusive to flying mode.

---

## 5. Flying Mode — Visual Layer

### Hand Rendering
While flying mode is active, the player's hand renders empty. No ghosted sword outline.
No residual visual. Simply an empty fist with the telekinetic shimmer effect applied.

### Telekinetic Shimmer
A faint violet/purple coronal glow around the player's hand. Implemented as a particle
overlay or shader effect on the hand model. The intensity does not change based on state —
it is constant while flying mode is active. It is present in both first-person and
third-person view.

### Hand Gesture System
The hand makes intent-based gestures, not tracking gestures. The hand does not physically
point toward the sword's world position. Gestures are state-driven:

| Familiar State | Hand Pose |
|---|---|
| HOVERING (no mobs nearby) | Relaxed, open, loosely extended |
| HOVERING (mob in range) | Fingers curl slightly inward |
| HOVERING (idle personality) | Same as no-mobs-nearby — relaxed, ambient |
| CHARGING | Fingers tighten, arm slightly raised |
| LAUNCHING (on launch frame) | Brief directional flick toward aim direction |
| TETHERING | Clenched fist, arm pulls back toward body — reeling-in gesture |
| SWEEPING_HOLD | Slow arc tracing the view direction |
| BLOCKING | Raised, palm forward |
| RETURNING | Relaxed, slight curl as if receiving |

Third-person presentation of hand gestures will be validated during playtesting.

---

## 6. The Familiar Entity

### Class Design
A custom non-living `Entity` subclass (not `Mob`, not `LivingEntity`). It has no health
bar, no AI goals, no pathfinding. All behavior is driven explicitly by the state machine
described in Section 10. The entity is registered as **persistent** so it serializes to
chunk NBT on unload and resumes on reload.

### Ownership
The entity stores the owning player's UUID in its NBT data (`ownerUUID`). This is private
entity data — not visible on the item, not visible to other players. The item itself carries
no owner data. When any player picks up the item and activates flying mode, a new familiar
spawns with that player's UUID as owner. Previous familiars are not affected.

### Model
Rendered via GeckoLib. Model is authored in Blockbench as a `.geo.json` with accompanying
`.animation.json`.

**Dimensions when fully upright:**
- Total height: 3 blocks
- Hilt position: 1 block above the player's head
- Blade tip: at the player's foot level, touching the ground

**The sword always travels tip-forward** during LAUNCHING and RETURNING, except during
SWEEPING_RELEASE and its associated return, which are hilt-first.

### Animation Clips
The following named clips must be defined in the animation JSON:

| Clip Name | Description |
|---|---|
| `idle` | Gentle upright float with slow vertical bob |
| `idle_curious` | Slight drift and inquisitive tilt toward a nearby block |
| `idle_figure_eight` | Slow lazy figure-eight trace at hover position |
| `idle_recoil` | Quick backward flinch from fire/lava/TNT |
| `idle_perk` | Brief upward tilt and alert pause (rain start) |
| `alert` | Tilting toward mob target — transitions from idle |
| `charge_spin` | Corkscrew drill spin on own axis, no orbiting |
| `launch` | Blade-forward dart, minimal visual |
| `stuck` | Embedded in block, slight vibration |
| `tether_pull` | Embedded vibration intensifies, blade glows as player is pulled toward it |
| `sweep_hold` | Sword held in front, hilt toward player |
| `block_stance` | Diagonal X-across-chest orientation |
| `block_slash` | Wide horizontal slash from guard position on G release |
| `guard_break` | Brief stagger/wobble when stamina depletes during BLOCKING |
| `return` | Tip-forward return travel |
| `return_hilt` | Hilt-forward return travel (sweep only) |
| `death_fall` | Visual-only stick-in-ground on player death |

### Transition Animation Notes
The following transitions require explicit visual treatment. Where no dedicated clip is
listed, GeckoLib's animation blending handles the crossfade between the source and
destination clips. These notes exist so the implementer knows which transitions are
intentionally simple and which need authored work.

| Transition | Visual Treatment |
|---|---|
| Flying mode entered (spawn) | **Sky-drop entrance** (implemented): the sword spawns high above its hover slot (16 blocks [TUNE]) and descends at 2.5 blocks/tick [TUNE] with an END_ROD falling streak, handled by the ARRIVING state. On touchdown it deals a landing impact (4 damage + knockback in a 3-block radius, owner excluded) and settles into HOVERING. If vertical clearance is below 6 blocks, it falls back to materializing directly at the player's side with a brief fade-in and particle burst. No flight-from-hand animation. |
| HOVERING → CHARGING | Sword glides smoothly from its current hover position to the left/right charge position. `charge_spin` ramps up from zero rotational speed over ~0.25 seconds rather than snapping to full spin instantly. |
| STUCK → TETHERING | Sword's `stuck` vibration intensifies and blade takes on a brighter glow. No positional change — the sword stays embedded. Visual cue is on the sword, not the player. |
| STUCK → RETURNING | Brief pull-out pause (~2–3 frames) before the sword wrenches free and begins return travel. Blends from `stuck` vibration into `return` clip. |
| TETHERING → RETURNING (arrival/timeout) | Sword wrenches free from embedded position with a brief pull-out pause. Particle burst on release. Transitions into `return` clip for tip-forward travel back to the player. |
| RETURNING → HOVERING | Sword decelerates using spring physics on arrival (overshoot, oscillate, settle). GeckoLib blends from `return` into `idle`. No dedicated clip needed. |
| CHARGING → BLOCKING | `charge_spin` winds down rapidly (~0.15 seconds) as the sword repositions from the side to X-across-chest in front. Blends into `block_stance`. |
| SWEEPING_HOLD → BLOCKING | Sword arrests its sweep momentum and slides into the guard position in front of the player. Blends from `sweep_hold` into `block_stance`. Brief visual only — no dedicated clip needed. |

---

## 7. Hover Position & Obstacle Avoidance

### Anchor Point
Mapped to the player's **torso** if the modding API provides a stable torso bone or position.
Fallback: player's head position. The sword orbits within a **1.5-block radius** of this
anchor.

### Default Position
The sword's default resting position is to the player's **right side**, relative to the
player's current facing direction (not a fixed world axis).

### Candidate Position Priority
When the default position is obstructed, the system checks the following positions in order
and uses the first unobstructed one:

1. Right of player
2. Left of player
3. Behind player
4. Above player
5. In front of player

### Collision Detection
Each candidate position is checked with a **small aggressive collision sphere** of
approximately **0.4 blocks radius**. Brief clipping during transition between positions is
acceptable.

### Transition Between Positions
The sword slides **smoothly** to a new candidate position when the current one becomes
obstructed. It does not snap. Brief clipping during the slide is acceptable behavior.

### Ceiling Handling (Vertical Tilt)
When overhead clearance is insufficient for the sword to stand fully upright, the tilt
system handles it gracefully:

- In a 3-block high room: sword stands fully upright
- As ceiling lowers: sword progressively rotates toward horizontal
- Fully horizontal: sword points in the player's current look direction
- In a crawl space (≈1.5 blocks or less): flying mode exits

The tilt direction when forced horizontal follows the player's look direction, so it reads
as "ready to lunge" rather than randomly oriented.

### Fallback — No Valid Position
If all five candidate positions are obstructed (e.g., the player is fully enclosed in a
1×1×1 space): flying mode exits immediately and the sword snaps to the player's hand. There
is **no automatic re-engagement**. The player must press F manually.

---

## 8. Lazy Follow & Spring Physics

### Follow Behavior
The familiar does not rigidly track the player. It follows with spring physics:

- It lags behind the player's movement
- It overshoots its target position slightly on arrival
- It oscillates back to center with damping

This gives the impression the sword has mass and personality rather than being a rigid
attachment.

### Catch-Up
Maximum lag distance before urgent catch-up: **3 blocks**. Beyond 3 blocks, the sword
increases its follow speed sharply. Catch-up speed is fixed at a value faster than player
sprint speed.

### Rotational Lag
If implementation cost permits: the sword's facing also follows with lazy delay, so it
momentarily holds its old orientation when the player turns. If this is too costly, rotation
tracks the player instantly and only position uses spring physics.

### Restrictions — Flying Mode Cannot Be Active While:
- Riding any entity (horse, boat, minecart, pig, strider, anything `is_passenger`)
- Using elytra
- In `EntityPose.SWIMMING`

Each of these conditions has its own dedicated section (Sections 13–15) describing exactly
what happens when a conflict arises. Dimension travel is covered separately in Section 16.

---

## 9. Mob Awareness

### Scan
Every tick, the familiar scans for **hostile mobs only** within a **16-block radius**.
Passive animals are ignored. Other players are tracked for awareness but never auto-targeted
and the sword never auto-attacks them.

### Auto-Target
The nearest hostile mob is selected as the awareness target. This selection is cosmetic
only — it does not lock the player's aim or change where the sword goes on launch. The
player always aims manually.

### Visual Response
When a hostile mob is within range, the sword smoothly transitions from its upright resting
orientation to point its **blade tip directly at the mob** in full 3D — like a compass
needle tracking a magnet. The transition is smooth, not instant.

When the last mob in range dies or exits the 16-block radius, the sword returns to its
upright resting orientation almost immediately. There is no "threat cooldown" delay — the
sword is intelligent enough to know immediately when the player is safe.

### Override
The auto-target does not override player aim. The sword's tilt is a visual indicator only.
Wherever the player looks when they launch, that is where the sword goes.

---

## 10. State Machine

All states below are substates of the **Flying Mode** parent state. They are mutually
exclusive. The familiar is always in exactly one state.

```
FLYING (parent)
├── ARRIVING          (spawn descent — implemented)
├── HOVERING
├── CHARGING
├── LAUNCHING
├── QUICK_FIRE        (V-key homing dart — implemented)
├── STUCK
├── TETHERING         (Phase 11 — not yet implemented)
├── SWEEPING_HOLD
├── SWEEPING_RELEASE
├── BLOCKING
├── RETURNING
└── DYING             (death animation hold — implemented)
```

Three of these states were added during implementation and exist in code today
(`FamiliarState.java`): ARRIVING, QUICK_FIRE, and DYING. They are specified below alongside
the original states. TETHERING is specified in this document but is not yet in code — it is
Phase 11 work.

### Universal Rules

**Input handling:** Every state defines an explicit input table listing all player inputs
and their result. Any input marked "Ignored" produces no effect, no feedback, and no state
change. If an input is not listed in a state's table, it is ignored. There are no implicit
transitions.

**Death:** Player death triggers the death logic described in Section 12 from **every**
state without exception.

**Entity validation:** Every server tick while flying mode is active, the server confirms
the familiar entity exists and is loaded. If the entity cannot be found for any reason
(chunk unloaded, removed by another mod, any unexpected absence), flying mode exits
immediately. See Section 18 for details.

**Hit detection on travel:** When the sword travels through space (LAUNCHING outbound,
RETURNING inbound), it maintains a **per-direction hit set**. Each entity can be damaged
at most **once per travel direction**. The hit set resets when the sword changes direction
(e.g., from outbound LAUNCHING to inbound RETURNING). This means an entity in the flight
path takes damage once on the way out and once on the way back — two hits total, not
per-tick.

---

### State: ARRIVING (implemented)

The spawn-entrance state. On flying mode activation, if there are at least **6 blocks** of
vertical clearance above the hover slot, the familiar spawns up to **16 blocks [TUNE]**
above the slot and descends at **2.5 blocks/tick [TUNE]**, emitting an END_ROD falling
streak. The descent target is recomputed each tick from the player's current hover slot, so
the sword lands beside the player even if they move.

**On touchdown:** plays an arrival sound (placeholder: `minecraft:block.amethyst_cluster.break`,
pitched down), deals a **landing impact** — 4 damage [TUNE] plus knockback to all living
entities within a 3-block radius (owner excluded) — and transitions to HOVERING.

**If clearance is under 6 blocks:** the state is skipped entirely; the sword materializes
directly at the hover slot (see Section 6 transition table).

**Input Handling:**
| Input | Transition |
|---|---|
| Touchdown at hover slot | → HOVERING |
| All combat/mode inputs | Ignored |
| Player dies | → death logic |

---

### State: HOVERING

The default state. The familiar floats at its current candidate hover position using spring
physics. It tilts toward the nearest mob if one is in range. It rests upright when no mobs
are nearby. The hand pose is relaxed.

**Entry:** Flying mode activated, or any active state concludes naturally.

**Input Handling:**
| Input | Transition |
|---|---|
| Left click (tap) | → LAUNCHING (uncharged) |
| Left click (hold) | → CHARGING |
| Right click (hold) | → SWEEPING_HOLD |
| G held | → BLOCKING |
| R pressed | Ignored (nothing to recall) |
| F pressed | Exit flying mode |
| Player dies | → death logic |

### Idle Personality Behaviors

When the player is in HOVERING with **no hostile mobs within awareness range** and **no
inputs for an extended period** [TUNE — suggested: 5+ seconds of idle], the familiar begins
exhibiting contextual idle behaviors. These are purely cosmetic — no new states, no
gameplay effect, no damage, no interaction. They run as branching logic within HOVERING's
idle animation system.

**Behaviors:**

- **Curious drift:** If a notable block is within 4 blocks (chest, crafting table, brewing
  stand, enchanting table, bookshelf), the sword slowly drifts 1–2 blocks toward it and
  tilts inquisitively, as if inspecting it. Holds for 2–3 seconds, then drifts back to its
  hover position. Only one curiosity drift per idle period — the sword does not ping-pong
  between multiple blocks.

- **Lazy figure-eight:** After 10+ seconds of complete idle (player standing still, no
  mobs, no block interactions), the sword traces a slow, lazy figure-eight in the air at
  its hover position. Gentle, meditative movement. Interrupts immediately on any player
  input or mob entering range.

- **Environmental reactions:** The sword recoils slightly (quick backward flinch, ~0.5
  blocks) from nearby fire, lava, or active TNT within 3 blocks. It perks up alertly (brief
  upward tilt and pause) when rain starts. These trigger once per stimulus, not continuously.

- **Resumption:** Any player input, any mob entering awareness range, or the player
  beginning to move cancels idle behaviors instantly. The sword snaps back to its standard
  hover position with no transition delay. Combat readiness is never compromised by
  personality.

---

### State: CHARGING

The sword moves to the **left or right side** of the player (whichever is currently
unobstructed, using the standard candidate position priority) and performs a **corkscrew
spin on its own axis** — a drill bit rotating in place, not orbiting the player. It stays
at the hover position and spins.

If **neither left nor right position is available**, CHARGING cannot be entered. The
left-click-hold input is silently ignored and the sword remains in HOVERING.

**Player movement:** Strongly slowed while charging, similar to drawing a bow.

**Stamina drain:** Epic Fight stamina bar drains at a medium rate. 3 seconds of full charge
drains approximately the same stamina as 3 seconds of active Epic Fight blocking.

**Taking damage does not interrupt charging.** The player can tank hits while building the
charge. This is intentional — the charge is a commitment the player protects by positioning
or by canceling into BLOCKING via G.

**If stamina depletes before 3 seconds:** Charge stops. Sword transitions to HOVERING.
No launch occurs.

**Charge tiers:**
- Release before 3 seconds: **normal strike** (16 damage, standard speed)
- Release at or after 3 seconds: **charged strike** (32 damage, double speed)

There are no intermediate tiers. Releasing early is always a normal strike.

**The sword does not auto-release at 3 seconds.** It continues corkscrewing at full charge
indefinitely while stamina remains, functioning as a held threat. The player chooses when
to release.

**Entry:** Left click held from HOVERING.

**Input Handling:**
| Input | Transition |
|---|---|
| Left click released | → LAUNCHING |
| G pressed | Cancel charge (no launch) → BLOCKING (no extra stamina cost for transition) |
| Stamina depleted | → HOVERING (no launch) |
| R pressed | Ignored |
| Right click | Ignored |
| F pressed | Ignored |
| Player dies | → death logic |

---

### State: LAUNCHING

The sword travels in a **straight line, tip-forward**, from its current world position
toward the player's **aim direction at the moment of release**. It does not arc. It does
not curve. It does not home toward the auto-targeted mob — it goes exactly where the player
aimed.

**Travel speeds:**
- Normal strike: same speed as a charged trident throw [TUNE]
- Charged strike: double that speed

**Maximum range:** 3 chunks = **48 blocks** from the launch point (not from the player).

**Damage — outbound:**
- Normal: **16 damage** per entity hit
- Charged: **32 damage** per entity hit
- The sword **pierces all entities** in the flight path. It does not stop at the first hit.
  Every entity in the line takes full damage.
- Each entity is hit **at most once** on the outbound path (per-direction hit set).

**Block contact:** The sword embeds in the first solid block face it contacts → STUCK.
Exception (pending — see Section 26): blocks in the `heirloomswordmod:pierceable` tag are
destroyed and flight continues. **As of Phase 8, the code embeds in every collidable block;
the pierceable exception is specified but not yet implemented.**

**At max range (no block hit):** The sword immediately transitions to RETURNING with no
pause. It flips to tip-forward and travels at **constant high speed** back to the player.

**Entry:** Left click released from CHARGING (or tapped from HOVERING for uncharged).

**Input Handling:**
| Input | Transition |
|---|---|
| R pressed | → RETURNING (after 1-tick delay) |
| Contacts solid block | → STUCK |
| Reaches 48-block max range | → RETURNING |
| Left click | Ignored |
| Right click | Ignored |
| G pressed | Ignored |
| F pressed | Ignored |
| Player dies | → death logic |

---

### State: QUICK_FIRE (implemented)

**Keybind: V** (configurable). A fast, low-commitment homing dart at the sword's current
**awareness target** (the server-side mob lock from Section 9 — never a client-supplied
target). Available from HOVERING only. If there is no living awareness target, or the
quick-fire cooldown is active, the input is silently ignored.

**Behavior:**
- The sword homes toward the target's center, re-aiming **every tick**, at normal launch
  speed.
- **On contact:** deals **12 damage [TUNE]**, light knockback (0.3), ignites undead
  (Section 10.5), then immediately transitions to RETURNING.
- **Quick-fire never sticks.** Block contact, target death/despawn, or exceeding the
  48-block max range all transition to RETURNING.
- **Cooldown:** 20 ticks (~1s) [TUNE] between quick-fires.
- No charge tiers, no stamina cost, no piercing — single-target only.

**Network:** `SwordQuickFirePacket` (Client → Server) carries only the keypress; the server
resolves the target from its own awareness lock and validates state.

**Entry:** V pressed during HOVERING with a live awareness target.

**Input Handling:**
| Input | Transition |
|---|---|
| Contacts target | Damage → RETURNING |
| Contacts solid block | → RETURNING (no STUCK) |
| Target dies or unloads | → RETURNING |
| Exceeds 48-block range from player | → RETURNING |
| All other inputs | Ignored |
| Player dies | → death logic |

---

### State: STUCK

The sword is embedded in a block face, tip-first. It vibrates slightly (animation clip:
`stuck`). It stays at its world position. The player can move freely.

**Auto-return timer:** 3 seconds. After 3 seconds with no input, transitions to RETURNING.

**Return from STUCK:** Tip-forward, 8 damage per entity on return arc (per-direction hit
set — each entity hit at most once on the return). Sword phases through blocks during
RETURNING (see Section 10, RETURNING state).

**Entry:** Sword contacts solid block during LAUNCHING.

**Input Handling:**
| Input | Transition |
|---|---|
| R pressed | → RETURNING (after 1-tick delay, cancels 3s timer) |
| Shift pressed (fresh) | → TETHERING (cancels 3s timer) |
| 3 seconds elapsed | → RETURNING |
| Left click | Ignored |
| Right click | Ignored |
| G pressed | Ignored |
| F pressed | Ignored |
| Player dies | → death logic (immediate despawn, no animation) |

---

### State: TETHERING

Shift pressed (fresh) while in STUCK. The player is yanked toward the **midpoint** between
their current position and the sword's embedded position — a half-distance grapple. The
sword stays embedded during the pull; on arrival, it transitions to RETURNING. This gives
the sword a traversal identity and rewards aggressive positioning: launch the sword into a
wall across a gap, then pull yourself halfway and let the sword fly back to meet you.

**Fresh press requirement:** Shift must be **pressed during STUCK**, not already held when
STUCK is entered. If the player was sneaking (holding Shift) when the sword embedded, they
must release Shift and press it again to trigger the tether. This prevents accidental
activation when the player was sneaking near an edge and launched.

**Midpoint calculation:** Player is at position A, sword is embedded at position B. The
tether destination is the midpoint M between A and B. The player is pulled toward M, not
toward the sword itself.

**Player movement:** A strong velocity vector is applied toward the midpoint each tick. The
player **collides with blocks normally** — they do not phase through terrain. The player can
still take damage during the pull. No player input controls steering; the pull direction is
always toward the midpoint.

**On arrival at midpoint:** When the player reaches within **2 blocks** of the midpoint,
the pull stops. The sword transitions to RETURNING — pulls out of the block, tip-forward,
8 damage per entity on the return path from the sword's position to the player's new
position, phases through blocks as normal RETURNING behavior. Arrives at the player →
HOVERING.

**On timeout:** If the player has not arrived within **2 seconds** [TUNE], the pull stops.
The sword transitions to RETURNING.

**On geometry block:** If the player's velocity toward the midpoint drops to effectively
zero (stuck against a wall or ceiling) for more than **10 ticks** [TUNE], the pull stops.
The sword transitions to RETURNING. The player is not left hanging.

**Pull speed:** Fast enough to feel dramatic, slow enough to read visually. Starting point:
roughly **1.5× sprint speed** [TUNE]. The pull accelerates slightly as the player gets
closer to sell the "reeling in" feel.

**Stamina cost:** None. The tether is a reward for landing a STUCK, not an additional drain.

**Entry:** Shift pressed (fresh) during STUCK.

**Input Handling:**
| Input | Transition |
|---|---|
| Arrives at midpoint (~2 blocks) | Pull stops → sword enters RETURNING |
| 2 seconds elapsed | Pull stops → sword enters RETURNING |
| Geometry blocked (10+ ticks) | Pull stops → sword enters RETURNING |
| Left click | Ignored |
| Right click | Ignored |
| G pressed | Ignored |
| R pressed | Ignored |
| F pressed | Ignored |
| Player dies | → death logic |

---

### State: SWEEPING_HOLD

Right click held. The sword moves from its side position to a point **2.5 blocks directly
in front of the player**, hilt facing the player, blade pointing away. From this position
it behaves like an object held with a physics gun (Garry's Mod reference): it tries to
remain at 2.5 blocks in front of wherever the player's crosshair points, with inertia.

**Physics:**
- Slow view movement: sword barely moves from its position
- Fast view movement: sword swings with momentum, lagging behind the view direction
- The faster the player turns, the more momentum the sword carries
- Minimum momentum threshold to engage swing: approximately **3 degrees per tick** [TUNE]
  Below this threshold, the sword stays roughly stationary even with right click held.

**Damage during SWEEPING_HOLD:**
- **4 damage** per entity the sword's hitbox overlaps
- **Invulnerability frames:** After the sword damages an entity, that entity receives a
  brief invulnerability window before it can be damaged again by the same sweep. This
  prevents per-tick stacking. Exact frame count is [TUNE — suggested starting point:
  10 ticks / 0.5 seconds].
- **No friendly fire** — the sword cannot damage its own player
- **Knockback:** Knockback 1 strength, applied in the **direction the sword is currently
  traveling** at the moment of contact (not away from the player)

**Stamina drain:** Low but noticeable drain on the Epic Fight stamina bar while held.
Cannot be sustained indefinitely.

**Charging is blocked** while in SWEEPING_HOLD. Left-click-hold during this state does
nothing.

**Entry:** Right click held from HOVERING.

**Input Handling:**
| Input | Transition |
|---|---|
| Right click released | → SWEEPING_RELEASE |
| G pressed | Cancel sweep → BLOCKING |
| F pressed | Instant exit flying mode (despawn entity, sword to inventory) |
| Left click | Ignored |
| R pressed | Ignored |
| Player dies | → death logic |

---

### State: SWEEPING_RELEASE

Right click released from SWEEPING_HOLD. The sword launches with **whatever velocity and
direction it had at the moment of release** — inherited directly from the swing momentum.
It maintains its orientation on departure (does not flip to tip-forward). If the sword was
nearly stationary at release, it drifts forward weakly. If it was mid-swing, it shoots in
that direction at speed.

**Maximum arc radius:** 12 blocks from the player.

**Damage during travel:** None. The sweep release is purely momentum-based travel with no
additional damage on the outbound path beyond what SWEEPING_HOLD already dealt.

**Return:** The sword returns **hilt-first** (not tip-forward). This distinguishes sweep
return from all other return states.

**Damage on SWEEPING_RELEASE return:** None. The hilt-first return is passive and
non-damaging.

**Block phasing on return:** The sword phases through all solid blocks during its hilt-first
return, identical to the RETURNING state's block phasing behavior. It always reaches the
player.

**Entry:** Right click released from SWEEPING_HOLD.

**Input Handling:**
| Input | Transition |
|---|---|
| Sword returns to player | → HOVERING |
| Left click | Ignored |
| Right click | Ignored |
| G pressed | Ignored |
| R pressed | Ignored |
| F pressed | Ignored |
| Player dies | → death logic |

Full commitment — the arc must complete naturally. No recall, no mode exit.

---

### State: BLOCKING

G key held. The sword moves to a position **directly in front of the player** and tilts
into a diagonal **X-across-the-chest** orientation. The sword tracks with the player's look
direction — it stays in front of the player as they turn, maintaining the X orientation
relative to the player's facing.

**Damage reduction:** Equivalent to a vanilla Minecraft shield. **Frontal damage only.**
Does not block explosions. Does not block magic or area-of-effect damage.

**Stamina drain:** Mimics Epic Fight's natural blocking stamina behavior, draining the
Epic Fight stamina bar.

**If stamina depletes during BLOCKING:** The guard breaks. The `guard_break` animation
plays (brief stagger/wobble). The sword transitions to HOVERING. **No horizontal slash
fires.** The G keybind enters a **3-second cooldown** before BLOCKING can be entered again.

**Projectile interception:** Physical projectiles (arrows, thrown tridents, fireballs)
that would hit the player are **intercepted** by the sword and deflected geometrically.
The deflection is based on the angle of incidence against the sword's face — the projectile
bounces off the blade's plane at a **reduced speed** (not full-speed reflection). Magic
projectiles, explosions, and area effects are not intercepted.

**Charging is blocked** while in BLOCKING. Left-click-hold during BLOCKING does nothing.

**On release of G key (with stamina remaining):** The sword executes a **wide horizontal
slash** in front of the player (one side to the other), using the `block_slash` animation
clip. This slash is always the same regardless of how long G was held. The slash uses a
fixed damage value [TUNE — suggested: 12–14 damage]. After the slash completes, the sword
returns to HOVERING.

**Entry:** G held from HOVERING, or G pressed during CHARGING (cancels charge) or
SWEEPING_HOLD (cancels sweep).

**Input Handling:**
| Input | Transition |
|---|---|
| G released (stamina remaining) | → `block_slash` animation → HOVERING |
| Stamina depleted | → `guard_break` animation → HOVERING (no slash, G on 3s cooldown) |
| F pressed | Instant exit flying mode (despawn entity, sword to inventory) |
| Left click | Ignored |
| Right click | Ignored |
| R pressed | Ignored |
| Player dies | → death logic |

---

### State: RETURNING

The RETURNING state is entered from LAUNCHING (max range or R pressed) and STUCK (timer or
R pressed). SWEEPING_RELEASE handles its own hilt-first return internally and does not
transition through RETURNING.

**Behavior:**
- Sword flips 180° to travel **tip-forward**
- Travels at **constant high speed** back to the player
- Deals **8 damage** per entity it passes through
- Each entity is hit **at most once** on the return path (per-direction hit set)

**Universal rule — block phasing:**
During the RETURNING state, the familiar **phases through all solid blocks**. It does not
collide with terrain on the return journey. It always reaches the player. This is identical
to a Loyalty trident's return behavior.

**Arrival:** When the familiar reaches within **vanilla item pickup range (~1.5 blocks)**
of the player, it transitions to HOVERING and resumes normal floating behavior. The sword
decelerates using spring physics (overshoot, oscillate, settle into hover position).

**Input Handling:**
| Input | Transition |
|---|---|
| Arrives at player | → HOVERING |
| Left click | Ignored |
| Right click | Ignored |
| G pressed | Ignored |
| R pressed | Ignored |
| F pressed | Ignored |
| Player dies | → death logic |

---

### State: DYING (implemented)

A terminal hold state entered by the death logic (Section 12) when the player dies in any
familiar state except STUCK. The familiar plays the `death_fall` clip, holds for the
animation duration, then discards itself. All inputs are ignored. This state exists so the
death animation has time to play before the entity despawns — it is an implementation
detail of Section 12, not a player-reachable combat state.

---

### 10.5 Undead Ignition (implemented)

The blade is anathema to the undead. Any entity tagged `minecraft:undead` that contacts the
sword's hitbox — in **any** state, including passive hover contact — is ignited for
**4 seconds [TUNE]**. Quick-fire hits also apply the ignite on contact. The check runs every
5 ticks on entities overlapping the sword's bounding box (inflated 0.2 blocks); the owner is
excluded. No config gate currently exists [TUNE — consider adding `combat.undeadIgnite`
toggle in the add-on config phase].

---

## 11. Recall — R Key

**Available from:** LAUNCHING, STUCK.
**Not available from:** HOVERING (nothing to recall), CHARGING (charge must be released or
canceled), SWEEPING_HOLD (must release right click), SWEEPING_RELEASE (full commitment),
BLOCKING (sword is in guard position, not away from the player), TETHERING (player is being
pulled to sword), RETURNING (already returning).

**Behavior:** A 1-tick delay occurs after R is pressed (the sword "hears" the command),
then the sword immediately transitions to RETURNING using tip-forward mode. The hit set
resets for the return direction.

**Damage on recalled return:** 8 damage per entity, each entity hit at most once on the
return path. Same as natural return behavior.

---

## 12. Death Logic

**Applies to all states.** When the player dies while flying mode is active, regardless of
which state the familiar is in:

**If the familiar is in any state except STUCK:**
1. The familiar plays the `death_fall` animation — a visual-only stick-in-ground animation
   from its current world position.
2. The familiar entity despawns entirely. No item is dropped by the entity.
3. The sword item drops at the **player's death coordinates** as part of the normal
   inventory drop. There is always exactly one sword item in the world at any time.

**If the familiar is in STUCK state:**
1. The familiar despawns immediately with no animation (it is already embedded in a block).
2. The sword item drops at the player's death coordinates as above.

The familiar entity never independently drops the sword as a separate item under any
circumstances. The item always travels with the inventory.

---

## 13. Water & Swimming Logic

> **OVERRULED (decision 2026-06-14) — Sections 13–16 mid-flight gating is NOT implemented and will
> not be.** Playtesting found the default behavior good as-is, so the mid-flight auto-exits (swimming
> §13, elytra §15, dimension §16) and the mount-block-while-flying (§14) were cut. The entity-
> validation tick (§18) already resets flying mode cleanly when the familiar becomes invalid (e.g.
> across a dimension change), which covers these cases naturally. The **only** change kept from this
> area is **vehicle-damage immunity** — the sword never damages the owner's own mount (commit
> `4bd047b`). The specs in §13–§16 below are retained for historical context only. See the Phase 7
> status note in Section 24.

**Trigger:** The player's `EntityPose` transitions to `EntityPose.SWIMMING`.
Wading through shallow water without entering the swimming pose has no effect on flying mode.

**What happens:**
- If the familiar is in any state other than RETURNING: it immediately transitions to
  RETURNING, phases through any blocks in the way, and reaches the player.
- If the familiar is already in RETURNING: it continues its return normally.
- On arrival at the player in either case: flying mode exits automatically.

**Re-engagement:** Flying mode **never re-engages automatically**. When the player exits
the water and the swimming pose ends, flying mode remains inactive. The player must press F
manually to re-enter flying mode.

---

## 14. Mounting & Riding Logic

Flying mode cannot be **entered** while the player is riding any entity.

> **Overruled (2026-06-14):** the mount-block-while-flying described below was **cut**. A flying-mode
> player may mount freely. The only implemented protection in this area is that the sword's attacks
> cannot damage the owner's own mount. The original spec is kept below for historical context.

If flying mode is **already active** and the player attempts to mount or ride anything
(horse, boat, minecart, pig, strider, or any other `is_passenger` situation), the
**mount action is blocked**. The player receives a hotbar message: *"Sheathe your sword
first."* The player must exit flying mode manually before mounting.

Flying mode **never exits automatically** due to mounting because the mount action is
prevented entirely.

---

## 15. Elytra Logic

Flying mode cannot be **entered** while the player is in elytra flight.

If flying mode is **already active** and the player attempts to enter elytra flight, the
elytra behavior follows the same pattern as water/swimming:

- If the familiar is in any state other than RETURNING: it immediately transitions to
  RETURNING, phases through any blocks in the way, and reaches the player.
- If the familiar is already in RETURNING: it continues its return normally.
- On arrival at the player: flying mode exits automatically.

**Re-engagement:** Flying mode **never re-engages automatically**. When elytra flight ends,
flying mode remains inactive. The player must press F manually to re-enter flying mode.

---

## 16. Dimension Travel

If the player enters a dimension transition (nether portal, end portal, any inter-dimension
travel) while flying mode is active, the behavior follows the same pattern as
water/swimming:

- If the familiar is in any state other than RETURNING: it immediately transitions to
  RETURNING, phases through any blocks in the way, and reaches the player.
- If the familiar is already in RETURNING: it continues its return normally.
- On arrival at the player: flying mode exits automatically.

The familiar does **not** travel across dimensions. It returns to the player and despawns
in the origin dimension before the player completes the transition.

**Re-engagement:** Flying mode **never re-engages automatically** after dimension travel.
The player must press F manually in the new dimension.

---

## 17. Disconnect & Reconnect

When the server detects the player disconnecting (voluntary logout or crash) while flying
mode is active:

1. The familiar entity is **immediately despawned**. No animation, no return travel.
2. The sword's mode is set to **normal mode**.
3. No state is persisted for restoration.

On reconnect, the player loads in with the sword in their hotbar in normal mode. Press F
to re-enter flying mode. This is a clean reset — no orphaned entities, no stale state.

This mirrors how Minecraft handles player-bound transient entities (e.g., fishing bobbers)
and avoids the fragile edge cases of trying to restore familiar state across a reconnect.

---

## 18. Entity Validation

Every server tick while flying mode is active, the server confirms that the familiar entity
exists and is loaded. If the entity cannot be found for **any reason** — chunk unloaded,
removed by another mod, any unexpected absence — the following occurs:

1. Flying mode exits immediately.
2. The sword returns to normal mode in the player's hotbar.
3. A hotbar message is displayed: *"The sword returns to your side."*

No animation, no return travel. This is a safety net that covers chunk unloading during
STUCK (where the player walks away and the chunk containing the embedded sword unloads),
as well as any other edge case where the entity goes missing. The 3-second STUCK auto-return
handles normal gameplay; this validation tick only fires in degenerate situations where the
entity is already gone and there is nothing to animate.

---

## 19. Multiplayer

- Each familiar entity is independently owned by the player whose UUID spawned it.
- Familiar entities do not interact with each other.
- A sword owned by Player A cannot be picked up by Player B while in any active state. The
  item stays in Player A's hotbar.
- If Player A dies, the item drops normally and Player B may pick it up. When Player B
  activates flying mode with that item, a new familiar spawns with Player B as the new owner.
  Previous familiar data is irrelevant.
- The sword's mob awareness tracking **excludes** other players — the sword never tilts
  toward players, never auto-attacks players, and never treats players as targets under any
  condition.
- Familiar entities are saved as persistent entities in chunk NBT. If the chunk containing
  a familiar unloads (edge case requiring deliberate player effort), the entity validation
  system (Section 18) handles recovery.

---

## 20. Rendering Summary

| Element | Renderer | Notes |
|---|---|---|
| Familiar entity | GeckoLib `GeoEntityRenderer` | Full animated model, all clips |
| In-hand (normal mode) | Standard item renderer | Default hold position |
| In-hand (flying mode) | Custom suppressed renderer | Empty hand, shimmer effect |
| Hotbar icon | Standard 2D + shader overlay | Purple glow while flying |
| Projectile deflection | Particle burst on sword face | On projectile intercept |
| Death animation | GeckoLib clip `death_fall` | Visual only, entity despawns after |
| Block slash | GeckoLib clip `block_slash` | On G release with stamina remaining |
| Guard break | GeckoLib clip `guard_break` | On stamina depletion during BLOCKING |
| Tether pull | GeckoLib clip `tether_pull` | Intensified vibration + glow while player pulled |
| Tether arrival | Particle burst on sword release | When pull ends and sword wrenches free into RETURNING |
| Idle personality | GeckoLib clips `idle_curious`, `idle_figure_eight`, `idle_recoil`, `idle_perk` | Cosmetic only, within HOVERING |
| Familiar spawn | Fade-in + particle burst | On flying mode entry |

---

## 21. Sound Design

Custom sounds required for final release. Minecraft audio placeholders used during
development. Placeholder mappings:

| Event | Placeholder |
|---|---|
| Familiar hovering (ambient loop) | `minecraft:block.enchantment_table.use` |
| Mode enter (flying) | `minecraft:entity.experience_orb.pickup` |
| Mode exit (normal) | `minecraft:entity.experience_orb.pickup` (pitched down) |
| Launch (uncharged) | `minecraft:item.trident.throw` |
| Launch (charged) | `minecraft:item.trident.throw` (pitched down, louder) |
| Impact on entity | `minecraft:entity.player.attack.sweep` |
| Embed in block | `minecraft:item.trident.hit_ground` |
| Return arrival | `minecraft:entity.experience_orb.pickup` |
| Sweep hold (contact) | `minecraft:entity.player.attack.knockback` |
| Block stance raised | `minecraft:item.shield.block` |
| Block stance hit | `minecraft:item.shield.block` |
| Projectile deflect | `minecraft:item.trident.riptide_1` |
| Blocking slash release | `minecraft:entity.player.attack.sweep` |
| Guard break | `minecraft:item.shield.break` |
| Tether pull start | `minecraft:block.chain.break` |
| Tether pull loop | `minecraft:entity.fishing_bobber.retrieve` (looped, pitched up) |
| Tether arrival | `minecraft:entity.enderman.teleport` (pitched up, quieter) |
| Charge building | `minecraft:block.amethyst_block.resonate` (looped) |
| Death fall animation | `minecraft:item.trident.hit_ground` |

---

## 22. Network Architecture

The following custom packets are required. All use NeoForge's `CustomPacketPayload` system.

| Packet | Direction | Purpose |
|---|---|---|
| `SwordModePacket` | Client → Server | Player pressed F (mode toggle or emergency exit) |
| `SwordLaunchPacket` | Client → Server | Launch vector and charge level on release |
| `SwordRecallPacket` | Client → Server | Player pressed R |
| `SwordGuardPacket` | Client → Server | G key pressed or released (from any valid source state: HOVERING, CHARGING, or SWEEPING_HOLD) |
| `SwordTetherPacket` | Client → Server | Player pressed Shift (fresh press) during STUCK (tether pull request) |
| `SwordQuickFirePacket` | Client → Server | Player pressed V (quick-fire request; server resolves target from its own awareness lock) |
| `SwordMomentumPacket` | Client → Server | Per-tick sweep velocity delta (during SWEEPING_HOLD) |
| `FamiliarStatePacket` | Server → Client | Authoritative state sync for rendering |

Server validates all incoming packets against the current server-side state. For example,
a `SwordGuardPacket` arriving while the familiar is in LAUNCHING is silently discarded.
The server is authoritative — client-side prediction is used only for smooth rendering of
the familiar's position between server updates.

All incoming vectors are validated for sanity (clamped range, plausible direction) before
applying.

---

## 23. Tuning Values (Playtesting Targets)

All values below are starting points subject to adjustment. They are not final.

| Value | Starting Point | Notes |
|---|---|---|
| Hover radius | 1.5 blocks | From player torso anchor |
| Collision sphere radius | 0.4 blocks | Aggressive mode |
| Max lag before catch-up | 3 blocks | Spring physics |
| Catch-up speed | Faster than sprint | Fixed speed |
| Spring overshoot amount | Moderate | Gives personality |
| Normal launch speed | Charged trident throw speed | Roughly 50 m/s |
| Charged launch speed | 2× normal | ~100 m/s |
| Max strike range | 48 blocks (3 chunks) | From launch point |
| Return speed | Constant high speed | Faster than launch |
| Sweep hold distance | 2.5 blocks in front | From player |
| Sweep minimum momentum | ~3°/tick | [TUNE] Might go to 2.5° |
| Sweep invulnerability frames | 10 ticks (0.5 seconds) | [TUNE] Per-entity after hit |
| Mob awareness radius | 16 blocks | Hostile mobs only |
| STUCK auto-return timer | 3 seconds | |
| Tether pull speed | ~1.5× sprint speed | [TUNE] Slight acceleration on approach |
| Tether arrival range | 2 blocks | From calculated midpoint |
| Tether timeout | 2 seconds | Pull stops, sword returns |
| Tether geometry-block threshold | 10 ticks | Zero velocity toward sword |
| Idle personality trigger | 5 seconds | No inputs, no mobs in range |
| Idle curiosity range | 4 blocks | Notable blocks only |
| Idle curiosity hold time | 2–3 seconds | Before drifting back |
| Idle figure-eight trigger | 10 seconds | Extended idle, player standing still |
| Idle recoil range | 3 blocks | Fire, lava, active TNT |
| Blocking slash damage | 12–14 | [TUNE] |
| Guard break cooldown | 3 seconds | Cooldown on G after stamina depletion |
| Normal mode base damage | 10 | Epic Fight value |
| Charge stamina drain | ~medium | Roughly 3s blocking equivalent |
| Sweep hold stamina drain | Low | Cannot sustain indefinitely |
| Blocking stamina drain | Mirrors Epic Fight shield | |
| Return arrival range | ~1.5 blocks | Vanilla item pickup range |
| Swimming trigger | EntityPose.SWIMMING | Not water contact |
| Mount block trigger | is_passenger == true | Any riding state |
| Quick-fire damage | 12 | [TUNE] Single target, no charge tiers |
| Quick-fire cooldown | 20 ticks (~1s) | [TUNE] |
| Undead ignite duration | 4 seconds | [TUNE] Any blade contact, all states |
| Sky-drop spawn height | 16 blocks above hover slot | [TUNE] Min clearance 6 blocks, else materialize |
| Sky-drop descent speed | 2.5 blocks/tick | [TUNE] |
| Landing impact | 4 damage + knockback, 3-block radius | [TUNE] Owner excluded |

---

## 24. Implementation Phase Order

Recommended development sequence. Do not skip phases.

**Phase 1 — Foundation**
NeoForge 1.21.1 MDK setup. Item registration. F keybind. Hotbar glow indicator. Mode flag
stored as `DataComponentType<SwordMode>`. Q-key drop prevention. Ancient City loot table
injection (5% per chest). Placeholder item texture.

**Phase 2 — Familiar Entity (HOVERING only)**
`SwordFamiliarEntity` registration. Ownership UUID. Spawn on mode enter (with fade-in and
particle burst). Despawn on mode exit. Candidate position system with obstacle avoidance.
Spring physics follow. Mob awareness scan and visual tilt. Entity validation tick. Use a
debug cube hitbox — no GeckoLib yet. Validate physics feel before any other state is built.

**Phase 3 — LAUNCHING and RETURNING**
Straight-line tip-forward travel. Piercing entity hit detection with per-direction hit set.
Damage values. STUCK state with 3-second timer. Block phasing on return. R key recall.
Return arrival using vanilla pickup range with spring physics deceleration. Death logic
(all states). Network packets for launch and recall.

**Phase 4 — CHARGING**
Corkscrew spin in place with ramp-up on entry. Charge timer and binary tier. Stamina drain
via Epic Fight. Slow-movement penalty. No damage interruption. Hold-as-threat behavior.
G-cancel into BLOCKING transition (no extra cost).

**Phase 5 — SWEEPING_HOLD and SWEEPING_RELEASE**
Physics-gun style sword hold. Momentum tracking and minimum threshold. Contact damage with
invulnerability frames and directional knockback. Hilt-forward return with no damage.
Stamina drain. G-cancel into BLOCKING transition. F emergency exit.

**Phase 6 — BLOCKING**
G keybind with 3-second cooldown after guard break. X-across-chest position. Damage
reduction. Stamina drain via Epic Fight. Guard break behavior on stamina depletion (no
slash, `guard_break` animation, cooldown). Projectile interception and geometric
deflection. Horizontal `block_slash` on G release with stamina remaining. F emergency exit.

**Phase 7 — Restrictions and Edge Cases**
Mount blocking (Section 14). Swimming pose detection and auto-exit (Section 13). Elytra
detection and auto-exit (Section 15). Dimension travel auto-exit (Section 16). Disconnect
clean reset (Section 17). Q-key prevention. Mode lock conditions with BLOCKING and
SWEEPING_HOLD exceptions for F.

> **Status (2026-06-15): COMPLETE — mid-flight gating overruled by design decision.** Done:
> entry-blocking (cannot summon while riding/swimming/elytra-flying, client + server), Q-drop
> prevention, entity-validation tick, disconnect cleanup, loot injection.
> **Overruled (decision 2026-06-14, do NOT implement):** (a) mount-action blocking *while flying*
> with a "Sheathe your sword first." message (Section 14); (b) mid-flight swimming auto-exit
> (Section 13); (c) mid-flight elytra auto-exit (Section 15); (d) dimension-travel auto-exit
> (Section 16). Playtesting showed the default behavior feels good as-is, and the entity-validation
> tick already resets flying mode cleanly when the familiar becomes invalid (e.g. across a dimension
> change). The **only** change kept from this area is **vehicle-damage immunity**: the sword's
> attacks never damage the owner's own mount (commit `4bd047b`). (Earlier cloud sessions left
> misleading commit/doc text implying these were still pending — they are not.)

**Phase 8 — GeckoLib Integration**
Replace debug hitbox with full Blockbench model and all animation clips (including
`block_slash` and `guard_break`). All transition animations per Section 6 transition notes.
Hand gesture system. Telekinetic shimmer effect. Hotbar purple glow shader. Familiar spawn
fade-in effect.

**Outstanding Work Before Phase 9** — RESOLVED (2026-06-15)
1. Phase 7 mid-flight gating (mount-block while flying, swimming/elytra/dimension auto-exits):
   **OVERRULED — not implemented, will not be** (see the Phase 7 status note above). The default
   behavior was kept; only vehicle-damage immunity was added (commit `4bd047b`).
2. Block piercing (Section 26): **DONE** — `heirloomswordmod:pierceable` wired into the LAUNCHING
   phase (commit `4bd047b`).

Phase 9 itself is also **DONE** (datapack-only Epic Fight greatsword compat; see the Phase 9 entry).

**Phase 9 — Epic Fight Combat Integration** (DESCOPED 2026-06-13)
> **Scope changed.** The `StaminaProvider` interface and the internal hidden stamina pool
> are **cancelled** — see Section 1, "Resource Model". Flying-mode action costs (CHARGING /
> SWEEPING_HOLD / BLOCKING drains + guard break) are powered by **mana**, which is **already
> implemented** before this phase by `docs/superpowers/plans/2026-06-13-mana-system.md`. Do
> not re-implement those drains or any stamina abstraction here.

This phase is now **only** the normal-mode greatsword integration: an `EpicFightCompat`
class guarded by `ModList.get().isLoaded("epicfight")`, greatsword weapon-type registration,
and the moveset/skill hookup so normal mode uses Epic Fight combat (with Epic Fight's own
native stamina). Vanilla-swing fallback when Epic Fight is absent. No mod-side stamina code.

> **Phase 9 status (2026-06-15): COMPLETE — datapack only, no code, no dependency.**
> Implemented purely as a bundled Epic Fight capability datapack
> (`data/heirloomswordmod/capabilities/weapons/heirloom_sword.json` → `epicfight:greatsword`).
> The `EpicFightCompat` Java class described above was **not** built: a code class would require
> linking against Epic Fight (a dependency), which is forbidden — the mod must be *compatible, not
> dependent*. Epic Fight is declared `optional` in `neoforge.mods.toml`. Vanilla netherite-sword
> fallback when Epic Fight is absent. No mod-side stamina/mana code added.
>
> **Explicitly out of scope (decided 2026-06-15):** guarding Epic Fight's battle-mode toggle so the
> sword's flying mode forces Epic Fight back to mining/vanilla mode. Epic Fight exposes no stable
> public API for mode control; the state lives in internal classes (`LocalPlayerPatch`,
> `InputManager`) that Epic Fight renames across minor versions. Any implementation (compileOnly or
> reflection) would couple the mod to Epic Fight internals and break on Epic Fight updates —
> unacceptable for an optional integration. Known residual interaction: while in Epic Fight battle
> mode, clicks route to Epic Fight combat rather than the familiar. Resolution is left to the player,
> not coded around: (1) Epic Fight's mode toggle and the sword's recall both default to **R** — the
> vanilla Controls screen flags this conflict, and the player rebinds either key; (2) the player
> manually toggles Epic Fight out of battle mode before/while using flying mode. No mod code involved.

**Phase 10 — Audio and Polish**
Custom sound event registration with Minecraft placeholders (including `guard_break` and
`tether_pull` sounds). Particle effects on impact, deflection, embed, spawn, and tether
arrival. Network sync polish and client prediction. Third-person review of all states and
transitions.

**Phase 11 — Tether Pull**
TETHERING state implementation. Shift input during STUCK (fresh press only — Shift already
held is ignored). Player velocity application toward midpoint between player and sword with
per-tick direction updates. Normal block collision (no phasing). Arrival detection at
midpoint → sword transitions to RETURNING. Timeout and geometry-block fallbacks →
RETURNING. `SwordTetherPacket` network packet. `tether_pull` animation clip with
intensified vibration and glow. Tether arrival particle burst. Sound events for start, loop,
and arrival.

**Phase 12 — Idle Personality**
Idle behavior branching within HOVERING state. Idle timer tracking (5s trigger for
curiosity, 10s for figure-eight). Notable block scanning within 4-block radius. Curious
drift movement with inquisitive tilt. Figure-eight trace path at hover position.
Environmental stimulus detection (fire, lava, TNT within 3 blocks; rain start event).
Recoil and perk reactions (one-shot per stimulus). Instant cancellation on any player input,
mob entering range, or player movement. Four idle animation clips: `idle_curious`,
`idle_figure_eight`, `idle_recoil`, `idle_perk`.

**Phase 13 — Add-On: Polish, Protection & Soul**
The hardening pass specified in Section 25 (items 1–12; item 6 already shipped, item 12a
dropped). Runs after all main phases. Items 1–3 (config, localization, tooltip) are
infrastructure the later items depend on — implement in the listed order, confirming
compilation after each item.

---

## 25. Add-On Phase — Polish, Protection & Soul (Phase 13)

Merged from the add-on phase prompt on 2026-06-12. All namespaces corrected from the
prompt's `alucardsword` to the real mod id **`heirloomswordmod`**. Implement items in
order — items 1–3 are infrastructure later items depend on. Confirm compilation after each
item.

### 25.1 Config file

A TOML config (NeoForge `ModConfigSpec`, COMMON type — already registered in the mod
constructor) exposing **every [TUNE] value from Section 23**, including the rows added for
quick-fire, undead ignite, sky-drop, and landing impact, plus the toggles introduced by the
items below. Sections:

| Section | Contents |
|---|---|
| `combat` | Damage values, speeds, ranges, quick-fire cooldown, undead ignite duration |
| `tether` | Pull speed, timeout, arrival range, geometry-block threshold |
| `idle` | Trigger timers, curiosity/recoil ranges, hold times |
| `mana` | Flying-mode drain rates, guard-break cooldown, warp cost/cooldown (Section 1, Resource Model) |
| `integration` | `allowPvpDamage`, `respectMobGriefing`, `sculkResonance` (items 5, 9, 11) |

Replace **all** hardcoded constants in the codebase with config lookups. Config changes
apply on world reload; no in-game GUI needed.

### 25.2 Localization

No hardcoded player-facing string may remain in Java code. **Partially done already:**
`assets/heirloomswordmod/lang/en_us.json` exists and the messages "The sword refuses to
leave your side." (`msg.heirloomswordmod.no_drop`), "Recall your sword first."
(`msg.heirloomswordmod.no_mount`), and "The sword returns to your side."
(`msg.heirloomswordmod.sword_returns`) are already lang keys. Remaining work:

- Audit all Java code for any remaining hardcoded user-visible strings.
- Add "Sheathe your sword first." (`msg.heirloomswordmod.sheathe_first`) when the Phase 7
  leftover mount-block-while-flying lands (Section 14). Note this is a *different* message
  from `no_mount`: `no_mount` fires when entering flying mode while riding; `sheathe_first`
  fires when mounting while flying.
- Tooltip lore keys (item 3) and advancement title/description keys (item 12c).

### 25.3 Tooltip lore

Italic, purple-tinted (`ChatFormatting.DARK_PURPLE` + `ITALIC`) lore on the item tooltip
via lang keys (`tooltip.heirloomswordmod.lore1`, `tooltip.heirloomswordmod.lore2`):

> *"Forged in darkness, bound by will."*
> *"It does not serve. It accompanies."*

### 25.4 Inventory movement lock

While flying mode is active, the sword item cannot be moved out of its hotbar slot by ANY
inventory interaction: dragging in the inventory screen, shift-clicking, number-key swap,
offhand swap (handle the **vanilla swap keybind whatever it is bound to** — this mod uses F
for mode toggle, so the player may have rebound vanilla offhand-swap), dropping into
containers, or cursor pickup. Cancel the relevant container events **server-side**. On any
blocked attempt, show the existing `msg.heirloomswordmod.no_drop` hotbar message. In normal
mode, all inventory movement works as usual.

This lock governs item **relocation**, not hotbar **selection**. The player may freely scroll
the selected slot away from the sword while flying — the familiar keeps hovering at their side
so they can mine, build, or use other hotbar items. Do **not** add a recall-on-scroll or
force-selected-slot behavior; the floating familiar during other actions is intended.

### 25.5 PvP damage rule

Launched sword damage (LAUNCHING outbound, RETURNING inbound), QUICK_FIRE contact damage,
and SWEEPING_HOLD contact damage CAN damage other players, but only when the server allows
PvP (respect the server `pvp` setting and team/friendly-fire rules). Config toggle
`integration.allowPvpDamage` (default `true`) disables all sword-vs-player damage even
where PvP is on. Mob awareness / auto-tilt / quick-fire targeting continues to **never**
target players regardless of this setting — unchanged from Section 9.

### 25.6 Charge-complete cue

**Already implemented.** No work.

### 25.7 Death drop protection

When the sword item exists as a dropped `ItemEntity` (any cause):

- **Fire and lava immune** — set `DataComponents.FIRE_RESISTANT` on the item (netherite
  behavior).
- **No despawn timer** — the item never despawns from age. Use a custom `ItemEntity`
  subclass via `Item#hasCustomEntity`/`Item#createEntity` (preferred — it also carries the
  void logic) with `setUnlimitedLifetime()`.
- **Void rescue** — if it falls below the world's min build height, teleport the
  `ItemEntity` to the world spawn point at a safe Y instead of letting it be destroyed.
  Implement in the custom ItemEntity's tick, before vanilla void-destruction at
  `minBuildHeight - 64`.

### 25.8 keepInventory handling

If the player dies with `keepInventory` enabled while flying mode is active: the familiar
still despawns per Section 12 death logic (`death_fall`/DYING, or instant from STUCK), the
mode resets to normal, and the item stays in the player's inventory (no drop). **Verify the
existing death logic doesn't assume a drop occurs.**

### 25.9 Block-destruction permissions

Depends on Section 26 (block piercing) being implemented first — it is scheduled before
Phase 9. The pierceable block destruction must respect the `mobGriefing` gamerule when
config `integration.respectMobGriefing` is `true` (default). When the gamerule blocks
destruction, the sword **phases through** pierceable blocks without destroying them — no
STUCK, no block change. Non-pierceable blocks still cause STUCK regardless.

### 25.10 Creative & spectator flight

- **Creative flight:** fully compatible with flying mode. No forced exit, no restrictions —
  spring physics follow handles vertical movement. Explicitly verify the elytra detection
  uses elytra-specific checks (`isFallFlying()`), not a generic "is flying" check, so
  creative flight cannot false-positive.
- **Spectator mode:** entering spectator force-exits flying mode immediately (despawn
  familiar, normal mode, no animation). Leaving spectator does not auto re-engage.

### 25.11 Sculk resonance

Entering LAUNCHING emits `GameEvent.PROJECTILE_SHOOT` at the launch position; entering
STUCK emits `GameEvent.PROJECTILE_LAND` at the embed position (or the closest current
1.21.1 equivalents — verify against mappings) so sculk sensors, shriekers, and the Warden
detect them. **Entering QUICK_FIRE also emits `PROJECTILE_SHOOT`** at the sword's position
(decided 2026-06-12); since quick-fire never sticks, it never emits `PROJECTILE_LAND`.
SWEEPING and BLOCKING emit nothing. Config toggle `integration.sculkResonance` (default
`true`) gates all of these emissions.

### 25.12 Soul

**a) Warden tremble — dropped.** No work.

**b) Awakening moment.** Track a boolean `awakened` in the sword item's DataComponent. On
the **first-ever** F activation for that item, play an extended spawn instead of the
default; all subsequent activations use the default ARRIVING sky-drop.

Distinction from the default spawn (decided 2026-06-12): the default ARRIVING drop is
*quick* (2.5 blocks/tick from up to 16 blocks ≈ a fraction of a second); the awakening is
a *ceremonial slow version of the same descent* — the familiar falls from the sky over
**~2.5 seconds**, oriented upright, then performs **one slow orbit** around the player
before settling into HOVERING. Implementation suggestion: reuse the ARRIVING state with an
`awakening` flag that slows the descent and appends the orbit leg; no landing-impact damage
during the awakening [TUNE].

The awakening cannot be interrupted by **player inputs** — all combat/mode inputs are
ignored during it. Universal rules still apply: player death, entity validation failure,
and dimension travel terminate it per Sections 12/16/18.

**c) Advancements.** Two advancements under Adventure (or a new tab — implementer's
choice):

| ID | Title | Trigger | Notes |
|---|---|---|---|
| `heirloomswordmod:soul_bound` | "Soul-Bound" | `inventory_changed` — obtain the sword item | |
| `heirloomswordmod:a_will_of_its_own` | "A Will of Its Own" | Custom trigger fired from the F-activation server code, first flying-mode activation | Parent: `soul_bound`; hidden until `soul_bound` is earned |

### 25.13 Verification checklist

After all items, run the client and test in order: config values load and apply; all
strings localized; tooltip shows both lore lines; item can't be moved from its hotbar slot
in flying mode (drag, shift-click, number swap, offhand swap, container drop, cursor
pickup); sword survives lava as a drop and never despawns; void drop teleports to spawn;
keepInventory death keeps the item and resets mode; sculk sensor triggers on launch and on
embed; first activation plays the awakening, second uses the default sky-drop; both
advancements grant.

---

## 26. Block Piercing (LAUNCHING) — Specified, NOT Yet Implemented

> **Status (2026-06-12):** Only the data half of this feature exists. The block tag was
> committed (`data/heirloomswordmod/tags/blocks/pierceable.json`, Phase 8 WIP commit), but
> **no Java code reads it** — `tickLaunching()` still enters STUCK on every collidable
> block. An earlier revision of this section incorrectly described the feature as done.
> Scheduled in "Outstanding Work Before Phase 9" (Section 24).

**Behavior:** During LAUNCHING (outbound only), each block contact is checked against the
`heirloomswordmod:pierceable` block tag:

- **Pierceable** (leaves, cobwebs, flowers, crops, vines, snow layers, carpets, candles,
  scaffolding, decorated pots, etc.): the block is **destroyed with drops** and the sword
  continues traveling at unchanged speed. Multiple pierceable blocks can be destroyed in a
  single launch.
- **Not pierceable:** the sword embeds → STUCK, exactly as before.

**Scope limits:**
- Applies to LAUNCHING only. RETURNING and SWEEPING_RELEASE returns already phase through
  all blocks and destroy nothing. QUICK_FIRE never destroys blocks (it returns on any block
  contact).
- When the add-on config lands (Section 25, item 9): if `integration.respectMobGriefing`
  is true (default) and the `mobGriefing` gamerule is off, the sword **phases through**
  pierceable blocks without destroying them — no STUCK, no block change. Non-pierceable
  blocks still cause STUCK regardless of the gamerule.
- Destruction is server-side via `Level.destroyBlock(pos, true)` (drops enabled); fire a
  block-destroy effect so clients see particles.

**Data-driven:** the tag lives at `data/heirloomswordmod/tags/blocks/pierceable.json` so
datapack authors can customize it. Namespace is `heirloomswordmod` (the real mod id), not
`alucardsword`.

---

*Document version: 3.2 (2026-06-13) — Supersedes the 2026-06-12 Stamina Model with the
two-resource Resource Model (Section 1): mana (the mod's own player attachment) powers all
flying-mode actions + warp; Epic Fight stamina is normal-mode combat only. Cancels the
`StaminaProvider` interface and hidden-pool fallback; descopes Phase 9 to Epic Fight combat
integration only. Mana/textures/warp are built before Phase 9 per the per-feature plans in
`docs/superpowers/plans/2026-06-13-*`.*

*Document version: 3.1 (2026-06-12) — Documents implemented-but-unspecified features
(ARRIVING sky-drop spawn, QUICK_FIRE V-key dart, DYING state, undead ignition, landing
impact); adds the Stamina Model decision (internal pool fallback without Epic Fight);
corrects the Block Piercing section to reflect its true status (tag only, no code) and
schedules it with the Phase 7 leftovers before Phase 9; merges the Add-On Phase (Polish,
Protection & Soul) as Section 25 / Phase 13.*

*Document version: 3.0 — Adds Tether Pull, Idle Personality, and Ancient City spawn. Parry
considered and cut (Minecraft TPS too imprecise for timing windows).*
