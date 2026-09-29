package com.sward.gtcables.graph;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.common.data.GTDamageTypes;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.ColorSprayBehaviour;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.sward.gtcables.*;
import com.sward.gtcables.blocks.ConnectorEntity;
import com.sward.gtcables.items.SpoolItem;
import com.sward.gtcables.network.CablePackets;
import com.sward.gtcables.network.clientbound.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

import static com.gregtechceu.gtceu.api.blockentity.IPaintable.UNPAINTED_COLOR;

/**
 * The cable network, stored per-dimension.
 * This allows for cables to transmit power through unloaded chunks.
 */
@FieldsAreNonnullByDefault
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class CableNetwork extends SavedData
{
	public record Heat(int temperature, long lastHeatedTick)
	{
	}

	private record Step(long node, long loss)
	{
	}

	private record Exposure(CableGeometry.ContactShape shape, CableActivity activity)
	{
	}

	public static final int AMBIENT_TEMPERATURE = 293;
	public static final int BURN_TEMPERATURE = 3000;

	private final ServerLevel level;
	private final CableGraph graph = new CableGraph();
	private final Long2LongMap used = new Long2LongOpenHashMap();
	private final Long2ObjectMap<Heat> heat = new Long2ObjectOpenHashMap<>();

	private final Long2ObjectMap<Exposure> exposures = new Long2ObjectOpenHashMap<>();
	private final LongSet powered = new LongOpenHashSet();
	private long poweredTick = Long.MIN_VALUE;
	private long nextId = 1;

	private long heatRevision;
	private long epoch = Long.MIN_VALUE;

	private int recursionDepth = 0;
	private final LongSet activeNodes = new LongOpenHashSet();

	private CableNetwork(ServerLevel level)
	{
		this.level = level;
	}

	public static CableNetwork get(ServerLevel level)
	{
		return level.getDataStorage().computeIfAbsent(
			nbt -> load(level, nbt),
			() -> new CableNetwork(level),
			"gtcables_network"
		);
	}

	public boolean hasCable(long id)
	{
		return this.graph.hasCable(id);
	}

	public @Nullable Cable getCable(long id)
	{
		return this.graph.getCable(id);
	}

	public boolean hasConnector(long id)
	{
		return this.graph.hasConnector(id);
	}

	public boolean hasConnector(BlockPos pos)
	{
		return this.graph.hasConnector(pos.asLong());
	}

	public @Nullable Direction getConnector(long id) { return this.graph.getConnector(id); }

	public @Nullable Direction getConnector(BlockPos pos) { return this.graph.getConnector(pos.asLong()); }

	public boolean connect(BlockPos a, BlockPos b, CableType cableType, int cm)
	{
		if (this.level.hasChunkAt(a) && this.level.getBlockEntity(a) instanceof ConnectorEntity ca)
		{
			addConnector(a, ca.attachedSide());
		}

		if (this.level.hasChunkAt(b) && this.level.getBlockEntity(b) instanceof ConnectorEntity cb)
		{
			addConnector(b, cb.attachedSide());
		}

		Cable cable = this.graph.addCable(this.nextId, a, b, a.asLong(), b.asLong(), cableType, cm, UNPAINTED_COLOR);

		if (cable == null)
		{
			return false;
		}

		this.nextId++;

		cacheContact(cable);

		setDirty();
		broadcastCableAdded(cable);

		return true;
	}

	public @Nullable Cable remove(long id)
	{
		return remove(id, false);
	}

	private @Nullable Cable remove(long id, boolean burned)
	{
		Cable cable = this.graph.removeCable(id);

		if (cable == null)
		{
			return null;
		}

		this.used.remove(id);
		this.heat.remove(id);
		this.exposures.remove(id);
		this.powered.remove(id);

		setDirty();
		broadcastCableRemoved(cable, burned);

		return cable;
	}

	private void cacheContact(Cable cable)
	{
		if (cable.cableType.shockHazard())
		{
			this.exposures.put(
				cable.id,
				new Exposure(
					new CableGeometry.ContactShape(
						this.graph.getConnectorPosition(cable.a),
						this.graph.getConnectorPosition(cable.b),
						cable.cableType.thickness() / 2
					),
					new CableActivity()
				)
			);
		}
	}

	public @Nullable Heat getHeat(Cable cable)
	{
		return this.heat.get(cable.id);
	}

	public void restoreHeat(Cable cable, int temperature, long lastHeatedTick)
	{
		if (this.graph.hasCable(cable.id) && temperature > AMBIENT_TEMPERATURE)
		{
			this.heat.put(cable.id, new Heat(Math.min(BURN_TEMPERATURE - 1, temperature), lastHeatedTick));
		}
	}

	public int temperature(Cable cable, long tick)
	{
		Heat heat = this.heat.get(cable.id);

		if (heat == null)
		{
			return AMBIENT_TEMPERATURE;
		}

		int temperature = heat.temperature;

		// GT does not cool during a heating tick. Replay only intervening idle ticks.
		// At least 1 K is lost each step, so even years of inactivity take <2707 steps.
		long idle = tick > heat.lastHeatedTick ? tick - heat.lastHeatedTick - 1 : 0;

		if (idle < 0)
		{
			idle = Long.MAX_VALUE; // Saturate a corrupt/overflowing saved interval.
		}

		while (idle-- > 0 && temperature > AMBIENT_TEMPERATURE)
		{
			temperature = Math.max(
				AMBIENT_TEMPERATURE,
				(int) (temperature - Math.pow(temperature - AMBIENT_TEMPERATURE, 0.35D))
			);
		}

		return temperature;
	}

	private void applyHeat(Cable cable, long amount, long tick)
	{
		if (amount <= 0 || this.graph.getCable(cable.id) != cable)
		{
			return;
		}

		int temperature = temperature(cable, tick);

		this.heatRevision++;

		if (amount >= BURN_TEMPERATURE - temperature)
		{
			remove(cable.id, true);
		}
		else
		{
			this.heat.put(cable.id, new Heat(temperature + (int) amount, tick));
		}
	}

	public void shockContacts()
	{
		if (this.poweredTick != this.level.getGameTime() || this.powered.isEmpty())
		{
			return;
		}

		this.level.getAllEntities().forEach(this::shockEntity);
	}

	private void shockEntity(Entity entity)
	{
		// Only living entities which are alive, not spectators and not invulnerable can be shocked.
		if (!(entity instanceof LivingEntity) ||
			!entity.isAlive() || entity.isSpectator() ||
			(entity instanceof Player player && player.getAbilities().invulnerable))
		{
			return;
		}

		for (long id : this.powered)
		{
			Exposure exposure = this.exposures.get(id);

			if (exposure == null)
			{
				continue;
			}

			long voltage = exposure.activity.voltage(this.level.getGameTime());
			double amps = exposure.activity.averageAmperage(this.level.getGameTime());

			if (voltage > 0 && amps > 0 && exposure.shape.touches(entity.getBoundingBox()))
			{
				float damage = (float) ((GTUtil.getTierByVoltage(voltage) + 1) * amps * 4);
				entity.hurt(GTDamageTypes.ELECTRIC.source(this.level), damage);
			}
		}
	}

	public List<Cable> attached(BlockPos pos)
	{
		return this.graph.getAdjacentCables(pos.asLong()).stream().toList();
	}

	public void addConnector(BlockPos pos, Direction facing)
	{
		long id = pos.asLong();

		if (this.graph.addConnector(id, facing))
		{
			CablePackets.CHANNEL.send(
				PacketDistributor.DIMENSION.with(this.level::dimension),
				new AddConnectorPacket(this.level.dimension().location(), id, facing)
			);
		}
	}

	public void removeConnector(BlockPos pos)
	{
		List<Cable> detached = attached(pos);
		detached.forEach(e -> remove(e.id));

		long id = pos.asLong();

		if (this.graph.removeConnector(id))
		{
			setDirty();
		}

		// Detach every span before spawning items so either endpoint can remove it only once.
		for (Cable edge : detached)
		{
			ItemStack wire = new ItemStack(edge.cableType.item());
			int remaining = edge.lengthCm / 100;

			while (!wire.isEmpty() && remaining > 0)
			{
				int count = Math.min(remaining, wire.getMaxStackSize());
				Block.popResource(this.level, pos, wire.copyWithCount(count));
				remaining -= count;
			}
		}

		CablePackets.CHANNEL.send(
			PacketDistributor.DIMENSION.with(this.level::dimension),
			new RemoveConnectorPacket(this.level.dimension().location(), id)
		);
	}

	public long transfer(BlockPos source, long voltage, long amps)
	{
		if (this.recursionDepth >= CablesConfig.maxRecursionDepth())
		{
			return 0;
		}

		long sourceId = source.asLong();

		if (this.activeNodes.contains(sourceId) || voltage <= 0 || amps <= 0)
		{
			return 0;
		}

		Direction connectorSide = this.graph.getConnector(source.asLong());

		if (connectorSide == null)
		{
			return 0;
		}

		BlockPos sourcePosition = source.relative(connectorSide);

		long revision = this.heatRevision;

		long tick = this.level.getGameTime();

		if (this.epoch != tick)
		{
			this.used.clear();
			this.epoch = tick;
		}

		this.activeNodes.add(sourceId);
		++this.recursionDepth;

		long accepted = 0;

		LongSet rejected = new LongOpenHashSet();
		LongSet voltageHeated = new LongOpenHashSet();

		Map<List<Long>, Long> routeFractions = new HashMap<>();

		Long2ObjectMap<Cable> previous = new Long2ObjectOpenHashMap<>();
		Long2LongMap distances = new Long2LongOpenHashMap();
		PriorityQueue<Step> queue = new PriorityQueue<>(
			Comparator.comparingLong(Step::loss).thenComparingLong(Step::node)
		);

		try
		{
			while (accepted < amps)
			{
				previous.clear();
				distances.clear();
				queue.clear();

				distances.put(sourceId, 0L);
				queue.add(new Step(sourceId, 0));

				Long target = null;

				while (!queue.isEmpty())
				{
					Step step = queue.remove();

					if (step.loss != distances.get(step.node))
					{
						continue;
					}

					if (step.node != sourceId &&
						!rejected.contains(step.node) &&
						canAcceptEnergy(sourcePosition, step.node)
					)
					{
						target = step.node;

						break;
					}

					for (Cable edge : this.graph.getAdjacentCables(step.node))
					{
						long next = edge.other(step.node);

						if (this.activeNodes.contains(next))
						{
							continue;
						}

						long loss = step.loss > Long.MAX_VALUE - edge.spanLoss
							? Long.MAX_VALUE
							: step.loss + edge.spanLoss;

						if (roundedLoss(loss) >= voltage || loss >= distances.getOrDefault(next, Long.MAX_VALUE))
						{
							continue;
						}

						distances.put(next, loss);
						previous.put(next, edge);
						queue.add(new Step(next, loss));
					}
				}

				if (target == null)
				{
					break;
				}

				List<Cable> path = new ArrayList<>();

				for (long n = target; n != sourceId; )
				{
					Cable edge = previous.get(n);
					path.add(edge);
					n = edge.other(n);
				}

				List<Long> key = path.stream().map(Cable::id).toList();

				long loss = distances.get((long)target);
				long fraction = routeFractions.getOrDefault(key, 0L);
				long packetLoss = loss / 100 + roundedLoss(fraction + loss % 100) - roundedLoss(fraction);
				long packetVoltage = voltage - packetLoss;

				boolean burned = false;

				for (Cable edge : path)
				{
					long wireVoltage = edge.cableType.voltage();

					if (voltage > wireVoltage)
					{
						// Overvoltage heats once per cable per offer, even if the sink then rejects it.
						if (voltageHeated.add(edge.id))
						{
							applyHeat(edge, voltageHeat(voltage, wireVoltage), tick);
						}

						packetVoltage = Math.min(packetVoltage, wireVoltage);
						burned |= !this.graph.hasCable(edge.id);
					}
				}

				if (burned)
				{
					continue; // Re-route only after removing the destroyed span(s).
				}

				// Reserve before foreign code. Block cycles, but allow disjoint networks to
				// cascade through a normal GregTech cable network during the same energy push.
				path.forEach(c -> this.used.merge(c.id, 1L, (a, b) -> a == Long.MAX_VALUE ? a : a + b));

				LongSet reservation = new LongOpenHashSet();

				path.forEach(
					c ->
					{
						if (this.activeNodes.add(c.aId))
						{
							reservation.add(c.aId);
						}

						if (this.activeNodes.add(c.bId))
						{
							reservation.add(c.bId);
						}
					}
				);

				boolean delivered = false;

				try
				{
					IEnergyContainer container = getSinkEnergyContainer(sourcePosition, target);

					//noinspection DataFlowIssue
					delivered = container != null && container.acceptEnergyFromNetwork(
						this.graph.getConnector(target).getOpposite(),
						packetVoltage,
						1
					) == 1;
				}
				finally
				{
					this.activeNodes.removeAll(reservation);

					if (!delivered)
					{
						path.forEach(c -> this.used.computeIfPresent(c.id, (k, v) -> v - 1));
					}
				}

				if (delivered)
				{
					routeFractions.put(key, (fraction + loss % 100) % 100);
					accepted++;

					long travelledLoss = 0;

					// Paths are stored sink-to-source; electrical exposure follows the actual current direction.
					for (int i = path.size() - 1; i >= 0; i--)
					{
						Cable cable = path.get(i);

						travelledLoss += cable.spanLoss;

						if (this.graph.getCable(cable.id) == cable)
						{
							Exposure exposure = this.exposures.get(cable.id);

							if (exposure != null)
							{
								if (this.poweredTick != tick)
								{
									this.powered.clear();
									this.poweredTick = tick;
								}

								exposure.activity.accept(voltage - roundedLoss(travelledLoss), tick);
								this.powered.add(cable.id);
							}
						}
					}

					for (Cable cable : path)
					{
						long excess = this.used.getOrDefault(cable.id, 0L) - cable.cableType.amps();

						// GT adds 40 K per excess amp in the current tick. This adapter sends 1 A packets.
						if (excess > 0)
						{
							applyHeat(cable, Math.min(excess, BURN_TEMPERATURE) * 40, tick);
						}
					}
				}
				else
				{
					rejected.add((long)target);
				}
			}
		}
		finally
		{
			--this.recursionDepth;
			this.activeNodes.remove(sourceId);

			if (revision != this.heatRevision)
			{
				setDirty();
			}
		}

		return accepted;
	}

	private @Nullable IEnergyContainer getSinkEnergyContainer(BlockPos sourceMachine, long connectorId)
	{
		Direction side = this.graph.getConnector(connectorId);

		if (side == null)
		{
			return null;
		}

		BlockPos connector = BlockPos.of(connectorId);
		BlockPos machine = connector.relative(side);

		if (machine.equals(sourceMachine) || !this.level.hasChunkAt(connector) || !this.level.hasChunkAt(machine))
		{
			return null;
		}

		if (!(this.level.getBlockEntity(connector) instanceof ConnectorEntity actual) || actual.attachedSide() != side)
		{
			return null;
		}

		BlockEntity be = this.level.getBlockEntity(machine);

		if (be == null || be instanceof ConnectorEntity)
		{
			return null;
		}

		//noinspection DataFlowIssue
		return be.getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, side.getOpposite()).orElse(null);
	}

	private boolean canAcceptEnergy(BlockPos sourceMachine, long connectorId)
	{
		IEnergyContainer container = getSinkEnergyContainer(sourceMachine, connectorId);

		//noinspection DataFlowIssue
		return container != null &&
			container.inputsEnergy(this.graph.getConnector(connectorId).getOpposite()) &&
			container.getEnergyCanBeInserted() > 0;
	}

	private static long roundedLoss(long hundredths)
	{
		return hundredths / 100 + (hundredths % 100 == 0 ? 0 : 1);
	}

	private static int tier(long voltage)
	{
		// GregTech's voltage tiers, capped at MAX (14).
		return voltage <= 8 ? 0 : Math.min(14, (62 - Long.numberOfLeadingZeros(voltage - 1)) >> 1);
	}

	private static int voltageHeat(long voltage, long rating)
	{
		return (int) (Math.log(Math.max(1, tier(voltage) - tier(rating))) * 45 + 36.5);
	}

	/**
	 * Removes the cables, recovering the spools.
	 */
	public boolean recover(Player player, List<@NotNull Cable> cables, boolean commit)
	{
		Inventory inventory = player.getInventory();

		Int2ObjectMap<ItemStack> edits = new Int2ObjectOpenHashMap<>();

		for (Cable edge : cables)
		{
			int remaining = edge.lengthCm;

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

					if (!spool.is(GregTechCables.SPOOL.get()) || (spool.getCount() != 1 && SpoolItem.length(spool) > 0) ||
						(SpoolItem.length(spool) > 0 && !edge.cableType.id().equals(SpoolItem.type(spool))))
					{
						continue;
					}

					int put = Math.min(remaining, CablesConfig.spoolCapacity() - SpoolItem.length(spool));

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
					SpoolItem.set(copy, edge.cableType.id(), SpoolItem.length(spool) + put);
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
			cables.forEach(e -> remove(e.id));
			player.containerMenu.broadcastChanges();
		}

		return true;
	}

	public void useWireCutters(PlayerInteractEvent e)
	{
		Player player = e.getEntity();
		Level level = e.getLevel();

		CableHitResult hit = this.graph.clip(player);

		if (hit == null)
		{
			return;
		}

		e.setCanceled(true);
		e.setCancellationResult(InteractionResult.SUCCESS);

		Cable cable = hit.cable();

		if (!level.mayInteract(player, cable.a) || !level.mayInteract(player, cable.b))
		{
			e.setCancellationResult(InteractionResult.CONSUME);

			return;
		}

		if (!recover(player, List.of(cable), true))
		{
			e.setCancellationResult(InteractionResult.CONSUME);

			SpoolItem.message(player, "no_space");

			return;
		}

		ToolHelper.damageItem(e.getItemStack(), player, 1);

		SpoolItem.message(player, "recovered");
	}

	public void useSprayCan(
		PlayerInteractEvent e,
		@Nullable DyeColor color,
		ColorSprayBehaviour behaviour
	)
	{
		Player player = e.getEntity();
		Level level = e.getLevel();

		CableHitResult hit = this.graph.clip(player);

		if (hit == null)
		{
			return;
		}

		e.setCanceled(true);
		e.setCancellationResult(InteractionResult.SUCCESS);

		Cable cable = hit.cable();

		if (!level.mayInteract(player, cable.a) || !level.mayInteract(player, cable.b))
		{
			e.setCancellationResult(InteractionResult.CONSUME);

			return;
		}

		// If color is null it means the player is using a solvent, so remove the painting color
		int rgb = color == null ? UNPAINTED_COLOR : color.getMapColor().col;

		// Do nothing if painting the same color.
		if (cable.color == rgb)
		{
			e.setCancellationResult(InteractionResult.CONSUME);

			return;
		}

		cable.color = rgb;

		setDirty();

		CablePackets.CHANNEL.send(
			PacketDistributor.DIMENSION.with(level::dimension),
			new CableColorChangedPacket(level.dimension().location(), cable.id, rgb)
		);

		behaviour.useItemDurability(player, e.getHand(), e.getItemStack(), GTItems.SPRAY_EMPTY.asStack());
	}

	private void broadcastCableAdded(Cable cable)
	{
		CablePackets.CHANNEL.send(
			PacketDistributor.DIMENSION.with(this.level::dimension),
			new AddCablePacket(
				this.level.dimension().location(),
				cable.id,
				cable.a,
				cable.b,
				cable.cableType.id(),
				cable.color
			)
		);
	}

	private void broadcastCableRemoved(Cable cable, boolean burned)
	{
		CablePackets.CHANNEL.send(
			PacketDistributor.DIMENSION.with(this.level::dimension),
			new RemoveCablePacket(this.level.dimension().location(), cable.id, burned)
		);
	}

	public void sync(ServerPlayer player)
	{
		CablePackets.CHANNEL.send(
			PacketDistributor.PLAYER.with(() -> player),
			SyncGraphPacket.create(this.level, this.graph.connectors(), this.graph.cables())
		);
	}

	@Override
	public CompoundTag save(CompoundTag tag)
	{
		ListTag ns = new ListTag();
		ListTag cs = new ListTag();

		this.graph.connectors().forEach(
			(pos, side) ->
			{
				CompoundTag n = new CompoundTag();

				n.putLong("Pos", pos);
				n.putByte("Side", (byte) side.ordinal());

				ns.add(n);
			}
		);

		this.graph.cables().forEach(
			cable ->
			{
				CompoundTag n = new CompoundTag();

				n.putLong("Id", cable.id);
				n.putLong("A", cable.aId);
				n.putLong("B", cable.bId);
				n.putString("CableType", cable.cableType.id().toString());

				if (cable.color != UNPAINTED_COLOR)
				{
					n.putInt("Color", cable.color);
				}

				Heat heat = getHeat(cable);

				if (heat != null)
				{
					n.putInt("Temperature", heat.temperature());
					n.putLong("LastHeatedTick", heat.lastHeatedTick());
				}

				cs.add(n);
			}
		);

		tag.putInt("Version", 2);
		tag.putLong("NextId", this.nextId);
		tag.put("Nodes", ns);
		tag.put("Links", cs);

		return tag;
	}

	static CableNetwork load(ServerLevel level, CompoundTag tag)
	{
		CableNetwork net = new CableNetwork(level);

		for (Tag t : tag.getList("Nodes", Tag.TAG_COMPOUND))
		{
			CompoundTag n = (CompoundTag) t;
			int side = n.getByte("Side");

			if (side >= 0 && side < 6)
			{
				net.graph.addConnector(n.getLong("Pos"), Direction.values()[side]);
			}
		}

		net.nextId = Math.max(1, tag.getLong("NextId"));

		for (Tag t : tag.getList("Links", Tag.TAG_COMPOUND))
		{
			CompoundTag n = (CompoundTag) t;

			long id = n.getLong("Id");

			if (id <= 0 || id == Long.MAX_VALUE)
			{
				GregTechCables.LOGGER.error(
					"Cable {} in dimension {} has invalid id.",
					id,
					level
				);

				continue;
			}

			if (net.graph.hasCable(id))
			{
				GregTechCables.LOGGER.error(
					"Cable with duplicate id {} in dimension {}.",
					id,
					level
				);

				continue;
			}

			long aId = n.getLong("A");
			long bId = n.getLong("B");

			BlockPos a = BlockPos.of(aId);
			BlockPos b = BlockPos.of(bId);

			if (aId == bId)
			{
				GregTechCables.LOGGER.error(
					"Cable {} in dimension {} at position {} connects to itself.",
					id,
					level,
					a
				);

				continue;
			}

			if (!net.hasConnector(aId) || !net.hasConnector(bId))
			{
				GregTechCables.LOGGER.error(
					"Cable {} [{}, {}] in dimension {} has invalid connector.",
					id,
					a,
					b,
					level
				);

				continue;
			}

			ResourceLocation cableTypeId = ResourceLocation.tryParse(n.getString("CableType"));

			if (cableTypeId == null)
			{
				GregTechCables.LOGGER.warn(
					"Cable {} [{}, {}] in dimension {} is missing its type.",
					id,
					a,
					b,
					level
				);

				continue;
			}

			CableType cableType = CableType.of(cableTypeId);

			if (cableType == null)
			{
				GregTechCables.LOGGER.warn(
					"Cable {} [{}, {}] in dimension {} has invalid type {}.",
					id,
					a,
					b,
					level,
					cableTypeId
				);

				continue;
			}

			int cm = CableGraph.lengthCm(a, b);

			if (cm > CablesConfig.connectionMaxLength())
			{
				GregTechCables.LOGGER.warn(
					"Cable {} [{}, {}] in dimension {} exceeds maximum connection length.",
					id,
					a,
					b,
					level
				);
			}

			int color = n.contains("Color") ? n.getInt("Color") : UNPAINTED_COLOR;

			Cable cable = net.graph.addCable(id, a, b, aId, bId, cableType, cm, color);

			if (cable == null)
			{
				GregTechCables.LOGGER.warn(
					"Failed to add cable {} [{}, {}] in dimension {}.",
					id,
					a,
					b,
					level
				);

				continue;
			}

			net.restoreHeat(cable, n.getInt("Temperature"), n.getLong("LastHeatedTick"));
			net.cacheContact(cable);

			net.nextId = Math.max(net.nextId, id + 1);
		}

		return net;
	}
}
