package com.sward.gtcables.network.clientbound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record AddCablePacket(ResourceLocation dimension, long id, BlockPos a, BlockPos b, ResourceLocation wireType, int color)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(this.dimension);
		buf.writeVarLong(this.id);
		buf.writeBlockPos(this.a);
		buf.writeBlockPos(this.b);
		buf.writeResourceLocation(this.wireType);
		buf.writeInt(this.color);
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
