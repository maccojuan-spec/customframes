package com.customframes.client;

import com.customframes.CustomFramesMod;
import com.customframes.Hashes;
import com.customframes.Payloads;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** Cache de texturas en el cliente + descarga desde el servidor cuando falta una imagen. */
@Environment(EnvType.CLIENT)
public final class ClientImages {
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static final Map<String, Long> LAST_REQUEST = new HashMap<>();
    private static final Map<String, Download> DOWNLOADS = new HashMap<>();

    private ClientImages() {}

    private static final class Download {
        final byte[][] parts;
        int received = 0;
        int bytes = 0;

        Download(int total) {
            this.parts = new byte[total][];
        }
    }

    private static Path cacheDir() {
        return FabricLoader.getInstance().getGameDir().resolve("customframes").resolve("cache");
    }

    /** Devuelve la textura lista, o null si todavia se esta descargando (y la pide al servidor). */
    public static Identifier textureFor(String hash) {
        Identifier id = TEXTURES.get(hash);
        if (id != null) {
            return id;
        }
        if (!Hashes.isValid(hash)) {
            return null;
        }

        long now = System.currentTimeMillis();
        Long last = LAST_REQUEST.get(hash);
        if (last != null && now - last < 8000) {
            return null;
        }
        LAST_REQUEST.put(hash, now);

        // 1) cache en disco de sesiones anteriores
        Path cached = cacheDir().resolve(hash + ".png");
        if (Files.isRegularFile(cached)) {
            try {
                registerPng(hash, Files.readAllBytes(cached));
                return TEXTURES.get(hash);
            } catch (Exception e) {
                try {
                    Files.deleteIfExists(cached);
                } catch (IOException ignored) {
                }
            }
        }

        // 2) pedirla al servidor
        try {
            ClientPlayNetworking.send(new Payloads.RequestImage(hash));
        } catch (Exception e) {
            CustomFramesMod.LOGGER.warn("No pude pedir la imagen {}", hash, e);
        }
        return null;
    }

    /** Para la imagen que acabamos de elegir nosotros: se ve al instante y queda en cache. */
    public static void registerLocal(String hash, byte[] png) {
        try {
            if (!TEXTURES.containsKey(hash)) {
                registerPng(hash, png);
            }
            saveCache(hash, png);
        } catch (Exception e) {
            CustomFramesMod.LOGGER.warn("No pude registrar la imagen local", e);
        }
    }

    /** Llega un trozo desde el servidor. */
    public static void onChunk(Payloads.ImageChunk p) {
        String hash = p.hash();
        if (TEXTURES.containsKey(hash) || !Hashes.isValid(hash)) {
            return;
        }
        int maxChunks = Payloads.MAX_BYTES / Payloads.CHUNK + 2;
        if (p.total() < 1 || p.total() > maxChunks || p.index() < 0 || p.index() >= p.total()) {
            return;
        }

        Download dl = DOWNLOADS.get(hash);
        if (dl == null || dl.parts.length != p.total()) {
            dl = new Download(p.total());
            DOWNLOADS.put(hash, dl);
        }
        if (dl.parts[p.index()] == null) {
            dl.parts[p.index()] = p.data();
            dl.received++;
            dl.bytes += p.data().length;
        }
        if (dl.received < dl.parts.length) {
            return;
        }

        DOWNLOADS.remove(hash);
        byte[] all = new byte[dl.bytes];
        int off = 0;
        for (byte[] part : dl.parts) {
            System.arraycopy(part, 0, all, off, part.length);
            off += part.length;
        }
        if (!Hashes.sha256(all).equals(hash)) {
            CustomFramesMod.LOGGER.warn("La imagen {} llego corrupta", hash);
            return;
        }
        try {
            registerPng(hash, all);
            saveCache(hash, all);
        } catch (Exception e) {
            CustomFramesMod.LOGGER.warn("No pude leer la imagen {}", hash, e);
        }
    }

    private static void registerPng(String hash, byte[] png) throws IOException {
        NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
        Identifier id = Identifier.of(CustomFramesMod.MOD_ID, "img/" + hash);
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, new NativeImageBackedTexture(() -> "customframes/" + hash, image));
        TEXTURES.put(hash, id);
    }

    private static void saveCache(String hash, byte[] png) {
        try {
            Files.createDirectories(cacheDir());
            Path file = cacheDir().resolve(hash + ".png");
            if (!Files.exists(file)) {
                Files.write(file, png);
            }
        } catch (IOException e) {
            CustomFramesMod.LOGGER.warn("No pude guardar la cache", e);
        }
    }
}
