package com.alucard.heirloomsword.network;

import com.alucard.heirloomsword.HeirloomSwordItem;
import com.alucard.heirloomsword.HeirloomSwordMod;
import com.alucard.heirloomsword.SwordFamiliarEntity;
import com.alucard.heirloomsword.SwordMode;
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
            SwordMode next = current == SwordMode.NORMAL ? SwordMode.FLYING : SwordMode.NORMAL;
            HeirloomSwordItem.setMode(held, next);

            ServerLevel level = player.serverLevel();
            if (next == SwordMode.FLYING) {
                SwordFamiliarEntity familiar = new SwordFamiliarEntity(level, player);
                level.addFreshEntity(familiar);
            } else {
                SwordFamiliarEntity.despawnForOwner(level, player.getUUID());
            }
        });
    }
}
