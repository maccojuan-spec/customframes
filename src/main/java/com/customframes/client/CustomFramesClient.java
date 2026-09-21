package com.customframes.client;

import com.customframes.CustomFrameBlock;
import com.customframes.CustomFramesMod;
import com.customframes.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

@Environment(EnvType.CLIENT)
public class CustomFramesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererFactories.register(CustomFramesMod.FRAME_BLOCK_ENTITY, FrameRenderer::new);

        CustomFrameBlock.clientOpen = pos -> MinecraftClient.getInstance().setScreen(new FrameScreen(pos));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.ImageChunk.ID,
                (payload, context) -> ClientImages.onChunk(payload));
    }
}
