package com.sward.gtcables.graph;

import java.util.Arrays;

public final class CableActivity
{
	private final long[] ticks = new long[20];
	private final long[] amps = new long[20];
	private long latest = Long.MIN_VALUE;
	private long maxVoltage;

	public CableActivity()
	{
		Arrays.fill(ticks, Long.MIN_VALUE);
	}

	public void accept(long voltage, long tick)
	{
		if (voltage <= 0)
		{
			return;
		}

		if (tick < latest)
		{
			Arrays.fill(ticks, Long.MIN_VALUE);
			Arrays.fill(amps, 0);
		}

		if (tick != latest)
		{
			latest = tick;
			maxVoltage = 0;
		}

		maxVoltage = Math.max(maxVoltage, voltage);

		int slot = Math.floorMod(tick, 20);

		if (ticks[slot] != tick)
		{
			ticks[slot] = tick;
			amps[slot] = 0;
		}

		if (amps[slot] < Long.MAX_VALUE)
		{
			amps[slot]++;
		}
	}

	public long voltage(long tick)
	{
		return tick == latest ? maxVoltage : 0;
	}

	public double averageAmperage(long tick)
	{
		double total = 0;

		for (int i = 0; i < ticks.length; i++)
		{
			long age = tick - ticks[i];

			if (ticks[i] != Long.MIN_VALUE && age >= 0 && age < ticks.length)
			{
				total += amps[i];
			}
		}

		return total / ticks.length;
	}
}
