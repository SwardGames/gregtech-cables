package com.sward.gtwires;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class WiresConfig
{
	static final ForgeConfigSpec SPEC;
	public static final WiresConfig COMMON;

	public final ForgeConfigSpec.IntValue spoolMaxCapacity;

	public final ForgeConfigSpec.IntValue connectionMaxLength;

	public WiresConfig(ForgeConfigSpec.Builder builder)
	{
		spoolMaxCapacity = builder
			.translation("config.gtwires.spool_max_capacity")
			.comment("How much wire a spool can hold (in meters)")
			.defineInRange("spool_max_capacity", 256, 1, Integer.MAX_VALUE);

		connectionMaxLength = builder
			.translation("config.gtwires.connection_max_length")
			.comment(
				"The maximum length of a connection (in centimeters).",
				"If 0, spool max capacity will be used instead."
			)
			.worldRestart()
			.defineInRange("connection_max_length", Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
	}

	static
	{
		Pair<WiresConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(WiresConfig::new);

		SPEC = pair.getRight();
		COMMON = pair.getLeft();
	}

	public static int connectionMaxLength()
	{
		int val = COMMON.connectionMaxLength.get();

		if (val <= 0D)
		{
			return COMMON.spoolMaxCapacity.get() * 100;
		}

		return val;
	}
}
