package com.customframes;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Paquetes de red del mod. */
public final class Payloads {
    /** Tamano maximo de cada trozo (el limite cliente->servidor es 32767 bytes). */
    public static final int CHUNK = 30000;
    /** Tamano maximo de una imagen ya convertida a PNG. */
    public static final int MAX_BYTES = 3_000_000;
    /** Tamano maximo del cuadro en bloques. */
    public static final int MAX_BLOCKS = 16;

    private Payloads() {}

    public static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(UploadChunk.ID, UploadChunk.CODEC);
        PayloadTypeRegistry.playC2S().register(SetFrame.ID, SetFrame.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestImage.ID, RequestImage.CODEC);
        PayloadTypeRegistry.playS2C().register(ImageChunk.ID, ImageChunk.CODEC);
    }

    private static Identifier payloadId(String path) {
        return Identifier.of(CustomFramesMod.MOD_ID, path);
    }

    /** Cliente -> servidor: un trozo de la imagen. */
    public record UploadChunk(String hash, int index, int total, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<UploadChunk> ID = new CustomPayload.Id<>(payloadId("upload_chunk"));
        public static final PacketCodec<RegistryByteBuf, UploadChunk> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeString(value.hash());
                    buf.writeVarInt(value.index());
                    buf.writeVarInt(value.total());
                    buf.writeByteArray(value.data());
                },
                buf -> new UploadChunk(buf.readString(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray()));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Cliente -> servidor: configurar un cuadro (imagen + tamano). hash vacio = quitar imagen. */
    public record SetFrame(BlockPos pos, String hash, int w, int h) implements CustomPayload {
        public static final CustomPayload.Id<SetFrame> ID = new CustomPayload.Id<>(payloadId("set_frame"));
        public static final PacketCodec<RegistryByteBuf, SetFrame> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeBlockPos(value.pos());
                    buf.writeString(value.hash());
                    buf.writeVarInt(value.w());
                    buf.writeVarInt(value.h());
                },
                buf -> new SetFrame(buf.readBlockPos(), buf.readString(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Cliente -> servidor: "mandame esta imagen". */
    public record RequestImage(String hash) implements CustomPayload {
        public static final CustomPayload.Id<RequestImage> ID = new CustomPayload.Id<>(payloadId("request_image"));
        public static final PacketCodec<RegistryByteBuf, RequestImage> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeString(value.hash()),
                buf -> new RequestImage(buf.readString()));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Servidor -> cliente: un trozo de imagen. */
    public record ImageChunk(String hash, int index, int total, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<ImageChunk> ID = new CustomPayload.Id<>(payloadId("image_chunk"));
        public static final PacketCodec<RegistryByteBuf, ImageChunk> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeString(value.hash());
                    buf.writeVarInt(value.index());
                    buf.writeVarInt(value.total());
                    buf.writeByteArray(value.data());
                },
                buf -> new ImageChunk(buf.readString(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray()));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
