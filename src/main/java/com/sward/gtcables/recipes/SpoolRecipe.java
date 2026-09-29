package com.sward.gtcables.recipes;

import com.sward.gtcables.GregTechCables;
import com.sward.gtcables.items.SpoolItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * First occupied slot is the receiving spool; second is donor, returned in its slot.
 */
public final class SpoolRecipe extends CustomRecipe
{
	public SpoolRecipe(@NotNull ResourceLocation id, @NotNull CraftingBookCategory category)
	{
		super(id, category);
	}

	private @NotNull List<Integer> slots(CraftingContainer inv)
	{
		List<Integer> slots = new ArrayList<>();

		for (int i = 0; i < inv.getContainerSize(); i++)
		{
			if (!inv.getItem(i).isEmpty())
			{
				if (!inv.getItem(i)
					.is(GregTechCables.SPOOL.get()) || (SpoolItem.length(inv.getItem(i)) > 0 && inv.getItem(i)
					.getCount() != 1))
				{
					return List.of();
				}

				slots.add(i);
			}
		}

		return slots;
	}

	@Override
	public boolean matches(@NotNull CraftingContainer inv, @NotNull Level level)
	{
		List<Integer> slots = slots(inv);

		if (slots.size() == 1)
		{
			return SpoolItem.length(inv.getItem(slots.get(0))) > 0;
		}

		if (slots.size() != 2)
		{
			return false;
		}

		ItemStack to = inv.getItem(slots.get(0));
		ItemStack from = inv.getItem(slots.get(1));

		return SpoolItem.length(from) > 0 && SpoolItem.type(from) != null &&
			(SpoolItem.length(to) == 0 || Objects.equals(SpoolItem.type(to), SpoolItem.type(from))) &&
			SpoolItem.transferable(SpoolItem.length(from), SpoolItem.length(to)) > 0;
	}

	@Override
	public @NotNull ItemStack assemble(@NotNull CraftingContainer inv, @NotNull RegistryAccess access)
	{
		List<Integer> slots = slots(inv);
		ItemStack out = new ItemStack(GregTechCables.SPOOL.get());

		if (slots.size() == 2)
		{
			ItemStack to = inv.getItem(slots.get(0));
			ItemStack from = inv.getItem(slots.get(1));

			SpoolItem.set(
				out,
				SpoolItem.type(from),
				SpoolItem.length(to) + SpoolItem.transferable(SpoolItem.length(from), SpoolItem.length(to))
			);
		}

		return out;
	}

	@Override
	public @NotNull NonNullList<ItemStack> getRemainingItems(CraftingContainer inv)
	{
		NonNullList<ItemStack> remains = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
		List<Integer> slots = slots(inv);

		if (slots.size() == 2)
		{
			ItemStack to = inv.getItem(slots.get(0));
			ItemStack from = inv.getItem(slots.get(1));
			ItemStack donor = new ItemStack(GregTechCables.SPOOL.get());

			SpoolItem.set(
				donor,
				SpoolItem.type(from),
				SpoolItem.length(from) - SpoolItem.transferable(SpoolItem.length(from), SpoolItem.length(to))
			);

			remains.set(slots.get(1), donor);
		}

		return remains;
	}

	@Override
	public boolean canCraftInDimensions(int w, int h)
	{
		return w * h >= 1;
	}

	@Override
	public @NotNull RecipeSerializer<?> getSerializer()
	{
		return GregTechCables.SPOOL_RECIPE.get();
	}
}
