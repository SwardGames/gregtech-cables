package com.sward.gtcables.network.clientbound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record AddCablePacket(ResourceLocation dimension, long id, BlockPos a, BlockPos b, ResourceLocation wireType, int color)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(dimension);
		buf.writeVarLong(id);
		buf.writeBlockPos(a);
		buf.writeBlockPos(b);
		buf.writeResourceLocation(wireType);
		buf.writeInt(color);
	}

	public static AddCablePacket decode(FriendlyByteBuf buf)
	{
		return new AddCablePacket(
			buf.readResourceLocation(),
			buf.readVarLong(),
			buf.readBlockPos(),
			buf.readBlockPos(),
			buf.readResourceLocation(),
			buf.readInt()
		);
	}
}
