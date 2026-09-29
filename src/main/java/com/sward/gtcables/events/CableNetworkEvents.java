package com.sward.gtcables.events;

import com.sward.gtcables.GregTechCables;
import com.sward.gtcables.graph.CableNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GregTechCables.ID)
public final class CableNetworkEvents
{
	@SubscribeEvent
	public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent e)
	{
		sync(e);
	}

	@SubscribeEvent
	public static void playerDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent e)
	{
		sync(e);
	}

	@SubscribeEvent
	public static void playerRespawned(PlayerEvent.PlayerRespawnEvent e)
	{
		sync(e);
	}

	@SubscribeEvent
	public static void tickShockContacts(TickEvent.LevelTickEvent e)
	{
		if (e.phase == TickEvent.Phase.END && e.level instanceof ServerLevel level)
		{
			CableNetwork.get(level).shockContacts();
		}
	}

	private static void sync(PlayerEvent e)
	{
		if (e.getEntity() instanceof ServerPlayer p)
		{
			CableNetwork.get(p.serverLevel()).sync(p);
		}
	}
}
