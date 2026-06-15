package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwordQuickFirePacket() implements CustomPacketPayload {
    public static final Type<SwordQuickFirePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_quick_fire"));

    public static final StreamCodec<ByteBuf, SwordQuickFirePacket> STREAM_CODEC =
            StreamCodec.unit(new SwordQuickFirePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwordQuickFirePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (ManaService.isLockedOut(player)) return; // inputs locked during depletion punishment

            // No held-item check: the familiar only exists in flying mode and is matched by owner
            // UUID, so its presence authorises the quick-fire even when the sword isn't selected
            // (lets the player loose it as a quick defense while holding/using something else).
            ServerLevel level = player.serverLevel();
            SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(level, player.getUUID());
            if (familiar == null) return;
            if (familiar.getState() != FamiliarState.HOVERING) return;

            familiar.quickFire(); // validates target + cooldown internally
        });
    }
}
