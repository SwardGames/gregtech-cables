package com.sward.gtwires.events;

import com.gregtechceu.gtceu.common.item.ColorSprayBehaviour;
import com.sward.gtwires.GregTechWires;
import com.sward.gtwires.client.ClientCableNetwork;
import com.sward.gtwires.graph.CableNetwork;
import com.sward.gtwires.util.CableTools;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.ImmutablePair;

@Mod.EventBusSubscriber(modid = GregTechWires.ID)
public class PlayerInteractEventHandlers
{
	@SubscribeEvent
	public static void rightClickItem(PlayerInteractEvent.RightClickItem e)
	{
		rightClickAny(e);
	}

	@SubscribeEvent
	public static void rightClickBlock(PlayerInteractEvent.RightClickBlock e)
	{
		rightClickAny(e);
	}

	@SubscribeEvent
	public static void rightClickEntity(PlayerInteractEvent.EntityInteract e)
	{
		rightClickAny(e);
	}

	private static void rightClickAny(PlayerInteractEvent e)
	{
		if (!e.getEntity().mayBuild())
		{
			return;
		}

		ItemStack stack = e.getItemStack();

		if (CableTools.isCutter(stack))
		{
			if (e.getSide() == LogicalSide.CLIENT)
			{
				ClientCableNetwork.useWireCutters(e);
			}
			else
			{
				CableNetwork.get((ServerLevel) e.getLevel()).useWireCutters(e);
			}

			return;
		}

		ImmutablePair<DyeColor, ColorSprayBehaviour> spray = CableTools.getSprayCan(stack);

		if (spray != null)
		{
			if (e.getSide() == LogicalSide.CLIENT)
			{
				ClientCableNetwork.useSprayCan(e, spray.getLeft(), spray.getRight());
			}
			else
			{
				CableNetwork.get((ServerLevel) e.getLevel()).useSprayCan(e, spray.getLeft(), spray.getRight());
			}
		}
	}
}
