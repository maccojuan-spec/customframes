package com.customframes.client;

import com.customframes.Payloads;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Lee una imagen del disco (PNG/JPG/GIF/BMP), la reduce a como maximo 512 px y la convierte a PNG. */
@Environment(EnvType.CLIENT)
public final class ImagePrep {
    static {
        System.setProperty("java.awt.headless", "true");
    }

    public record Result(byte[] png, int width, int height) {}

    private ImagePrep() {}

    public static Result prepare(Path file) throws IOException {
        BufferedImage src = ImageIO.read(file.toFile());
        if (src == null) {
            throw new IOException("Formato no soportado (usa PNG, JPG, GIF o BMP)");
        }

        int maxDim = 512;
        while (true) {
            BufferedImage scaled = scale(src, maxDim);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(scaled, "png", out)) {
                throw new IOException("No pude convertir la imagen a PNG");
            }
            byte[] bytes = out.toByteArray();
            if (bytes.length <= Payloads.MAX_BYTES - 50_000 || maxDim <= 64) {
                return new Result(bytes, scaled.getWidth(), scaled.getHeight());
            }
            maxDim = (int) (maxDim * 0.75);
        }
    }

    private static BufferedImage scale(BufferedImage src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        double k = Math.min(1.0, (double) maxDim / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * k));
        int th = Math.max(1, (int) Math.round(h * k));
        int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;

        BufferedImage cur = src;
        // Reducir a la mitad varias veces sale mucho mas nitido que un solo salto grande.
        while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
            cur = draw(cur, cur.getWidth() / 2, cur.getHeight() / 2, type);
        }
        if (cur.getWidth() != tw || cur.getHeight() != th || cur.getType() != type) {
            cur = draw(cur, tw, th, type);
        }
        return cur;
    }

    private static BufferedImage draw(BufferedImage in, int w, int h, int type) {
        BufferedImage out = new BufferedImage(w, h, type);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(in, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
