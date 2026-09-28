package com.sward.gtwires.network;

import com.sward.gtwires.GregTechWires;
import com.sward.gtwires.client.WireClient;
import com.sward.gtwires.network.clientbound.*;
import com.sward.gtwires.network.serverbound.CutEdgePacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class WirePackets
{
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
		GregTechWires.id("wires"),
		() -> "100",
		"100"::equals,
		"100"::equals
	);

	public static void init()
	{
		int id = 0;

		// Clientbound

		CHANNEL.messageBuilder(SyncGraphPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(SyncGraphPacket::encode)
			.decoder(SyncGraphPacket::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> WireClient.syncGraph(msg)
			))
			.add();

		CHANNEL.messageBuilder(ResetGraphPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(ResetGraphPacket::encode)
			.decoder(ResetGraphPacket::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> WireClient.resetGraphPacket(msg)
			))
			.add();

		CHANNEL.messageBuilder(AddNodePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(AddNodePacket::encode)
			.decoder(AddNodePacket::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> WireClient.addNodePacket(msg)
			))
			.add();

		CHANNEL.messageBuilder(AddEdgePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(AddEdgePacket::encode)
			.decoder(AddEdgePacket::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> WireClient.addEdgePacket(msg)
			))
			.add();

		CHANNEL.messageBuilder(RemoveNodePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(RemoveNodePacket::encode)
			.decoder(RemoveNodePacket::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> WireClient.removeNodePacket(msg)
			))
			.add();

		CHANNEL.messageBuilder(RemoveEdgePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(RemoveEdgePacket::encode)
			.decoder(RemoveEdgePacket::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> WireClient.removeEdgePacket(msg)
			))
			.add();

		// Serverbound

		CHANNEL.messageBuilder(CutEdgePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
			.encoder(CutEdgePacket::encode)
			.decoder(CutEdgePacket::decode)
			.consumerMainThread(CutEdgePacket::handle)
			.add();
	}
}
