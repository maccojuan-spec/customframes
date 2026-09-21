package com.customframes;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;

public class CustomFrameBlockEntity extends BlockEntity {
    /** SHA-256 (hex) del PNG. Vacio = sin imagen. */
    public String imageHash = "";
    /** Tamano en bloques. */
    public int frameW = 2;
    public int frameH = 2;

    public CustomFrameBlockEntity(BlockPos pos, BlockState state) {
        super(CustomFramesMod.FRAME_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.putString("hash", imageHash);
        view.putInt("w", frameW);
        view.putInt("h", frameH);
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        imageHash = view.getString("hash", "");
        frameW = view.getInt("w", 2);
        frameH = view.getInt("h", 2);
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    /** Llamar solo en el servidor. */
    public void setFrame(String hash, int w, int h) {
        this.imageHash = hash;
        this.frameW = w;
        this.frameH = h;
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }
}
