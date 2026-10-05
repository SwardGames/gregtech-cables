package com.sward.gtcables.graph;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import com.gregtechceu.gtceu.common.data.GTDamageTypes;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.ColorSprayBehaviour;
import com.gregtechceu.gtceu.common.pipelike.cable.EnergyNet;
import com.gregtechceu.gtceu.common.pipelike.cable.EnergyNetHandler;
import com.gregtechceu.gtceu.common.pipelike.cable.EnergyRoutePath;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.sward.gtcables.*;
import com.sward.gtcables.blocks.ConnectorEntity;
import com.sward.gtcables.items.SpoolItem;
import com.sward.gtcables.network.CablePackets;
import com.sward.gtcables.network.clientbound.*;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.*;
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
import net.minecraft.world.phys.Vec3;
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

	private record Exposure(CableGeometry.ContactShape shape, CableActivity activity)
	{
	}

	private record Step(NodeKey key, long loss, int mode)
	{
	}

	private record NodeKey(long node, Direction side) implements Comparable<NodeKey>
	{
		@Override
		public int compareTo(NodeKey that)
		{
			if (this.node == that.node)
			{
				return this.side.compareTo(that.side);
			}

			return Long.compare(this.node, that.node);
		}
	}

	private record Previous(NodeKey from, Object edge, @Nullable EnergyNet network)
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

	private boolean isTransferringPower = false;

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

	public CableGraph graph() { return this.graph; }

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

	public @Nullable Direction getConnector(long id)
	{
		return this.graph.getConnector(id);
	}

	public @Nullable Direction getConnector(BlockPos pos)
	{
		return this.graph.getConnector(pos.asLong());
	}

	public Vec3 getConnectorPosition(BlockPos pos) { return this.graph.getConnectorPosition(pos); }

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

	public long transfer(BlockPos connector, long voltage, long amps)
	{
		if (this.isTransferringPower)
		{
			return 0;
		}

		if (voltage <= 0 || amps <= 0)
		{
			return 0;
		}

		long sourceConnectorId = connector.asLong();

		Direction sourceSide = this.graph.getConnector(sourceConnectorId);

		if (sourceSide == null)
		{
			return 0;
		}

		long remainingAmps = amps;

		BlockPos source = connector.relative(sourceSide);

		long revision = this.heatRevision;

		long tick = this.level.getGameTime();

		if (this.epoch != tick)
		{
			this.used.clear();
			this.epoch = tick;
		}

		this.isTransferringPower = true;

		try
		{
			ObjectSet<NodeKey> rejected = new ObjectOpenHashSet<>();
			LongSet voltageHeated = new LongOpenHashSet();
			LongSet voltageHeatedBlocks = new LongOpenHashSet();

			Object2ObjectMap<NodeKey, Previous> previous = new Object2ObjectOpenHashMap<>();
			Object2LongMap<NodeKey> distances = new Object2LongOpenHashMap<>();
			PriorityQueue<Step> queue = new PriorityQueue<>(Comparator.comparingLong(Step::loss).thenComparing(Step::key));

			NodeKey sourceKey = new NodeKey(sourceConnectorId, sourceSide);

			while (remainingAmps > 0)
			{
				previous.clear();
				distances.clear();
				queue.clear();

				distances.put(sourceKey, 0L);
				queue.add(new Step(sourceKey, 0, 1));

				Step target = null;

				while (!queue.isEmpty())
				{
					Step step = queue.remove();

					NodeKey key = step.key;
					long node = key.node;
					Direction side = key.side;
					long stepLoss = step.loss;

					if (stepLoss != distances.getLong(key))
					{
						continue;
					}

					if (step.mode == 0 && !rejected.contains(key))
					{
						BlockPos machine = BlockPos.of(node).relative(side);

						if (!machine.equals(source) && !this.graph.hasConnector(machine.asLong()))
						{
							IEnergyContainer container = getEnergyContainer(machine, side.getOpposite());

							if (container != null)
							{
								// If the container is a cable, treat it as an extension of the network.
								if (container instanceof EnergyNetHandler energyNetHandler)
								{
									EnergyNet net = energyNetHandler.getNet();

									for (EnergyRoutePath path : net.getNetData(machine))
									{
										BlockPos nextPos = path.getTargetPipePos();
										Direction nextFacing = path.getTargetFacing();

										BlockPos targetMachine = nextPos.relative(nextFacing);
										long targetMachineId = targetMachine.asLong();

										Direction targetConnector = this.graph.getConnector(targetMachineId);

										int mode = 0;

										if (targetConnector != null)
										{
											if (targetConnector != nextFacing.getOpposite())
											{
												continue;
											}

											nextPos = targetMachine;
											nextFacing = targetConnector;
											mode = 1;
										}

										long next = nextPos.asLong();

										NodeKey nextKey = new NodeKey(next, nextFacing);

										long loss = path.getMaxLoss() * 100;

										long totalLoss = stepLoss > Long.MAX_VALUE - loss
											? Long.MAX_VALUE
											: stepLoss + loss;

										if (roundedLoss(totalLoss) >= voltage || totalLoss >= distances.getOrDefault(
											nextKey,
											Long.MAX_VALUE
										))
										{
											continue;
										}

										distances.put(nextKey, totalLoss);
										previous.put(nextKey, new Previous(key, path, net));
										queue.add(new Step(nextKey, totalLoss, mode));
									}
								}
								else if (container.inputsEnergy(side.getOpposite()) &&
									container.getEnergyCanBeInserted() > 0)
								{
									target = step;

									break;
								}
							}
						}
					}

					for (Cable edge : this.graph.getAdjacentCables(node))
					{
						long next = edge.other(node);

						long loss = stepLoss > Long.MAX_VALUE - edge.spanLoss
							? Long.MAX_VALUE
							: stepLoss + edge.spanLoss;

						NodeKey nextKey = new NodeKey(next, Objects.requireNonNull(this.graph.getConnector(next)));

						if (roundedLoss(loss) >= voltage || loss >= distances.getOrDefault(nextKey, Long.MAX_VALUE))
						{
							continue;
						}

						distances.put(nextKey, loss);
						previous.put(nextKey, new Previous(key, edge, null));
						queue.add(new Step(nextKey, loss, 0));
					}
				}

				if (target == null)
				{
					break;
				}

				List<Previous> path = new ArrayList<>();

				for (NodeKey key = target.key; key.node != sourceConnectorId; )
				{
					Previous prev = previous.get(key);
					path.add(prev);
					key = prev.from;
				}

				long loss = distances.getLong(target.key);
				long fraction = 0L;

				boolean delivered;

				do
				{
					delivered = false;

					long packetLoss = loss / 100 + roundedLoss(fraction + loss % 100) - roundedLoss(fraction);
					long packetVoltage = voltage - packetLoss;

					boolean burned = false;

					for (Previous segment : path)
					{
						Object edge = segment.edge;

						if (edge instanceof Cable cableEdge)
						{
							long wireVoltage = cableEdge.cableType.voltage();

							if (voltage > wireVoltage)
							{
								packetVoltage = Math.min(packetVoltage, wireVoltage);

								// Overvoltage heats once per cable per offer, even if the sink then rejects it.
								if (voltageHeated.add(cableEdge.id))
								{
									applyHeat(cableEdge, voltageHeat(voltage, wireVoltage), tick);
								}

								burned |= !this.graph.hasCable(cableEdge.id);
							}
						}
						else if (edge instanceof EnergyRoutePath routeEdge)
						{
							for (CableBlockEntity c : routeEdge.getPath())
							{
								long wireVoltage = c.getMaxVoltage();

								if (voltage > wireVoltage)
								{
									packetVoltage = Math.min(packetVoltage, wireVoltage);

									if (voltageHeatedBlocks.add(c.getBlockPos().asLong()))
									{
										c.applyHeat(voltageHeat(voltage, wireVoltage));
									}

									burned |= c.isInValid();
								}
							}
						}
						else
						{
							throw new IllegalStateException("Unexpected route edge: " + edge);
						}
					}

					if (burned)
					{
						break;
					}

					// Reserve before foreign code. Block cycles, but allow disjoint networks to
					// cascade through a normal GregTech cable network during the same energy push.
					path.forEach(
						segment ->
						{
							Object e = segment.edge;

							if (e instanceof Cable c)
							{
								this.used.merge(c.id, 1L, (a, b) -> a == Long.MAX_VALUE ? a : a + b);
							}
						}
					);

					try
					{
						Direction side = target.key.side;
						BlockPos pos = BlockPos.of(target.key.node).relative(side);

						IEnergyContainer container = getEnergyContainer(pos, side.getOpposite());

						if (container != null)
						{
							delivered = container.acceptEnergyFromNetwork(
								side.getOpposite(),
								packetVoltage,
								1
							) == 1;
						}
					}
					finally
					{
						if (!delivered)
						{
							path.forEach(
								segment ->
								{
									Object e = segment.edge;

									if (e instanceof Cable c)
									{
										this.used.computeIfPresent(c.id, (k, v) -> v - 1);
									}
								}
							);
						}
					}

					if (delivered)
					{
						fraction = (fraction + loss % 100) % 100;
						remainingAmps -= 1;

						long travelledLoss = 0;

						// Paths are stored sink-to-source; electrical exposure follows the actual current direction.
						for (int i = path.size() - 1; i >= 0; i--)
						{
							Previous segment = path.get(i);
							Object edge = segment.edge;

							if (edge instanceof Cable cable && this.graph.getCable(cable.id) == edge)
							{
								Exposure exposure = this.exposures.get(cable.id);

								travelledLoss += cable.spanLoss;

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

								long excess = this.used.getOrDefault(cable.id, 0L) - cable.cableType.amps();

								// GT adds 40 K per excess amp in the current tick. This adapter sends 1 A packets.
								if (excess > 0)
								{
									applyHeat(cable, Math.min(excess, BURN_TEMPERATURE) * 40, tick);

									burned |= !this.graph.hasCable(cable.id);
								}
							}
							else if (edge instanceof EnergyRoutePath routePath)
							{
								Objects.requireNonNull(segment.network).addEnergyFluxPerSec(voltage - roundedLoss(travelledLoss));

								for (CableBlockEntity c : routePath.getPath())
								{
									travelledLoss += (long) c.getNodeData().getLossPerBlock() * 100;

									if (!c.isInValid())
									{
										c.incrementAmperage(1, voltage - roundedLoss(travelledLoss));

										burned |= c.isInValid();
									}
								}
							}
							else
							{
								throw new IllegalStateException("Unexpected route edge: " + edge);
							}
						}

						if (burned)
						{
							break;
						}
					}
					else
					{
						rejected.add(target.key);
					}
				}
				while (delivered && remainingAmps > 0);
			}
		}
		finally
		{
			this.isTransferringPower = false;

			if (revision != this.heatRevision)
			{
				setDirty();
			}
		}

		return amps - remainingAmps;
	}

	private @Nullable IEnergyContainer getEnergyContainer(BlockPos machine, Direction side)
	{
		if (!this.level.hasChunkAt(machine))
		{
			return null;
		}

		BlockEntity be = this.level.getBlockEntity(machine);

		if (be == null || be instanceof ConnectorEntity)
		{
			return null;
		}

		//noinspection DataFlowIssue
		return be.getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, side).orElse(null);
	}

	private static long roundedLoss(long hundredths)
	{
		return (hundredths + 99) / 100;
	}

	private static int voltageHeat(long voltage, long rating)
	{
		return (int) (Math.log(Math.max(
			1,
			GTUtil.getTierByVoltage(voltage) - GTUtil.getTierByVoltage(rating)
		)) * 45 + 36.5);
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
