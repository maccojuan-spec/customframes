package com.customframes.client;

import com.customframes.CustomFrameBlockEntity;
import com.customframes.Hashes;
import com.customframes.Payloads;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Pantalla para elegir la imagen y el tamano del cuadro. */
@Environment(EnvType.CLIENT)
public class FrameScreen extends Screen {
    private final BlockPos pos;
    private final List<Path> files = new ArrayList<>();
    private int fileIndex = -1;

    private TextFieldWidget fileField;
    private TextFieldWidget widthField;
    private TextFieldWidget heightField;

    private Text status = Text.empty();
    private boolean busy = false;
    private int top = 0;

    private String existingHash = "";
    private int existingW = 2;
    private int existingH = 2;

    public FrameScreen(BlockPos pos) {
        super(Text.literal("Cuadro personalizado"));
        this.pos = pos;
    }

    public static Path imageFolder() {
        return FabricLoader.getInstance().getGameDir().resolve("customframes");
    }

    @Override
    protected void init() {
        String prevFile = fileField != null ? fileField.getText() : "";
        String prevW = widthField != null ? widthField.getText() : null;
        String prevH = heightField != null ? heightField.getText() : null;

        scanFolder();

        if (this.client != null && this.client.world != null
                && this.client.world.getBlockEntity(pos) instanceof CustomFrameBlockEntity be) {
            existingHash = be.imageHash;
            existingW = be.frameW;
            existingH = be.frameH;
        }

        int cx = this.width / 2;
        top = Math.max(8, this.height / 2 - 100);

        fileField = new TextFieldWidget(this.textRenderer, cx - 150, top + 26, 300, 20, Text.literal("Imagen"));
        fileField.setMaxLength(600);
        fileField.setText(prevFile);
        addDrawableChild(fileField);

        addDrawableChild(ButtonWidget.builder(Text.literal("< Anterior"), b -> cycle(-1))
                .dimensions(cx - 150, top + 50, 148, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Siguiente >"), b -> cycle(1))
                .dimensions(cx + 2, top + 50, 148, 20).build());

        widthField = new TextFieldWidget(this.textRenderer, cx - 150, top + 92, 100, 20, Text.literal("Ancho"));
        widthField.setMaxLength(2);
        widthField.setText(prevW != null ? prevW : String.valueOf(existingW));
        addDrawableChild(widthField);

        heightField = new TextFieldWidget(this.textRenderer, cx - 40, top + 92, 100, 20, Text.literal("Alto"));
        heightField.setMaxLength(2);
        heightField.setText(prevH != null ? prevH : "");
        addDrawableChild(heightField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Aplicar"), b -> apply())
                .dimensions(cx - 150, top + 122, 148, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Quitar imagen"), b -> clearImage())
                .dimensions(cx + 2, top + 122, 148, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cerrar"), b -> close())
                .dimensions(cx - 150, top + 146, 300, 20).build());
    }

    private void scanFolder() {
        files.clear();
        Path dir = imageFolder();
        try {
            Files.createDirectories(dir);
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(Files::isRegularFile)
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                                    || n.endsWith(".gif") || n.endsWith(".bmp");
                        })
                        .sorted()
                        .forEach(files::add);
            }
        } catch (IOException ignored) {
        }
    }

    private void cycle(int delta) {
        if (files.isEmpty()) {
            status = Text.literal("La carpeta customframes esta vacia. Pone tus imagenes ahi.");
            return;
        }
        if (fileIndex < 0) {
            fileIndex = delta > 0 ? 0 : files.size() - 1;
        } else {
            fileIndex = (fileIndex + delta + files.size()) % files.size();
        }
        fileField.setText(files.get(fileIndex).getFileName().toString());
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(Payloads.MAX_BLOCKS, v));
    }

    private Path resolvePath(String raw) {
        try {
            String s = raw.trim();
            if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
                s = s.substring(1, s.length() - 1);
            }
            Path p = Path.of(s);
            return p.isAbsolute() ? p : imageFolder().resolve(s);
        } catch (Exception e) {
            return null;
        }
    }

    private void apply() {
        if (busy) {
            return;
        }
        String raw = fileField.getText().trim();
        int w = clamp(parseInt(widthField.getText(), 2));
        int manualH = parseInt(heightField.getText(), -1); // -1 = automatico

        // Sin archivo: solo cambiar el tamano de la imagen que ya tiene el cuadro.
        if (raw.isEmpty()) {
            if (existingHash.isEmpty()) {
                status = Text.literal("Elegi una imagen primero.");
                return;
            }
            int h = manualH > 0 ? clamp(manualH) : existingH;
            ClientPlayNetworking.send(new Payloads.SetFrame(pos, existingHash, w, h));
            close();
            return;
        }

        final Path path = resolvePath(raw);
        if (path == null || !Files.isRegularFile(path)) {
            status = Text.literal("No encuentro ese archivo.");
            return;
        }

        busy = true;
        status = Text.literal("Procesando imagen...");
        MinecraftClient mc = MinecraftClient.getInstance();
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return ImagePrep.prepare(path);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                })
                .whenComplete((res, err) -> mc.execute(() -> onPrepared(res, err, w, manualH)));
    }

    private void onPrepared(ImagePrep.Result res, Throwable err, int w, int manualH) {
        busy = false;
        if (err != null) {
            Throwable cause = err.getCause() != null ? err.getCause() : err;
            status = Text.literal("Error: " + cause.getMessage());
            return;
        }

        int h = manualH > 0 ? clamp(manualH) : clamp((int) Math.round(w * (double) res.height() / res.width()));
        byte[] png = res.png();
        String hash = Hashes.sha256(png);

        ClientImages.registerLocal(hash, png);

        int total = (png.length + Payloads.CHUNK - 1) / Payloads.CHUNK;
        for (int i = 0; i < total; i++) {
            int from = i * Payloads.CHUNK;
            int to = Math.min(png.length, from + Payloads.CHUNK);
            ClientPlayNetworking.send(new Payloads.UploadChunk(hash, i, total, Arrays.copyOfRange(png, from, to)));
        }
        ClientPlayNetworking.send(new Payloads.SetFrame(pos, hash, w, h));

        if (this.client != null && this.client.currentScreen == this) {
            close();
        }
    }

    private void clearImage() {
        int w = clamp(parseInt(widthField.getText(), existingW));
        int h = clamp(parseInt(heightField.getText(), existingH));
        ClientPlayNetworking.send(new Payloads.SetFrame(pos, "", w, h));
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title.asOrderedText(), cx, top, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Imagen: nombre en la carpeta o ruta completa").asOrderedText(), cx - 150, top + 14, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Ancho (bloques, 1-16)").asOrderedText(), cx - 150, top + 80, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Alto (vacio = automatico)").asOrderedText(), cx - 40, top + 80, 0xFFAAAAAA);

        context.drawCenteredTextWithShadow(this.textRenderer, status.asOrderedText(), cx, top + 172, 0xFFFFD866);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Carpeta: .minecraft/customframes (" + files.size() + " imagenes)").asOrderedText(),
                cx, top + 186, 0xFF888888);
    }
}
