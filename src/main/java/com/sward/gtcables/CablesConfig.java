package com.sward.gtcables;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class CablesConfig
{
	public enum CableIntersectionTest
	{
		NONE,
		LINE,
		CABLE
	}

	public static class Server
	{
		public final ForgeConfigSpec.IntValue spoolCapacity;

		public final ForgeConfigSpec.IntValue connectionMaxLength;

		public final ForgeConfigSpec.EnumValue<CableIntersectionTest> cableIntersectionTest;

		public final ForgeConfigSpec.IntValue playerSyncDistance;

		public Server(ForgeConfigSpec.Builder builder)
		{
			this.spoolCapacity = builder
				.translation("config.gtcables.spool_max_capacity")
				.comment("How much cable a spool can hold (in meters)")
				.defineInRange("spool_max_capacity", 256, 1, 10000000);

			this.connectionMaxLength = builder
				.translation("config.gtcables.connection_max_length")
				.comment(
					"The maximum length of a connection (in centimeters).",
					"If 0, spool max capacity will be used instead."
				)
				.worldRestart()
				.defineInRange("connection_max_length", Integer.MAX_VALUE, 0, Integer.MAX_VALUE);

			this.cableIntersectionTest = builder
				.translation("config.gtcables.cable_intersection_test")
				.comment(
					"What block intersection tests are performed when placing a cable between two connectors.",
					"Blocks can be ignored by tagging them as 'gtcables:cable_passthrough'.",
					"",
					"Options are:",
					"NONE: No tests are performed, cables can be placed through blocks freely.",
					"LINE: The straight line between the two connectors is tested.",
					"CABLE: The centerline of the cable is tested.",
					"",
					"This setting will not be applied retroactively."
				)
				.defineEnum("cable_intersection_test", CableIntersectionTest.CABLE);

			this.playerSyncDistance = builder
				.translation("config.gtcables.player_sync_distance")
				.comment("The distance (in chunks) that a player will be notified of a cables existence.")
				.defineInRange("player_sync_distance", 12, 8, 256);
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

	public static CableIntersectionTest cableIntersectionTest()
	{
		return SERVER.cableIntersectionTest.get();
	}

	public static int playerSyncDistance()
	{
		return SERVER.playerSyncDistance.get();
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
