package com.sward.gtcables;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class CablesConfig
{
	public static class Server
	{
		public final ForgeConfigSpec.IntValue spoolCapacity;

		public final ForgeConfigSpec.IntValue connectionMaxLength;

		public final ForgeConfigSpec.IntValue maxRecursionDepth;

		public Server(ForgeConfigSpec.Builder builder)
		{
			this.spoolCapacity = builder
				.translation("config.gtcables.spool_max_capacity")
				.comment("How much cable a spool can hold (in meters)")
				.defineInRange("spool_max_capacity", 256, 1, Integer.MAX_VALUE);

			this.connectionMaxLength = builder
				.translation("config.gtcables.connection_max_length")
				.comment(
					"The maximum length of a connection (in centimeters).",
					"If 0, spool max capacity will be used instead."
				)
				.worldRestart()
				.defineInRange("connection_max_length", Integer.MAX_VALUE, 0, Integer.MAX_VALUE);

			this.maxRecursionDepth = builder
				.translation("config.gtcables.max_recursion_depth")
				.comment(
					"How many times it may recursively attempt to enter the cable network before failing.",
					"This is used to prevent factorial growth when mixing cable networks with cable blocks.",
					"If set to 1, then once energy flows through a cable network into a cable block, it cannot re-enter the network.",
					"This will not prevent other blocks, such as diodes, from accepting power."
				)
				.defineInRange("max_recursion_depth", 3, 1, Integer.MAX_VALUE);
		}
	}

	public static class Client
	{
		public final ForgeConfigSpec.IntValue renderDistance;

		public Client(ForgeConfigSpec.Builder builder)
		{
			this.renderDistance = builder
				.translation("config.gtcables.render_distance")
				.comment("The maximum render distance for cables. If 0, will use the normal render distance.")
				.defineInRange("render_distance", 0, 0, Integer.MAX_VALUE);
		}
	}

	static final ForgeConfigSpec SERVER_SPEC;
	public static final Server SERVER;

	static
	{
		Pair<Server, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Server::new);

		SERVER_SPEC = pair.getRight();
		SERVER = pair.getLeft();
	}

	public static int spoolCapacity()
	{
		return SERVER.spoolCapacity.get() * 100;
	}

	public static int connectionMaxLength()
	{
		int val = SERVER.connectionMaxLength.get();

		if (val <= 0D)
		{
			return SERVER.spoolCapacity.get() * 100;
		}

		return val;
	}

	public static int maxRecursionDepth()
	{
		return SERVER.maxRecursionDepth.get();
	}

	static final ForgeConfigSpec CLIENT_SPEC;
	public static final Client CLIENT;

	static
	{
		Pair<Client, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Client::new);

		CLIENT_SPEC = pair.getRight();
		CLIENT = pair.getLeft();
	}

	public static int renderDistance()
	{
		return CLIENT.renderDistance.get();
	}
}
