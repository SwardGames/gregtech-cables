package com.sward.gtwires.network;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.common.data.GTDamageTypes;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.sward.gtwires.*;
import com.sward.gtwires.blocks.ConnectorEntity;
import com.sward.gtwires.core.*;
import com.sward.gtwires.items.SpoolItem;
import com.sward.gtwires.network.clientbound.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Owned by dimension SavedData, so relay chunks never need to be loaded.
 */
public final class WireNetwork extends SavedData
{
	public record Link(long id, BlockPos a, BlockPos b, ResourceLocation wire, int cm)
	{
	}

	private final ServerLevel level;
	private final WireGraph graph = new WireGraph(this::burn, this::energize);
	private final Map<Long, Link> links = new LinkedHashMap<>();
	private final Map<Long, Direction> nodes = new HashMap<>();

	private record Exposure(CableGeometry.ContactShape shape, WireActivity activity)
	{
	}

	private final Map<Long, Exposure> exposures = new HashMap<>();
	private final Set<Long> powered = new HashSet<>();
	private long poweredTick = Long.MIN_VALUE;
	private long nextId = 1;

	private WireNetwork(ServerLevel level)
	{
		this.level = level;
	}

	public static WireNetwork get(ServerLevel level)
	{
		return level.getDataStorage()
			.computeIfAbsent(nbt -> load(level, nbt), () -> new WireNetwork(level), "gtwires_network");
	}

	public Link link(long id)
	{
		return links.get(id);
	}

	public boolean hasConnector(BlockPos pos)
	{
		return level.hasChunkAt(pos)
			? level.getBlockEntity(pos) instanceof ConnectorEntity
			: nodes.containsKey(pos.asLong());
	}

	public boolean connect(BlockPos a, BlockPos b, WireType wire, int cm)
	{
		if (!hasConnector(a) || !hasConnector(b))
		{
			return false;
		}

		if (links.values().stream().anyMatch(e -> (e.a.equals(a) && e.b.equals(b)) || (e.a.equals(b) && e.b.equals(a))))
		{
			return false;
		}

		long id = nextId;

		if (!graph.add(new WireGraph.Edge(
			id,
			a.asLong(),
			b.asLong(),
			cm,
			wire
		)))
		{
			return false;
		}

		nextId++;

		if (level.hasChunkAt(a) && level.getBlockEntity(a) instanceof ConnectorEntity ca)
		{
			addConnector(a, ca.attachedSide());
		}

		if (level.hasChunkAt(b) && level.getBlockEntity(b) instanceof ConnectorEntity cb)
		{
			addConnector(b, cb.attachedSide());
		}

		Link link = new Link(id, a.immutable(), b.immutable(), wire.id(), cm);

		cacheContact(link, wire);
		links.put(id, link);

		setDirty();
		broadcastEdgeAdded(link);

		return true;
	}

	public void remove(long id)
	{
		Link link = links.remove(id);

		if (link == null)
		{
			return;
		}

		graph.remove(id);

		setDirty();
		broadcastEdgeRemoved(link, false);

		exposures.remove(id);
		powered.remove(id);
	}

	private void burn(long id)
	{
		Link link = links.remove(id); // The graph already removed the overheated edge.

		if (link == null)
		{
			return;
		}

		exposures.remove(id);
		powered.remove(id);

		setDirty();

		broadcastEdgeRemoved(link, true);
	}

	private void cacheContact(Link link, WireType type)
	{
		if (type != null && type.shockHazard())
		{
			exposures.put(
				link.id, new Exposure(
					new CableGeometry.ContactShape(
						Vec3.atCenterOf(link.a),
						Vec3.atCenterOf(link.b),
						type.thickness() / 2
					),
					new WireActivity()
				)
			);
		}
	}

	private void energize(WireGraph.Edge edge, long voltage, long tick)
	{
		Exposure exposure = exposures.get(edge.id());

		if (exposure == null || voltage <= 0)
		{
			return;
		}

		if (poweredTick != tick)
		{
			powered.clear();
			poweredTick = tick;
		}

		exposure.activity.accept(voltage, tick);
		powered.add(edge.id());
	}

	/**
	 * One contact pass after the level finishes moving entities and accepting energy for this tick.
	 */
	public void shockPlayers()
	{
		if (poweredTick != level.getGameTime() || powered.isEmpty())
		{
			return;
		}

		for (ServerPlayer player : level.players())
		{
			shockEntity(player);
		}
	}

	void shockEntity(LivingEntity entity)
	{
		if (entity.level() != level || !entity.isAlive() || entity.isSpectator() ||
			(entity instanceof Player player && player.getAbilities().invulnerable) || poweredTick != level.getGameTime())
		{
			return;
		}

		for (long id : List.copyOf(powered))
		{
			Exposure exposure = exposures.get(id);

			if (exposure == null)
			{
				continue;
			}

			long voltage = exposure.activity.voltage(level.getGameTime());
			double amps = exposure.activity.averageAmperage(level.getGameTime());

			if (voltage > 0 && amps > 0 && exposure.shape.touches(entity.getBoundingBox()))
			{
				float damage = (float) ((GTUtil.getTierByVoltage(voltage) + 1) * amps * 4);
				entity.hurt(GTDamageTypes.ELECTRIC.source(level), damage);
			}
		}
	}

	public List<Link> attached(BlockPos pos)
	{
		return links.values().stream().filter(e -> e.a.equals(pos) || e.b.equals(pos)).toList();
	}

	public void addConnector(BlockPos pos, Direction facing)
	{
		long id = pos.asLong();

		if (nodes.containsKey(id) && nodes.get(id) == facing)
		{
			return;
		}

		nodes.put(id, facing);

		WirePackets.CHANNEL.send(
			PacketDistributor.DIMENSION.with(level::dimension),
			new AddNodePacket(level.dimension().location(), id, facing)
		);
	}

	public void removeConnector(BlockPos pos)
	{
		List<Link> detached = attached(pos);
		detached.forEach(e -> remove(e.id));

		long id = pos.asLong();

		if (nodes.remove(id) != null)
		{
			setDirty();
		}

		// Detach every span before spawning items so either endpoint can remove it only once.
		for (Link link : detached)
		{
			if (!ForgeRegistries.ITEMS.containsKey(link.wire))
			{
				continue;
			}

			ItemStack wire = new ItemStack(ForgeRegistries.ITEMS.getValue(link.wire));
			int remaining = link.cm / 100;

			while (!wire.isEmpty() && remaining > 0)
			{
				int count = Math.min(remaining, wire.getMaxStackSize());
				Block.popResource(level, pos, wire.copyWithCount(count));
				remaining -= count;
			}
		}

		WirePackets.CHANNEL.send(
			PacketDistributor.DIMENSION.with(level::dimension),
			new RemoveNodePacket(level.dimension().location(), id)
		);
	}

	public long transfer(BlockPos source, long voltage, long amps)
	{
		Direction sourceSide = nodes.get(source.asLong());

		if (sourceSide == null)
		{
			return 0;
		}

		BlockPos sourceMachine = source.relative(sourceSide);
		long revision = graph.heatRevision();

		try
		{
			return graph.transfer(
				source.asLong(), voltage, amps, level.getGameTime(), new WireGraph.Sink()
				{
					private IEnergyContainer receiver(long node)
					{
						Direction side = nodes.get(node);

						if (side == null)
						{
							return null;
						}

						BlockPos connector = BlockPos.of(node);
						BlockPos machine = connector.relative(side);

						if (machine.equals(sourceMachine) || !level.hasChunkAt(connector) || !level.hasChunkAt(machine))
						{
							return null;
						}

						if (!(level.getBlockEntity(connector) instanceof ConnectorEntity actual) || actual.attachedSide() != side)
						{
							return null;
						}

						BlockEntity be = level.getBlockEntity(machine);

						if (be == null || be instanceof ConnectorEntity)
						{
							return null;
						}

						return be.getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, side.getOpposite())
							.orElse(null);
					}

					@Override
					public boolean available(long node)
					{
						IEnergyContainer sink = receiver(node);
						return sink != null && sink.inputsEnergy(nodes.get(node)
							.getOpposite()) && sink.getEnergyCanBeInserted() > 0;
					}

					@Override
					public boolean accept(long node, long packetVoltage)
					{
						IEnergyContainer sink = receiver(node);
						return sink != null && sink.acceptEnergyFromNetwork(
							nodes.get(node).getOpposite(),
							packetVoltage,
							1
						) == 1;
					}
				}
			);
		}
		finally
		{
			if (revision != graph.heatRevision())
			{
				setDirty();
			}
		}
	}

	/**
	 * Simulate all inventory edits before committing; fractional centimetres are preserved.
	 */
	public boolean recover(ServerPlayer player, List<Link> removed, boolean commit)
	{
		Inventory inventory = player.getInventory();

		Map<Integer, ItemStack> edits = new LinkedHashMap<>();

		for (Link link : removed)
		{
			int remaining = link.cm;

			for (int pass = 0; pass < 2 && remaining > 0; pass++)
			{
				for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++)
				{
					ItemStack spool = edits.getOrDefault(i, inventory.getItem(i));

					if (i >= 36 && i != 40)
					{
						continue;
					}

					if ((SpoolItem.length(spool) > 0) != (pass == 0))
					{
						continue;
					}

					if (!spool.is(GregTechWires.SPOOL.get()) || (spool.getCount() != 1 && SpoolItem.length(spool) > 0) ||
						(SpoolItem.length(spool) > 0 && !link.wire.equals(SpoolItem.type(spool))))
					{
						continue;
					}

					int put = Math.min(remaining, SpoolMath.capacity() - SpoolItem.length(spool));

					if (put == 0)
					{
						continue;
					}

					int destination = i;

					if (spool.getCount() > 1)
					{
						destination = -1;

						for (int slot = 0; slot < 36; slot++)
						{
							if (edits.getOrDefault(slot, inventory.getItem(slot)).isEmpty())
							{
								destination = slot;
								break;
							}
						}

						if (destination < 0)
						{
							continue;
						}

						ItemStack empties = spool.copy();

						empties.shrink(1);
						edits.put(i, empties);
					}

					ItemStack copy = spool.copyWithCount(1);
					SpoolItem.set(copy, link.wire, SpoolItem.length(spool) + put);
					edits.put(destination, copy);
					remaining -= put;
				}
			}
			if (remaining > 0)
			{
				return false;
			}
		}

		if (commit)
		{
			edits.forEach(inventory::setItem);
			inventory.setChanged();
			removed.forEach(e -> remove(e.id));
			player.containerMenu.broadcastChanges();
		}

		return true;
	}

	private void broadcastEdgeAdded(Link link)
	{
		WirePackets.CHANNEL.send(
			PacketDistributor.DIMENSION.with(level::dimension),
			new AddEdgePacket(level.dimension().location(), link.id(), link.a(), link.b(), link.wire())
		);
	}

	private void broadcastEdgeRemoved(Link link, boolean burned)
	{
		WirePackets.CHANNEL.send(
			PacketDistributor.DIMENSION.with(level::dimension),
			new RemoveEdgePacket(level.dimension().location(), link.id(), burned)
		);
	}

	public void sync(ServerPlayer player)
	{
		WirePackets.CHANNEL.send(
			PacketDistributor.PLAYER.with(() -> player),
			SyncGraphPacket.create(level, nodes, links.values())
		);
	}

	@Override
	public @NotNull CompoundTag save(CompoundTag tag)
	{
		ListTag ns = new ListTag();
		ListTag es = new ListTag();

		nodes.forEach(
			(pos, side) ->
			{
				CompoundTag n = new CompoundTag();

				n.putLong("Pos", pos);
				n.putByte("Side", (byte) side.ordinal());

				ns.add(n);
			}
		);

		links.values().forEach(
			e ->
			{
				CompoundTag n = new CompoundTag();

				n.putLong("Id", e.id);
				n.putLong("A", e.a.asLong());
				n.putLong("B", e.b.asLong());
				n.putString("Wire", e.wire.toString());
				n.putInt("Cm", e.cm);

				WireGraph.Heat heat = graph.heat(e.id);

				if (heat != null)
				{
					n.putInt("Temperature", heat.temperature());
					n.putLong("LastHeatedTick", heat.lastHeatedTick());
				}

				es.add(n);
			}
		);

		tag.putInt("Version", 2);
		tag.putLong("NextId", nextId);
		tag.put("Nodes", ns);
		tag.put("Links", es);

		return tag;
	}

	static WireNetwork load(ServerLevel level, CompoundTag tag)
	{
		WireNetwork net = new WireNetwork(level);

		for (Tag t : tag.getList("Nodes", Tag.TAG_COMPOUND))
		{
			CompoundTag n = (CompoundTag) t;
			int side = n.getByte("Side");
			if (side >= 0 && side < 6)
			{
				net.nodes.put(n.getLong("Pos"), Direction.values()[side]);
			}
		}

		net.nextId = Math.max(1, tag.getLong("NextId"));

		for (Tag t : tag.getList("Links", Tag.TAG_COMPOUND))
		{
			CompoundTag n = (CompoundTag) t;

			long id = n.getLong("Id");

			if (id <= 0 || id == Long.MAX_VALUE || net.links.containsKey(id))
			{
				continue;
			}

			ResourceLocation wire = ResourceLocation.tryParse(n.getString("Wire"));

			if (wire == null)
			{
				continue;
			}

			long a = n.getLong("A");
			long b = n.getLong("B");

			if (a == b || !net.nodes.containsKey(a) || !net.nodes.containsKey(b))
			{
				GregTechWires.LOGGER.error("Wire in dimension {} at position {} connects to itself.", level, a);

				continue;
			}

			BlockPos aBlockPos = BlockPos.of(a);
			BlockPos bBlockPos = BlockPos.of(b);

			int cm = WireGraph.lengthCm(aBlockPos, bBlockPos);

			if (cm > WiresConfig.connectionMaxLength() || cm > SpoolMath.capacity())
			{
				continue;
			}

			WireType wt = WireType.of(wire);

			if (wt != null && !net.graph.add(new WireGraph.Edge(
				id,
				a,
				b,
				cm,
				wt
			)))
			{
				continue;
			}

			net.graph.restoreHeat(id, n.getInt("Temperature"), n.getLong("LastHeatedTick"));

			Link link = new Link(id, aBlockPos, bBlockPos, wire, cm);

			net.links.put(id, link);
			net.cacheContact(link, wt);
			net.nextId = Math.max(net.nextId, id + 1);
		}

		return net;
	}
}
