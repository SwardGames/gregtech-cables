package com.sward.gtwires.network.serverbound;

import com.sward.gtwires.WireEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record CutEdgePacket(long id)
{
	public void encode(FriendlyByteBuf buf)
	{
		buf.writeVarLong(id);
	}

	public static CutEdgePacket decode(FriendlyByteBuf buf)
	{
		return new CutEdgePacket(buf.readVarLong());
	}

	public static void handle(CutEdgePacket msg, Supplier<NetworkEvent.Context> context)
	{
		ServerPlayer player = context.get().getSender();

		if (player != null)
		{
			WireEvents.cut(player, msg.id());
		}
	}
}
