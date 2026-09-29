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
		Arrays.fill(this.ticks, Long.MIN_VALUE);
	}

	public void accept(long voltage, long tick)
	{
		if (voltage <= 0)
		{
			return;
		}

		if (tick < this.latest)
		{
			Arrays.fill(this.ticks, Long.MIN_VALUE);
			Arrays.fill(this.amps, 0);
		}

		if (tick != this.latest)
		{
			this.latest = tick;
			this.maxVoltage = 0;
		}

		this.maxVoltage = Math.max(this.maxVoltage, voltage);

		int slot = Math.floorMod(tick, 20);

		if (this.ticks[slot] != tick)
		{
			this.ticks[slot] = tick;
			this.amps[slot] = 0;
		}

		if (this.amps[slot] < Long.MAX_VALUE)
		{
			this.amps[slot]++;
		}
	}

	public long voltage(long tick)
	{
		return tick == this.latest ? this.maxVoltage : 0;
	}

	public double averageAmperage(long tick)
	{
		double total = 0;

		for (int i = 0; i < this.ticks.length; i++)
		{
			long age = tick - this.ticks[i];

			if (this.ticks[i] != Long.MIN_VALUE && age >= 0 && age < this.ticks.length)
			{
				total += this.amps[i];
			}
		}

		return total / this.ticks.length;
	}
}
