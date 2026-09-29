package com.sward.gtcables.blocks;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.sward.gtcables.GregTechCables;
import com.sward.gtcables.graph.CableNetwork;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

public final class ConnectorEntity extends BlockEntity implements IEnergyContainer
{
	private LazyOptional<IEnergyContainer> energy = LazyOptional.of(() -> this);

	public ConnectorEntity(BlockPos pos, BlockState state)
	{
		super(GregTechCables.CONNECTOR_ENTITY.get(), pos, state);
	}

	public Direction attachedSide()
	{
		return getBlockState().getValue(ConnectorBlock.FACING).getOpposite();
	}

	@Override
	public void onLoad()
	{
		super.onLoad();

		if (level instanceof ServerLevel server)
		{
			CableNetwork.get(server).addConnector(worldPosition, attachedSide());
		}
	}

	@Override
	public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, Direction side)
	{
		if (cap == GTCapability.CAPABILITY_ENERGY_CONTAINER && (side == null || side == attachedSide()))
		{
			return energy.cast();
		}

		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps()
	{
		super.invalidateCaps();
		energy.invalidate();
	}

	@Override
	public void reviveCaps()
	{
		super.reviveCaps();
		energy = LazyOptional.of(() -> this);
	}

	@Override
	public long acceptEnergyFromNetwork(Direction side, long voltage, long amps)
	{
		return !isRemoved() && inputsEnergy(side) && level instanceof ServerLevel server
			? CableNetwork.get(server).transfer(worldPosition, voltage, amps)
			: 0;
	}

	@Override
	public boolean inputsEnergy(Direction side)
	{
		return side == null || side == attachedSide();
	}

	@Override
	public boolean outputsEnergy(Direction side)
	{
		return inputsEnergy(side);
	}

	@Override
	public long changeEnergy(long amount)
	{
		return 0;
	}

	@Override
	public long getEnergyStored()
	{
		return 0;
	}

	@Override
	public long getEnergyCapacity()
	{
		return Long.MAX_VALUE;
	}

	@Override
	public long getInputVoltage()
	{
		return Long.MAX_VALUE;
	}

	@Override
	public long getInputAmperage()
	{
		return Integer.MAX_VALUE;
	}
}
