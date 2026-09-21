package com.customframes.client;

import com.customframes.CustomFrameBlock;
import com.customframes.CustomFrameBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

/** Dibuja la imagen como un rectangulo plano delante de la placa. */
@Environment(EnvType.CLIENT)
public class FrameRenderer implements BlockEntityRenderer<CustomFrameBlockEntity, BlockEntityRenderState> {
    private static final int FULL_LIGHT = 0xF000F0;

    public FrameRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public BlockEntityRenderState createRenderState() {
        return new BlockEntityRenderState();
    }

    @Override
    public void render(BlockEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        if (!(world.getBlockEntity(state.pos) instanceof CustomFrameBlockEntity frame)) {
            return;
        }
        if (frame.imageHash.isEmpty()) {
            return;
        }
        Identifier texture = ClientImages.textureFor(frame.imageHash);
        if (texture == null) {
            return; // todavia descargando
        }

        Direction facing = frame.getCachedState().get(CustomFrameBlock.FACING);
        final float nx = facing.getOffsetX(); // hacia donde mira el frente
        final float nz = facing.getOffsetZ();
        final float rx = nz;                  // "derecha" vista desde el frente
        final float rz = -nx;
        final float w = frame.frameW;
        final float h = frame.frameH;

        // El plano del frente esta a 1/16 de la pared; lo adelantamos 0.002 para evitar z-fighting.
        final float d = -0.4375f + 0.002f;
        // Esquina inferior-izquierda (vista desde el frente) y esquina inferior-derecha.
        final float x0 = 0.5f + nx * d - rx * 0.5f;
        final float z0 = 0.5f + nz * d - rz * 0.5f;
        final float x1 = x0 + rx * w;
        final float z1 = z0 + rz * w;

        queue.submitCustom(matrices, RenderLayer.getEntityTranslucent(texture), (entry, consumer) -> {
            put(consumer, entry, x0, 0f, z0, 0f, 1f);
            put(consumer, entry, x1, 0f, z1, 1f, 1f);
            put(consumer, entry, x1, h, z1, 1f, 0f);
            put(consumer, entry, x0, h, z0, 0f, 0f);
        });
    }

    private static void put(VertexConsumer c, MatrixStack.Entry e, float x, float y, float z, float u, float v) {
        // normal hacia arriba = brillo completo en cualquier pared
        c.vertex(e, x, y, z)
                .color(0xFFFFFFFF)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_LIGHT)
                .normal(e, 0f, 1f, 0f);
    }

    @Override
    public boolean rendersOutsideBoundingBox() {
        return true; // el cuadro puede ser mucho mas grande que su bloque
    }

    @Override
    public int getRenderDistance() {
        return 128;
    }
}
