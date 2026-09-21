package com.customframes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Lado servidor: recibe imagenes, las guarda en la carpeta del mundo y las reparte a quien las pida. */
public final class ServerImages {
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

    private ServerImages() {}

    private static final class Upload {
        final String hash;
        final byte[][] parts;
        int received = 0;
        int bytes = 0;

        Upload(String hash, int total) {
            this.hash = hash;
            this.parts = new byte[total][];
        }
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.UploadChunk.ID,
                (payload, context) -> onUpload(payload, context.player(), context.server()));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.SetFrame.ID,
                (payload, context) -> onSetFrame(payload, context.player(), context.server()));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.RequestImage.ID,
                (payload, context) -> onRequest(payload, context.player(), context.server()));
    }

    private static Path dir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("customframes");
    }

    private static void onUpload(Payloads.UploadChunk p, ServerPlayerEntity player, MinecraftServer server) {
        String hash = p.hash();
        int maxChunks = Payloads.MAX_BYTES / Payloads.CHUNK + 2;
        if (!Hashes.isValid(hash) || p.total() < 1 || p.total() > maxChunks
                || p.index() < 0 || p.index() >= p.total() || p.data().length > Payloads.CHUNK) {
            return;
        }

        UUID who = player.getUuid();
        Upload up = UPLOADS.get(who);
        if (up == null || !up.hash.equals(hash) || up.parts.length != p.total()) {
            up = new Upload(hash, p.total());
            UPLOADS.put(who, up);
        }

        if (up.parts[p.index()] == null) {
            up.parts[p.index()] = p.data();
            up.received++;
            up.bytes += p.data().length;
        }
        if (up.bytes > Payloads.MAX_BYTES) {
            UPLOADS.remove(who);
            player.sendMessage(Text.literal("La imagen es demasiado grande."), true);
            return;
        }
        if (up.received < up.parts.length) {
            return;
        }

        UPLOADS.remove(who);
        byte[] all = new byte[up.bytes];
        int off = 0;
        for (byte[] part : up.parts) {
            System.arraycopy(part, 0, all, off, part.length);
            off += part.length;
        }

        boolean isPng = all.length > 8 && (all[0] & 0xFF) == 0x89 && all[1] == 'P' && all[2] == 'N' && all[3] == 'G';
        if (!isPng || !Hashes.sha256(all).equals(hash)) {
            player.sendMessage(Text.literal("La imagen llego danada. Proba de nuevo."), true);
            return;
        }

        try {
            Path d = dir(server);
            Files.createDirectories(d);
            Path file = d.resolve(hash + ".png");
            if (!Files.exists(file)) {
                Files.write(file, all);
            }
        } catch (IOException e) {
            CustomFramesMod.LOGGER.error("No pude guardar la imagen {}", hash, e);
            player.sendMessage(Text.literal("El servidor no pudo guardar la imagen."), true);
        }
    }

    private static void onSetFrame(Payloads.SetFrame p, ServerPlayerEntity player, MinecraftServer server) {
        BlockPos pos = p.pos();
        double dx = player.getX() - (pos.getX() + 0.5);
        double dy = player.getY() - (pos.getY() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > 24.0 * 24.0) {
            return;
        }

        World world = player.getEntityWorld();
        if (!(world.getBlockEntity(pos) instanceof CustomFrameBlockEntity frame)) {
            return;
        }

        String hash = p.hash();
        if (!hash.isEmpty()) {
            if (!Hashes.isValid(hash) || !Files.isRegularFile(dir(server).resolve(hash + ".png"))) {
                player.sendMessage(Text.literal("El servidor no tiene esa imagen."), true);
                return;
            }
        }

        int w = Math.max(1, Math.min(Payloads.MAX_BLOCKS, p.w()));
        int h = Math.max(1, Math.min(Payloads.MAX_BLOCKS, p.h()));
        frame.setFrame(hash, w, h);
    }

    private static void onRequest(Payloads.RequestImage p, ServerPlayerEntity player, MinecraftServer server) {
        String hash = p.hash();
        if (!Hashes.isValid(hash)) {
            return;
        }
        Path file = dir(server).resolve(hash + ".png");
        if (!Files.isRegularFile(file)) {
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            return;
        }
        int total = (bytes.length + Payloads.CHUNK - 1) / Payloads.CHUNK;
        for (int i = 0; i < total; i++) {
            int from = i * Payloads.CHUNK;
            int to = Math.min(bytes.length, from + Payloads.CHUNK);
            ServerPlayNetworking.send(player, new Payloads.ImageChunk(hash, i, total, Arrays.copyOfRange(bytes, from, to)));
        }
    }
}
