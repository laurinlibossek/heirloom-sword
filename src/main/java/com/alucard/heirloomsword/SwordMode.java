package com.alucard.heirloomsword;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum SwordMode implements StringRepresentable {
    NORMAL("normal"),
    FLYING("flying");

    public static final Codec<SwordMode> CODEC = StringRepresentable.fromEnum(SwordMode::values);
    public static final StreamCodec<ByteBuf, SwordMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> i == 0 ? NORMAL : FLYING, m -> m == NORMAL ? 0 : 1);

    private final String name;

    SwordMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
