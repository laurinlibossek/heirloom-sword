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
    public static final ModConfigSpec.DoubleValue BLOODLUST_DAMAGE_MULT;
    public static final ModConfigSpec.DoubleValue TETHER_SLAM_DAMAGE;
    public static final ModConfigSpec.BooleanValue SWEEP_MOWS_PLANTS;
    public static final ModConfigSpec.BooleanValue PIN_TO_WALL;
    public static final ModConfigSpec.BooleanValue SWORD_FETCHES_ITEMS;

    // === integration ===
    public static final ModConfigSpec.BooleanValue ALLOW_PVP_DAMAGE;
    public static final ModConfigSpec.BooleanValue SCULK_RESONANCE;
    public static final ModConfigSpec.BooleanValue ENDER_MOBS_FLEE_SWORD;

    static {
        BUILDER.comment("Combat balance — damage values, cooldowns, and the mana master switch.")
                .push("combat");
        LAUNCH_DAMAGE_NORMAL = BUILDER.comment("Damage from an uncharged launched sword (outbound).")
                .defineInRange("launchDamageNormal", 14.0, 0.0, 1024.0);
        LAUNCH_DAMAGE_CHARGED = BUILDER.comment("Damage from a fully charged launched sword (outbound).")
                .defineInRange("launchDamageCharged", 32.0, 0.0, 1024.0);
        RETURN_DAMAGE = BUILDER.comment("Damage from the returning sword (inbound).")
                .defineInRange("returnDamage", 6.0, 0.0, 1024.0);
        QUICK_FIRE_DAMAGE = BUILDER.comment("Quick-fire (V) dart contact damage.")
                .defineInRange("quickFireDamage", 8.0, 0.0, 1024.0);
        SWEEP_CONTACT_DAMAGE = BUILDER.comment("Per-contact damage while the sword sweeps around the player.")
                .defineInRange("sweepContactDamage", 4.0, 0.0, 1024.0);
        SWEEP_RELEASE_DAMAGE = BUILDER.comment("Damage from the sweep release fling.")
                .defineInRange("sweepReleaseDamage", 18.0, 0.0, 1024.0);
        BLOCK_SLASH_DAMAGE = BUILDER.comment("Counter-slash damage from a successful guard.")
                .defineInRange("blockSlashDamage", 12.0, 0.0, 1024.0);
        LANDING_IMPACT_DAMAGE = BUILDER.comment("Sky-drop landing impact AoE damage on spawn.")
                .defineInRange("landingImpactDamage", 14.0, 0.0, 1024.0);
        QUICK_FIRE_COOLDOWN_TICKS = BUILDER.comment("Minimum ticks between quick-fires (30 = 1.5s).")
                .defineInRange("quickFireCooldownTicks", 30, 0, 1200);
        GUARD_BREAK_COOLDOWN_TICKS = BUILDER.comment("Guard lockout ticks after a guard break (60 = 3s).")
                .defineInRange("guardBreakCooldownTicks", 60, 0, 1200);
        UNDEAD_IGNITE_SECONDS = BUILDER.comment("Seconds an undead target burns when struck by the holy blade.")
                .defineInRange("undeadIgniteSeconds", 4.0, 0.0, 120.0);
        CONSUME_MANA = BUILDER.comment(
                        "Master mana switch. false = every flying-mode action (charge, sweep, block, warp)",
                        "is free with no drain, no minimum-cost gate, and no depletion lockout.")
                .define("consumeMana", true);
        BLOODLUST_DAMAGE_MULT = BUILDER.comment(
                        "Damage multiplier applied (in BOTH normal and flying mode) while the blade is bloodied (blood > 0).",
                        "1.0 disables the Bloodlust passive; 1.2 = +20%.")
                .defineInRange("bloodlustDamageMult", 1.2, 1.0, 4.0);
        TETHER_SLAM_DAMAGE = BUILDER.comment(
                        "AoE damage to all valid entities in a short radius when a tether pull slams the",
                        "player through an enemy.")
                .defineInRange("tetherSlamDamage", 12.0, 0.0, 1024.0);
        SWEEP_MOWS_PLANTS = BUILDER.comment(
                        "While the sword sweeps (SWEEPING_HOLD), destroy and drop foliage it passes",
                        "through (grass, flowers, bamboo, sugar cane, cobweb, vines, saplings, nether",
                        "foliage). Crops are never affected. false disables the mowing.")
                .define("sweepMowsPlants", true);
        PIN_TO_WALL = BUILDER.comment(
                        "A fully-charged launched sword that embeds in a wall pins the enemy it struck",
                        "against that wall for the duration it stays stuck (recall/tether frees them",
                        "early). Never pins players or bosses. false disables it.")
                .define("pinToWall", true);
        SWORD_FETCHES_ITEMS = BUILDER.comment(
                        "While the sword flies back to you it sweeps up dropped items it passes over",
                        "and drops them at your feet on arrival (onto the ground, never straight into",
                        "your inventory). false disables it.")
                .define("swordFetchesItems", true);
        BUILDER.pop();

        BUILDER.comment("Cross-mod / server integration toggles.").push("integration");
        ALLOW_PVP_DAMAGE = BUILDER.comment(
                        "Allow the sword to damage other players where the server already permits PvP.",
                        "false disables ALL sword-vs-player damage even when server PvP is on.")
                .define("allowPvpDamage", true);
        SCULK_RESONANCE = BUILDER.comment(
                        "Emit vibration game-events (sculk sensors / shrieker / Warden) on launch, embed, and quick-fire.")
                .define("sculkResonance", true);
        ENDER_MOBS_FLEE_SWORD = BUILDER.comment(
                        "Mobs in the #heirloomswordmod:flees_from_sword entity tag (endermen and",
                        "endermites by default) flee from the deployed flying sword. false disables it.")
                .define("enderMobsFleeSword", true);
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
