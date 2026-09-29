package com.sward.gtwires.network.clientbound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record RemoveConnectorPacket(ResourceLocation dimension, long id)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(dimension);
		buf.writeVarLong(id);
	}

	public static RemoveConnectorPacket decode(FriendlyByteBuf buf)
	{
		return new RemoveConnectorPacket(buf.readResourceLocation(), buf.readVarLong());
	}
}
