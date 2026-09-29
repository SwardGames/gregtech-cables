package com.sward.gtcables.util;

import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.ColorSprayBehaviour;
import com.sward.gtcables.GregTechCables;
import com.sward.gtcables.items.SpoolItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.ImmutablePair;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class CableTools
{
	public static final TagKey<Item> CUTTERS = TagKey.create(Registries.ITEM, GregTechCables.id("wire_cutters"));

	public static boolean isCutter(ItemStack stack)
	{
		return stack.is(CUTTERS);
	}

	public static boolean isSpool(ItemStack stack, boolean canBeEmpty)
	{
		if (!stack.is(GregTechCables.SPOOL.get()))
		{
			return false;
		}

		if (!canBeEmpty)
		{
			return SpoolItem.type(stack) != null;
		}

		return true;
	}

	public static ImmutablePair<DyeColor, ColorSprayBehaviour> getSprayCan(ItemStack stack)
	{
		if (stack.isEmpty())
		{
			return null;
		}

		for (DyeColor color : DyeColor.values())
		{
			ComponentItem item = GTItems.SPRAY_CAN_DYES[color.getId()].get();

			if (!stack.is(item))
			{
				continue;
			}

			for (IItemComponent component : item.getComponents())
			{
				if (component instanceof ColorSprayBehaviour spray)
				{
					return ImmutablePair.of(color, spray);
				}
			}
		}

		ComponentItem item = GTItems.SPRAY_SOLVENT.get();

		if (!stack.is(item))
		{
			return null;
		}

		for (IItemComponent component : item.getComponents())
		{
			if (component instanceof ColorSprayBehaviour spray)
			{
				return ImmutablePair.of(null, spray);
			}
		}

		return null;
	}
}
