package com.sward.gtcables.client;

import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconType;
import com.gregtechceu.gtceu.common.block.CableBlock;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.sward.gtcables.GregTechCables;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Read the actual baked GT block model, including resource-pack sprites, tint indices and overlays.
 */
@Mod.EventBusSubscriber(modid = GregTechCables.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CableAppearance
{
	public record Appearance(TextureAtlasSprite sprite, int color)
	{
	}

	private static final Map<ResourceLocation, Appearance> CACHE = new ConcurrentHashMap<>();

	@SubscribeEvent
	public static void rebaked(ModelEvent.BakingCompleted event)
	{
		CACHE.clear();
	}

	public static Appearance get(ResourceLocation cableType)
	{
		return CACHE.computeIfAbsent(cableType, CableAppearance::resolve);
	}

	private static Appearance resolve(ResourceLocation cableType)
	{
		Minecraft mc = Minecraft.getInstance();
		Function<ResourceLocation, TextureAtlasSprite> atlas = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS);

		if (!(ForgeRegistries.ITEMS.getValue(cableType) instanceof BlockItem item)
			|| !(item.getBlock() instanceof CableBlock cable))
		{
			return new Appearance(atlas.apply(MissingTextureAtlasSprite.getLocation()), 0xffffff);
		}

		return new Appearance(
			atlas.apply(cable.pipeType.isCable
				? GregTechCables.id("block/insulation")
				: MaterialIconType.wire.getBlockTexturePath(cable.material.getMaterialIconSet(), "side", true)),
			cable.pipeType.isCable ? GTMaterials.Rubber.getMaterialRGB() : cable.material.getMaterialRGB()
		);
	}
}
