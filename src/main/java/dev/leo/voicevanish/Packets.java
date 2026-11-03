package dev.leo.voicevanish;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class Packets {
    private Packets() {}
    public record Speech(String text) implements CustomPayload {
        public static final Id<Speech> ID = new Id<>(Identifier.of("voicevanish", "speech"));
        public static final PacketCodec<RegistryByteBuf, Speech> CODEC = PacketCodec.tuple(PacketCodecs.string(512), Speech::text, Speech::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record State(boolean armed) implements CustomPayload {
        public static final Id<State> ID = new Id<>(Identifier.of("voicevanish", "state"));
        public static final PacketCodec<RegistryByteBuf, State> CODEC = PacketCodec.tuple(PacketCodecs.BOOLEAN, State::armed, State::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}
