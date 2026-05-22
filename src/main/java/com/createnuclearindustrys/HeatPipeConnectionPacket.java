package com.createnuclearindustrys;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HeatPipeConnectionPacket(long posA, long posB, boolean connected)
        implements CustomPacketPayload {

    public static final Type<HeatPipeConnectionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    CreateNuclearIndustrys.MODID, "heat_pipe_connection"));

    public static final StreamCodec<FriendlyByteBuf, HeatPipeConnectionPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> { buf.writeLong(pkt.posA); buf.writeLong(pkt.posB); buf.writeBoolean(pkt.connected); },
                    buf -> new HeatPipeConnectionPacket(buf.readLong(), buf.readLong(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
