# Graph Report - .  (2026-06-17)

## Corpus Check
- 73 files · ~50,000 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1050 nodes · 1972 edges · 83 communities (66 shown, 17 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 220 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Event Handlers & Mana Ticks|Event Handlers & Mana Ticks]]
- [[_COMMUNITY_Client HUD & Input Events|Client HUD & Input Events]]
- [[_COMMUNITY_Client State & Mod Boot|Client State & Mod Boot]]
- [[_COMMUNITY_Design Concepts & Features|Design Concepts & Features]]
- [[_COMMUNITY_GeckoLib & Item Interfaces|GeckoLib & Item Interfaces]]
- [[_COMMUNITY_Familiar State Methods|Familiar State Methods]]
- [[_COMMUNITY_Item Model Display Contexts|Item Model Display Contexts]]
- [[_COMMUNITY_Familiar GeoRenderer Pipeline|Familiar GeoRenderer Pipeline]]
- [[_COMMUNITY_ManaTexturesWarp Design Doc|Mana/Textures/Warp Design Doc]]
- [[_COMMUNITY_Idle Personality Behaviors|Idle Personality Behaviors]]
- [[_COMMUNITY_Animation Keyframe Values|Animation Keyframe Values]]
- [[_COMMUNITY_Core Classes (semantic)|Core Classes (semantic)]]
- [[_COMMUNITY_State Machine & Game Events|State Machine & Game Events]]
- [[_COMMUNITY_Build & Render Infrastructure|Build & Render Infrastructure]]
- [[_COMMUNITY_Phase 6 Blocking Design Doc|Phase 6 Blocking Design Doc]]
- [[_COMMUNITY_Rotation Keyframe Values|Rotation Keyframe Values]]
- [[_COMMUNITY_Warp Handler & Packet|Warp Handler & Packet]]
- [[_COMMUNITY_Sword Mechanics Fix Plan|Sword Mechanics Fix Plan]]
- [[_COMMUNITY_NBT & FamiliarState Codec|NBT & FamiliarState Codec]]
- [[_COMMUNITY_Idle Personality Design Doc|Idle Personality Design Doc]]
- [[_COMMUNITY_Lang File Keys|Lang File Keys]]
- [[_COMMUNITY_Entity Base Types (AST)|Entity Base Types (AST)]]
- [[_COMMUNITY_Polish & Sky-Drop Plan|Polish & Sky-Drop Plan]]
- [[_COMMUNITY_Combat Damage Helpers|Combat Damage Helpers]]
- [[_COMMUNITY_Render Alignment Plan|Render Alignment Plan]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
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
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
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
- [[_COMMUNITY_Community 77|Community 77]]
- [[_COMMUNITY_Community 79|Community 79]]
- [[_COMMUNITY_Community 80|Community 80]]
- [[_COMMUNITY_Community 81|Community 81]]
- [[_COMMUNITY_Community 82|Community 82]]

## God Nodes (most connected - your core abstractions)
1. `SwordFamiliarEntity` - 116 edges
2. `position` - 32 edges
3. `Player` - 31 edges
4. `rotation` - 31 edges
5. `ServerLevel` - 27 edges
6. `Level` - 26 edges
7. `HeirloomSwordItem` - 23 edges
8. `root` - 19 edges
9. `SwordSounds` - 18 edges
10. `animations` - 18 edges

## Surprising Connections (you probably didn't know these)
- `Alucard Sword Base Texture` --conceptually_related_to--> `GeckoLib Entity & Item Renderer Pipeline`  [INFERRED]
  src/main/resources/assets/heirloomswordmod/textures/entity/alucard_sword.png → docs/superpowers/specs/2026-06-09-phase8-geckolib-design.md
- `GeckoLib Entity & Item Renderer Pipeline` --references--> `Alucard Sword Bloodied Overlay`  [INFERRED]
  docs/superpowers/specs/2026-06-09-phase8-geckolib-design.md → src/main/resources/assets/heirloomswordmod/textures/entity/alucard_sword_bloodied.png
- `GeckoLib Entity & Item Renderer Pipeline` --references--> `Alucard Sword Runes Overlay`  [INFERRED]
  docs/superpowers/specs/2026-06-09-phase8-geckolib-design.md → src/main/resources/assets/heirloomswordmod/textures/entity/alucard_sword_runes.png
- `Build Configuration` --references--> `HeirloomSwordMod`  [INFERRED]
  build.gradle → src/main/java/com/alucard/heirloomsword/HeirloomSwordMod.java
- `Runes & Blood Overlay Plan` --references--> `Alucard Sword Base Texture`  [EXTRACTED]
  docs/superpowers/plans/2026-06-13-runes-and-blood.md → src/main/resources/assets/heirloomswordmod/textures/entity/alucard_sword.png

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **State Machine System** — swordfamiliarentity_swordfamiliarentity, familiarstate_familiarstate, heirloomswordmodclient_heirloomswordmodclient, swordeventhandler_swordeventhandler [EXTRACTED 1.00]
- **Mana Resource System** — manaservice_manaservice, manaattachments_manaattachments, clientmanastate_clientmanastate, config_config [EXTRACTED 1.00]
- **Sword Item Data Components** — moddatacomponents_moddatacomponents, swordmode_swordmode, heirloomsworditem_heirloomsworditem [EXTRACTED 1.00]
- **GeckoLib Render Pipeline** — client_swordfamiliargeorenderer, client_swordfamiliarmodel, client_fadingoverlaylayer, client_swordtextures, client_heirloomsworditemrenderer, client_heirloomsworditemmodel [EXTRACTED 1.00]
- **Idle Personality System** — idlepersonality_idlepersonality, swordfamiliarentity_swordfamiliarentity [EXTRACTED 1.00]
- **Mod Registration Hub** — heirloomswordmod_heirloomswordmod, modentities_modentities, moddatacomponents_moddatacomponents, manaattachments_manaattachments, swordeventhandler_swordeventhandler, config_config [EXTRACTED 1.00]
- **Client Input Pipeline** — heirloomswordmodclient_heirloomswordmodclient, modkeybinds_modkeybinds, clientmanastate_clientmanastate, swordfamiliarentity_swordfamiliarentity [EXTRACTED 1.00]
- **Client-to-Server Packets (11 packets)** — SwordModePacket, SwordLaunchPacket, SwordRecallPacket, SwordChargePacket, SwordSweepPacket, SwordMomentumPacket, SwordGuardPacket, SwordQuickFirePacket, SwordWarpPacket, SwordCancelChargePacket, SwordTetherPacket [EXTRACTED 1.00]
- **Server-to-Client Packets** — ManaSyncPacket [EXTRACTED 1.00]
- **Mana-Gated Packets (require mana check)** — SwordChargePacket, SwordGuardPacket, SwordSweepPacket, SwordQuickFirePacket, SwordRecallPacket, SwordTetherPacket [EXTRACTED 1.00]
- **Hand Poses Mapped to Familiar States** — TelekinesisHandRenderer, FamiliarState, HandPose [EXTRACTED 1.00]
- **GeckoLib Model Assets** — alucard_sword_animation, alucard_sword_geo, item_model_heirloom_sword [EXTRACTED 0.95]
- **Advancement Chain: Soul-Bound → It flies?** — adv_soul_bound, adv_a_will_of_its_own, HeirloomSwordItem [EXTRACTED 1.00]
- **Animation Clips Matching Familiar States** — alucard_sword_animation, FamiliarState, SwordFamiliarEntity [INFERRED 0.95]
- **Sword Overlay Texture Set (base + blood + runes)** — tex_alucard_sword, tex_alucard_sword_bloodied, tex_alucard_sword_runes, concept_overlay_system [EXTRACTED 1.00]
- **Phase 13 Item Protection Suite** — concept_death_drop_protection, concept_pvp_gate, concept_sculk_resonance, concept_advancements, rationale_death_drop_event [EXTRACTED 1.00]
- **Mana + Config System Integration** — concept_mana_system, concept_config_system, rationale_consume_mana_switch [EXTRACTED 1.00]
- **Idle Personality Sync System** — concept_idle_personality, rationale_idle_server_auth, spec_phase12_idle_design, plan_phase12_idle [EXTRACTED 1.00]
- **Familiar State Machine Core States** — concept_state_machine, concept_blocking_state, concept_tether, concept_awakening, concept_idle_personality [EXTRACTED 1.00]

## Communities (83 total, 17 thin omitted)

### Community 0 - "Event Handlers & Mana Ticks"
Cohesion: 0.06
Nodes (43): EntityJoinLevelEvent, ManaService, ModDataComponents, SwordEventHandler, ItemTossEvent, LivingIncomingDamageEvent, LootTableLoadEvent, handle() (+35 more)

### Community 1 - "Client HUD & Input Events"
Cohesion: 0.07
Nodes (30): FMLClientSetupEvent, GuiGraphics, ClientEvents, HeirloomSwordModClient, ModBusEvents, InteractionKeyMappingTriggered, LocalPlayer, ModNetwork (+22 more)

### Community 2 - "Client State & Mod Boot"
Cohesion: 0.08
Nodes (26): Client Mana State, Heirloom Sword Mod (main), Mana Sync Packet (Server→Client), HeirloomSwordItemModel, HeirloomSwordItemRenderer, SwordFamiliarModel, SwordTextures, HeirloomSwordItem (+18 more)

### Community 3 - "Design Concepts & Features"
Cohesion: 0.10
Nodes (42): Soul-Bound and It Flies? Advancements, First-Activation Awakening Ceremony, BLOCKING State (Guard Stance), NeoForge ModConfigSpec Combat + Integration Config, Death-Drop Protection (No Despawn + Void Rescue), GeckoLib Entity & Item Renderer Pipeline, Idle Personality Behaviors, Mana Resource System (+34 more)

### Community 4 - "GeckoLib & Item Interfaces"
Cohesion: 0.11
Nodes (21): Component, Consumer, Enchantment, GeoItem, GeoRenderProvider, HeirloomSwordItem, Holder, List (+13 more)

### Community 5 - "Familiar State Methods"
Cohesion: 0.09
Nodes (16): SwordSounds, handle(), type(), handle(), type(), ServerPlayer, CustomPacketPayload, IPayloadContext (+8 more)

### Community 6 - "Item Model Display Contexts"
Cohesion: 0.06
Nodes (34): display, firstperson_lefthand, firstperson_righthand, fixed, ground, gui, head, thirdperson_lefthand (+26 more)

### Community 7 - "Familiar GeoRenderer Pipeline"
Cohesion: 0.11
Nodes (15): BakedGeoModel, SwordFamiliarGeoRenderer, lerp(), TelekinesisHandRenderer, Context, HandPose, BakedGeoModel, MultiBufferSource (+7 more)

### Community 8 - "Mana/Textures/Warp Design Doc"
Cohesion: 0.07
Nodes (28): 0. Verified baseline (what the code actually does today), 1. Resolved conflicts with design doc v3, 2. Feature 1 — Mana system, 3. Feature 2 — Default texture rework, 4. Features 3 & 4 — Runes and Blood (unified render-layer system), 5. Feature 5 — Warp-next-to-target (normal mode), 6. Shared: reusable "action-denied" cue, 7. Sequencing (+20 more)

### Community 9 - "Idle Personality Behaviors"
Cohesion: 0.17
Nodes (8): IdlePersonality, Optional, BlockPos, Player, SwordFamiliarEntity, Vec3, BlockPos, String

### Community 10 - "Animation Keyframe Values"
Cohesion: 0.07
Nodes (27): 0.0, 0.04, 0.05, 0.08, 0.1, 0.12, 0.15, 0.16 (+19 more)

### Community 11 - "Core Classes (semantic)"
Cohesion: 0.18
Nodes (27): Familiar State Enum, Hand Pose (record), Heirloom Sword Item, Mana Service, Mod Data Components, Mod Network Registry, Sword Cancel Charge Packet, Sword Charge Packet (+19 more)

### Community 12 - "State Machine & Game Events"
Cohesion: 0.15
Nodes (4): FamiliarState, GameEvent, SwordFamiliarEntity, Holder

### Community 13 - "Build & Render Infrastructure"
Cohesion: 0.22
Nodes (24): Build Configuration, FadingOverlayLayer, HeirloomSwordItemModel, HeirloomSwordItemRenderer, SwordFamiliarGeoRenderer, SwordFamiliarModel, SwordTextures, ClientManaState (+16 more)

### Community 14 - "Phase 6 Blocking Design Doc"
Cohesion: 0.08
Nodes (23): Cooldown decrement, Explicitly Deferred, `FamiliarState.java`, `HeirloomSwordModClient.java` — `ClientEvents.onClientTick`, In Scope, Modified Files, `ModKeybinds.java`, `ModNetwork.java` (+15 more)

### Community 15 - "Rotation Keyframe Values"
Cohesion: 0.09
Nodes (23): rotation, 0.1, 0.16, 0.2, 0.24, 0.25, 0.36, 0.4 (+15 more)

### Community 16 - "Warp Handler & Packet"
Cohesion: 0.15
Nodes (15): Sword Warp Packet, Warp Handler, EntityDimensions, WarpHandler, handle(), type(), Pose, CustomPacketPayload (+7 more)

### Community 17 - "Sword Mechanics Fix Plan"
Cohesion: 0.12
Nodes (16): Context for the implementer, File Map, Root-cause notes (already diagnosed — trust these, don't re-derive), Sword Mechanics, State Glitches & Functional Guarding Implementation Plan, Task 10: Block slash deals damage on G release, Task 11: Guard entry from CHARGING and SWEEPING_HOLD (spec completion), Task 12: Final end-to-end verification, Task 1: Vanilla sword mechanics — 12 damage, netherite cooldown, sweep (+8 more)

### Community 18 - "NBT & FamiliarState Codec"
Cohesion: 0.15
Nodes (7): Builder, CompoundTag, FamiliarState(), fromId(), getId(), ControllerRegistrar, Override

### Community 19 - "Idle Personality Design Doc"
Cohesion: 0.12
Nodes (16): 1. Goal, 2.1 Sync model, 2.2 Server vs client split inside `IdlePersonality.tick(owner)`, 2. Architecture, 3.1 Idle timer & cancellation (server-side), 3.2 Curious drift, 3.3 Lazy figure-eight, 3.4 Environmental reactions (one-shots) (+8 more)

### Community 20 - "Lang File Keys"
Cohesion: 0.12
Nodes (16): entity.heirloomswordmod.sword_familiar, heirloomswordmod.configuration.section.heirloomswordmod.common.toml, heirloomswordmod.configuration.section.heirloomswordmod.common.toml.title, heirloomswordmod.configuration.title, item.heirloomswordmod.heirloom_sword, itemGroup.heirloomswordmod, key.categories.heirloomswordmod, key.heirloomswordmod.guard (+8 more)

### Community 21 - "Entity Base Types (AST)"
Cohesion: 0.15
Nodes (10): AABB, AnimationState, Entity, EntityType, GeoEntity, ModEntities, PlayState, AnimatableInstanceCache (+2 more)

### Community 22 - "Polish & Sky-Drop Plan"
Cohesion: 0.12
Nodes (15): Combat Feel, Smoothness, Sky-Drop Spawn, Quick-Fire & Hand Poses Implementation Plan, Context for the implementer, File Map, Smoothness audit (what "smooth between every realistic state" maps to), Task 10: First-person telekinesis hand poses, Task 11: Final end-to-end verification, Task 1: Combat & speed tuning constants, Task 2: Smoothstep easing on the horizontal blend (+7 more)

### Community 23 - "Combat Damage Helpers"
Cohesion: 0.19
Nodes (3): Integer, Set, LivingEntity

### Community 24 - "Render Alignment Plan"
Cohesion: 0.13
Nodes (14): Explicitly out of scope (do not "fix" these), File Structure, Render Alignment & Polish Fixes Implementation Plan, Task 1: Rewrite `SwordFamiliarGeoRenderer.preRender`, Task 2: Remove baked whole-body rotations from animations, Task 3: Simplify SWEEPING_HOLD yaw math in `updateOrientation`, Task 4: Retune charging height, Task 5: BLOCKING-specific bounding box (+6 more)

### Community 25 - "Community 25"
Cohesion: 0.24
Nodes (11): AlphaFn, FadingOverlayLayer, GeoRenderer, RenderType, BakedGeoModel, MultiBufferSource, Override, PoseStack (+3 more)

### Community 29 - "Community 29"
Cohesion: 0.15
Nodes (12): File Structure, Mana System Implementation Plan, Self-Review (coverage against the spec), Task 1: Mana core spine (service, attachments, client cache, sync packet), Task 2: Shared "action-denied" cue, Task 3: Mana regeneration tick, Task 4: CHARGING — drain + entry gate, Task 5: SWEEPING_HOLD — drain + entry gate (+4 more)

### Community 30 - "Community 30"
Cohesion: 0.15
Nodes (12): File Structure, Phase 10 (Plan A) — Audio Triggers & Missing Particles Implementation Plan, Scope & Guardrails, Self-Review, Task 1: Add the new sound helpers (central, all in one file), Task 2: Mode enter / exit sounds, Task 3: Launch, impact (+particle), return-arrival, quick-fire, death-fall, Task 4: Sweep-contact, guard-raised, guard-break (+4 more)

### Community 32 - "Community 32"
Cohesion: 0.26
Nodes (7): BuildCreativeModeTabContentsEvent, FMLCommonSetupEvent, HeirloomSwordMod, IEventBus, ServerStartingEvent, ModContainer, SubscribeEvent

### Community 33 - "Community 33"
Cohesion: 0.17
Nodes (11): 10. Out of Scope, 1. GeckoLib Dependency, 2. Entity Changes — GeoEntity Interface, 3. Renderer — GeoEntityRenderer, 4. Model — Placeholder Geometry, 5. Hand Gesture Suppression, 6. Telekinetic Shimmer Particles, 7. Spawn Fade-In (+3 more)

### Community 34 - "Community 34"
Cohesion: 0.18
Nodes (10): File Structure, Phase 12 — Idle Personality Implementation Plan, Scope & Guardrails, Self-Review, Task 1: Curiosities block tag, Task 2: Entity scaffolding (synced data, accessors, hooks, predicate, controllers), Task 3: `IdlePersonality` helper class, Task 4: Wire the helper into the entity (+2 more)

### Community 35 - "Community 35"
Cohesion: 0.38
Nodes (9): decode(), encode(), handle(), type(), CustomPacketPayload, FriendlyByteBuf, IPayloadContext, Override (+1 more)

### Community 36 - "Community 36"
Cohesion: 0.20
Nodes (9): 1. Planned work (for reference), 2. Unplanned changes, 2a. Bug fixes (from in-game testing), 2b. Polish / UX (requested mid-session), 2c. New feature requests (STUCK feedback + ambient FX), 3. Files touched by the unplanned work, 4. Verification, 5. Known deferred items (+1 more)

### Community 37 - "Community 37"
Cohesion: 0.20
Nodes (9): File Structure, Runes & Blood Overlay System Implementation Plan, Self-Review (coverage against the spec), Task 1: Add the blood DataComponent, Task 2: Blood accessors + decay on the item, Task 3: Bloody the blade on flying-mode hits, Task 4: Overlay texture refs + the fading render layer, Task 5: Wire the layers into the renderers (+1 more)

### Community 38 - "Community 38"
Cohesion: 0.20
Nodes (9): Decision Record (read before implementing), File Structure, Phase 9 — Epic Fight Compatibility (Datapack, No Dependency) Implementation Plan, Self-Review, Task 1: Create the Epic Fight greatsword capability file, Task 2: Declare Epic Fight as an optional dependency (load-order + documentation), Task 3: Verify vanilla-only fallback (no Epic Fight) still builds and behaves, Task 4: Manual in-game verification with Epic Fight installed (+1 more)

### Community 39 - "Community 39"
Cohesion: 0.20
Nodes (9): File Structure, Phase 11 — Tether Pull Implementation Plan, Scope & Guardrails, Self-Review (completed during authoring), Task 1: Tether sound helpers, Task 2: TETHERING state + yank physics, Task 3: SwordTetherPacket + registration, Task 4: Fresh-Shift-press client detection (+1 more)

### Community 40 - "Community 40"
Cohesion: 0.38
Nodes (9): decode(), encode(), handle(), type(), CustomPacketPayload, FriendlyByteBuf, IPayloadContext, Override (+1 more)

### Community 41 - "Community 41"
Cohesion: 0.22
Nodes (8): File Structure, Self-Review (coverage against the spec), Task 1: Warp cooldown attachment + tick, Task 2: Warp logic, Task 3: Warp packet + registration, Task 4: Bind V to warp in normal mode, Task 5: In-game verification, Warp-Next-To-Target Implementation Plan

### Community 42 - "Community 42"
Cohesion: 0.25
Nodes (8): rotation, blade, pommel, rotation, 0.0, 0.08, 0.15, 1.0

### Community 43 - "Community 43"
Cohesion: 0.29
Nodes (6): animation_length, bones, loop, animations, animation.alucard_sword.idle, format_version

### Community 44 - "Community 44"
Cohesion: 0.47
Nodes (5): getSerializedName(), SwordMode(), Override, String, String

### Community 45 - "Community 45"
Cohesion: 0.40
Nodes (5): animation_length, bones, loop, animation.alucard_sword.alert, root

### Community 46 - "Community 46"
Cohesion: 0.67
Nodes (4): lerp_mode, post, 0.14, 0.14

### Community 47 - "Community 47"
Cohesion: 0.67
Nodes (4): lerp_mode, post, 0.22, 0.22

### Community 48 - "Community 48"
Cohesion: 0.67
Nodes (4): lerp_mode, post, 0.3, 0.3

### Community 49 - "Community 49"
Cohesion: 0.67
Nodes (4): lerp_mode, post, 0.38, 0.38

### Community 50 - "Community 50"
Cohesion: 0.67
Nodes (4): lerp_mode, post, 0.5, 0.5

### Community 51 - "Community 51"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.block_slash

### Community 52 - "Community 52"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.charge_spin

### Community 53 - "Community 53"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.death_fall

### Community 54 - "Community 54"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.block_stance

### Community 55 - "Community 55"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.guard_break

### Community 56 - "Community 56"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.idle_curious

### Community 57 - "Community 57"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.idle_figure_eight

### Community 58 - "Community 58"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.idle_perk

### Community 59 - "Community 59"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.idle_recoil

### Community 60 - "Community 60"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.launch

### Community 61 - "Community 61"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.return

### Community 62 - "Community 62"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.return_hilt

### Community 63 - "Community 63"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.stuck

### Community 64 - "Community 64"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.sweep_hold

### Community 65 - "Community 65"
Cohesion: 0.50
Nodes (4): animation_length, bones, loop, animation.alucard_sword.tether_pull

## Knowledge Gaps
- **351 isolated node(s):** `ClientManaState`, `Config`, `ControllerRegistrar`, `RegisterRenderers`, `InteractionKeyMappingTriggered` (+346 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **17 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SwordFamiliarEntity` connect `State Machine & Game Events` to `Event Handlers & Mana Ticks`, `Familiar State Methods`, `Familiar GeoRenderer Pipeline`, `Idle Personality Behaviors`, `Warp Handler & Packet`, `NBT & FamiliarState Codec`, `Entity Base Types (AST)`, `Combat Damage Helpers`, `Community 26`, `Community 27`, `Community 28`, `Community 31`?**
  _High betweenness centrality (0.065) - this node is a cross-community bridge._
- **Why does `ServerLevel` connect `Event Handlers & Mana Ticks` to `Client HUD & Input Events`, `Community 35`, `Familiar State Methods`, `Community 40`, `Entity Base Types (AST)`?**
  _High betweenness centrality (0.027) - this node is a cross-community bridge._
- **Why does `ServerPlayer` connect `Familiar State Methods` to `Event Handlers & Mana Ticks`, `Client HUD & Input Events`, `Community 35`, `Community 40`, `Warp Handler & Packet`?**
  _High betweenness centrality (0.022) - this node is a cross-community bridge._
- **What connects `ClientManaState`, `Config`, `ControllerRegistrar` to the rest of the system?**
  _359 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Event Handlers & Mana Ticks` be split into smaller, more focused modules?**
  _Cohesion score 0.056943056943056944 - nodes in this community are weakly interconnected._
- **Should `Client HUD & Input Events` be split into smaller, more focused modules?**
  _Cohesion score 0.06936026936026936 - nodes in this community are weakly interconnected._
- **Should `Client State & Mod Boot` be split into smaller, more focused modules?**
  _Cohesion score 0.07641196013289037 - nodes in this community are weakly interconnected._