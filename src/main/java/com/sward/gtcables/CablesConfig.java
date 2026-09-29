package com.sward.gtcables;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class CablesConfig
{
	public static class Common
	{
		public final ForgeConfigSpec.IntValue spoolCapacity;

		public final ForgeConfigSpec.IntValue connectionMaxLength;

		public Common(ForgeConfigSpec.Builder builder)
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
	}

	public static class Client
	{
		public final ForgeConfigSpec.IntValue renderDistance;

		public Client(ForgeConfigSpec.Builder builder)
		{
			renderDistance = builder
				.translation("config.gtcables.render_distance")
				.comment("The maximum render distance for cables. If 0, will use the normal render distance.")
				.defineInRange("render_distance", 0, 0, Integer.MAX_VALUE);
		}
	}

	static final ForgeConfigSpec COMMON_SPEC;
	public static final Common COMMON;

	static
	{
		Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);

		COMMON_SPEC = pair.getRight();
		COMMON = pair.getLeft();
	}

	static final ForgeConfigSpec CLIENT_SPEC;
	public static final Client CLIENT;

	static
	{
		Pair<Client, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Client::new);

		CLIENT_SPEC = pair.getRight();
		CLIENT = pair.getLeft();
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

	public static int renderDistance()
	{
		return CLIENT.renderDistance.get();
	}
}
