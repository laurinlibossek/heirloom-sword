# Graph Report - HeirloomSword  (2026-06-16)

## Corpus Check
- 64 files · ~61,187 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 918 nodes · 1671 edges · 84 communities (72 shown, 12 thin omitted)
- Extraction: 88% EXTRACTED · 12% INFERRED · 0% AMBIGUOUS · INFERRED: 198 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `9bf1b7a2`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 59|Community 59]]
- [[_COMMUNITY_Community 60|Community 60]]
- [[_COMMUNITY_Community 61|Community 61]]
- [[_COMMUNITY_Community 62|Community 62]]
- [[_COMMUNITY_Community 63|Community 63]]
- [[_COMMUNITY_Community 64|Community 64]]
- [[_COMMUNITY_Community 65|Community 65]]
- [[_COMMUNITY_Community 66|Community 66]]
- [[_COMMUNITY_Community 67|Community 67]]
- [[_COMMUNITY_Community 68|Community 68]]
- [[_COMMUNITY_Community 69|Community 69]]
- [[_COMMUNITY_Community 70|Community 70]]
- [[_COMMUNITY_Community 71|Community 71]]
- [[_COMMUNITY_Community 72|Community 72]]
- [[_COMMUNITY_Community 73|Community 73]]
- [[_COMMUNITY_Community 74|Community 74]]
- [[_COMMUNITY_Community 75|Community 75]]
- [[_COMMUNITY_Community 76|Community 76]]
- [[_COMMUNITY_Community 77|Community 77]]
- [[_COMMUNITY_Community 78|Community 78]]
- [[_COMMUNITY_Community 79|Community 79]]
- [[_COMMUNITY_Community 80|Community 80]]
- [[_COMMUNITY_Community 81|Community 81]]
- [[_COMMUNITY_Community 82|Community 82]]
- [[_COMMUNITY_Community 83|Community 83]]

## God Nodes (most connected - your core abstractions)
1. `SwordFamiliarEntity` - 102 edges
2. `position` - 32 edges
3. `rotation` - 31 edges
4. `Player` - 30 edges
5. `ServerLevel` - 27 edges
6. `Level` - 26 edges
7. `HeirloomSwordItem` - 22 edges
8. `root` - 19 edges
9. `SwordSounds` - 18 edges
10. `animations` - 18 edges

## Surprising Connections (you probably didn't know these)
- `handle()` --calls--> `ServerLevel`  [EXTRACTED]
  src/main/java/com/alucard/heirloomsword/network/SwordLaunchPacket.java → src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
- `handle()` --calls--> `ServerLevel`  [EXTRACTED]
  src/main/java/com/alucard/heirloomsword/network/SwordModePacket.java → src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
- `handle()` --calls--> `ServerLevel`  [EXTRACTED]
  src/main/java/com/alucard/heirloomsword/network/SwordCancelChargePacket.java → src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
- `handle()` --calls--> `ServerLevel`  [EXTRACTED]
  src/main/java/com/alucard/heirloomsword/network/SwordChargePacket.java → src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
- `handle()` --calls--> `ServerLevel`  [EXTRACTED]
  src/main/java/com/alucard/heirloomsword/network/SwordGuardPacket.java → src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java

## Import Cycles
- None detected.

## Communities (84 total, 12 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.10
Nodes (14): AABB, AnimationState, Entity, EntityDimensions, EntityType, GeoEntity, ModEntities, PlayState (+6 more)

### Community 1 - "Community 1"
Cohesion: 0.21
Nodes (10): SwordEventHandler, ItemTossEvent, LivingIncomingDamageEvent, LootTableLoadEvent, PlayerLoggedOutEvent, ProjectileImpactEvent, ItemStack, Player (+2 more)

### Community 2 - "Community 2"
Cohesion: 0.08
Nodes (25): FMLClientSetupEvent, GuiGraphics, ClientEvents, HeirloomSwordModClient, ModBusEvents, InteractionKeyMappingTriggered, LocalPlayer, ModNetwork (+17 more)

### Community 3 - "Community 3"
Cohesion: 0.06
Nodes (34): display, firstperson_lefthand, firstperson_righthand, fixed, ground, gui, head, thirdperson_lefthand (+26 more)

### Community 4 - "Community 4"
Cohesion: 0.09
Nodes (23): rotation, 0.1, 0.16, 0.2, 0.24, 0.25, 0.36, 0.4 (+15 more)

### Community 5 - "Community 5"
Cohesion: 0.07
Nodes (27): 0.0, 0.04, 0.05, 0.08, 0.1, 0.12, 0.15, 0.16 (+19 more)

### Community 6 - "Community 6"
Cohesion: 0.07
Nodes (26): BakedGeoModel, HeirloomSwordItemModel, HeirloomSwordItemRenderer, SwordFamiliarGeoRenderer, lerp(), TelekinesisHandRenderer, Context, FamiliarState (+18 more)

### Community 7 - "Community 7"
Cohesion: 0.08
Nodes (23): Cooldown decrement, Explicitly Deferred, `FamiliarState.java`, `HeirloomSwordModClient.java` — `ClientEvents.onClientTick`, In Scope, Modified Files, `ModKeybinds.java`, `ModNetwork.java` (+15 more)

### Community 8 - "Community 8"
Cohesion: 0.13
Nodes (16): Consumer, Enchantment, GeoItem, GeoRenderProvider, HeirloomSwordItem, Holder, AnimatableInstanceCache, ControllerRegistrar (+8 more)

### Community 9 - "Community 9"
Cohesion: 0.17
Nodes (8): IdlePersonality, Optional, BlockPos, Player, SwordFamiliarEntity, Vec3, BlockPos, String

### Community 10 - "Community 10"
Cohesion: 0.12
Nodes (16): Context for the implementer, File Map, Root-cause notes (already diagnosed — trust these, don't re-derive), Sword Mechanics, State Glitches & Functional Guarding Implementation Plan, Task 10: Block slash deals damage on G release, Task 11: Guard entry from CHARGING and SWEEPING_HOLD (spec completion), Task 12: Final end-to-end verification, Task 1: Vanilla sword mechanics — 12 damage, netherite cooldown, sweep (+8 more)

### Community 11 - "Community 11"
Cohesion: 0.13
Nodes (14): Explicitly out of scope (do not "fix" these), File Structure, Render Alignment & Polish Fixes Implementation Plan, Task 1: Rewrite `SwordFamiliarGeoRenderer.preRender`, Task 2: Remove baked whole-body rotations from animations, Task 3: Simplify SWEEPING_HOLD yaw math in `updateOrientation`, Task 4: Retune charging height, Task 5: BLOCKING-specific bounding box (+6 more)

### Community 12 - "Community 12"
Cohesion: 0.12
Nodes (16): entity.heirloomswordmod.sword_familiar, heirloomswordmod.configuration.section.heirloomswordmod.common.toml, heirloomswordmod.configuration.section.heirloomswordmod.common.toml.title, heirloomswordmod.configuration.title, item.heirloomswordmod.heirloom_sword, itemGroup.heirloomswordmod, key.categories.heirloomswordmod, key.heirloomswordmod.guard (+8 more)

### Community 13 - "Community 13"
Cohesion: 0.26
Nodes (7): BuildCreativeModeTabContentsEvent, FMLCommonSetupEvent, HeirloomSwordMod, IEventBus, ServerStartingEvent, ModContainer, SubscribeEvent

### Community 14 - "Community 14"
Cohesion: 0.17
Nodes (11): 10. Out of Scope, 1. GeckoLib Dependency, 2. Entity Changes — GeoEntity Interface, 3. Renderer — GeoEntityRenderer, 4. Model — Placeholder Geometry, 5. Hand Gesture Suppression, 6. Telekinetic Shimmer Particles, 7. Spawn Fade-In (+3 more)

### Community 15 - "Community 15"
Cohesion: 0.12
Nodes (15): Combat Feel, Smoothness, Sky-Drop Spawn, Quick-Fire & Hand Poses Implementation Plan, Context for the implementer, File Map, Smoothness audit (what "smooth between every realistic state" maps to), Task 10: First-person telekinesis hand poses, Task 11: Final end-to-end verification, Task 1: Combat & speed tuning constants, Task 2: Smoothstep easing on the horizontal blend (+7 more)

### Community 16 - "Community 16"
Cohesion: 0.38
Nodes (9): decode(), encode(), handle(), type(), CustomPacketPayload, FriendlyByteBuf, IPayloadContext, Override (+1 more)

### Community 17 - "Community 17"
Cohesion: 0.54
Nodes (4): SwordFamiliarModel, Override, ResourceLocation, SwordFamiliarEntity

### Community 18 - "Community 18"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.block_stance

### Community 19 - "Community 19"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.tether_pull

### Community 20 - "Community 20"
Cohesion: 0.47
Nodes (5): getSerializedName(), SwordMode(), Override, String, String

### Community 21 - "Community 21"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.block_slash

### Community 22 - "Community 22"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.charge_spin

### Community 23 - "Community 23"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.death_fall

### Community 24 - "Community 24"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.guard_break

### Community 25 - "Community 25"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.launch

### Community 26 - "Community 26"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.return

### Community 27 - "Community 27"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.return_hilt

### Community 28 - "Community 28"
Cohesion: 0.40
Nodes (5): animation_length, bones, loop, animation.alucard_sword.alert, root

### Community 29 - "Community 29"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.idle_curious

### Community 30 - "Community 30"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.idle_figure_eight

### Community 31 - "Community 31"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.idle_perk

### Community 32 - "Community 32"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.idle_recoil

### Community 33 - "Community 33"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.stuck

### Community 34 - "Community 34"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.sweep_hold

### Community 35 - "Community 35"
Cohesion: 0.29
Nodes (6): animation_length, bones, loop, animations, animation.alucard_sword.idle, format_version

### Community 37 - "Community 37"
Cohesion: 0.24
Nodes (11): AlphaFn, FadingOverlayLayer, GeoRenderer, RenderType, BakedGeoModel, MultiBufferSource, Override, PoseStack (+3 more)

### Community 44 - "Community 44"
Cohesion: 0.25
Nodes (8): rotation, blade, pommel, rotation, 0.0, 0.08, 0.15, 1.0

### Community 45 - "Community 45"
Cohesion: 0.67
Nodes (4): lerp_mode, post, 0.14, 0.14

### Community 46 - "Community 46"
Cohesion: 0.67
Nodes (4): lerp_mode, post, 0.22, 0.22

### Community 47 - "Community 47"
Cohesion: 0.67
Nodes (4): lerp_mode, post, 0.38, 0.38

### Community 48 - "Community 48"
Cohesion: 0.67
Nodes (4): lerp_mode, post, 0.3, 0.3

### Community 49 - "Community 49"
Cohesion: 0.67
Nodes (4): lerp_mode, post, 0.5, 0.5

### Community 50 - "Community 50"
Cohesion: 0.07
Nodes (28): 0. Verified baseline (what the code actually does today), 1. Resolved conflicts with design doc v3, 2. Feature 1 — Mana system, 3. Feature 2 — Default texture rework, 4. Features 3 & 4 — Runes and Blood (unified render-layer system), 5. Feature 5 — Warp-next-to-target (normal mode), 6. Shared: reusable "action-denied" cue, 7. Sequencing (+20 more)

### Community 51 - "Community 51"
Cohesion: 0.14
Nodes (13): ManaService, WarpHandler, ManaSyncPacket, handle(), type(), Player, CustomPacketPayload, IPayloadContext (+5 more)

### Community 52 - "Community 52"
Cohesion: 0.15
Nodes (12): File Structure, Phase 10 (Plan A) — Audio Triggers & Missing Particles Implementation Plan, Scope & Guardrails, Self-Review, Task 1: Add the new sound helpers (central, all in one file), Task 2: Mode enter / exit sounds, Task 3: Launch, impact (+particle), return-arrival, quick-fire, death-fall, Task 4: Sweep-contact, guard-raised, guard-break (+4 more)

### Community 53 - "Community 53"
Cohesion: 0.15
Nodes (12): File Structure, Mana System Implementation Plan, Self-Review (coverage against the spec), Task 1: Mana core spine (service, attachments, client cache, sync packet), Task 2: Shared "action-denied" cue, Task 3: Mana regeneration tick, Task 4: CHARGING — drain + entry gate, Task 5: SWEEPING_HOLD — drain + entry gate (+4 more)

### Community 54 - "Community 54"
Cohesion: 0.15
Nodes (8): SwordSounds, handle(), type(), CustomPacketPayload, IPayloadContext, Override, Level, SwordModePacket

### Community 55 - "Community 55"
Cohesion: 0.20
Nodes (9): File Structure, Runes & Blood Overlay System Implementation Plan, Self-Review (coverage against the spec), Task 1: Add the blood DataComponent, Task 2: Blood accessors + decay on the item, Task 3: Bloody the blade on flying-mode hits, Task 4: Overlay texture refs + the fading render layer, Task 5: Wire the layers into the renderers (+1 more)

### Community 56 - "Community 56"
Cohesion: 0.20
Nodes (9): 1. Planned work (for reference), 2. Unplanned changes, 2a. Bug fixes (from in-game testing), 2b. Polish / UX (requested mid-session), 2c. New feature requests (STUCK feedback + ambient FX), 3. Files touched by the unplanned work, 4. Verification, 5. Known deferred items (+1 more)

### Community 57 - "Community 57"
Cohesion: 0.22
Nodes (8): File Structure, Self-Review (coverage against the spec), Task 1: Warp cooldown attachment + tick, Task 2: Warp logic, Task 3: Warp packet + registration, Task 4: Bind V to warp in normal mode, Task 5: In-game verification, Warp-Next-To-Target Implementation Plan

### Community 58 - "Community 58"
Cohesion: 0.20
Nodes (9): Decision Record (read before implementing), File Structure, Phase 9 — Epic Fight Compatibility (Datapack, No Dependency) Implementation Plan, Self-Review, Task 1: Create the Epic Fight greatsword capability file, Task 2: Declare Epic Fight as an optional dependency (load-order + documentation), Task 3: Verify vanilla-only fallback (no Epic Fight) still builds and behaves, Task 4: Manual in-game verification with Epic Fight installed (+1 more)

### Community 59 - "Community 59"
Cohesion: 0.20
Nodes (9): File Structure, Phase 11 — Tether Pull Implementation Plan, Scope & Guardrails, Self-Review (completed during authoring), Task 1: Tether sound helpers, Task 2: TETHERING state + yank physics, Task 3: SwordTetherPacket + registration, Task 4: Fresh-Shift-press client detection (+1 more)

### Community 60 - "Community 60"
Cohesion: 0.38
Nodes (6): handle(), type(), CustomPacketPayload, IPayloadContext, Override, SwordWarpPacket

### Community 61 - "Community 61"
Cohesion: 0.40
Nodes (3): SwordTextures, ResourceLocation, String

### Community 68 - "Community 68"
Cohesion: 0.12
Nodes (16): 1. Goal, 2.1 Sync model, 2.2 Server vs client split inside `IdlePersonality.tick(owner)`, 2. Architecture, 3.1 Idle timer & cancellation (server-side), 3.2 Curious drift, 3.3 Lazy figure-eight, 3.4 Environmental reactions (one-shots) (+8 more)

### Community 70 - "Community 70"
Cohesion: 0.35
Nodes (9): handle(), handle(), handle(), handle(), handle(), handle(), handle(), handle() (+1 more)

### Community 71 - "Community 71"
Cohesion: 0.15
Nodes (8): Builder, CompoundTag, FamiliarState(), fromId(), getId(), ModDataComponents, ServerLevel, UUID

### Community 72 - "Community 72"
Cohesion: 0.18
Nodes (10): File Structure, Phase 12 — Idle Personality Implementation Plan, Scope & Guardrails, Self-Review, Task 1: Curiosities block tag, Task 2: Entity scaffolding (synced data, accessors, hooks, predicate, controllers), Task 3: `IdlePersonality` helper class, Task 4: Wire the helper into the entity (+2 more)

### Community 74 - "Community 74"
Cohesion: 0.33
Nodes (3): Integer, Set, LivingEntity

### Community 75 - "Community 75"
Cohesion: 0.28
Nodes (6): type(), ServerPlayer, CustomPacketPayload, IPayloadContext, Override, ServerPlayer

### Community 76 - "Community 76"
Cohesion: 0.39
Nodes (8): decode(), encode(), type(), CustomPacketPayload, FriendlyByteBuf, IPayloadContext, Override, SwordMomentumPacket

### Community 77 - "Community 77"
Cohesion: 0.50
Nodes (4): type(), CustomPacketPayload, IPayloadContext, Override

### Community 78 - "Community 78"
Cohesion: 0.50
Nodes (4): type(), CustomPacketPayload, IPayloadContext, Override

### Community 79 - "Community 79"
Cohesion: 0.50
Nodes (4): type(), CustomPacketPayload, IPayloadContext, Override

### Community 80 - "Community 80"
Cohesion: 0.50
Nodes (4): type(), CustomPacketPayload, IPayloadContext, Override

### Community 81 - "Community 81"
Cohesion: 0.50
Nodes (4): type(), CustomPacketPayload, IPayloadContext, Override

### Community 82 - "Community 82"
Cohesion: 0.50
Nodes (4): type(), CustomPacketPayload, IPayloadContext, Override

## Knowledge Gaps
- **331 isolated node(s):** `ClientManaState`, `Config`, `ControllerRegistrar`, `RegisterRenderers`, `InteractionKeyMappingTriggered` (+326 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **12 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SwordFamiliarEntity` connect `Community 67` to `Community 0`, `Community 66`, `Community 69`, `Community 70`, `Community 71`, `Community 6`, `Community 9`, `Community 74`, `Community 73`?**
  _High betweenness centrality (0.061) - this node is a cross-community bridge._
- **Why does `ServerLevel` connect `Community 70` to `Community 0`, `Community 1`, `Community 71`, `Community 75`, `Community 76`, `Community 77`, `Community 78`, `Community 79`, `Community 16`, `Community 80`, `Community 81`, `Community 82`, `Community 54`?**
  _High betweenness centrality (0.020) - this node is a cross-community bridge._
- **Why does `root` connect `Community 28` to `Community 32`, `Community 33`, `Community 34`, `Community 35`, `Community 4`, `Community 5`, `Community 18`, `Community 19`, `Community 21`, `Community 22`, `Community 23`, `Community 24`, `Community 25`, `Community 26`, `Community 27`, `Community 29`, `Community 30`, `Community 31`?**
  _High betweenness centrality (0.016) - this node is a cross-community bridge._
- **What connects `ClientManaState`, `Config`, `ControllerRegistrar` to the rest of the system?**
  _331 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.09538461538461539 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.07955596669750231 - nodes in this community are weakly interconnected._
- **Should `Community 3` be split into smaller, more focused modules?**
  _Cohesion score 0.05714285714285714 - nodes in this community are weakly interconnected._