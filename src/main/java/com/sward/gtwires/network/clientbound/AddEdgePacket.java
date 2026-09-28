package com.sward.gtwires.network.clientbound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record AddEdgePacket(ResourceLocation dimension, long id, BlockPos a, BlockPos b, ResourceLocation wire)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(dimension);
		buf.writeVarLong(id);
		buf.writeBlockPos(a);
		buf.writeBlockPos(b);
		buf.writeResourceLocation(wire);
	}

	public static AddEdgePacket decode(FriendlyByteBuf buf)
	{
		return new AddEdgePacket(
			buf.readResourceLocation(),
			buf.readVarLong(),
			buf.readBlockPos(),
			buf.readBlockPos(),
			buf.readResourceLocation()
		);
	}
}
