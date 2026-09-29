package com.sward.gtcables;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.sward.gtcables.blocks.ConnectorBlock;
import com.sward.gtcables.blocks.ConnectorEntity;
import com.sward.gtcables.items.SpoolItem;
import com.sward.gtcables.network.CablePackets;
import com.sward.gtcables.recipes.SpoolRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(GregTechCables.ID)
public final class GregTechCables
{
	public static final String ID = "gtcables";

	public static final Logger LOGGER = LogManager.getLogger(ID);

	public static final GTRegistrate REGISTRATE = GTRegistrate.create(ID);

	// Registers
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
	public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(
		ForgeRegistries.BLOCK_ENTITY_TYPES,
		ID
	);
	public static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(
		ForgeRegistries.RECIPE_SERIALIZERS,
		ID
	);

	// Blocks
	public static final RegistryObject<Block> CONNECTOR = BLOCKS.register("connector", ConnectorBlock::new);

	// Items
	public static final RegistryObject<Item> CONNECTOR_ITEM = ITEMS.register(
		"connector",
		() -> new BlockItem(CONNECTOR.get(), new Item.Properties())
	);
	public static final RegistryObject<Item> SPOOL = ITEMS.register("spool", SpoolItem::new);

	// Block Entities
	public static final RegistryObject<BlockEntityType<ConnectorEntity>> CONNECTOR_ENTITY = ENTITIES.register(
		"connector",
		() -> BlockEntityType.Builder.of(ConnectorEntity::new, CONNECTOR.get()).build(null)
	);

	// Recipes
	public static final RegistryObject<RecipeSerializer<SpoolRecipe>> SPOOL_RECIPE = RECIPES.register(
		"spool_transfer",
		() -> new SimpleCraftingRecipeSerializer<>(SpoolRecipe::new)
	);

	public GregTechCables(FMLJavaModLoadingContext context)
	{
		IEventBus bus = context.getModEventBus();

		context.registerConfig(ModConfig.Type.COMMON, CablesConfig.SPEC);

		BLOCKS.register(bus);
		ITEMS.register(bus);
		ENTITIES.register(bus);
		RECIPES.register(bus);
		REGISTRATE.registerRegistrate();

		CablePackets.init();

		bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e) ->
		{
			if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
			{
				e.accept(SPOOL.get());
				e.accept(CONNECTOR_ITEM.get());
			}
		});
	}

	public static ResourceLocation id(String path)
	{
		return ResourceLocation.fromNamespaceAndPath(ID, path);
	}
}
