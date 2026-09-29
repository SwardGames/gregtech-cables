package com.sward.gtcables.graph;

import com.sward.gtcables.CableType;
import com.sward.gtcables.util.PlayerHelper;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Consumer;

/**
 * Pure server-side graph.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class CableGraph
{
	private static final Vec3[] CONNECTOR_OFFSETS = {
		new Vec3(0.50D, 0.25D, 0.50D),
		new Vec3(0.50D, 0.75D, 0.50D),
		new Vec3(0.50D, 0.50D, 0.25D),
		new Vec3(0.50D, 0.50D, 0.75D),
		new Vec3(0.25D, 0.50D, 0.50D),
		new Vec3(0.75D, 0.50D, 0.50D)
	};

	// All connectors in the graph
	private final Map<Long, Direction> connectors = new HashMap<>();

	// All cables in the graph
	private final Map<Long, Cable> cables = new HashMap<>();

	// All cables connected to a given block position
	private final Map<Long, List<Cable>> adjacency = new HashMap<>();

	// Maps chunks to cables whose bounds overlaps that chunk
	private final Map<ChunkPos, Set<Cable>> chunkCables = new HashMap<>();

	// Maps cables to the list of chunks their bounds overlaps with
	private final Map<Cable, List<ChunkPos>> cableChunks = new HashMap<>();

	public static int lengthCm(BlockPos a, BlockPos b)
	{
		int dx = b.getX() - a.getX();
		int dy = b.getY() - a.getY();
		int dz = b.getZ() - a.getZ();

		double length = Math.sqrt(dx * dx + dy * dy + dz * dz) * 100;

		return Math.max(1, (int) Math.ceil(length - 1e-9D));
	}

	public Collection<Cable> cables()
	{
		return Collections.unmodifiableCollection(cables.values());
	}

	public boolean hasConnector(long id)
	{
		return connectors.containsKey(id);
	}

	public @Nullable Direction getConnector(long id)
	{
		return connectors.get(id);
	}

	/**
	 * Returns the connector position for the given block position.
	 * Will return the center of the block if it fails to find the node.
	 */
	public Vec3 getConnectorPosition(BlockPos blockPos)
	{
		long id = blockPos.asLong();

		Vec3 pos = Vec3.atLowerCornerOf(blockPos);

		Direction connector = getConnector(id);

		if (connector != null)
		{
			pos = pos.add(CONNECTOR_OFFSETS[connector.ordinal()]);
		}

		return pos;
	}


	public Map<Long, Direction> connectors()
	{
		return connectors;
	}

	public boolean hasCable(long id)
	{
		return cables.containsKey(id);
	}

	public boolean hasCable(BlockPos a, BlockPos b)
	{
		return hasCable(a.asLong(), b.asLong());
	}

	public boolean hasCable(long aId, long bId)
	{
		return adjacency.getOrDefault(aId, List.of())
			.stream()
			.anyMatch(e -> e.other(aId) == bId);
	}

	public @Nullable Cable getCable(long id)
	{
		return cables.get(id);
	}

	public Collection<Cable> getAdjacentCables(long node)
	{
		return adjacency.getOrDefault(node, List.of());
	}

	public boolean addConnector(long id, Direction direction)
	{
		if (connectors.get(id) == direction)
		{
			return false;
		}

		connectors.put(id, direction);

		return true;
	}

	public boolean removeConnector(long id)
	{
		return connectors.remove(id) != null;
	}

	public @Nullable Cable addCable(
		long id,
		BlockPos a,
		BlockPos b,
		long aId,
		long bId,
		CableType cableType,
		int cm,
		int color
	)
	{
		// Cable with id already exists
		if (hasCable(id))
		{
			return null;
		}

		// Connectors already connected
		if (hasCable(aId, bId))
		{
			return null;
		}

		// No connector
		if (!connectors.containsKey(aId) || !connectors.containsKey(bId))
		{
			return null;
		}

		Cable cable = new Cable(id, a, b, cableType, cm, color);

		cables.put(cable.id, cable);

		adjacency.computeIfAbsent(aId, k -> new ArrayList<>()).add(cable);
		adjacency.computeIfAbsent(bId, k -> new ArrayList<>()).add(cable);

		List<ChunkPos> chunks = new ArrayList<>();

		int aChunkX = SectionPos.blockToSectionCoord(a.getX());
		int bChunkX = SectionPos.blockToSectionCoord(b.getX());
		int aChunkZ = SectionPos.blockToSectionCoord(a.getZ());
		int bChunkZ = SectionPos.blockToSectionCoord(b.getZ());

		int chunkX0 = Math.min(aChunkX, bChunkX);
		int chunkX1 = Math.max(aChunkX, bChunkX);
		int chunkZ0 = Math.min(aChunkZ, bChunkZ);
		int chunkZ1 = Math.max(aChunkZ, bChunkZ);

		for (int chunkX = chunkX0; chunkX <= chunkX1; ++chunkX)
		{
			for (int chunkZ = chunkZ0; chunkZ <= chunkZ1; ++chunkZ)
			{
				ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

				chunks.add(chunkPos);

				chunkCables.computeIfAbsent(chunkPos, k -> new HashSet<>()).add(cable);
			}
		}

		cableChunks.put(cable, chunks);

		return cable;
	}

	public @Nullable Cable removeCable(long id)
	{
		Cable cable = cables.remove(id);

		if (cable == null)
		{
			return null;
		}

		for (long node : new long[]{cable.aId, cable.bId})
		{
			List<Cable> list = adjacency.get(node);
			list.remove(cable);

			if (list.isEmpty())
			{
				adjacency.remove(node);
			}
		}

		List<ChunkPos> chunks = cableChunks.remove(cable);

		if (chunks != null)
		{
			for (ChunkPos chunk : chunks)
			{
				Set<Cable> chunkWires = chunkCables.get(chunk);

				chunkWires.remove(cable);
			}
		}

		return cable;
	}

	public void clear()
	{
		connectors.clear();
		cables.clear();
		adjacency.clear();
		chunkCables.clear();
		cableChunks.clear();
	}

	public void forEachOverlap(AABB bounds, Consumer<@NotNull Cable> cableConsumer)
	{
		int chunkX0 = SectionPos.posToSectionCoord(bounds.minX);
		int chunkX1 = SectionPos.posToSectionCoord(bounds.maxX + 1);
		int chunkZ0 = SectionPos.posToSectionCoord(bounds.minZ);
		int chunkZ1 = SectionPos.posToSectionCoord(bounds.maxZ + 1);

		for (int chunkX = chunkX0; chunkX <= chunkX1; ++chunkX)
		{
			for (int chunkZ = chunkZ0; chunkZ <= chunkZ1; ++chunkZ)
			{
				Set<Cable> cables = chunkCables.get(new ChunkPos(chunkX, chunkZ));

				if (cables == null)
				{
					continue;
				}

				for (Cable cable : cables)
				{
					if (cable.bounds.intersects(bounds))
					{
						cableConsumer.accept(cable);
					}
				}
			}
		}
	}

	/**
	 * One nearest-cable selection shared by Jade, the outline, and the cutter click.
	 */
	public @Nullable CableHitResult clip(Vec3 start, Vec3 dir, double maxDistance)
	{
		var ref = new Object()
		{
			double best = Double.POSITIVE_INFINITY;
			Cable hit = null;
		};

		forEachOverlap(
			new AABB(start, start.add(dir.scale(maxDistance))),
			c ->
			{
				double hitDist = CableGeometry.hit(
					getConnectorPosition(c.a),
					getConnectorPosition(c.b),
					start,
					dir,
					maxDistance,
					c.cableType.thickness() / 2 + 0.08D
				);

				if (hitDist < ref.best)
				{
					ref.best = hitDist;
					ref.hit = c;
				}
			}
		);

		return ref.hit == null ? null : new CableHitResult(ref.hit, start.add(dir.scale(ref.best)), ref.best);
	}

	public @Nullable CableHitResult clip(Player player)
	{
		return clip(player.getEyePosition(), player.getLookAngle(), PlayerHelper.unobstructedReach(player));
	}
}
