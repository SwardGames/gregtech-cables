package com.sward.gtwires.client;

import com.gregtechceu.gtceu.common.block.CableBlock;
import com.sward.gtwires.*;
import com.sward.gtwires.items.SpoolItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = GregTechWires.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup
{
	@SubscribeEvent
	public static void setup(FMLClientSetupEvent e)
	{
		e.enqueueWork(
			() -> ItemProperties.register(
				GregTechWires.SPOOL.get(),
				GregTechWires.id("size"),
				(stack, level, entity, seed) ->
				{
					ResourceLocation type = SpoolItem.type(stack);

					if (type == null)
					{
						return 0;
					}

					Item item = ForgeRegistries.ITEMS.getValue(type);

					if (!(item instanceof BlockItem bi) || !(bi.getBlock() instanceof CableBlock cable))
					{
						return 0;
					}

					return cable.pipeType.amperage;
				}
			)
		);

		e.enqueueWork(
			() -> ItemProperties.register(
				GregTechWires.SPOOL.get(),
				GregTechWires.id("is_cable"),
				(stack, level, entity, seed) ->
				{
					ResourceLocation type = SpoolItem.type(stack);

					if (type == null)
					{
						return 0;
					}

					Item item = ForgeRegistries.ITEMS.getValue(type);

					if (!(item instanceof BlockItem bi) || !(bi.getBlock() instanceof CableBlock cable))
					{
						return 0;
					}

					return cable.pipeType.isCable() ? 1 : 0;
				}
			)
		);
	}

	@SubscribeEvent
	public static void colors(RegisterColorHandlersEvent.Item e)
	{
		e.register(
			(stack, tint) ->
			{
				ResourceLocation type = SpoolItem.type(stack);

				if (type == null)
				{
					return -1;
				}

				switch (tint)
				{
					case 0:
					{
						Item item = ForgeRegistries.ITEMS.getValue(type);

						if (!(item instanceof BlockItem bi) || !(bi.getBlock() instanceof CableBlock cable))
						{
							return -1;
						}

						return cable.material.getMaterialRGB();
					}
					case 1:
					{
						return 0x34343c;
					}
					default:
					{
						return -1;
					}
				}
			}, GregTechWires.SPOOL.get()
		);
	}
}
