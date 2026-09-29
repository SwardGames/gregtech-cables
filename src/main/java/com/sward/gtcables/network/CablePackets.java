package com.sward.gtcables.network;

import com.sward.gtcables.GregTechCables;
import com.sward.gtcables.client.ClientCableNetwork;
import com.sward.gtcables.network.clientbound.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CablePackets
{
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
		GregTechCables.id("wires"),
		() -> "1",
		"1"::equals,
		"1"::equals
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
				() -> () -> ClientCableNetwork.syncGraph(msg)
			))
			.add();

		CHANNEL.messageBuilder(AddConnectorPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(AddConnectorPacket::encode)
			.decoder(AddConnectorPacket::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> ClientCableNetwork.addConnectorPacket(msg)
			))
			.add();

		CHANNEL.messageBuilder(AddCablePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(AddCablePacket::encode)
			.decoder(AddCablePacket::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> ClientCableNetwork.addCablePacket(msg)
			))
			.add();

		CHANNEL.messageBuilder(RemoveConnectorPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(RemoveConnectorPacket::encode)
			.decoder(RemoveConnectorPacket::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> ClientCableNetwork.removeConnectorPacket(msg)
			))
			.add();

		CHANNEL.messageBuilder(RemoveCablePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(RemoveCablePacket::encode)
			.decoder(RemoveCablePacket::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> ClientCableNetwork.removeCablePacket(msg)
			))
			.add();

		CHANNEL.messageBuilder(CableColorChangedPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(CableColorChangedPacket::encode)
			.decoder(CableColorChangedPacket::decode)
			.consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(
				Dist.CLIENT,
				() -> () -> ClientCableNetwork.cableColorChangedPacket(msg)
			))
			.add();

		// Serverbound
	}
}
