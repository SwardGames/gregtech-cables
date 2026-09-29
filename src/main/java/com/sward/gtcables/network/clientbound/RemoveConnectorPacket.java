package com.sward.gtcables.network.clientbound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record RemoveConnectorPacket(ResourceLocation dimension, long id)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(this.dimension);
		buf.writeVarLong(this.id);
	}

	public static RemoveConnectorPacket decode(FriendlyByteBuf buf)
	{
		return new RemoveConnectorPacket(buf.readResourceLocation(), buf.readVarLong());
	}
}
