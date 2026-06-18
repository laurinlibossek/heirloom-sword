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
                .defineInRange("sweepReleaseDamage", 14.0, 0.0, 1024.0);
        BLOCK_SLASH_DAMAGE = BUILDER.comment("Counter-slash damage from a successful guard.")
                .defineInRange("blockSlashDamage", 12.0, 0.0, 1024.0);
        LANDING_IMPACT_DAMAGE = BUILDER.comment("Sky-drop landing impact AoE damage on spawn.")
                .defineInRange("landingImpactDamage", 14.0, 0.0, 1024.0);
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
