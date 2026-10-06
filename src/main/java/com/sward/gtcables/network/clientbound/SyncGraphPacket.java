package com.sward.gtcables.network.clientbound;

import com.sward.gtcables.graph.Cable;
import com.sward.gtcables.graph.CableGraph;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

public record SyncGraphPacket(ResourceLocation dimension, ConnectorData[] connectors, CableData[] cables, boolean replace)
{
	public record ConnectorData(long id, Direction direction)
	{
	}

	public record CableData(long id, BlockPos a, BlockPos b, ResourceLocation wireType, int color)
	{
	}

	public static SyncGraphPacket create(ServerLevel level, CableGraph graph, LongSet cableSet, LongSet connectorSet, boolean replace)
	{
		int connectorIdx = 0;
		ConnectorData[] connectors = new ConnectorData[connectorSet.size()];
		for (long connectorId : connectorSet)
		{
			connectors[connectorIdx++] = new ConnectorData(connectorId, graph.getConnector(connectorId));
		}

		int cableIdx = 0;
		CableData[] cables = new CableData[cableSet.size()];
		for (long cableId : cableSet)
		{
			Cable cable = Objects.requireNonNull(graph.getCable(cableId));
			cables[cableIdx++] = new CableData(cableId, cable.a, cable.b, cable.cableType.id(), cable.color);
		}

		return new SyncGraphPacket(level.dimension().location(), connectors, cables, replace);
	}

	public void encode(FriendlyByteBuf buf)
	{
		buf.writeResourceLocation(this.dimension);

		buf.writeVarInt(this.connectors.length);

		for (ConnectorData connector : this.connectors)
		{
			buf.writeVarLong(connector.id);
			buf.writeEnum(connector.direction);
		}

		buf.writeVarInt(this.cables.length);

		for (CableData cable : this.cables)
		{
			buf.writeVarLong(cable.id);
			buf.writeBlockPos(cable.a);
			buf.writeBlockPos(cable.b);
			buf.writeResourceLocation(cable.wireType);
			buf.writeInt(cable.color);
		}

		buf.writeBoolean(this.replace);
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

		return new SyncGraphPacket(dimension, connectors, cables, buf.readBoolean());
	}
}
