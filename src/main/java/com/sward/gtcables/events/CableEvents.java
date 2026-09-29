package com.sward.gtcables.events;

import com.sward.gtcables.GregTechCables;
import com.sward.gtcables.graph.CableNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GregTechCables.ID)
public final class CableEvents
{
	@SubscribeEvent
	public static void login(PlayerEvent.PlayerLoggedInEvent e)
	{
		sync(e);
	}

	@SubscribeEvent
	public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e)
	{
		sync(e);
	}

	@SubscribeEvent
	public static void respawn(PlayerEvent.PlayerRespawnEvent e)
	{
		sync(e);
	}

	private static void sync(PlayerEvent e)
	{
		if (e.getEntity() instanceof ServerPlayer p)
		{
			CableNetwork.get(p.serverLevel()).sync(p);
		}
	}

	@SubscribeEvent
	public static void shockContacts(TickEvent.LevelTickEvent e)
	{
		if (e.phase == TickEvent.Phase.END && e.level instanceof ServerLevel level)
		{
			CableNetwork.get(level).shockContacts();
		}
	}

	public static double unobstructedReach(net.minecraft.world.entity.player.Player player)
	{
		double reach = Math.min(64, player.getAttributeValue(ForgeMod.BLOCK_REACH.get()));

		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));

		BlockHitResult block = player.level()
			.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

		return block.getType() == HitResult.Type.MISS ? reach : Math.min(reach, eye.distanceTo(block.getLocation()));
	}
}
