package com.sward.gtwires.network.clientbound;

import com.sward.gtwires.network.WireNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.Map;

public record SyncGraphPacket(ResourceLocation dimension, NodeData[] nodes, EdgeData[] edges)
{
	public record NodeData(long id, Direction direction)
	{
	}

	public record EdgeData(long id, BlockPos a, BlockPos b, ResourceLocation wire)
	{
	}

	public static SyncGraphPacket create(ServerLevel level, Map<Long, Direction> nodeMap, Collection<WireNetwork.Link> links)
	{
		int nodeIdx = 0;
		NodeData[] nodes = new NodeData[nodeMap.size()];
		for (Map.Entry<Long, Direction> node : nodeMap.entrySet())
		{
			nodes[nodeIdx++] = new NodeData(node.getKey(), node.getValue());
		}

		int edgeIdx = 0;
		EdgeData[] edges = new EdgeData[links.size()];
		for (WireNetwork.Link link : links)
		{
			edges[edgeIdx++] = new EdgeData(link.id(), link.a(), link.b(), link.wire());
		}

		return new SyncGraphPacket(level.dimension().location(), nodes, edges);
	}

	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(dimension);

		buf.writeVarInt(nodes.length);

		for (NodeData node : nodes)
		{
			buf.writeVarLong(node.id);
			buf.writeEnum(node.direction);
		}

		buf.writeVarInt(edges.length);

		for (EdgeData edge : edges)
		{
			buf.writeVarLong(edge.id);
			buf.writeBlockPos(edge.a);
			buf.writeBlockPos(edge.b);
			buf.writeResourceLocation(edge.wire);
		}
	}

	public static SyncGraphPacket decode(FriendlyByteBuf buf)
	{
		ResourceLocation dimension = buf.readResourceLocation();

		int nodeCount = buf.readVarInt();
		NodeData[] nodes = new NodeData[nodeCount];

		for (int i = 0; i < nodeCount; ++i)
		{
			nodes[i] = new NodeData(buf.readVarLong(), buf.readEnum(Direction.class));
		}

		int edgeCount = buf.readVarInt();
		EdgeData[] edges = new EdgeData[edgeCount];

		for (int i = 0; i < edgeCount; ++i)
		{
			edges[i] = new EdgeData(
				buf.readVarLong(),
				buf.readBlockPos(),
				buf.readBlockPos(),
				buf.readResourceLocation()
			);
		}

		return new SyncGraphPacket(dimension, nodes, edges);
	}
}
