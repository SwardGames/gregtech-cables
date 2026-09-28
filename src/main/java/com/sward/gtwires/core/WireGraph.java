package com.sward.gtwires.core;

import com.sward.gtwires.WiresConfig;
import com.sward.gtwires.WireType;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.LongConsumer;

/**
 * Pure server-side graph. No world, chunk or tick subscriptions.
 */
public final class WireGraph
{
	public static final int AMBIENT_TEMPERATURE = 293;
	public static final int BURN_TEMPERATURE = 3000;

	public record Heat(int temperature, long lastHeatedTick)
	{
	}

	public record Edge(long id, long a, long b, int centimetres, @NotNull WireType wireType)
	{
		public Edge
		{
			if (a == b || centimetres < 1 || centimetres > WiresConfig.connectionMaxLength() || wireType == null)
			{
				throw new IllegalArgumentException("Invalid wire");
			}
		}

		public long other(long node)
		{
			return node == a ? b : a;
		}

		public long lossHundredths()
		{
			return (long) centimetres * wireType.lossPerMetre();
		}
	}

	public interface Sink
	{
		boolean available(long node);

		/**
		 * Accept at most one amp; return true only if the receiver consumed it.
		 */
		boolean accept(long node, long voltage);
	}

	@FunctionalInterface
	public interface PowerListener
	{
		/**
		 * One accepted amp, at the voltage remaining after this span's cumulative route loss.
		 */
		void accept(Edge edge, long voltage, long tick);
	}

	private record Step(long node, long loss)
	{
	}

	private final Map<Long, Edge> edges = new LinkedHashMap<>();
	private final Map<Long, List<Edge>> adjacency = new HashMap<>();
	private final Map<Long, Long> used = new HashMap<>();
	private final Map<Long, Heat> heat = new HashMap<>();

	private final LongConsumer onBurn;
	private final PowerListener onPower;

	private long heatRevision;
	private long epoch = Long.MIN_VALUE;

	private final Set<Long> activeNodes = new HashSet<>();

	public WireGraph()
	{
		this(id ->
		{
		});
	}

	public WireGraph(LongConsumer onBurn)
	{
		this(
			onBurn, (edge, voltage, tick) ->
			{
			}
		);
	}

	public WireGraph(LongConsumer onBurn, PowerListener onPower)
	{
		this.onBurn = Objects.requireNonNull(onBurn);
		this.onPower = Objects.requireNonNull(onPower);
	}

	public long heatRevision()
	{
		return heatRevision;
	}

	public Heat heat(long id)
	{
		return heat.get(id);
	}

	public void restoreHeat(long id, int temperature, long lastHeatedTick)
	{
		if (edges.containsKey(id) && temperature > AMBIENT_TEMPERATURE)
		{
			heat.put(id, new Heat(Math.min(BURN_TEMPERATURE - 1, temperature), lastHeatedTick));
		}
	}

	public int temperature(long id, long tick)
	{
		Heat state = heat.get(id);

		if (state == null)
		{
			return AMBIENT_TEMPERATURE;
		}

		int temperature = state.temperature;

		// GT does not cool during a heating tick. Replay only intervening idle ticks.
		// At least 1 K is lost each step, so even years of inactivity take <2707 steps.
		long idle = tick > state.lastHeatedTick ? tick - state.lastHeatedTick - 1 : 0;

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

	private void applyHeat(Edge edge, long amount, long tick)
	{
		if (amount <= 0 || edges.get(edge.id) != edge)
		{
			return;
		}

		int temperature = temperature(edge.id, tick);

		heatRevision++;

		if (amount >= BURN_TEMPERATURE - temperature)
		{
			remove(edge.id);
			onBurn.accept(edge.id);
		}
		else
		{
			heat.put(edge.id, new Heat(temperature + (int) amount, tick));
		}
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

	public Collection<Edge> edges()
	{
		return Collections.unmodifiableCollection(edges.values());
	}

	public Edge getEdge(long id)
	{
		return edges.get(id);
	}

	public boolean add(Edge edge)
	{
		if (edges.containsKey(edge.id) || adjacency.getOrDefault(edge.a, List.of()).stream()
			.anyMatch(e -> e.other(edge.a) == edge.b))
		{
			return false;
		}

		edges.put(edge.id, edge);
		adjacency.computeIfAbsent(edge.a, k -> new ArrayList<>()).add(edge);
		adjacency.computeIfAbsent(edge.b, k -> new ArrayList<>()).add(edge);

		return true;
	}

	public void remove(long id)
	{
		Edge e = edges.remove(id);

		if (e == null)
		{
			return;
		}

		for (long node : new long[]{e.a, e.b})
		{
			List<Edge> list = adjacency.get(node);
			list.remove(e);
			if (list.isEmpty())
			{
				adjacency.remove(node);
			}
		}

		used.remove(id);
		heat.remove(id);
	}

	public static long roundedLoss(long hundredths)
	{
		return hundredths / 100 + (hundredths % 100 == 0 ? 0 : 1);
	}

	public static int lengthCm(BlockPos a, BlockPos b)
	{
		int dx = b.getX() - a.getX();
		int dy = b.getY() - a.getY();
		int dz = b.getZ() - a.getZ();

		double length = Math.sqrt(dx * dx + dy * dy + dz * dz) * 100;

		if (!Double.isFinite(length) || length > WiresConfig.connectionMaxLength())
		{
			return -1;
		}

		return Math.max(1, (int) Math.ceil(length - 1e-9D));
	}

	/**
	 * Called by GT's energy push. Heating/cooling and shared amp counts need no tick subscription.
	 */
	public long transfer(long source, long voltage, long amperage, long tick, Sink sink)
	{
		if (activeNodes.contains(source) || voltage <= 0 || amperage <= 0)
		{
			return 0;
		}

		if (epoch != tick)
		{
			used.clear();
			epoch = tick;
		}

		activeNodes.add(source);

		long accepted = 0;

		Set<Long> rejected = new HashSet<>();
		Set<Long> voltageHeated = new HashSet<>();

		Map<List<Long>, Long> routeFractions = new HashMap<>();

		try
		{
			while (accepted < amperage)
			{
				Map<Long, Edge> previous = new HashMap<>();
				Map<Long, Long> distances = new HashMap<>();
				PriorityQueue<Step> queue = new PriorityQueue<>(
					Comparator.comparingLong(Step::loss).thenComparingLong(Step::node)
				);

				distances.put(source, 0L);
				queue.add(new Step(source, 0));

				Long target = null;

				while (!queue.isEmpty())
				{
					Step step = queue.remove();

					if (step.loss != distances.get(step.node))
					{
						continue;
					}

					if (step.node != source && !rejected.contains(step.node) && sink.available(step.node))
					{
						target = step.node;
						break;
					}

					for (Edge edge : adjacency.getOrDefault(step.node, List.of()))
					{
						long next = edge.other(step.node);

						if (activeNodes.contains(next))
						{
							continue;
						}

						long loss = step.loss > Long.MAX_VALUE - edge.lossHundredths() ? Long.MAX_VALUE
							: step.loss + edge.lossHundredths();

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

				List<Edge> path = new ArrayList<>();

				for (long n = target; n != source; )
				{
					Edge edge = previous.get(n);
					path.add(edge);
					n = edge.other(n);
				}

				List<Long> key = path.stream().map(Edge::id).toList();

				long loss = distances.get(target);
				long fraction = routeFractions.getOrDefault(key, 0L);
				long packetLoss = loss / 100 + roundedLoss(fraction + loss % 100) - roundedLoss(fraction);
				long packetVoltage = voltage - packetLoss;

				boolean burned = false;

				for (Edge edge : path)
				{
					long wireVoltage = edge.wireType.voltage();

					if (voltage > wireVoltage)
					{
						// Overvoltage heats once per wire per offer, even if the sink then rejects it.
						if (voltageHeated.add(edge.id))
						{
							applyHeat(edge, voltageHeat(voltage, wireVoltage), tick);
						}
						packetVoltage = Math.min(packetVoltage, wireVoltage);
						burned |= !edges.containsKey(edge.id);
					}
				}

				if (burned)
				{
					continue; // Re-route only after removing the destroyed span(s).
				}

				// Reserve before foreign code. Block cycles, but allow disjoint networks to
				// cascade through a normal GregTech cable network during the same energy push.
				path.forEach(e -> used.merge(e.id, 1L, (a, b) -> a == Long.MAX_VALUE ? a : a + b));

				Set<Long> reservation = new HashSet<>();

				path.forEach(
					e ->
					{
						if (activeNodes.add(e.a))
						{
							reservation.add(e.a);
						}

						if (activeNodes.add(e.b))
						{
							reservation.add(e.b);
						}

					}
				);

				boolean delivered = false;

				try
				{
					delivered = sink.accept(target, packetVoltage);
				}
				finally
				{
					activeNodes.removeAll(reservation);

					if (!delivered)
					{
						path.forEach(e -> used.computeIfPresent(e.id, (k, v) -> v - 1));
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
						Edge edge = path.get(i);

						travelledLoss += edge.lossHundredths();

						if (edges.get(edge.id) == edge)
						{
							onPower.accept(edge, voltage - roundedLoss(travelledLoss), tick);
						}
					}

					for (Edge edge : path)
					{
						long excess = used.getOrDefault(edge.id, 0L) - edge.wireType.amps();

						// GT adds 40 K per excess amp in the current tick. This adapter sends 1 A packets.
						if (excess > 0)
						{
							applyHeat(edge, Math.min(excess, BURN_TEMPERATURE) * 40, tick);
						}
					}
				}
				else
				{
					rejected.add(target);
				}
			}
		}
		finally
		{
			activeNodes.remove(source);
		}

		return accepted;
	}
}
