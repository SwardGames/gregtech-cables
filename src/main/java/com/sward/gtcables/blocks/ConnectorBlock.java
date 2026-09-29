package com.sward.gtcables.blocks;

import com.sward.gtcables.graph.CableNetwork;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.NotNull;

public final class ConnectorBlock extends BaseEntityBlock implements SimpleWaterloggedBlock
{
	public static final DirectionProperty FACING = BlockStateProperties.FACING;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	private static final VoxelShape UP_SHAPE = Shapes.join(box(6, 1, 6, 10, 6, 10), box(4, 0, 4, 12, 1, 12), BooleanOp.OR);
	private static final VoxelShape DOWN_SHAPE = Shapes.join(box(6, 10, 6, 10, 15, 10), box(4, 15, 4, 12, 16, 12), BooleanOp.OR);
	private static final VoxelShape NORTH_SHAPE = Shapes.join(box(6, 6, 10, 10, 10, 15), box(4, 4, 15, 12, 12, 16), BooleanOp.OR);
	private static final VoxelShape SOUTH_SHAPE = Shapes.join(box(6, 6, 1, 10, 10, 6), box(4, 4, 0, 12, 12, 1), BooleanOp.OR);
	private static final VoxelShape EAST_SHAPE = Shapes.join(box(1, 6, 6, 6, 10, 10), box(0, 4, 4, 1, 12, 12), BooleanOp.OR);
	private static final VoxelShape WEST_SHAPE = Shapes.join(box(10, 6, 6, 16, 10, 10), box(15, 4, 4, 16, 12, 12), BooleanOp.OR);

	public ConnectorBlock()
	{
		super(Properties.of().strength(1.5F, 6).noOcclusion());

		registerDefaultState(
			this.stateDefinition.any()
				.setValue(FACING, Direction.UP)
				.setValue(WATERLOGGED, false)
		);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b)
	{
		b.add(FACING, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext c)
	{
		FluidState fluid = c.getLevel().getFluidState(c.getClickedPos());

		return defaultBlockState()
			.setValue(FACING, c.getClickedFace())
			.setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
	}

	@Override
	public @NotNull FluidState getFluidState(BlockState state)
	{
		return state.getValue(WATERLOGGED)
			? Fluids.WATER.getSource(false)
			: super.getFluidState(state);
	}

	@Override
	public @NotNull BlockState updateShape(
		BlockState state,
		@NotNull Direction direction,
		@NotNull BlockState neighbourState,
		@NotNull LevelAccessor level,
		@NotNull BlockPos pos,
		@NotNull BlockPos neighbourPos
	)
	{
		if (state.getValue(WATERLOGGED))
		{
			level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}

		return super.updateShape(state, direction, neighbourState, level, pos, neighbourPos);
	}

	@Override
	public @NotNull RenderShape getRenderShape(@NotNull BlockState s)
	{
		return RenderShape.MODEL;
	}

	@Override
	public @NotNull VoxelShape getShape(
		@NotNull BlockState s,
		@NotNull BlockGetter l,
		@NotNull BlockPos p,
		@NotNull CollisionContext c
	)
	{
		switch (s.getValue(FACING))
		{
			case UP ->
			{
				return UP_SHAPE;
			}
			case DOWN ->
			{
				return DOWN_SHAPE;
			}
			case NORTH ->
			{
				return NORTH_SHAPE;
			}
			case SOUTH ->
			{
				return SOUTH_SHAPE;
			}
			case EAST ->
			{
				return EAST_SHAPE;
			}
			case WEST ->
			{
				return WEST_SHAPE;
			}
		}

		return UP_SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos p, @NotNull BlockState s)
	{
		return new ConnectorEntity(p, s);
	}

	@Override
	public void onRemove(BlockState old, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving)
	{
		if (!old.is(next.getBlock()) && level instanceof ServerLevel server)
		{
			CableNetwork.get(server).removeConnector(pos);
		}

		super.onRemove(old, level, pos, next, moving);
	}

	@Override
	public PushReaction getPistonPushReaction(BlockState state)
	{
		return PushReaction.BLOCK;
	}
}
