package com.sward.gtwires.network;

import com.sward.gtwires.GregTechWires;
import com.sward.gtwires.WireEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class WirePackets
{
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
		GregTechWires.id("wires"),
		() -> "2",
		"2"::equals,
		"2"::equals
	);

	public static void init()
	{
		CHANNEL.messageBuilder(Update.class, 0, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(Update::encode)
			.decoder(Update::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> com.sward.gtwires.client.WireClient.update(msg)
			))
			.add();

		CHANNEL.messageBuilder(Cut.class, 1, NetworkDirection.PLAY_TO_SERVER)
			.encoder((m, b) -> b.writeLong(m.id))
			.decoder(b -> new Cut(b.readLong())).consumerMainThread(Cut::handle).add();
	}

	public record Update(
		ResourceLocation dimension,
		byte operation,
		long id,
		BlockPos a,
		BlockPos b,
		ResourceLocation wire,
		int cm
	)
	{
		public static Update of(ServerLevel level, WireNetwork.Link e, boolean remove)
		{
			return new Update(
				level.dimension().location(),
				(byte) (remove ? 2 : 1),
				e.id(),
				e.a(),
				e.b(),
				e.wire(),
				e.cm()
			);
		}

		public static Update burn(ServerLevel level, WireNetwork.Link e)
		{
			return new Update(level.dimension().location(), (byte) 3, e.id(), e.a(), e.b(), e.wire(), e.cm());
		}

		public static Update reset(ServerLevel level)
		{
			return new Update(
				level.dimension().location(),
				(byte) 0,
				0,
				BlockPos.ZERO,
				BlockPos.ZERO,
				GregTechWires.id("empty"),
				0
			);
		}

		public void encode(FriendlyByteBuf b)
		{
			b.writeResourceLocation(dimension);
			b.writeByte(operation);
			b.writeLong(id);
			b.writeBlockPos(a);
			b.writeBlockPos(this.b);
			b.writeResourceLocation(wire);
			b.writeVarInt(cm);
		}

		public static Update decode(FriendlyByteBuf b)
		{
			return new Update(
				b.readResourceLocation(),
				b.readByte(),
				b.readLong(),
				b.readBlockPos(),
				b.readBlockPos(),
				b.readResourceLocation(),
				b.readVarInt()
			);
		}
	}

	public record Cut(long id)
	{
		static void handle(Cut msg, Supplier<NetworkEvent.Context> context)
		{
			ServerPlayer player = context.get().getSender();

			if (player != null)
			{
				WireEvents.cut(player, msg.id);
			}
		}
	}
}
