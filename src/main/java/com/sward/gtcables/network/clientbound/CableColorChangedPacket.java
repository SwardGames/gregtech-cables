package com.sward.gtcables.network.clientbound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record CableColorChangedPacket(ResourceLocation dimension, long id, int color)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(this.dimension);
		buf.writeVarLong(this.id);
		buf.writeInt(this.color);
	}

	public static CableColorChangedPacket decode(FriendlyByteBuf buf)
	{
		return new CableColorChangedPacket(buf.readResourceLocation(), buf.readVarLong(), buf.readInt());
	}
}
