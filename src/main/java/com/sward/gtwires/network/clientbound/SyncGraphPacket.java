package com.sward.gtwires.network.clientbound;

import com.sward.gtwires.graph.Cable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.Map;

public record SyncGraphPacket(ResourceLocation dimension, ConnectorData[] connectors, CableData[] cables)
{
	public record ConnectorData(long id, Direction direction)
	{
	}

	public record CableData(long id, BlockPos a, BlockPos b, ResourceLocation wireType, int color)
	{
	}

	public static SyncGraphPacket create(ServerLevel level, Map<Long, Direction> connectorsMap, Collection<Cable> cablesCollection)
	{
		int connectorIdx = 0;
		ConnectorData[] connectors = new ConnectorData[connectorsMap.size()];
		for (Map.Entry<Long, Direction> connector : connectorsMap.entrySet())
		{
			connectors[connectorIdx++] = new ConnectorData(connector.getKey(), connector.getValue());
		}

		int cableIdx = 0;
		CableData[] cables = new CableData[cablesCollection.size()];
		for (Cable cable : cablesCollection)
		{
			cables[cableIdx++] = new CableData(cable.id, cable.a, cable.b, cable.wireType.id(), cable.color);
		}

		return new SyncGraphPacket(level.dimension().location(), connectors, cables);
	}

	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(dimension);

		buf.writeVarInt(connectors.length);

		for (ConnectorData connector : connectors)
		{
			buf.writeVarLong(connector.id);
			buf.writeEnum(connector.direction);
		}

		buf.writeVarInt(cables.length);

		for (CableData cable : cables)
		{
			buf.writeVarLong(cable.id);
			buf.writeBlockPos(cable.a);
			buf.writeBlockPos(cable.b);
			buf.writeResourceLocation(cable.wireType);
			buf.writeInt(cable.color);
		}
	}

	public static SyncGraphPacket decode(FriendlyByteBuf buf)
	{
		ResourceLocation dimension = buf.readResourceLocation();

		int connectorCount = buf.readVarInt();
		ConnectorData[] connectors = new ConnectorData[connectorCount];

		for (int i = 0; i < connectorCount; ++i)
		{
			connectors[i] = new ConnectorData(buf.readVarLong(), buf.readEnum(Direction.class));
		}

		int cablesCount = buf.readVarInt();
		CableData[] cables = new CableData[cablesCount];

		for (int i = 0; i < cablesCount; ++i)
		{
			cables[i] = new CableData(
				buf.readVarLong(),
				buf.readBlockPos(),
				buf.readBlockPos(),
				buf.readResourceLocation(),
				buf.readInt()
			);
		}

		return new SyncGraphPacket(dimension, connectors, cables);
	}
}
