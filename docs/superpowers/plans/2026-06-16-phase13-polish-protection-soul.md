# Phase 13 — Polish, Protection & Soul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Phase 13 hardening pass from design doc §25 — admin config (combat balance + integration toggles), localization audit, tooltip lore, server-aware PvP gating, death-drop protection, spectator force-exit, sculk resonance, and the "awakening" first-activation ceremony + two advancements.

**Architecture:** Server-authoritative throughout, matching every prior phase. Config is a NeoForge `ModConfigSpec` (COMMON) already registered in the mod constructor; combat constants become config-backed accessor methods so the compiler flags every call-site. Mana is gated by a single `consumeMana` switch folded into `ManaService.isExempt`. Death-drop protection is **event-based** (reload-robust) rather than a custom `ItemEntity` subclass. The awakening reuses the existing `ARRIVING` state with a transient flag that slows the descent and appends one orbit leg.

**Tech Stack:** NeoForge 1.21.1, Java 21, GeckoLib 4.x. No new assets, no `sounds.json`, no custom `EntityType`. Advancements are hand-authored JSON (this repo has no datagen class).

## Global Constraints

- **Mod id:** `heirloomswordmod` — every `ResourceLocation`, lang key, and data path uses it. Never `alucardsword`.
- **Base package:** `com.alucard.heirloomsword`.
- **Minecraft 1.21.1 / NeoForge 21.1.233 / Java 21.** Data folders are 1.21-singular: `data/heirloomswordmod/advancement/…` (matches the existing `tags/block/…`).
- **No player-facing string literal may remain in Java** — use `Component.translatable` + an `en_us.json` key.
- **Server authority:** all damage, state, and config-gated decisions run server-side. Client code is render/prediction only.
- **No unit-test harness exists.** Every code task ends with `./gradlew build` (compile gate) + a commit. Runtime behavior is verified in-game per Task 13's matrix. The three exhaustive `switch (FamiliarState)` blocks compile-enforce state completeness.

---

## Scope & Guardrails

**In scope (design doc §25.1–25.12):** config (25.1), localization audit (25.2), tooltip lore (25.3), PvP gate (25.5), death-drop protection (25.7), keepInventory verify (25.8), spectator force-exit (25.10), sculk resonance (25.11), awakening (25.12b), advancements (25.12c).

**Out of scope (closed or dropped — do NOT implement):**
- **25.4 Inventory-relocation lock — DROPPED.** Item safety is already covered by the `onItemToss` Q-drop block + death-drop protection (Task 7). Do not add slot/container interception.
- **25.6 Charge cue — already shipped.**
- **25.9 Block-destruction permissions — already satisfied.** Pierceable blocks phase through without destruction ([SwordFamiliarEntity.java:690](../../../src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java#L690)); nothing changes the world, so there is no `mobGriefing` gate and **no `respectMobGriefing` config**.
- **25.12a Warden tremble — dropped.**

**Decisions locked this pass:**
- Mana config is a single `combat.consumeMana` on/off switch, **not** per-rate control. Per-rate drain constants stay hardcoded `[TUNE]` in `ManaService`.
- Integration config = `allowPvpDamage` + `sculkResonance` only.
- Death-drop protection is event-based (no custom `ItemEntity` subclass) for reload-robustness — a deliberate deviation from §25.7's "preferred" subclass note.
- Advancement `a_will_of_its_own` uses an `minecraft:impossible` criterion awarded from code (no custom `CriterionTrigger` class).

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `Config.java` | Config spec | Populate `combat` + `integration` sections |
| `ManaService.java` | Mana pool | `consumeMana` gate in `isExempt` |
| `SwordFamiliarEntity.java` | State machine | Config-backed damage/cooldown/undead accessors; PvP gate; sculk emits; awakening ARRIVING variant |
| `ModDataComponents.java` | Item components | Add `AWAKENED` boolean component |
| `network/SwordModePacket.java` | F-activation | First-activation awakening flag + advancement award |
| `HeirloomSwordItem.java` | Item | Tooltip lore override |
| `SwordEventHandler.java` | Game events | Death-drop protection events; spectator force-exit |
| `assets/heirloomswordmod/lang/en_us.json` | Localization | Tooltip + advancement keys |
| `data/heirloomswordmod/advancement/soul_bound.json` | Advancement | New |
| `data/heirloomswordmod/advancement/a_will_of_its_own.json` | Advancement | New |

**Dependency order:** Config (Task 1) → combat wiring (Task 2) + mana gate (Task 3) → independent items (Tasks 4–12). Tasks 4–12 only depend on `Config` existing.

---

## Task 1: Config definitions

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/Config.java` (replace entire body)

**Interfaces:**
- Produces: `Config.LAUNCH_DAMAGE_NORMAL`, `LAUNCH_DAMAGE_CHARGED`, `RETURN_DAMAGE`, `QUICK_FIRE_DAMAGE`, `SWEEP_CONTACT_DAMAGE`, `SWEEP_RELEASE_DAMAGE`, `BLOCK_SLASH_DAMAGE`, `LANDING_IMPACT_DAMAGE` (all `ModConfigSpec.DoubleValue`); `QUICK_FIRE_COOLDOWN_TICKS`, `GUARD_BREAK_COOLDOWN_TICKS` (`IntValue`); `UNDEAD_IGNITE_SECONDS` (`DoubleValue`); `CONSUME_MANA`, `ALLOW_PVP_DAMAGE`, `SCULK_RESONANCE` (`BooleanValue`). Read with `.getAsDouble()` / `.getAsInt()` / `.getAsBoolean()`.

- [ ] **Step 1: Replace `Config.java` with the populated spec**

```java
package com.alucard.heirloomsword;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // === combat ===
    public static final ModConfigSpec.DoubleValue LAUNCH_DAMAGE_NORMAL;
    public static final ModConfigSpec.DoubleValue LAUNCH_DAMAGE_CHARGED;
    public static final ModConfigSpec.DoubleValue RETURN_DAMAGE;
    public static final ModConfigSpec.DoubleValue QUICK_FIRE_DAMAGE;
    public static final ModConfigSpec.DoubleValue SWEEP_CONTACT_DAMAGE;
    public static final ModConfigSpec.DoubleValue SWEEP_RELEASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue BLOCK_SLASH_DAMAGE;
    public static final ModConfigSpec.DoubleValue LANDING_IMPACT_DAMAGE;
    public static final ModConfigSpec.IntValue QUICK_FIRE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue GUARD_BREAK_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue UNDEAD_IGNITE_SECONDS;
    public static final ModConfigSpec.BooleanValue CONSUME_MANA;

    // === integration ===
    public static final ModConfigSpec.BooleanValue ALLOW_PVP_DAMAGE;
    public static final ModConfigSpec.BooleanValue SCULK_RESONANCE;

    static {
        BUILDER.comment("Combat balance — damage values, cooldowns, and the mana master switch.")
                .push("combat");
        LAUNCH_DAMAGE_NORMAL = BUILDER.comment("Damage from an uncharged launched sword (outbound).")
                .defineInRange("launchDamageNormal", 16.0, 0.0, 1024.0);
        LAUNCH_DAMAGE_CHARGED = BUILDER.comment("Damage from a fully charged launched sword (outbound).")
                .defineInRange("launchDamageCharged", 32.0, 0.0, 1024.0);
        RETURN_DAMAGE = BUILDER.comment("Damage from the returning sword (inbound).")
                .defineInRange("returnDamage", 8.0, 0.0, 1024.0);
        QUICK_FIRE_DAMAGE = BUILDER.comment("Quick-fire (V) dart contact damage.")
                .defineInRange("quickFireDamage", 12.0, 0.0, 1024.0);
        SWEEP_CONTACT_DAMAGE = BUILDER.comment("Per-contact damage while the sword sweeps around the player.")
                .defineInRange("sweepContactDamage", 4.0, 0.0, 1024.0);
        SWEEP_RELEASE_DAMAGE = BUILDER.comment("Damage from the sweep release fling.")
                .defineInRange("sweepReleaseDamage", 8.0, 0.0, 1024.0);
        BLOCK_SLASH_DAMAGE = BUILDER.comment("Counter-slash damage from a successful guard.")
                .defineInRange("blockSlashDamage", 13.0, 0.0, 1024.0);
        LANDING_IMPACT_DAMAGE = BUILDER.comment("Sky-drop landing impact AoE damage on spawn.")
                .defineInRange("landingImpactDamage", 4.0, 0.0, 1024.0);
        QUICK_FIRE_COOLDOWN_TICKS = BUILDER.comment("Minimum ticks between quick-fires (20 = 1s).")
                .defineInRange("quickFireCooldownTicks", 20, 0, 1200);
        GUARD_BREAK_COOLDOWN_TICKS = BUILDER.comment("Guard lockout ticks after a guard break (60 = 3s).")
                .defineInRange("guardBreakCooldownTicks", 60, 0, 1200);
        UNDEAD_IGNITE_SECONDS = BUILDER.comment("Seconds an undead target burns when struck by the holy blade.")
                .defineInRange("undeadIgniteSeconds", 4.0, 0.0, 120.0);
        CONSUME_MANA = BUILDER.comment(
                        "Master mana switch. false = every flying-mode action (charge, sweep, block, warp)",
                        "is free with no drain, no minimum-cost gate, and no depletion lockout.")
                .define("consumeMana", true);
        BUILDER.pop();

        BUILDER.comment("Cross-mod / server integration toggles.").push("integration");
        ALLOW_PVP_DAMAGE = BUILDER.comment(
                        "Allow the sword to damage other players where the server already permits PvP.",
                        "false disables ALL sword-vs-player damage even when server PvP is on.")
                .define("allowPvpDamage", true);
        SCULK_RESONANCE = BUILDER.comment(
                        "Emit vibration game-events (sculk sensors / shrieker / Warden) on launch, embed, and quick-fire.")
                .define("sculkResonance", true);
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. (Config is defined but not yet read — that is fine.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/Config.java
git commit -m "feat(phase13): populate combat + integration config (25.1)"
```

---

## Task 2: Wire combat constants to config (SwordFamiliarEntity)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

**Interfaces:**
- Consumes: all `Config.*` combat values from Task 1.

**Approach:** Replace the `private static final float` damage constants and the two cooldown/undead literals with config-reading accessor methods. Deleting the constants makes the compiler list every remaining reference — fix each by calling the accessor.

- [ ] **Step 1: Delete the combat damage constants and add accessors**

Remove these lines (currently near 105–119):

```java
    private static final float QUICK_FIRE_DAMAGE = 12.0f;       // [TUNE]
```
```java
    private static final float LAUNCH_DAMAGE_NORMAL = 16.0f;
    private static final float LAUNCH_DAMAGE_CHARGED = 32.0f;
    private static final float RETURN_DAMAGE = 8.0f;
    private static final float BLOCK_SLASH_DAMAGE = 13.0f;  // [TUNE 12-14 per design doc]
```
```java
    private static final float SWEEP_CONTACT_DAMAGE = 4.0f;
    private static final float SWEEP_RELEASE_DAMAGE = 8.0f;
```
```java
    private static final float UNDEAD_BURN_SECONDS = 4.0f; // [TUNE] holy blade ignites undead
```
```java
    private static final int QUICK_FIRE_COOLDOWN_TICKS = 20;    // [TUNE] ~1s
```

Add this accessor block (place it where the constants were, e.g. after the remaining `private static final` block near line 120):

```java
    // Combat values are config-backed (design §25.1). Read per-use so a /reload-style config
    // edit on world reload applies without a restart. Names mirror the deleted constants.
    private static float launchDamageNormal()  { return (float) Config.LAUNCH_DAMAGE_NORMAL.getAsDouble(); }
    private static float launchDamageCharged() { return (float) Config.LAUNCH_DAMAGE_CHARGED.getAsDouble(); }
    private static float returnDamage()        { return (float) Config.RETURN_DAMAGE.getAsDouble(); }
    private static float quickFireDamage()     { return (float) Config.QUICK_FIRE_DAMAGE.getAsDouble(); }
    private static float sweepContactDamage()  { return (float) Config.SWEEP_CONTACT_DAMAGE.getAsDouble(); }
    private static float sweepReleaseDamage()  { return (float) Config.SWEEP_RELEASE_DAMAGE.getAsDouble(); }
    private static float blockSlashDamage()    { return (float) Config.BLOCK_SLASH_DAMAGE.getAsDouble(); }
    private static float landingImpactDamage() { return (float) Config.LANDING_IMPACT_DAMAGE.getAsDouble(); }
    private static int   quickFireCooldownTicks() { return Config.QUICK_FIRE_COOLDOWN_TICKS.getAsInt(); }
    private static int   guardBreakCooldownTicks() { return Config.GUARD_BREAK_COOLDOWN_TICKS.getAsInt(); }
    private static float undeadBurnSeconds()   { return (float) Config.UNDEAD_IGNITE_SECONDS.getAsDouble(); }
```

- [ ] **Step 2: Build to list the broken references**

Run: `./gradlew build`
Expected: FAIL — compile errors "cannot find symbol" at each old constant reference. Note each file:line.

- [ ] **Step 3: Replace each broken reference with its accessor**

Known sites (verify against the compiler output — fix every one it reports):
- Launch damage ternary in `tickLaunching` (~line 710): `chargedLaunch ? LAUNCH_DAMAGE_CHARGED : LAUNCH_DAMAGE_NORMAL` → `chargedLaunch ? launchDamageCharged() : launchDamageNormal()`.
- `BLOCK_SLASH_DAMAGE` in the guard counter-slash (~line 510) → `blockSlashDamage()`.
- `SWEEP_CONTACT_DAMAGE` in `tickSweepingHold` contact (~line 1007) → `sweepContactDamage()`.
- `QUICK_FIRE_DAMAGE` in `tickQuickFire` (~line 1154) → `quickFireDamage()`.
- `UNDEAD_BURN_SECONDS` in `igniteIfUndead` (~1195) and `burnUndeadOnContact` (~1238) → `undeadBurnSeconds()`.
- `QUICK_FIRE_COOLDOWN_TICKS` assignment in `tryQuickFire` (~1131) → `quickFireCooldownTicks()`.
- Any `RETURN_DAMAGE` / `SWEEP_RELEASE_DAMAGE` references the compiler flags → `returnDamage()` / `sweepReleaseDamage()`.

Also replace the two **literals** that have no constant:
- Sky-drop landing impact in `tickArriving` (~line 1498): `target.hurt(this.level().damageSources().playerAttack(owner), 4.0f);` → `..., landingImpactDamage());`
- Guard-break cooldown in `guardBreak()` (~line 523): `setGuardCooldown(60);` → `setGuardCooldown(guardBreakCooldownTicks());`

- [ ] **Step 4: Build to green**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat(phase13): config-back combat damage, cooldowns, undead ignite (25.1)"
```

---

## Task 3: Mana master switch (ManaService)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/ManaService.java:35-37`

**Interfaces:**
- Consumes: `Config.CONSUME_MANA`.

**Approach:** `isExempt` is already the single chokepoint honored by `spend`, `drain`, `hasAtLeast`, and `isLockedOut`. Folding `consumeMana` into it makes mana-disabled behave exactly like universal creative exemption (free actions, no lockout).

- [ ] **Step 1: Augment `isExempt`**

Replace:

```java
    /** Creative players have infinite mana — they bypass every cost, gate, and the lockout. */
    public static boolean isExempt(Player player) {
        return player.getAbilities().instabuild;
    }
```

with:

```java
    /**
     * True when mana costs do not apply to this player: either {@code combat.consumeMana=false}
     * (mana disabled server-wide) or the player is in creative. Bypasses every cost, gate, and
     * the depletion lockout.
     */
    public static boolean isExempt(Player player) {
        return !Config.CONSUME_MANA.getAsBoolean() || player.getAbilities().instabuild;
    }
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/ManaService.java
git commit -m "feat(phase13): consumeMana master switch via isExempt (25.1)"
```

---

## Task 4: Localization audit (25.2)

**Files:**
- Modify (only if the audit finds a literal): the offending `.java` file + `en_us.json`

**Approach:** §25.2 is mostly closed — all known messages are lang keys and `sheathe_first` is dropped. Confirm no user-facing literal remains.

- [ ] **Step 1: Grep for hardcoded user-facing strings**

Run:
```bash
grep -rn "Component.literal\|displayClientMessage\|sendSystemMessage" src/main/java/com/alucard/heirloomsword
```
Expected: every `displayClientMessage` argument is a `Component.translatable(...)`; no `Component.literal(...)` carrying player-facing prose. (Debug-only `LOGGER` strings are exempt — they are not player-facing.)

- [ ] **Step 2: If a literal is found, convert it**

For each offending literal, add a key to `en_us.json` (e.g. `"msg.heirloomswordmod.<name>": "<text>"`) and replace the literal with `Component.translatable("msg.heirloomswordmod.<name>")`. If none found, no change.

- [ ] **Step 3: Commit (only if Step 2 changed anything)**

```bash
git add -A
git commit -m "chore(phase13): localization audit — convert remaining literals (25.2)"
```

If nothing changed, record "25.2 audit clean — no literals" in the task notes and skip the commit.

---

## Task 5: Tooltip lore (25.3)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/HeirloomSwordItem.java`
- Modify: `src/main/resources/assets/heirloomswordmod/lang/en_us.json`

- [ ] **Step 1: Add the lang keys**

In `en_us.json`, add (before the closing `}`; remember the trailing comma on the prior line):

```json
  "tooltip.heirloomswordmod.lore1": "Forged in darkness, bound by will.",
  "tooltip.heirloomswordmod.lore2": "It remembers the hand that wields it.",
```

- [ ] **Step 2: Override `appendHoverText`**

Add these imports to `HeirloomSwordItem.java`:

```java
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
```

Add the override (e.g. after `hurtEnemy`, near line 70):

```java
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heirloomswordmod.lore1")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.heirloomswordmod.lore2")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        super.appendHoverText(stack, context, tooltip, flag);
    }
```

> If the signature does not resolve, verify the 1.21.1 `Item#appendHoverText` parameters via `search_mappings` (the context type is `net.minecraft.world.item.Item.TooltipContext`).

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/HeirloomSwordItem.java src/main/resources/assets/heirloomswordmod/lang/en_us.json
git commit -m "feat(phase13): italic dark-purple tooltip lore (25.3)"
```

---

## Task 6: PvP damage gate (25.5)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

**Interfaces:**
- Consumes: `Config.ALLOW_PVP_DAMAGE`.
- Produces: `private boolean canDamage(Player owner, LivingEntity target)` — call before every sword `hurt()`.

**Approach:** Gate every player-target hit behind config + the server PvP flag + vanilla team friendly-fire. Non-player targets are always allowed (unchanged).

- [ ] **Step 1: Add the gate helper**

Add to `SwordFamiliarEntity` (near the other private helpers):

```java
    /**
     * Whether the sword may damage this target. Non-players: always. Players: only when
     * {@code integration.allowPvpDamage} is on, the server permits PvP, and vanilla team
     * friendly-fire rules allow it. Mirrors §25.5.
     */
    private boolean canDamage(Player owner, LivingEntity target) {
        if (!(target instanceof Player victim)) return true;
        if (!Config.ALLOW_PVP_DAMAGE.getAsBoolean()) return false;
        var server = this.level().getServer();
        if (server == null || !server.isPvpAllowed()) return false;
        return owner.canHarmPlayer(victim); // honors scoreboard team friendly-fire
    }
```

- [ ] **Step 2: Guard each `hurt()` call-site**

Wrap each sword damage application so a blocked player target is skipped (but undead ignite / knockback that follow are also skipped for that target). Sites (verify line numbers):

- Guard counter-slash (~510):
```java
            if (!canDamage(owner, entity)) continue;
            entity.hurt(source, blockSlashDamage());
            igniteIfUndead(entity);
```
- Sweep contact (~1007): add `if (!canDamage(owner, entity)) continue;` before `entity.hurt(source, sweepContactDamage());`.
- Quick-fire (~1154): guard `living.hurt(...)` — `if (canDamage(owner, living)) { living.hurt(...); igniteIfUndead(living); }`.
- Path damage helper `damageEntitiesInPath` (~1259, covers launch / return / sweep-release): add `if (!canDamage(owner, entity)) continue;` before `entity.hurt(source, damage);`.
- Sky-drop landing impact (~1498): guard `target.hurt(...)` and its knockback — `if (!canDamage(owner, target)) continue;`.

Match each site's existing loop control (`continue` inside a for-loop; an `if` wrap for the single quick-fire hit).

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat(phase13): server-aware PvP damage gate (25.5)"
```

---

## Task 7: Death-drop protection (25.7)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordEventHandler.java`

**Approach:** Event-based, reload-robust. Fire/lava immunity is **already** provided by `.fireResistant()` on the item ([HeirloomSwordItem.java:33](../../../src/main/java/com/alucard/heirloomsword/HeirloomSwordItem.java#L33)) — no work for that bullet. Add: (a) unlimited lifetime on any heirloom `ItemEntity` so it never despawns, and (b) void rescue in the item-entity tick.

- [ ] **Step 1: Add imports**

```java
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
```

- [ ] **Step 2: No-despawn on spawn**

Add to `SwordEventHandler`:

```java
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item
                && item.getItem().getItem() instanceof HeirloomSwordItem) {
            item.setUnlimitedLifetime();  // never despawns from age (persists via Age NBT)
            item.setExtendedLifetime();   // also exempt from merge-despawn shortcuts
        }
    }
```

- [ ] **Step 3: Void rescue in the item-entity tick**

```java
    @SubscribeEvent
    public void onItemEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity item)) return;
        if (!(item.getItem().getItem() instanceof HeirloomSwordItem)) return;
        Level level = item.level();
        if (level.isClientSide) return;
        // Rescue before vanilla void-destruction at minBuildHeight - 64.
        if (item.getY() < level.getMinBuildHeight() - 32) {
            BlockPos spawn = level.getSharedSpawnPos();
            int safeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    spawn.getX(), spawn.getZ());
            item.setPos(spawn.getX() + 0.5, safeY + 1.0, spawn.getZ() + 0.5);
            item.setDeltaMovement(Vec3.ZERO);
            item.fallDistance = 0.0f;
        }
    }
```

> Verify `ItemEntity#setUnlimitedLifetime`, `setExtendedLifetime`, and `EntityTickEvent.Post` exist in 21.1.233 via `search_mappings` / NeoForge docs if compilation fails. If `setExtendedLifetime` is absent, `setUnlimitedLifetime` alone satisfies the no-despawn requirement.

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordEventHandler.java
git commit -m "feat(phase13): death-drop protection — no despawn + void rescue (25.7)"
```

---

## Task 8: keepInventory verification (25.8)

**Files:** none expected (verification task).

**Approach:** The familiar already self-discards when `!owner.isAlive()` ([SwordFamiliarEntity.java:329](../../../src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java#L329)); the item is a normal inventory item so `keepInventory` retains it; `onPlayerTick` resets the mode once the familiar is gone. Confirm no code path force-drops or duplicates the sword on death.

- [ ] **Step 1: Read the death path**

Confirm: (a) `serverTick` discards the familiar on owner death; (b) nothing in `SwordEventHandler` or the entity explicitly spawns/drops the sword `ItemStack` on death; (c) `onPlayerTick`'s missing-familiar branch resets `SwordMode.NORMAL` and clears `FAMILIAR_UUID`.

- [ ] **Step 2: In-game verify (record result, no commit)**

`/gamerule keepInventory true`, enter flying mode, `/kill`. Expected: familiar despawns, item stays in inventory, mode is `NORMAL` after respawn (verify by toggling F works normally). If the familiar lingers or the mode stays FLYING, capture the discrepancy and add a fix as a follow-up step (e.g. a `LivingDeathEvent` handler that calls `despawnForOwner` + mode reset). Record the outcome in the task notes.

---

## Task 9: Spectator force-exit (25.10)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordEventHandler.java`

**Approach:** On switching **into** spectator, force-exit flying mode (despawn familiar, reset mode, clear UUID). Creative/elytra are overruled — no work there.

- [ ] **Step 1: Add imports**

```java
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangeGameModeEvent;
```

- [ ] **Step 2: Add the handler**

```java
    @SubscribeEvent
    public void onChangeGameMode(PlayerChangeGameModeEvent event) {
        if (event.getNewGameMode() != GameType.SPECTATOR) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack swordStack = findFlyingSword(player);
        if (swordStack != null) {
            HeirloomSwordItem.setMode(swordStack, SwordMode.NORMAL);
            HeirloomSwordItem.setBlood(swordStack, 0f);
            swordStack.remove(ModDataComponents.FAMILIAR_UUID.get());
        }
        SwordFamiliarEntity.despawnForOwner(player.serverLevel(), player.getUUID());
    }
```

> This mirrors the existing `onPlayerLogout` despawn pattern. `despawnForOwner` uses `discard()` (no death animation), satisfying §25.10's "no animation".

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordEventHandler.java
git commit -m "feat(phase13): force-exit flying mode on entering spectator (25.10)"
```

---

## Task 10: Sculk resonance (25.11)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`

**Interfaces:**
- Consumes: `Config.SCULK_RESONANCE`.
- Produces: `private void emitVibration(net.minecraft.world.level.gameevent.GameEvent event)`.

**Approach:** Emit a vibration game-event when **entering** LAUNCHING (`PROJECTILE_SHOOT`), STUCK (`PROJECTILE_LAND`), and QUICK_FIRE (`PROJECTILE_SHOOT`), gated on config. SWEEPING/BLOCKING emit nothing.

- [ ] **Step 1: Add the helper**

```java
    private void emitVibration(net.minecraft.world.level.gameevent.GameEvent event) {
        if (!Config.SCULK_RESONANCE.getAsBoolean()) return;
        if (this.level().isClientSide) return;
        Player owner = getOwner();
        // Context = the owner, so detectors attribute the vibration to the player (like a real projectile).
        this.level().gameEvent(owner, event, this.position());
    }
```

> Verify the 1.21.1 game-event constants `GameEvent.PROJECTILE_SHOOT` / `GameEvent.PROJECTILE_LAND` and the `Level#gameEvent(Entity, Holder<GameEvent>, Vec3)` overload via `search_mappings` (the param may be `Holder<GameEvent>` — `GameEvent.PROJECTILE_SHOOT` already resolves correctly for `gameEvent(Entity, GameEvent, Vec3)` in 1.21.1).

- [ ] **Step 2: Emit on LAUNCHING entry**

Find where LAUNCHING is entered (the `enterLaunching`/launch-start method that calls `setState(FamiliarState.LAUNCHING)`). Immediately after the `setState`, add:

```java
        emitVibration(net.minecraft.world.level.gameevent.GameEvent.PROJECTILE_SHOOT);
```

- [ ] **Step 3: Emit on STUCK entry**

In `enterStuck()` ([line 730](../../../src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java#L730)), inside the existing `if (!this.level().isClientSide)` block, add:

```java
            emitVibration(net.minecraft.world.level.gameevent.GameEvent.PROJECTILE_LAND);
```

- [ ] **Step 4: Emit on QUICK_FIRE entry**

In the quick-fire start (`tryQuickFire`, where `setState(FamiliarState.QUICK_FIRE)` / cooldown is set, ~1131), add:

```java
        emitVibration(net.minecraft.world.level.gameevent.GameEvent.PROJECTILE_SHOOT);
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java
git commit -m "feat(phase13): sculk vibration emits on launch/embed/quick-fire (25.11)"
```

---

## Task 11: Awakening moment (25.12b)

**Files:**
- Modify: `src/main/java/com/alucard/heirloomsword/ModDataComponents.java`
- Modify: `src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java`
- Modify: `src/main/java/com/alucard/heirloomsword/network/SwordModePacket.java`

**Interfaces:**
- Produces: `ModDataComponents.AWAKENED` (`DataComponentType<Boolean>`); `SwordFamiliarEntity#setAwakening(boolean)`.

**Approach:** A boolean `AWAKENED` component on the stack. First-ever F activation sets it and flags the spawned familiar; the flag turns the existing `ARRIVING` into a ~2.5s slow descent followed by one slow orbit, with no landing impact. ARRIVING already ignores combat inputs and F is locked during it, so non-interruptibility is free. The flag is transient (not saved) — a reload mid-awakening finishes as a normal ARRIVING (acceptable, rare).

- [ ] **Step 1: Register the `AWAKENED` component**

Add imports to `ModDataComponents.java`:

```java
import net.minecraft.network.codec.ByteBufCodecs;
```
(`Codec` is already imported.)

Add the component:

```java
    // True once the sword has ever been activated into flying mode. Drives the one-time awakening.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> AWAKENED =
            DATA_COMPONENTS.register("awakened", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());
```

- [ ] **Step 2: Add awakening state to the entity**

In `SwordFamiliarEntity`, add fields near the ARRIVING constants (~line 135):

```java
    private boolean awakening = false;
    private int awakeningOrbitTicks = 0;
    private static final double AWAKENING_DESCENT_SPEED = 0.32; // [TUNE] ~2.5s over a ~16-block drop
    private static final int    AWAKENING_ORBIT_TICKS  = 40;    // [TUNE] one slow orbit (~2s)
    private static final double AWAKENING_ORBIT_RADIUS = 1.6;   // [TUNE] blocks from the owner

    public void setAwakening(boolean value) { this.awakening = value; }
```

- [ ] **Step 3: Branch `tickArriving` for the awakening**

Replace `tickArriving` ([lines 1483-1506](../../../src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java#L1483)) with:

```java
    private void tickArriving(Player owner) {
        Vec3 hoverPos = computeCandidatePosition(owner, 0);

        if (awakening) {
            // Phase 1: ceremonial slow descent.
            Vec3 toTarget = hoverPos.subtract(this.position());
            if (toTarget.length() > AWAKENING_DESCENT_SPEED && awakeningOrbitTicks == 0) {
                this.setPos(this.position().add(toTarget.normalize().scale(AWAKENING_DESCENT_SPEED)));
                return;
            }
            // Phase 2: one slow orbit around the owner, then settle. No landing impact.
            awakeningOrbitTicks++;
            double angle = (awakeningOrbitTicks / (double) AWAKENING_ORBIT_TICKS) * (Math.PI * 2.0);
            Vec3 orbit = hoverPos.add(Math.cos(angle) * AWAKENING_ORBIT_RADIUS, 0,
                    Math.sin(angle) * AWAKENING_ORBIT_RADIUS);
            this.setPos(orbit);
            if (awakeningOrbitTicks >= AWAKENING_ORBIT_TICKS) {
                this.setPos(hoverPos);
                this.targetPosition = hoverPos;
                this.velocity = Vec3.ZERO;
                this.smoothedAnchorY = Double.NaN;
                this.awakening = false;
                setState(FamiliarState.HOVERING);
                this.level().playSound(null, this.blockPosition(),
                        net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.7f);
            }
            return;
        }

        // Default fast sky-drop (unchanged).
        Vec3 toTarget = hoverPos.subtract(this.position());
        if (toTarget.length() <= ARRIVE_SPEED) {
            this.setPos(hoverPos);
            this.targetPosition = hoverPos;
            this.velocity = Vec3.ZERO;
            this.smoothedAnchorY = Double.NaN;
            setState(FamiliarState.HOVERING);
            this.level().playSound(null, this.blockPosition(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.7f);
            for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(3.0), e -> e != owner && e.isAlive())) {
                if (!canDamage(owner, target)) continue;
                target.hurt(this.level().damageSources().playerAttack(owner), landingImpactDamage());
                double dx = target.getX() - this.getX();
                double dz = target.getZ() - this.getZ();
                target.knockback(0.5, -dx, -dz);
            }
            return;
        }
        this.setPos(this.position().add(toTarget.normalize().scale(ARRIVE_SPEED)));
    }
```

> Note: this folds in the Task 6 `canDamage` guard on the landing impact. If Task 6 is implemented first, keep that guard; the awakening branch deals no landing damage at all.

- [ ] **Step 4: Set the awakening flag on first activation**

In `SwordModePacket.handle`, in the **enter-flying** branch ([line 66-70](../../../src/main/java/com/alucard/heirloomsword/network/SwordModePacket.java#L66)), replace:

```java
                HeirloomSwordItem.setMode(held, SwordMode.FLYING);
                SwordFamiliarEntity familiar = new SwordFamiliarEntity(level, player);
                level.addFreshEntity(familiar);
                held.set(ModDataComponents.FAMILIAR_UUID.get(), familiar.getUUID());
                SwordSounds.playModeEnter(level, player.getX(), player.getY(), player.getZ());
```

with:

```java
                HeirloomSwordItem.setMode(held, SwordMode.FLYING);
                boolean firstAwakening = !held.getOrDefault(ModDataComponents.AWAKENED.get(), false);
                SwordFamiliarEntity familiar = new SwordFamiliarEntity(level, player);
                if (firstAwakening) {
                    held.set(ModDataComponents.AWAKENED.get(), true);
                    // Only the sky-drop entrance can be ceremonial; if it materialized (no clearance)
                    // there is nothing to slow — the flag is harmless either way.
                    familiar.setAwakening(true);
                }
                level.addFreshEntity(familiar);
                held.set(ModDataComponents.FAMILIAR_UUID.get(), familiar.getUUID());
                SwordSounds.playModeEnter(level, player.getX(), player.getY(), player.getZ());
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/alucard/heirloomsword/ModDataComponents.java src/main/java/com/alucard/heirloomsword/SwordFamiliarEntity.java src/main/java/com/alucard/heirloomsword/network/SwordModePacket.java
git commit -m "feat(phase13): one-time awakening spawn ceremony (25.12b)"
```

---

## Task 12: Advancements (25.12c)

**Files:**
- Create: `src/main/resources/data/heirloomswordmod/advancement/soul_bound.json`
- Create: `src/main/resources/data/heirloomswordmod/advancement/a_will_of_its_own.json`
- Modify: `src/main/java/com/alucard/heirloomsword/network/SwordModePacket.java`
- Modify: `src/main/resources/assets/heirloomswordmod/lang/en_us.json`

**Approach:** `soul_bound` auto-grants via vanilla `inventory_changed`. `a_will_of_its_own` uses an `impossible` criterion and is awarded from the first-activation code — no custom `CriterionTrigger` class.

- [ ] **Step 1: Create `soul_bound.json`**

```json
{
  "parent": "minecraft:adventure/root",
  "display": {
    "icon": { "id": "heirloomswordmod:heirloom_sword" },
    "title": { "translate": "advancements.heirloomswordmod.soul_bound.title" },
    "description": { "translate": "advancements.heirloomswordmod.soul_bound.description" },
    "frame": "goal",
    "show_toast": true,
    "announce_to_chat": true,
    "hidden": false
  },
  "criteria": {
    "has_sword": {
      "trigger": "minecraft:inventory_changed",
      "conditions": { "items": [ { "items": "heirloomswordmod:heirloom_sword" } ] }
    }
  },
  "requirements": [ [ "has_sword" ] ]
}
```

- [ ] **Step 2: Create `a_will_of_its_own.json`**

```json
{
  "parent": "heirloomswordmod:soul_bound",
  "display": {
    "icon": { "id": "heirloomswordmod:heirloom_sword" },
    "title": { "translate": "advancements.heirloomswordmod.a_will_of_its_own.title" },
    "description": { "translate": "advancements.heirloomswordmod.a_will_of_its_own.description" },
    "frame": "goal",
    "show_toast": true,
    "announce_to_chat": true,
    "hidden": true
  },
  "criteria": {
    "activated": { "trigger": "minecraft:impossible" }
  },
  "requirements": [ [ "activated" ] ]
}
```

> Verify the 1.21.1 advancement JSON shape via `./gradlew runData` or in-game `/advancement grant`: icon uses `{"id": ...}` (1.20.5+), and the item predicate `items` is a list of `{"items": "<id-or-#tag>"}`. Adjust if the game log reports a parse error.

- [ ] **Step 3: Add the lang keys**

In `en_us.json`:

```json
  "advancements.heirloomswordmod.soul_bound.title": "Soul-Bound",
  "advancements.heirloomswordmod.soul_bound.description": "Obtain Alucard's Sword.",
  "advancements.heirloomswordmod.a_will_of_its_own.title": "It flies?",
  "advancements.heirloomswordmod.a_will_of_its_own.description": "Awaken the sword into flight for the first time.",
```

- [ ] **Step 4: Award `a_will_of_its_own` on first activation**

In `SwordModePacket.handle`, inside the `if (firstAwakening)` block from Task 11, after setting the component, award the advancement:

```java
                if (firstAwakening) {
                    held.set(ModDataComponents.AWAKENED.get(), true);
                    familiar.setAwakening(true);
                    var adv = player.server.getAdvancements().get(
                            ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "a_will_of_its_own"));
                    if (adv != null) {
                        player.getAdvancements().award(adv, "activated");
                    }
                }
```

Add the import if missing: `import com.alucard.heirloomsword.HeirloomSwordMod;` (the package wildcard `com.alucard.heirloomsword.*` at the top of `SwordModePacket` already covers it; `ResourceLocation` is already imported).

> `MinecraftServer#getAdvancements().get(ResourceLocation)` returns an `AdvancementHolder`; `PlayerAdvancements#award(AdvancementHolder, String)` grants the named criterion. Verify both via `search_mappings` if they do not resolve.

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/data/heirloomswordmod/advancement/ src/main/java/com/alucard/heirloomsword/network/SwordModePacket.java src/main/resources/assets/heirloomswordmod/lang/en_us.json
git commit -m "feat(phase13): Soul-Bound + It flies? advancements (25.12c)"
```

---

## Task 13: In-game verification matrix (§25.13)

**Files:** none (manual `./gradlew runClient` test).

- [ ] **Step 1: Run the client and walk the matrix**

Run: `./gradlew runClient`, then verify in order:
- **Config:** edit `config/heirloomswordmod-common.toml` (e.g. `quickFireDamage = 30.0`, `consumeMana = false`), reload the world; quick-fire hits harder and flying actions cost no mana.
- **Localization:** no untranslated `tooltip.*` / `advancements.*` / `msg.*` keys appear as raw strings.
- **Tooltip:** both lore lines show italic dark-purple.
- **PvP:** with `/gamerule` server PvP off (or `allowPvpDamage=false`), launched/quick-fire/sweep hits do not damage another player; with PvP on and `allowPvpDamage=true`, they do; mobs always take damage.
- **Death drop:** drop the sword in lava (survives), leave it (never despawns), drop it into the void (teleports to spawn).
- **keepInventory:** `keepInventory true` + `/kill` while flying → item kept, familiar gone, mode reset.
- **Spectator:** `/gamemode spectator` while flying → familiar despawns, mode resets.
- **Sculk:** place a sculk sensor; launch and embed near it → it triggers; `sculkResonance=false` → silent.
- **Awakening:** first-ever F on a fresh sword → slow descent + one orbit; second activation → fast sky-drop.
- **Advancements:** obtaining the sword grants *Soul-Bound*; first flight grants *It flies?*.

- [ ] **Step 2: Record results**

Note any failures against the responsible task and fix before closing Phase 13.

---

## Self-Review

- **Spec coverage:** 25.1 → Tasks 1-3; 25.2 → Task 4; 25.3 → Task 5; 25.5 → Task 6; 25.7 → Task 7; 25.8 → Task 8; 25.10 → Task 9; 25.11 → Task 10; 25.12b → Task 11; 25.12c → Task 12; 25.13 → Task 13. 25.4/25.6/25.9/25.12a are out of scope (closed/dropped) per the guardrails.
- **Type consistency:** `canDamage(Player, LivingEntity)` defined in Task 6 and reused in Task 11. Accessor names in Task 2 (`launchDamageNormal()` …) match their use-sites. `setAwakening(boolean)` defined in Task 11 Step 2 and called in Task 11 Step 4. `AWAKENED` component defined in Task 11 Step 1 and read in Tasks 11/12.
- **Ordering:** Config (1) precedes all readers. Task 11's `tickArriving` rewrite includes the Task 6 `canDamage` guard — if executed out of order, drop that one line until Task 6 lands.
