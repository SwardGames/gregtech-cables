package com.sward.gtcables;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class CablesConfig
{
	static final ForgeConfigSpec SPEC;
	public static final CablesConfig COMMON;

	public final ForgeConfigSpec.IntValue spoolCapacity;

	public final ForgeConfigSpec.IntValue connectionMaxLength;

	public CablesConfig(ForgeConfigSpec.Builder builder)
	{
		spoolCapacity = builder
			.translation("config.gtcables.spool_max_capacity")
			.comment("How much cable a spool can hold (in meters)")
			.defineInRange("spool_max_capacity", 256, 1, Integer.MAX_VALUE);

		connectionMaxLength = builder
			.translation("config.gtcables.connection_max_length")
			.comment(
				"The maximum length of a connection (in centimeters).",
				"If 0, spool max capacity will be used instead."
			)
			.worldRestart()
			.defineInRange("connection_max_length", Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
	}

	static
	{
		Pair<CablesConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(CablesConfig::new);

		SPEC = pair.getRight();
		COMMON = pair.getLeft();
	}

	public static int spoolCapacity()
	{
		return COMMON.spoolCapacity.get() * 100;
	}

	public static int connectionMaxLength()
	{
		int val = COMMON.connectionMaxLength.get();

		if (val <= 0D)
		{
			return COMMON.spoolCapacity.get() * 100;
		}

		return val;
	}
}
