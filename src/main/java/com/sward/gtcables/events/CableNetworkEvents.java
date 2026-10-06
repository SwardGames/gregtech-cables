package com.sward.gtcables.events;

import com.sward.gtcables.CablesConfig;
import com.sward.gtcables.GregTechCables;
import com.sward.gtcables.graph.Cable;
import com.sward.gtcables.graph.CableGeometry;
import com.sward.gtcables.graph.CableGraph;
import com.sward.gtcables.graph.CableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = GregTechCables.ID)
public final class CableNetworkEvents
{
	@SubscribeEvent
	public static void tickShockContacts(TickEvent.LevelTickEvent e)
	{
		if (e.phase == TickEvent.Phase.END && e.level instanceof ServerLevel level)
		{
			CableNetwork.get(level).shockContacts();
		}
	}

	@SubscribeEvent
	public static void onBlockPlaced(BlockEvent.EntityPlaceEvent e)
	{
		if (CablesConfig.cableIntersectionTest() == CablesConfig.CableIntersectionTest.NONE)
		{
			return;
		}

		if (!(e.getEntity() instanceof Player) || !(e.getLevel() instanceof ServerLevel level))
		{
			return;
		}

		if (e instanceof BlockEvent.EntityMultiPlaceEvent multi)
		{
			for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots())
			{
				if (intersectsCable(level, snapshot.getPos()))
				{
					e.setCanceled(true);

					return;
				}
			}
		}
		else
		{
			if (intersectsCable(level, e.getPos()))
			{
				e.setCanceled(true);
			}
		}
	}

	private static boolean intersectsCable(ServerLevel level, BlockPos pos)
	{
		BlockState state = level.getBlockState(pos);

		if (state.is(GregTechCables.CABLE_PASSTHROUGH))
		{
			return false;
		}

		return getIntersectingCable(CableNetwork.get(level).graph(), state.getCollisionShape(level, pos), pos) != null;
	}

	static @Nullable Cable getIntersectingCable(CableGraph graph, VoxelShape shape, BlockPos pos)
	{
		if (shape.isEmpty())
		{
			return null;
		}

		AABB bounds = shape.bounds().move(pos);

		switch (CablesConfig.cableIntersectionTest())
		{
			case LINE ->
			{
				return graph.findCable(
					bounds,
					c ->
					{
						Vec3 aPos = graph.getConnectorPosition(c.a);
						Vec3 bPos = graph.getConnectorPosition(c.b);

						return bounds.intersects(new AABB(aPos, bPos)) && shape.clip(aPos, bPos, pos) != null;
					}
				);
			}
			case CABLE ->
			{
				return graph.findCable(
					bounds,
					c ->
					{
						Vec3 aPos = graph.getConnectorPosition(c.a);
						Vec3 bPos = graph.getConnectorPosition(c.b);

						return CableGeometry.cableIntersects(
							shape,
							pos,
							aPos,
							bPos
						);
					}
				);
			}
		}

		return null;
	}
}
