package com.sward.gtcables.graph;

import com.sward.gtcables.CableType;
import com.sward.gtcables.util.PlayerHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
	private final Long2ObjectMap<Direction> connectors = new Long2ObjectOpenHashMap<>();

	// All cables in the graph
	private final Long2ObjectMap<Cable> cables = new Long2ObjectOpenHashMap<>();

	// All cables connected to a given block position
	private final Long2ObjectMap<List<Cable>> adjacency = new Long2ObjectOpenHashMap<>();

	// Maps chunks to cables whose bounds overlaps that chunk
	private final Long2ObjectMap<Set<Cable>> chunkCables = new Long2ObjectOpenHashMap<>();

	// Maps cables to the list of chunks their bounds overlaps with
	private final Long2ObjectMap<long[]> cableChunks = new Long2ObjectOpenHashMap<>();

	public static int lengthCm(BlockPos a, BlockPos b)
	{
		long dx = b.getX() - a.getX();
		long dy = b.getY() - a.getY();
		long dz = b.getZ() - a.getZ();

		double length = Math.sqrt(dx * dx + dy * dy + dz * dz) * 100;

		return Math.max(1, (int) Math.ceil(length - 1e-9D));
	}

	public Collection<Cable> cables()
	{
		return Collections.unmodifiableCollection(this.cables.values());
	}

	public boolean hasConnector(long id)
	{
		return this.connectors.containsKey(id);
	}

	public @Nullable Direction getConnector(long id)
	{
		return this.connectors.get(id);
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
		return this.connectors;
	}

	public boolean hasCable(long id)
	{
		return this.cables.containsKey(id);
	}

	public boolean hasCable(BlockPos a, BlockPos b)
	{
		return hasCable(a.asLong(), b.asLong());
	}

	public boolean hasCable(long aId, long bId)
	{
		return this.adjacency.getOrDefault(aId, List.of())
			.stream()
			.anyMatch(e -> e.other(aId) == bId);
	}

	public @Nullable Cable getCable(long id)
	{
		return this.cables.get(id);
	}

	public Collection<Cable> getAdjacentCables(long node)
	{
		return this.adjacency.getOrDefault(node, List.of());
	}

	public boolean addConnector(long id, Direction direction)
	{
		if (this.connectors.get(id) == direction)
		{
			return false;
		}

		this.connectors.put(id, direction);

		return true;
	}

	public boolean removeConnector(long id)
	{
		return this.connectors.remove(id) != null;
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
		if (!this.connectors.containsKey(aId) || !this.connectors.containsKey(bId))
		{
			return null;
		}

		Cable cable = new Cable(id, a, b, cableType, cm, color);

		this.cables.put(cable.id, cable);

		this.adjacency.computeIfAbsent(aId, k -> new ArrayList<>()).add(cable);
		this.adjacency.computeIfAbsent(bId, k -> new ArrayList<>()).add(cable);

		long[] chunks = crossedChunks(getConnectorPosition(a), getConnectorPosition(b));

		for (long chunkId : chunks)
		{
			this.chunkCables.computeIfAbsent(chunkId, k -> new HashSet<>()).add(cable);
		}

		this.cableChunks.put(cable.id, chunks);

		return cable;
	}

	public @Nullable Cable removeCable(long id)
	{
		Cable cable = this.cables.remove(id);

		if (cable == null)
		{
			return null;
		}

		for (long node : new long[]{cable.aId, cable.bId})
		{
			List<Cable> list = this.adjacency.get(node);
			list.remove(cable);

			if (list.isEmpty())
			{
				this.adjacency.remove(node);
			}
		}

		long[] chunkIds = this.cableChunks.remove(cable.id);

		if (chunkIds != null)
		{
			for (long chunk : chunkIds)
			{
				Set<Cable> chunkWires = this.chunkCables.get(chunk);

				chunkWires.remove(cable);

				if (chunkWires.isEmpty())
				{
					this.chunkCables.remove(chunk);
				}
			}
		}

		return cable;
	}

	public void clear()
	{
		this.connectors.clear();
		this.cables.clear();
		this.adjacency.clear();
		this.chunkCables.clear();
		this.cableChunks.clear();
	}

	public void forEachCableInBounds(AABB bounds, Consumer<@NotNull Cable> cableConsumer)
	{
		int chunkX0 = SectionPos.posToSectionCoord(Mth.floor(bounds.minX));
		int chunkX1 = SectionPos.posToSectionCoord(Mth.ceil(bounds.maxX));
		int chunkZ0 = SectionPos.posToSectionCoord(Mth.floor(bounds.minZ));
		int chunkZ1 = SectionPos.posToSectionCoord(Mth.ceil(bounds.maxZ));

		Set<Cable> checkedCables = new HashSet<>();

		for (int chunkX = chunkX0; chunkX <= chunkX1; ++chunkX)
		{
			for (int chunkZ = chunkZ0; chunkZ <= chunkZ1; ++chunkZ)
			{
				Set<Cable> cables = this.chunkCables.get(getChunkId(chunkX, chunkZ));

				if (cables == null)
				{
					continue;
				}

				for (Cable cable : cables)
				{
					if (checkedCables.add(cable) && cable.bounds.intersects(bounds))
					{
						cableConsumer.accept(cable);
					}
				}
			}
		}
	}

	public @Nullable Cable findCable(AABB bounds, Predicate<@NotNull Cable> predicate)
	{
		int chunkX0 = SectionPos.posToSectionCoord(Mth.floor(bounds.minX));
		int chunkX1 = SectionPos.posToSectionCoord(Mth.ceil(bounds.maxX));
		int chunkZ0 = SectionPos.posToSectionCoord(Mth.floor(bounds.minZ));
		int chunkZ1 = SectionPos.posToSectionCoord(Mth.ceil(bounds.maxZ));

		Set<Cable> checkedCables = new HashSet<>();

		for (int chunkX = chunkX0; chunkX <= chunkX1; ++chunkX)
		{
			for (int chunkZ = chunkZ0; chunkZ <= chunkZ1; ++chunkZ)
			{
				Set<Cable> cables = this.chunkCables.get(getChunkId(chunkX, chunkZ));

				if (cables == null)
				{
					continue;
				}

				for (Cable cable : cables)
				{
					if (checkedCables.add(cable) && cable.bounds.intersects(bounds))
					{
						if (predicate.test(cable))
						{
							return cable;
						}
					}
				}
			}
		}

		return null;
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

		forEachCableInBounds(
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

	private static long getChunkId(int chunkX, int chunkZ)
	{
		return ((long) chunkZ << 32) | Integer.toUnsignedLong(chunkX);
	}

	private static long[] crossedChunks(Vec3 a, Vec3 b)
	{
		LongSet chunks = new LongOpenHashSet();

		Vec3 flatA = new Vec3(a.x, 0D, a.z);
		Vec3 flatB = new Vec3(b.x, 0D, b.z);

		Vec3 gridA = new Vec3(a.x / 16D, 0.5D, a.z / 16D);
		Vec3 gridB = new Vec3(b.x / 16D, 0.5D, b.z / 16D);

		// Ensures that the endpoints are handled.
		collectChunks(chunks, flatA, flatB, Mth.floor(gridA.x), Mth.floor(gridA.z));
		collectChunks(chunks, flatA, flatB, Mth.floor(gridB.x), Mth.floor(gridB.z));

		BlockGetter.traverseBlocks(
			gridA, gridB, chunks,
			(result, cell) ->
			{
				collectChunks(result, flatA, flatB, cell.getX(), cell.getZ());

				return null;
			},
			result -> null
		);

		return chunks.toLongArray();
	}

	private static void collectChunks(LongSet chunks, Vec3 a, Vec3 b, int chunkX, int chunkZ)
	{
		double padding = 0.5D + 1e-7D;

		for (int x = chunkX - 1; x <= chunkX + 1; x++)
		{
			for (int z = chunkZ - 1; z <= chunkZ + 1; z++)
			{
				AABB bounds = new AABB(
					x * 16D - padding,
					-1,
					z * 16D - padding,

					(x + 1) * 16D + padding,
					1,
					(z + 1) * 16D + padding
				);

				if (bounds.contains(a) || bounds.contains(b) || bounds.clip(a, b).isPresent())
				{
					chunks.add(getChunkId(x, z));
				}
			}
		}
	}
}
