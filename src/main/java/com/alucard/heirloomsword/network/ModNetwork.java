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
        registrar.playToServer(
                SwordLaunchPacket.TYPE,
                SwordLaunchPacket.STREAM_CODEC,
                SwordLaunchPacket::handle
        );
        registrar.playToServer(
                SwordRecallPacket.TYPE,
                SwordRecallPacket.STREAM_CODEC,
                SwordRecallPacket::handle
        );
        registrar.playToServer(
                SwordChargePacket.TYPE,
                SwordChargePacket.STREAM_CODEC,
                SwordChargePacket::handle
        );
        registrar.playToServer(
                SwordSweepPacket.TYPE,
                SwordSweepPacket.STREAM_CODEC,
                SwordSweepPacket::handle
        );
        registrar.playToServer(
                SwordMomentumPacket.TYPE,
                SwordMomentumPacket.STREAM_CODEC,
                SwordMomentumPacket::handle
        );
    }
}
