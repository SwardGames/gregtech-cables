package com.sward.gtwires.network.clientbound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ResetGraphPacket(ResourceLocation dimension)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(dimension);
	}

	public static ResetGraphPacket decode(FriendlyByteBuf buf)
	{
		return new ResetGraphPacket(buf.readResourceLocation());
	}
}
