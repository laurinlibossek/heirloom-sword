package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwordModePacket() implements CustomPacketPayload {
    public static final Type<SwordModePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_mode"));

    public static final StreamCodec<ByteBuf, SwordModePacket> STREAM_CODEC =
            StreamCodec.unit(new SwordModePacket());

    /**
     * Re-entry cooldown into flying mode, in ticks (40 = 2s). Deliberately a hardcoded constant,
     * NOT a config value: it is an anti-abuse guard against spamming the sky-drop entrance, so it
     * must not be weakened or disabled server-side. A single shared constant also guarantees the
     * client prediction can never drift from the server timer (no config-sync hole).
     */
    public static final int MODE_SWITCH_COOLDOWN_TICKS = 40;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof HeirloomSwordItem)) return;

            SwordMode current = HeirloomSwordItem.getMode(held);
            ServerLevel level = player.serverLevel();

            if (current == SwordMode.FLYING) {
                // Validate: allow exit from HOVERING or SWEEPING_HOLD (emergency exit)
                SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(level, player.getUUID());
                if (familiar != null
                        && familiar.getState() != FamiliarState.HOVERING
                        && familiar.getState() != FamiliarState.SWEEPING_HOLD
                        && familiar.getState() != FamiliarState.BLOCKING) {
                    return; // F is locked during other active states
                }
                HeirloomSwordItem.setMode(held, SwordMode.NORMAL);
                held.remove(ModDataComponents.FAMILIAR_UUID.get());
                SwordFamiliarEntity.despawnForOwner(level, player.getUUID());
                SwordSounds.playModeExit(player);
                player.setData(ManaAttachments.MODE_SWITCH_COOLDOWN.get(), MODE_SWITCH_COOLDOWN_TICKS);
            } else {
                // Cooldown gates ONLY the transition INTO flying mode. Exiting is always allowed
                // (it matches the client's optimistic prediction, so no desync). This throttles
                // re-summoning — notably spamming the sky-drop entrance. Silent fail + denial cue.
                if (player.getData(ManaAttachments.MODE_SWITCH_COOLDOWN.get()) > 0) {
                    SwordSounds.playDenied(player);
                    return;
                }
                if (player.isPassenger()) {
                    player.displayClientMessage(
                            Component.translatable("msg.heirloomswordmod.no_mount"), true);
                    return;
                }
                if (player.isFallFlying()) {
                    player.displayClientMessage(
                            Component.translatable("msg.heirloomswordmod.no_enter_elytra"), true);
                    return;
                }
                if (player.getPose() == Pose.SWIMMING) {
                    player.displayClientMessage(
                            Component.translatable("msg.heirloomswordmod.no_enter_swimming"), true);
                    return;
                }
                boolean alreadyFlying = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.getItem() instanceof HeirloomSwordItem && HeirloomSwordItem.isFlying(stack)) {
                        alreadyFlying = true;
                        break;
                    }
                }
                if (alreadyFlying || SwordFamiliarEntity.findForOwner(level, player.getUUID()) != null) {
                    return;
                }
                HeirloomSwordItem.setMode(held, SwordMode.FLYING);
                boolean firstAwakening = !held.getOrDefault(ModDataComponents.AWAKENED.get(), false);
                SwordFamiliarEntity familiar = new SwordFamiliarEntity(level, player);
                if (firstAwakening) {
                    held.set(ModDataComponents.AWAKENED.get(), true);
                    // Only the sky-drop entrance can be ceremonial; if it materialized (no clearance)
                    // there is nothing to slow — the flag is harmless either way.
                    familiar.setAwakening(true);
                    var adv = player.server.getAdvancements().get(
                            ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "a_will_of_its_own"));
                    if (adv != null) {
                        player.getAdvancements().award(adv, "activated");
                    }
                }
                level.addFreshEntity(familiar);
                held.set(ModDataComponents.FAMILIAR_UUID.get(), familiar.getUUID());
            }
        });
    }
}
