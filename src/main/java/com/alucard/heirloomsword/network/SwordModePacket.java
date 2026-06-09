package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwordModePacket() implements CustomPacketPayload {
    public static final Type<SwordModePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeirloomSwordMod.MODID, "sword_mode"));

    public static final StreamCodec<ByteBuf, SwordModePacket> STREAM_CODEC =
            StreamCodec.unit(new SwordModePacket());

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
                // Validate: only allow exit from HOVERING
                SwordFamiliarEntity familiar = SwordFamiliarEntity.findForOwner(level, player.getUUID());
                if (familiar != null && familiar.getState() != FamiliarState.HOVERING) {
                    return; // F is locked during active states
                }
                HeirloomSwordItem.setMode(held, SwordMode.NORMAL);
                SwordFamiliarEntity.despawnForOwner(level, player.getUUID());
            } else {
                HeirloomSwordItem.setMode(held, SwordMode.FLYING);
                SwordFamiliarEntity familiar = new SwordFamiliarEntity(level, player);
                level.addFreshEntity(familiar);
            }
        });
    }
}
