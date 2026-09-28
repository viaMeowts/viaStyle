package com.viameowts.viastyle.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Raw plugin message on the {@code viastyle:net} channel, exchanged with the
 * viaStyle Velocity plugin. The body is UTF-8 JSON (see {@link Network}).
 */
public record NetPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<NetPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("viastyle", "net"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.data),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new NetPayload(bytes);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
