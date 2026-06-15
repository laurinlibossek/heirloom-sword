# Phase 9 — Epic Fight Compatibility (Datapack, No Dependency) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Heirloom Sword use Epic Fight's **greatsword** moveset/combat in normal mode when Epic Fight is installed, with a vanilla-sword fallback when it is not — achieved entirely through a bundled Epic Fight item-capability datapack file, with **no build dependency and no Java code**.

**Architecture:** Epic Fight reads "item capability" JSON files from every loaded datapack, including the `data/` folder of any installed mod. We ship a single capability file at `data/heirloomswordmod/capabilities/weapons/heirloom_sword.json` that assigns our item the built-in `epicfight:greatsword` weapon type. Epic Fight consumes it only when present; when Epic Fight is absent the file is inert and the item behaves as the existing netherite sword. This satisfies "compatible, not dependent": there is no `compileOnly` Epic Fight artifact, no `ModList.isLoaded` guard, and no class that imports Epic Fight.

**Tech Stack:** NeoForge 1.21.1, Java 21, Epic Fight (runtime-only soft compat via datapack JSON, `pack_format` 48).

---

## Decision Record (read before implementing)

**This phase intentionally diverges from the written design.** Both `docs/alucard_sword_design_v3.md` (§4, §24 Phase 9) and `CLAUDE.md` ("Epic Fight Integration") specify a Java `EpicFightCompat` class guarded by `ModList.get().isLoaded("epicfight")`. That approach is **retired** for this phase because:

- A Java class that registers a weapon type must compile and class-load against Epic Fight's API (`yesman.epicfight.api.*`), which is a dependency — the exact thing the user ruled out ("compatible, not dependent").
- The datapack-capability route delivers the **same** outcome (item treated as an Epic Fight greatsword, native Epic Fight stamina, full combo moveset, no custom skills — exactly design §4) with **zero** code and **zero** dependency, and is automatically inert without Epic Fight (vanilla fallback, design §4).

Tasks 5 updates both documents so the codebase and the design stay consistent with what is actually built. If a future phase needs *code-level* Epic Fight integration (custom skills, animation events), that is a separate, dependency-bearing effort and is explicitly **out of scope** here — design §4 says "No custom skills added," so it is not needed.

**Scope guardrails (from design §24 Phase 9, DESCOPED 2026-06-13):**
- Do **not** add any mana/stamina code. Flying-mode costs are already mana (`ManaService`); normal-mode combat uses Epic Fight's own native stamina with no mod-side involvement.
- Do **not** add a `StaminaProvider` interface or hidden pool — cancelled.
- Normal mode has **no special right-click** (design §4), so the Epic Fight EDP `force_use_method` addon/tag is **not** needed and must **not** be added.

---

## File Structure

| File | Responsibility | Action |
|---|---|---|
| `src/main/resources/data/heirloomswordmod/capabilities/weapons/heirloom_sword.json` | The Epic Fight item-capability descriptor that assigns the `epicfight:greatsword` weapon type to our item. The entire functional payload of Phase 9. | **Create** |
| `src/main/templates/META-INF/neoforge.mods.toml` | Declare Epic Fight as an **optional** dependency for load-order/documentation only (does not require it). | **Modify** |
| `CLAUDE.md` | Update the "Epic Fight Integration" architecture rule to describe the datapack approach instead of an `EpicFightCompat` class. | **Modify** |
| `docs/alucard_sword_design_v3.md` | Mark Phase 9 complete and record that the datapack approach supersedes the `EpicFightCompat` class. | **Modify** |

There is **no** Java source change in this phase. There is **no** test harness in this repo (`src/test`/`src/gametest` do not exist), and Epic Fight is not a compile/test dependency, so Phase 9 is **verification-driven, not TDD-driven**: the deliverable is a data file consumed at runtime by an external mod. The real validation signals are (a) `./gradlew build` packaging the resource and (b) the in-game matrix in Task 4. Malformed capability JSON surfaces as an Epic Fight datapack-load error in the game log on world load — that is the automated parse check.

---

### Task 1: Create the Epic Fight greatsword capability file

**Files:**
- Create: `src/main/resources/data/heirloomswordmod/capabilities/weapons/heirloom_sword.json`

Path components are fixed by Epic Fight's loader: `data / <our-modid> / capabilities / weapons / <item-registry-name>.json`. Our modid is `heirloomswordmod` and the item registry name is `heirloom_sword` (from `ITEMS.register("heirloom_sword", ...)` in `HeirloomSwordMod.java:35`). A mod's own `data/` folder is loaded as a built-in datapack by NeoForge, so **no `pack.mcmeta` is required** (that is only for standalone datapacks dropped into a world's `datapacks/` folder).

- [ ] **Step 1: Write the capability file (minimal, recommended)**

This is the faithful "mimic the existing Epic Fight greatsword, add nothing custom" form (design §4). The `type` is the only required field; Epic Fight derives swing damage from the item's existing attack-damage attribute and uses the greatsword type's default colliders, combos, and innate skills.

```json
{
  "type": "epicfight:greatsword"
}
```

> **Optional tuned variant — do NOT apply unless playtesting in Task 4 asks for it.** Greatsword is a Two-Handed style, so attributes go under the `two_hand` key. These values are bonuses **added** to the type's base. Keep them for reference; ship the minimal form above first.
>
> ```json
> {
>   "type": "epicfight:greatsword",
>   "attributes": {
>     "two_hand": {
>       "armor_negation": 0.0,
>       "impact": 1.3,
>       "max_strikes": 3
>     }
>   }
> }
> ```

- [ ] **Step 2: Confirm the file is valid JSON and on the exact path**

Run (git-bash, from repo root):

```bash
test -f src/main/resources/data/heirloomswordmod/capabilities/weapons/heirloom_sword.json \
  && cat src/main/resources/data/heirloomswordmod/capabilities/weapons/heirloom_sword.json \
  && echo "PATH+CONTENT OK"
```

Expected: prints the JSON object then `PATH+CONTENT OK`. Verify the printed text contains `"type": "epicfight:greatsword"` and no trailing commas.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/data/heirloomswordmod/capabilities/weapons/heirloom_sword.json
git commit -m "feat(epicfight): assign greatsword weapon type via item-capability datapack"
```

---

### Task 2: Declare Epic Fight as an optional dependency (load-order + documentation)

**Files:**
- Modify: `src/main/templates/META-INF/neoforge.mods.toml`

This does **not** require Epic Fight (`type="optional"`). It documents the soft relationship and, when Epic Fight *is* present, asks the loader to place Epic Fight before us so our `data/` is registered cleanly during datapack reload. It is a safety/clarity nicety, not a functional requirement of the capability system.

- [ ] **Step 1: Append an optional dependency block**

Add the following at the end of the `[[dependencies.${mod_id}]]` section of `src/main/templates/META-INF/neoforge.mods.toml` (after the existing `neoforge` and `minecraft` dependency blocks):

```toml
[[dependencies.${mod_id}]]
    modId="epicfight"
    type="optional"
    reason="When present, the heirloom sword uses Epic Fight's greatsword combat via a bundled item-capability datapack."
    versionRange="*"
    ordering="AFTER"
    side="BOTH"
```

- [ ] **Step 2: Verify the build still processes the template**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. (On Windows use `gradlew.bat build`.) The processed `neoforge.mods.toml` in the built jar now lists `epicfight` as optional.

- [ ] **Step 3: Commit**

```bash
git add src/main/templates/META-INF/neoforge.mods.toml
git commit -m "chore(epicfight): declare epicfight as optional soft dependency"
```

---

### Task 3: Verify vanilla-only fallback (no Epic Fight) still builds and behaves

**Files:** none changed — this is a guard that Tasks 1–2 introduced no regression to the no-Epic-Fight path.

- [ ] **Step 1: Build the mod**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. The capability JSON is a passive resource; it cannot affect a build that has no Epic Fight on the classpath.

- [ ] **Step 2: Launch the dev client (Epic Fight NOT installed — default dev env)**

Run:

```bash
./gradlew runClient
```

Expected in-game (no Epic Fight present):
- The heirloom sword is the normal netherite-tier sword: vanilla left-click attack, the existing 12 attack damage / 1.6 attack speed (`HeirloomSwordItem.java:36`), no Epic Fight HUD, no combos.
- No errors mentioning `epicfight` or `capabilities` in the log.
- Flying mode (existing M-key toggle, mana, warp, etc.) is completely unaffected.

- [ ] **Step 3: No commit** (verification only). If a regression appears, stop and debug before proceeding — the no-Epic-Fight path is the mod's guaranteed baseline.

---

### Task 4: Manual in-game verification with Epic Fight installed

**Files:** none changed. Epic Fight runtime behavior **cannot** be unit-tested in this repo (no Epic Fight on the classpath, no test harness), so this manual matrix is the authoritative acceptance check. Record the outcome in the commit message of Task 5.

- [ ] **Step 1: Drop a matching Epic Fight build into the dev run mods folder**

Download the Epic Fight **NeoForge 1.21.1** jar (e.g. `epicfight-neoforge-21.x.y-1.21.1.jar` from [Modrinth](https://modrinth.com/mod/epic-fight) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/epic-fight-mod)) and place it in the dev run mods directory:

```bash
mkdir -p run/mods
# copy the downloaded jar into run/mods/ (do NOT commit the jar)
ls run/mods
```

Expected: the Epic Fight jar is listed. Confirm `run/` is gitignored (it is the standard NeoForge run dir); if not, do not stage it.

- [ ] **Step 2: Launch and run the acceptance matrix**

Run:

```bash
./gradlew runClient
```

Verify each row in-game with the heirloom sword in normal mode:

| Check | Expected |
|---|---|
| Item is recognized as a greatsword | Epic Fight treats the sword as the `epicfight:greatsword` archetype (two-handed; offhand disabled while held). |
| Combo moveset | Left-click chains play Epic Fight's greatsword combo animations, not vanilla swings. |
| Native stamina | Attacking/guarding consumes Epic Fight's own stamina bar; the mod's mana bar is untouched in normal mode. |
| Damage | Hits deal damage consistent with the item's 12 attack-damage attribute (Epic Fight derives from the item). |
| No log errors | No `epicfight` capability parse/load errors in the game log on world load. |
| Mode switch intact | Toggling to flying mode still works; greatsword behavior applies only in normal mode. |

- [ ] **Step 3: If a row fails, fix data only**

Only the capability JSON (Task 1) should change. Common fixes: wrong path casing, a stray trailing comma, or wanting the tuned attribute variant. Re-run Step 2 after any edit. Do **not** introduce Java or a dependency to satisfy a row.

- [ ] **Step 4: No commit** (verification only — captured in Task 5's message).

---

### Task 5: Update design doc, CLAUDE.md, and close out Phase 9

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/alucard_sword_design_v3.md`

- [ ] **Step 1: Update the CLAUDE.md Epic Fight architecture rule**

In `CLAUDE.md`, under "### Epic Fight Integration", replace the bullets that describe a dedicated `EpicFightCompat` class / `ModList.isLoaded` guard with the datapack reality:

```markdown
### Epic Fight Integration
- Normal-mode Epic Fight combat is provided by a bundled **item-capability datapack**, not Java code:
  `src/main/resources/data/heirloomswordmod/capabilities/weapons/heirloom_sword.json` assigns the
  `epicfight:greatsword` weapon type.
- This is **compatible, not dependent**: there is no Epic Fight build dependency and no class that imports
  Epic Fight. The file is read only when Epic Fight is installed; otherwise it is inert.
- Fallback: with Epic Fight absent, the item is a plain netherite-tier sword (vanilla behavior).
- Do NOT add a `compileOnly` Epic Fight artifact or an `EpicFightCompat` class unless a future phase needs
  code-level integration (custom skills/animation events), which is currently out of scope (design §4:
  "No custom skills added").
```

- [ ] **Step 2: Mark Phase 9 complete in the design doc**

In `docs/alucard_sword_design_v3.md`, in the "Phase 9 — Epic Fight Combat Integration" entry (Section 24, ~line 1195) and the registration note in Section 4 (~line 116), record completion and the approach change. Append to the Phase 9 entry:

```markdown
**Phase 9 status (2026-06-14): COMPLETE via datapack.** Implemented as a bundled Epic Fight
item-capability file (`data/heirloomswordmod/capabilities/weapons/heirloom_sword.json` →
`epicfight:greatsword`) instead of an `EpicFightCompat` Java class. This keeps the mod compatible with,
but not dependent on, Epic Fight (no build dependency, no API class-loading). Vanilla netherite-sword
fallback when Epic Fight is absent. No mod-side stamina/mana code added (normal mode uses Epic Fight's
native stamina; flying mode keeps mana).
```

- [ ] **Step 3: Update the graphify graph (project convention)**

Run:

```bash
graphify update .
```

Expected: completes without error (AST-only, no API cost). If `graphify` is unavailable, skip and note it in the commit body.

- [ ] **Step 4: Commit the close-out**

```bash
git add CLAUDE.md docs/alucard_sword_design_v3.md
git commit -m "docs(epicfight): Phase 9 complete via datapack; retire EpicFightCompat class

Verified in-game: with Epic Fight 1.21.1 installed the heirloom sword uses the
greatsword moveset and native Epic Fight stamina; without Epic Fight it is a
vanilla netherite sword. No build dependency, no Epic Fight API class-loading."
```

> Do not add a `Co-Authored-By: Claude` trailer — repository convention keeps attribution in the README, not commit trailers.

---

## Self-Review

**Spec coverage (design §4 + §24 Phase 9, DESCOPED):**
- "Registered with Epic Fight's weapon type system as a greatsword archetype" → Task 1 (`epicfight:greatsword`).
- "If Epic Fight is absent, vanilla sword behavior applies" → Task 3 (fallback) + inert-file behavior.
- "Mimics whichever Epic Fight greatsword … No custom skills added" → minimal `type`-only file; greatsword defaults supply combos/skills.
- "No mod-side stamina code" → no Java change; Decision Record + scope guardrails enforce it.
- "compatible, not dependent" (user) → no `compileOnly`/runtime Epic Fight artifact; optional-only toml entry (Task 2).

**Placeholder scan:** No TBD/TODO; every code step shows the exact file content or command; the only "optional" content (tuned attributes, in-game fixes) is explicitly gated behind Task 4 playtesting.

**Type/path consistency:** Modid `heirloomswordmod`, item registry name `heirloom_sword`, and weapon type `epicfight:greatsword` are used identically in Task 1, Task 5 docs, and the file path throughout. Capability folder is `capabilities/weapons/` in every reference.

**Divergence handled:** The design/CLAUDE.md `EpicFightCompat`-class expectation is explicitly retired in the Decision Record and reconciled in Task 5, so no documented requirement is silently dropped.
