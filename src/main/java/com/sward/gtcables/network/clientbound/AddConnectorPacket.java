package com.sward.gtcables.network.clientbound;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record AddConnectorPacket(ResourceLocation dimension, long id, Direction direction)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(this.dimension);
		buf.writeVarLong(this.id);
		buf.writeEnum(this.direction);
	}

	public static AddConnectorPacket decode(FriendlyByteBuf buf)
	{
		return new AddConnectorPacket(buf.readResourceLocation(), buf.readVarLong(), buf.readEnum(Direction.class));
	}
}
