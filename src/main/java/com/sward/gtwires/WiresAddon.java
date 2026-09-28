package com.sward.gtwires;

import com.gregtechceu.gtceu.api.addon.*;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

@GTAddon
public final class WiresAddon implements IGTAddon
{
	@Override
	public GTRegistrate getRegistrate()
	{
		return GregTechWires.REGISTRATE;
	}

	@Override
	public String addonModId()
	{
		return GregTechWires.ID;
	}

	@Override
	public void initializeAddon()
	{
	}

	@Override
	public void addRecipes(Consumer<FinishedRecipe> output)
	{
		ResourceLocation plankTagId = ResourceLocation.fromNamespaceAndPath("forge", "plates/wood");
		ResourceLocation stickTagId = ResourceLocation.fromNamespaceAndPath("forge", "rods/wood");

		GTRecipeBuilder.of(GregTechWires.id("assemble_spools"), GTRecipeTypes.ASSEMBLER_RECIPES)
			.inputItems(TagKey.create(Registries.ITEM, plankTagId), 2)
			.inputItems(TagKey.create(Registries.ITEM, stickTagId))
			.outputItems(GregTechWires.SPOOL.get(), 2)
			.duration(80)
			.EUt(8)
			.circuitMeta(8)
			.save(output);

		ResourceLocation treatedPlankTagId = ResourceLocation.fromNamespaceAndPath("forge", "plates/treated_wood");
		ResourceLocation treatedStickTagId = ResourceLocation.fromNamespaceAndPath("forge", "rods/treated_wood");

		GTRecipeBuilder.of(GregTechWires.id("assemble_spools_from_treated_wood"), GTRecipeTypes.ASSEMBLER_RECIPES)
			.inputItems(TagKey.create(Registries.ITEM, treatedPlankTagId), 2)
			.inputItems(TagKey.create(Registries.ITEM, treatedStickTagId))
			.outputItems(GregTechWires.SPOOL.get(), 4)
			.duration(80)
			.EUt(8)
			.circuitMeta(8)
			.save(output);
	}
}
