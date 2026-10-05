package com.sward.gtcables.events;

import com.gregtechceu.gtceu.common.item.ColorSprayBehaviour;
import com.sward.gtcables.GregTechCables;
import com.sward.gtcables.client.ClientCableNetwork;
import com.sward.gtcables.graph.Cable;
import com.sward.gtcables.graph.CableNetwork;
import com.sward.gtcables.util.CableTools;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.ImmutablePair;

@Mod.EventBusSubscriber(modid = GregTechCables.ID)
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

		if (!e.getLevel().isClientSide)
		{
			return;
		}

		if (!e.getLevel().dimension().location().equals(ClientCableNetwork.dimension()))
		{
			return;
		}

		ItemStack stack = e.getItemStack();

		if (!(stack.getItem() instanceof BlockItem item))
		{
			return;
		}

		BlockPlaceContext context = new BlockPlaceContext(e.getEntity(), e.getHand(), stack, e.getHitVec());

		if (!context.canPlace())
		{
			return;
		}

		context = item.updatePlacementContext(context);

		if (context == null)
		{
			return;
		}

		BlockPos pos = context.getClickedPos();
		BlockState state = item.getBlock().getStateForPlacement(context);

		if (state == null || state.is(GregTechCables.CABLE_PASSTHROUGH))
		{
			return;
		}

		Cable intersectingCable = CableNetworkEvents.getIntersectingCable(
			ClientCableNetwork.graph(),
			state.getCollisionShape(e.getLevel(), pos),
			pos
		);

		if (intersectingCable != null)
		{
			ClientCableNetwork.highlightCable(intersectingCable, 0xFF0000);

			e.setUseItem(Event.Result.DENY);
		}
	}

	@SubscribeEvent
	public static void rightClickEntity(PlayerInteractEvent.EntityInteract e)
	{
		rightClickAny(e);
	}

	@SubscribeEvent
	public static void rightClickEntitySpecific(PlayerInteractEvent.EntityInteractSpecific e)
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
