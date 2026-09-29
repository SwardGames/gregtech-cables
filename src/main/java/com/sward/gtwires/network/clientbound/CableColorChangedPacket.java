package com.sward.gtwires.network.clientbound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record CableColorChangedPacket(ResourceLocation dimension, long id, int color)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(dimension);
		buf.writeVarLong(id);
		buf.writeInt(color);
	}

	public static CableColorChangedPacket decode(FriendlyByteBuf buf)
	{
		return new CableColorChangedPacket(buf.readResourceLocation(), buf.readVarLong(), buf.readInt());
	}
}
