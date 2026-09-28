package com.sward.gtwires.network.clientbound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record RemoveNodePacket(ResourceLocation dimension, long id)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(dimension);
		buf.writeVarLong(id);
	}

	public static RemoveNodePacket decode(FriendlyByteBuf buf)
	{
		return new RemoveNodePacket(buf.readResourceLocation(), buf.readVarLong());
	}
}
