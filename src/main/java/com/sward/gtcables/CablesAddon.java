package com.sward.gtcables;

import com.google.common.collect.ImmutableList;
import com.gregtechceu.gtceu.api.addon.*;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.function.Consumer;

@GTAddon
public final class CablesAddon implements IGTAddon
{
	@Override
	public GTRegistrate getRegistrate()
	{
		return GregTechCables.REGISTRATE;
	}

	@Override
	public String addonModId()
	{
		return GregTechCables.ID;
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

		GTRecipeBuilder.of(GregTechCables.id("assemble_spools"), GTRecipeTypes.ASSEMBLER_RECIPES)
			.inputItems(TagKey.create(Registries.ITEM, plankTagId), 2)
			.inputItems(TagKey.create(Registries.ITEM, stickTagId))
			.outputItems(GregTechCables.SPOOL.get(), 2)
			.duration(80)
			.EUt(8)
			.circuitMeta(8)
			.save(output);

		ResourceLocation treatedPlankTagId = ResourceLocation.fromNamespaceAndPath("forge", "plates/treated_wood");
		ResourceLocation treatedStickTagId = ResourceLocation.fromNamespaceAndPath("forge", "rods/treated_wood");

		GTRecipeBuilder.of(GregTechCables.id("assemble_spools_from_treated_wood"), GTRecipeTypes.ASSEMBLER_RECIPES)
			.inputItems(TagKey.create(Registries.ITEM, treatedPlankTagId), 2)
			.inputItems(TagKey.create(Registries.ITEM, treatedStickTagId))
			.outputItems(GregTechCables.SPOOL.get(), 4)
			.duration(80)
			.EUt(8)
			.circuitMeta(8)
			.save(output);

		ImmutableList<ImmutablePair<String, Integer>> connectorMaterials = ImmutableList.of(
			ImmutablePair.of("iron", 2),
			ImmutablePair.of("wrought_iron", 3),
			ImmutablePair.of("steel", 4),
			ImmutablePair.of("aluminium", 4),
			ImmutablePair.of("stainless_steel", 8),
			ImmutablePair.of("titanium", 12),
			ImmutablePair.of("tungsten_steel", 16)
		);

		for (ImmutablePair<String, Integer> material : connectorMaterials)
		{
			String mat = material.getLeft();
			GTRecipeBuilder.of(GregTechCables.id("assemble_connectors_from_" + mat), GTRecipeTypes.ASSEMBLER_RECIPES)
				.inputItems(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "plates/" + mat)))
				.inputItems(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "rings/" + mat)))
				.outputItems(GregTechCables.CONNECTOR_ITEM.get(), material.getRight())
				.duration(100)
				.EUt(16)
				.circuitMeta(5)
				.save(output);
		}
	}
}
