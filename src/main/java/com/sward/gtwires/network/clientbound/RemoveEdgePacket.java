package com.sward.gtwires.network.clientbound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record RemoveEdgePacket(ResourceLocation dimension, long id, boolean burned)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(dimension);
		buf.writeVarLong(id);
		buf.writeBoolean(burned);
	}

	public static RemoveEdgePacket decode(FriendlyByteBuf buf)
	{
		return new RemoveEdgePacket(
			buf.readResourceLocation(),
			buf.readVarLong(),
			buf.readBoolean()
		);
	}
}
