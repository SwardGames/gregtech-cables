package com.sward.gtcables.events;

import com.sward.gtcables.CablesConfig;
import com.sward.gtcables.GregTechCables;
import com.sward.gtcables.graph.Cable;
import com.sward.gtcables.graph.CableGraph;
import com.sward.gtcables.graph.CableNetwork;
import com.sward.gtcables.network.CablePackets;
import com.sward.gtcables.network.clientbound.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = GregTechCables.ID)
public class CableNetworkSynchronization
{
	private static final Object2ObjectMap<Player, LongSet> PLAYER_CABLES = new Object2ObjectOpenHashMap<>();
	private static final Object2ObjectMap<Player, LongSet> PLAYER_CONNECTORS = new Object2ObjectOpenHashMap<>();

	@SubscribeEvent
	public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent e)
	{
		PLAYER_CABLES.put(e.getEntity(), new LongOpenHashSet());
		PLAYER_CONNECTORS.put(e.getEntity(), new LongOpenHashSet());

		sync(e);
	}

	@SubscribeEvent
	public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent e)
	{
		PLAYER_CABLES.remove(e.getEntity());
		PLAYER_CONNECTORS.remove(e.getEntity());
	}

	@SubscribeEvent
	public static void playerDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent e)
	{
		sync(e);
	}

	@SubscribeEvent
	public static void playerRespawned(PlayerEvent.PlayerRespawnEvent e)
	{
		sync(e);
	}

	@SubscribeEvent
	public static void playerChunkLoaded(EntityEvent.EnteringSection e)
	{
		if (!e.didChunkChange())
		{
			return;
		}

		if (!(e.getEntity() instanceof ServerPlayer p))
		{
			return;
		}

		LongSet cables = PLAYER_CABLES.get(p);
		LongSet connectors = PLAYER_CONNECTORS.get(p);

		if (cables == null || connectors == null)
		{
			return;
		}

		LongSet newCables = new LongOpenHashSet();
		LongSet newConnectors = new LongOpenHashSet();

		ServerLevel level = p.serverLevel();

		CableGraph graph = CableNetwork.get(level).graph();

		graph.forEachCableInChunks(
			SectionPos.posToSectionCoord(p.getX()),
			SectionPos.posToSectionCoord(p.getZ()),
			CablesConfig.playerSyncDistance(),
			c ->
			{
				if (cables.add(c.id))
				{
					newCables.add(c.id);
				}

				if (connectors.add(c.aId))
				{
					newConnectors.add(c.aId);
				}

				if (connectors.add(c.bId))
				{
					newConnectors.add(c.bId);
				}
			}
		);

		CablePackets.CHANNEL.send(
			PacketDistributor.PLAYER.with(() -> p),
			SyncGraphPacket.create(level, graph, newCables, newConnectors, false)
		);
	}

	public static void broadcastCableAdded(ServerLevel level, CableNetwork network, Cable cable)
	{
		for (ServerPlayer p : level.players())
		{
			LongSet cables = PLAYER_CABLES.get(p);
			LongSet connectors = PLAYER_CONNECTORS.get(p);

			if (cables == null || connectors == null)
			{
				continue;
			}

			int x = SectionPos.posToSectionCoord(p.getX());
			int z = SectionPos.posToSectionCoord(p.getZ());
			int r = CablesConfig.playerSyncDistance();

			if (cable.bounds.intersects(new AABB(
				(x - r) * 16D,
				Double.NEGATIVE_INFINITY,
				(z - r) * 16D,
				(x + 1 + r) * 16D,
				Double.POSITIVE_INFINITY,
				(z + 1 + r) * 16D
			)))
			{
				cables.add(cable.id);
				connectors.add(cable.aId);
				connectors.add(cable.bId);

				CablePackets.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> p),
					new AddCablePacket(
						level.dimension().location(),
						cable.id,
						cable.a,
						cable.b,
						cable.cableType.id(),
						cable.color,
						network.getConnector(cable.a),
						network.getConnector(cable.b)
					)
				);
			}
		}
	}

	public static void broadcastCableRemoved(ServerLevel level, CableNetwork network, Cable cable, boolean burned)
	{
		for (ServerPlayer p : level.players())
		{
			LongSet cables = PLAYER_CABLES.get(p);

			if (cables == null)
			{
				continue;
			}

			if (cables.remove(cable.id))
			{
				CablePackets.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> p),
					new RemoveCablePacket(level.dimension().location(), cable.id, burned)
				);
			}
		}
	}

	public static void broadcastConnectorAdded(
		ServerLevel level,
		CableNetwork network,
		long connectorId,
		Direction connector
	)
	{
		for (ServerPlayer p : level.players())
		{
			LongSet connectors = PLAYER_CONNECTORS.get(p);

			if (connectors == null)
			{
				continue;
			}

			int x = SectionPos.posToSectionCoord(p.getX());
			int z = SectionPos.posToSectionCoord(p.getZ());
			int r = CablesConfig.playerSyncDistance();

			int connectorChunkX = SectionPos.blockToSectionCoord(BlockPos.getX(connectorId));
			int connectorChunkZ = SectionPos.blockToSectionCoord(BlockPos.getZ(connectorId));

			if (connectorChunkX >= x - r && connectorChunkX <= x + r && connectorChunkZ >= z - r && connectorChunkZ <= z + r)
			{
				connectors.add(connectorId);

				CablePackets.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> p),
					new AddConnectorPacket(level.dimension().location(), connectorId, connector)
				);
			}
		}
	}

	public static void broadcastConnectorRemoved(ServerLevel level, CableNetwork network, long connectorId)
	{
		for (ServerPlayer p : level.players())
		{
			LongSet connectors = PLAYER_CONNECTORS.get(p);

			if (connectors == null)
			{
				continue;
			}

			if (connectors.remove(connectorId))
			{
				CablePackets.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> p),
					new RemoveConnectorPacket(level.dimension().location(), connectorId)
				);
			}
		}
	}

	public static void broadcastCablePainted(ServerLevel level, CableNetwork network, Cable cable, int rgb)
	{
		for (ServerPlayer p : level.players())
		{
			LongSet cables = PLAYER_CABLES.get(p);

			if (cables == null)
			{
				continue;
			}

			if (cables.contains(cable.id))
			{
				CablePackets.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> p),
					new CableColorChangedPacket(level.dimension().location(), cable.id, rgb)
				);
			}
		}
	}

	private static void sync(PlayerEvent e)
	{
		if (e.getEntity() instanceof ServerPlayer p)
		{
			LongSet cables = PLAYER_CABLES.get(p);
			LongSet connectors = PLAYER_CONNECTORS.get(p);

			if (cables == null || connectors == null)
			{
				return;
			}

			cables.clear();
			connectors.clear();

			ServerLevel level = p.serverLevel();
			CableGraph graph = CableNetwork.get(level).graph();

			graph.forEachCableInChunks(
				SectionPos.posToSectionCoord(p.getX()),
				SectionPos.posToSectionCoord(p.getZ()),
				CablesConfig.playerSyncDistance(),
				c ->
				{
					cables.add(c.id);
					connectors.add(c.aId);
					connectors.add(c.bId);
				}
			);

			CablePackets.CHANNEL.send(
				PacketDistributor.PLAYER.with(() -> p),
				SyncGraphPacket.create(level, graph, cables, connectors, true)
			);
		}
	}
}
