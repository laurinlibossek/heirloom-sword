package com.alucard.heirloomsword.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                SwordModePacket.TYPE,
                SwordModePacket.STREAM_CODEC,
                SwordModePacket::handle
        );
    }
}
