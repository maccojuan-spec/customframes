package com.customframes;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Placa finita pegada a una pared. La imagen se dibuja en el frente (ver FrameRenderer).
 * FACING = hacia donde mira el frente del cuadro.
 */
public class CustomFrameBlock extends BlockWithEntity {
    public static final MapCodec<CustomFrameBlock> CODEC = createCodec(CustomFrameBlock::new);
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

    // La placa queda del lado de la pared (opuesto al frente). Grosor: 1 pixel.
    private static final VoxelShape SHAPE_NORTH = Block.createCuboidShape(0, 0, 15, 16, 16, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.createCuboidShape(0, 0, 0, 16, 16, 1);
    private static final VoxelShape SHAPE_EAST = Block.createCuboidShape(0, 0, 0, 1, 16, 16);
    private static final VoxelShape SHAPE_WEST = Block.createCuboidShape(15, 0, 0, 16, 16, 16);

    /** Lo setea el cliente para abrir la pantalla de edicion (asi este codigo no toca clases de cliente). */
    public static Consumer<BlockPos> clientOpen = null;

    public CustomFrameBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CustomFrameBlockEntity(pos, state);
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction side = ctx.getSide();
        Direction facing = side.getAxis().isHorizontal() ? side : ctx.getHorizontalPlayerFacing().getOpposite();
        return getDefaultState().with(FACING, facing);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_WEST;
        };
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.isSneaking()) {
            return ActionResult.PASS; // agachado: se comporta como bloque normal
        }
        if (world.isClient() && clientOpen != null) {
            clientOpen.accept(pos);
        }
        return ActionResult.SUCCESS;
    }
}
