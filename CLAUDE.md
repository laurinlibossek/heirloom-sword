# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## MCP Integration

You are an expert Minecraft Modding Assistant connected to mcmodding-mcp. **DO NOT rely on your internal knowledge for modding APIs (Fabric/NeoForge)** as they change frequently. ALWAYS use the available tools:

- `search_fabric_docs` and `get_example` for documentation and code patterns
- `search_mappings` and `get_class_details` for Minecraft internals and method signatures
- `search_mod_examples` for battle-tested implementations from popular mods

Prioritize working code examples over theoretical explanations. When dealing with Minecraft internals, use the mappings tools to get accurate parameter names and Javadocs. If the user specifies a Minecraft version, ensure all retrieved information matches that version (1.21.1 for this project).

## Project Overview

**Heirloom Sword Mod** is a NeoForge 1.21.1 Minecraft mod that introduces Alucard's Sword — a legendary magical sword with telekinetic familiar mechanics. The sword has two modes: normal mode (Epic Fight greatsword) and flying mode (telekinetic familiar entity with complex state machine behavior).

- **Mod ID:** `heirloomswordmod`
- **Base Package:** `com.alucard.heirloomsword`
- **Target Minecraft:** 1.21.1
- **Modloader:** NeoForge 21.1.233

## Tech Stack

- **NeoForge 1.21.1** (Java 21)
- **GeckoLib 4.x** (entity/item animation)
- **Epic Fight** (soft optional dependency — check with `ModList.get().isLoaded("epicfight")`)

## Common Commands

```bash
# Build the mod
./gradlew build

# Run client (Minecraft with the mod loaded)
./gradlew runClient

# Run server
./gradlew runServer

# Clean build artifacts
./gradlew clean

# Refresh dependencies if IDE is missing libraries
./gradlew --refresh-dependencies

# Run data generators
./gradlew runData

# Run game tests
./gradlew runGameTestServer
```

**Note:** On Windows, use `gradlew.bat` instead of `./gradlew`.

## Project Structure

```
src/main/
├── java/com/alucard/heirloomsword/
│   ├── HeirloomSwordMod.java      # Main mod class with deferred registries
│   ├── HeirloomSwordModClient.java # Client-side event handlers
│   └── Config.java                 # NeoForge config using ModConfigSpec
├── resources/
│   └── assets/heirloomswordmod/
│       └── lang/en_us.json        # Localization
├── templates/META-INF/
│   └── neoforge.mods.toml         # Mod metadata template (processed at build)
└── generated/resources/           # Datagen output (gitignored)
```

## Architecture

### Registration Pattern

The mod uses NeoForge's **DeferredRegister** system for all registrations:

- `BLOCKS` — `DeferredRegister.Blocks` for block registration
- `ITEMS` — `DeferredRegister.Items` for item registration
- `CREATIVE_MODE_TABS` — `DeferredRegister<CreativeModeTab>` for creative tabs

All registries are registered to the mod event bus in the constructor:
```java
BLOCKS.register(modEventBus);
ITEMS.register(modEventBus);
CREATIVE_MODE_TABS.register(modEventBus);
```

### Event Bus Architecture

**Two event buses:**
1. **Mod Event Bus** (`IEventBus modEventBus`) — Used for mod lifecycle events like `FMLCommonSetupEvent`
2. **NeoForge Event Bus** (`NeoForge.EVENT_BUS`) — Used for gameplay events like `ServerStartingEvent`

Register on the mod event bus via `modEventBus.addListener(...)`. Register on the game event bus via `NeoForge.EVENT_BUS.register(this)` (requires `@SubscribeEvent` methods).

### Config System

The mod uses **NeoForge's ModConfigSpec** system. Config is defined in `Config.java` and registered in the mod constructor:
```java
modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
```

Config values are accessed as suppliers (e.g., `Config.MAGIC_NUMBER.getAsInt()`).

### Client vs Server

- `HeirloomSwordMod.java` — Common code (runs on both sides)
- `HeirloomSwordModClient.java` — Client-only code (annotated with `@EventBusSubscriber(modid = MODID, bus = Bus.MOD, value = Dist.CLIENT)`)

Always check distribution context when registering renderers or handling input.

## Design Document

The complete design specification is at `docs/alucard_sword_design_v2.md`. **Read it before making any architectural decisions.** All gameplay decisions are final unless marked [TUNE].

Key topics covered:
- State machine behavior (8 states: HOVERING, CHARGING, LAUNCHING, STUCK, SWEEPING_HOLD, SWEEPING_RELEASE, BLOCKING, RETURNING)
- Physics system (spring follow, lazy lag, obstacle avoidance)
- Combat mechanics (charge tiers, stamina costs, damage values)
- Edge cases (swimming, elytra, dimension travel, mounting, death)
- **Implementation phase order (Section 24)** — follow the 10-phase sequence, do not skip phases

## Architecture Rules

### Sword Familiar Entity
- The sword familiar is a **plain Entity subclass**, NOT LivingEntity, NOT Mob
- All familiar behavior is driven by an **explicit state machine** — no AI goals, no pathfinding
- The state machine has **8 states**: HOVERING, CHARGING, LAUNCHING, STUCK, SWEEPING_HOLD, SWEEPING_RELEASE, BLOCKING, RETURNING
- Each state defines explicit input handling tables — any input not listed is ignored

### Epic Fight Integration
- Epic Fight integration goes in a dedicated **EpicFightCompat class**, never in core logic
- Check availability: `ModList.get().isLoaded("epicfight")`
- Fallback: vanilla sword behavior if Epic Fight is absent

### Network & Authority
- **Server is authoritative** for all state transitions
- Client does prediction for rendering only
- All incoming client packets must be validated against current server-side state

### Item Properties
- The item has **no durability** and **no enchantability**
- It cannot be dropped (Q key) while in flying mode
- It always remains in the player's hotbar during flying mode

## Implementation Phases

**Follow the 10-phase order in `docs/alucard_sword_design_v2.md` Section 24. Do not skip phases.**

- **Phase 1** — Foundation (item, keybind, mode flag)
- **Phase 2** — Familiar Entity (HOVERING only) — **Use a debug cube hitbox, no GeckoLib until Phase 8**
- **Phase 3** — LAUNCHING and RETURNING
- **Phase 4** — CHARGING
- **Phase 5** — SWEEPING_HOLD and SWEEPING_RELEASE
- **Phase 6** — BLOCKING
- **Phase 7** — Restrictions and edge cases
- **Phase 8** — GeckoLib integration (replace debug hitbox with full model)
- **Phase 9** — Epic Fight integration
- **Phase 10** — Audio and polish

## Code Style

- **Package root:** `com.alucard.heirloomsword`
- **One class per state** is fine if it keeps the state machine readable
- **Prefer composition over inheritance** for familiar behavior
- Use NeoForge's DeferredRegister pattern for all registrations
- Client-side code goes in `HeirloomSwordModClient.java` with `@EventBusSubscriber(value = Dist.CLIENT)`

## Important Implementation Notes

- **Java 21 required** — Mojang ships Java 21 with 1.21.1
- **Parchment mappings** — The project uses Parchment for better parameter names and Javadocs
- **Entity validation** — The familiar entity must be validated every tick while flying mode is active (see Section 18 of design doc)
- **Network packets** — All client→server input must be validated against server-side state (see Section 22 of design doc)
- **Use MCP tools** — Always query `search_mappings` or `get_class_details` for Minecraft internals rather than relying on outdated knowledge

## Common Tasks

### Adding a new item
```java
public static final DeferredItem<Item> MY_ITEM = ITEMS.registerSimpleItem("my_item", new Item.Properties());
```

### Adding a creative tab entry
```java
private void addCreative(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() == CreativeModeTabs.COMBAT) {
        event.accept(MY_ITEM);
    }
}
```

### Adding config values
```java
// In Config.java
public static final ModConfigSpec.BooleanValue MY_SETTING = BUILDER
    .comment("Description of what this does")
    .define("mySettingKey", true);
```

### Adding network packets
Use NeoForge's `CustomPacketPayload` system. See Section 22 of the design doc for the required packet types.

## Gradle Properties Reference

Key properties in `gradle.properties`:

- `minecraft_version=1.21.1`
- `neo_version=21.1.233`
- `mod_id=heirloomswordmod`
- `mod_name=Heirloom Sword Mod`
- `mod_version=1.0.0`
- `mod_group_id=com.alucard.heirloomsword`

## Resources

- NeoForged Docs: https://docs.neoforged.net/
- NeoForged Discord: https://discord.neoforged.net/
- Mojang Mappings License: https://github.com/NeoForged/NeoForm/blob/main/Mojang.md
