package com.sward.gtcables.network.clientbound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record RemoveCablePacket(ResourceLocation dimension, long id, boolean burned)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(dimension);
		buf.writeVarLong(id);
		buf.writeBoolean(burned);
	}

	public static RemoveCablePacket decode(FriendlyByteBuf buf)
	{
		return new RemoveCablePacket(
			buf.readResourceLocation(),
			buf.readVarLong(),
			buf.readBoolean()
		);
	}
}
