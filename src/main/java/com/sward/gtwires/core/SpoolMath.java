package com.sward.gtwires.core;

import com.sward.gtwires.WiresConfig;

public final class SpoolMath
{
	private SpoolMath()
	{
	}

	public static int capacity()
	{
		return WiresConfig.COMMON.spoolMaxCapacity.get() * 100;
	}

	public static int transferable(int source, int target)
	{
		return Math.min(source, capacity() - target);
	}

	public static int removableItems(int length, int stackLimit)
	{
		return Math.min(length / 100, stackLimit);
	}
}
