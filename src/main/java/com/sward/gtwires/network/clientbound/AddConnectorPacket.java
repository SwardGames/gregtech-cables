package com.sward.gtwires.network.clientbound;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record AddConnectorPacket(ResourceLocation dimension, long id, Direction direction)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(dimension);
		buf.writeVarLong(id);
		buf.writeEnum(direction);
	}

	public static AddConnectorPacket decode(FriendlyByteBuf buf)
	{
		return new AddConnectorPacket(buf.readResourceLocation(), buf.readVarLong(), buf.readEnum(Direction.class));
	}
}
