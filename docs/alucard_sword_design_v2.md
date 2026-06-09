# Heirloom Sword — Mod Design Document
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

---

## 2. The Item

### Identity
A single legendary magical sword. One item, one mod. No crafting recipe is defined here;
that is left to the implementer or a datapack.

### Durability & Enchantments
The sword has no durability bar. It cannot be damaged, repaired, or enchanted. It does not
accept any enchantment at an enchanting table or anvil. It is a magical artifact and behaves
as one.

### Hotbar Behavior
The item always remains in the player's hotbar, including while in flying mode. The hotbar 
slot displays a faint purplish glow on the item icon at all times while flying mode is 
active. No glow in normal mode. No other HUD indicators are added.

### Drop Prevention
The item cannot be dropped via the Q key while in flying mode. Attempting to do so produces
a hotbar message: *"The sword refuses to leave your side."* No item is dropped and no
state is changed.

---

## 3. Mode System

### Toggle
**Keybind: F** (configurable). Switching is instant. No cost, no cooldown.

### Lock Condition
Mode switching via F is available in the following states:

- **HOVERING:** Normal toggle. Exits flying mode, despawns the sword entity.
- **SWEEPING_HOLD:** Emergency exit. Instant despawn, sword returns to inventory.
- **BLOCKING:** Emergency exit. Instant despawn, sword returns to inventory.

In all other active states (LAUNCHING, SWEEPING_RELEASE, CHARGING, STUCK, RETURNING),
the F keybind is locked and does nothing. The sword is committed and must complete its
current action before mode switching becomes available again.

### Normal → Flying Transition
The Heirloom Sword entity is spawned at the player's side. The item remains in the hotbar. The
player's hand rendering switches to the gesture system.

### Flying → Normal Transition
The sword entity despawns instantly — fast enough that no despawn animation is needed. The
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
| CHARGING | Fingers tighten, arm slightly raised |
| LAUNCHING (on launch frame) | Brief directional flick toward aim direction |
| SWEEPING_HOLD | Slow arc tracing the view direction |
| BLOCKING | Raised, palm forward |
| RETURNING | Relaxed, slight curl as if receiving |

Third-person presentation of hand gestures will be validated during playtesting.

---

## 6. The Heirloom Sword Entity

### Class Design
A custom non-living `Entity` subclass (not `Mob`, not `LivingEntity`). It has no health
bar, no AI goals, no pathfinding. All behavior is driven explicitly by the state machine
described in Section 10. The entity is registered as **persistent** so it serializes to
chunk NBT on unload and resumes on reload.

### Ownership
The entity stores the owning player's UUID in its NBT data (`ownerUUID`). This is private
entity data — not visible on the item, not visible to other players. The item itself carries
no owner data. When any player picks up the item and activates flying mode, a new sword 
entity spawns with that player's UUID as owner. Previous entities are not affected.

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
| `alert` | Tilting toward mob target — transitions from idle |
| `charge_spin` | Corkscrew drill spin on own axis, no orbiting |
| `launch` | Blade-forward dart, minimal visual |
| `stuck` | Embedded in block, slight vibration |
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
| Flying mode entered (spawn) | Sword materializes at the player's side with a brief fade-in and particle burst matching the telekinetic shimmer color. No flight-from-hand animation. |
| HOVERING → CHARGING | Sword glides smoothly from its current hover position to the left/right charge position. `charge_spin` ramps up from zero rotational speed over ~0.25 seconds rather than snapping to full spin instantly. |
| STUCK → RETURNING | Brief pull-out pause (~2–3 frames) before the sword wrenches free and begins return travel. Blends from `stuck` vibration into `return` clip. |
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
1×1×1 space): flying mode exits immediately and the sword returns to the player's hand. There
is **no automatic re-engagement**. The player must press F manually.

---

## 8. Lazy Follow & Spring Physics

### Follow Behavior
The sword does not rigidly track the player. It follows with spring physics:

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
Every tick, the sword scans for **hostile mobs only** within a **16-block radius**.
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
├── HOVERING
├── CHARGING
├── LAUNCHING
├── STUCK
├── SWEEPING_HOLD
├── SWEEPING_RELEASE
├── BLOCKING
└── RETURNING
```

### Universal Rules

**Input handling:** Every state defines an explicit input table listing all player inputs
and their result. Any input marked "Ignored" produces no effect, no feedback, and no state
change. If an input is not listed in a state's table, it is ignored. There are no implicit
transitions.

**Death:** Player death triggers the death logic described in Section 12 from **every**
state without exception.

**Entity validation:** Every server tick while flying mode is active, the server confirms
the sword entity exists and is loaded. If the entity cannot be found for any reason
(chunk unloaded, removed by another mod, any unexpected absence), flying mode exits
immediately. See Section 18 for details.

**Hit detection on travel:** When the sword travels through space (LAUNCHING outbound,
RETURNING inbound), it maintains a **per-direction hit set**. Each entity can be damaged
at most **once per travel direction**. The hit set resets when the sword changes direction
(e.g., from outbound LAUNCHING to inbound RETURNING). This means an entity in the flight
path takes damage once on the way out and once on the way back — two hits total, not
per-tick.

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

**If stamina depletes before 3 seconds:** Charge stops. Sword transitions to HOVERING state.
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

**Block contact:** The sword embeds in the first solid block face it contacts → STUCK state.

**At max range (no block hit):** The sword immediately transitions to RETURNING state with no
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

### State: STUCK

The sword is embedded in a block face, tip-first. It vibrates slightly (animation clip:
`stuck`). It stays at its world position. The player can move freely.

**Auto-return timer:** 3 seconds. After 3 seconds with no input, transitions to RETURNING state.

**Return from STUCK:** Tip-forward, 8 damage per entity on return arc (per-direction hit
set — each entity hit at most once on the return). Sword phases through blocks during
return (see Section 10, RETURNING state).

**Entry:** Sword contacts solid block during LAUNCHING.

**Input Handling:**
| Input | Transition |
|---|---|
| R pressed | → RETURNING (after 1-tick delay, cancels 3s timer) |
| 3 seconds elapsed | → RETURNING |
| Left click | Ignored |
| Right click | Ignored |
| G pressed | Ignored |
| F pressed | Ignored |
| Player dies | → death logic (immediate despawn, no animation) |

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

**Entry:** Right click held from HOVERING state.

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

**Entry:** Right click released from SWEEPING_HOLD state.

**Input Handling:**
| Input | Transition |
|---|---|
| Sword returns to player | → HOVERING state |
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
plays (brief stagger/wobble). The sword transitions to HOVERING state. **No horizontal slash
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
returns to HOVERING state.

**Entry:** G held from HOVERING state, or G pressed during CHARGING state (cancels charge) or
SWEEPING_HOLD state (cancels sweep).

**Input Handling:**
| Input | Transition |
|---|---|
| G released (stamina remaining) | → `block_slash` animation → HOVERING state |
| Stamina depleted | → `guard_break` animation → HOVERING state (no slash, G on 3s cooldown) |
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

**Arrival:** When the sword reaches within **vanilla item pickup range (~1.5 blocks)**
of the player, it transitions to HOVERING state and resumes normal floating behavior. The sword
decelerates using spring physics (overshoot, oscillate, settle into hover position).

**Input Handling:**
| Input | Transition |
|---|---|
| Arrives at player | → HOVERING state |
| Left click | Ignored |
| Right click | Ignored |
| G pressed | Ignored |
| R pressed | Ignored |
| F pressed | Ignored |
| Player dies | → death logic |

---

## 11. Recall — R Key

**Available from:** LAUNCHING, STUCK.
**Not available from:** HOVERING (nothing to recall), CHARGING (charge must be released or
canceled), SWEEPING_HOLD (must release right click), SWEEPING_RELEASE (full commitment),
BLOCKING (sword is in guard position, not away from the player), RETURNING (already
returning).

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

**Trigger:** The player's `EntityPose` transitions to `EntityPose.SWIMMING`.
Wading through shallow water without entering the swimming pose has no effect on flying mode.

**What happens:**
- If the sword is in any state other than RETURNING: it immediately transitions to
  RETURNING state, phases through any blocks in the way, and reaches the player.
- If the sword is already in RETURNING: it continues its return normally.
- On arrival at the player in either case: flying mode exits automatically.

**Re-engagement:** Flying mode **never re-engages automatically**. When the player exits
the water and the swimming pose ends, flying mode remains inactive. The player must press F
manually to re-enter flying mode.

---

## 14. Mounting & Riding Logic

Flying mode cannot be **entered** while the player is riding any entity.

If flying mode is **already active** and the player attempts to mount or ride anything
(horse, boat, minecart, pig, strider, or any other `is_passenger` situation), the
**mount action is blocked**. The player receives a hotbar message: *"The sword must return 
first."* The player must exit flying mode manually before mounting.

Flying mode **never exits automatically** due to mounting because the mount action is
prevented entirely.

---

## 15. Elytra Logic

Flying mode cannot be **entered** while the player is in elytra flight.

If flying mode is **already active** and the player attempts to enter elytra flight, the
elytra behavior follows the same pattern as water/swimming:

- If the sword is in any state other than RETURNING: it immediately transitions to
  RETURNING state, phases through any blocks in the way, and reaches the player.
- If the sword is already in RETURNING: it continues its return normally.
- On arrival at the player: flying mode exits automatically.

**Re-engagement:** Flying mode **never re-engages automatically**. When elytra flight ends,
flying mode remains inactive. The player must press F manually to re-enter flying mode.

---

## 16. Dimension Travel

If the player enters a dimension transition (nether portal, end portal, any inter-dimension
travel) while flying mode is active, the behavior follows the same pattern as
water/swimming:

- If the sword is in any state other than RETURNING: it immediately transitions to
  RETURNING state, phases through any blocks in the way, and reaches the player.
- If the sword is already in RETURNING: it continues its return normally.
- On arrival at the player: flying mode exits automatically.

The sword does **not** travel across dimensions. It returns to the player and despawns
in the origin dimension before the player completes the transition.

**Re-engagement:** Flying mode **never re-engages automatically** after dimension travel.
The player must press F manually in the new dimension.

---

## 17. Disconnect & Reconnect

When the server detects the player disconnecting (voluntary logout or crash) while flying
mode is active:

1. The sword entity is **immediately despawned**. No animation, no return travel.
2. The sword's mode is set to **normal mode**.
3. No state is persisted for restoration.

On reconnect, the player loads in with the sword in their hotbar in normal mode. Press F
to re-enter flying mode. This is a clean reset — no orphaned entities, no stale state.

This mirrors how Minecraft handles player-bound transient entities (e.g., fishing bobbers)
and avoids the fragile edge cases of trying to restore sword state across a reconnect.

---

## 18. Entity Validation

Every server tick while flying mode is active, the server confirms that the sword entity
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

- Each sword entity is independently owned by the player whose UUID spawned it.
- Sword entities do not interact with each other.
- A sword owned by Player A cannot be picked up by Player B while in any active state. The
  item stays in Player A's hotbar.
- If Player A dies, the item drops normally and Player B may pick it up. When Player B
  activates flying mode with that item, a new sword entity spawns with Player B as the new owner.
  Previous entity data is irrelevant.
- The sword's mob awareness tracking **excludes** other players — the sword never tilts
  toward players, never auto-attacks players, and never treats players as targets under any
  condition.
- Sword entities are saved as persistent entities in chunk NBT. If the chunk containing
  a sword unloads (edge case requiring deliberate player effort), the entity validation
  system (Section 18) handles recovery.

---

## 20. Rendering Summary

| Element | Renderer | Notes |
|---|---|---|
| Sword entity | GeckoLib `GeoEntityRenderer` | Full animated model, all clips |
| In-hand (normal mode) | Standard item renderer | Default hold position |
| In-hand (flying mode) | Custom suppressed renderer | Empty hand, shimmer effect |
| Hotbar icon | Standard 2D + shader overlay | Purple glow while flying |
| Projectile deflection | Particle burst on sword face | On projectile intercept |
| Death animation | GeckoLib clip `death_fall` | Visual only, entity despawns after |
| Block slash | GeckoLib clip `block_slash` | On G release with stamina remaining |
| Guard break | GeckoLib clip `guard_break` | On stamina depletion during BLOCKING |
| Sword spawn | Fade-in + particle burst | On flying mode entry |

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
| Blocking slash damage | 12–14 | [TUNE] |
| Guard break cooldown | 3 seconds | Cooldown on G after stamina depletion |
| Normal mode base damage | 10 | Epic Fight value |
| Charge stamina drain | ~medium | Roughly 3s blocking equivalent |
| Sweep hold stamina drain | Low | Cannot sustain indefinitely |
| Blocking stamina drain | Mirrors Epic Fight shield | |
| Return arrival range | ~1.5 blocks | Vanilla item pickup range |
| Swimming trigger | EntityPose.SWIMMING | Not water contact |
| Mount block trigger | is_passenger == true | Any riding state |

---

## 24. Implementation Phase Order

Recommended development sequence. Do not skip phases.

**Phase 1 — Foundation**
NeoForge 1.21.1 MDK setup. Item registration. F keybind. Hotbar glow indicator. Mode flag
stored as `DataComponentType<SwordMode>`. Q-key drop prevention. Placeholder item texture.

**Phase 2 — Sword Entity (HOVERING only)**
`SwordEntity` registration. Ownership UUID. Spawn on mode enter (with fade-in and
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

**Phase 8 — GeckoLib Integration**
Replace debug hitbox with full Blockbench model and all animation clips (including
`block_slash` and `guard_break`). All transition animations per Section 6 transition notes.
Hand gesture system. Telekinetic shimmer effect. Hotbar purple glow shader. Familiar spawn
fade-in effect.

**Phase 9 — Epic Fight Integration**
`EpicFightCompat` class. Greatsword weapon type registration. Stamina bar hookup for
CHARGING, SWEEPING_HOLD, and BLOCKING states. Guard break cooldown enforcement.

**Phase 10 — Audio and Polish**
Custom sound event registration with Minecraft placeholders (including `guard_break` sound).
Particle effects on impact, deflection, embed, and spawn. Network sync polish and client
prediction. Third-person review of all states and transitions.

---

*Document version: 2.0 — All decisions confirmed. Ready for implementation.*
