package com.sward.gtwires;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.item.PipeBlockItem;
import com.gregtechceu.gtceu.common.block.CableBlock;
import com.gregtechceu.gtceu.common.pipelike.cable.Insulation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public record WireType(
	ResourceLocation id,
	PipeBlockItem item,
	Material material,
	Insulation insulation,
	WireProperties wireProperties
)
{
	private static final HashMap<Item, WireType> WireTypes = new HashMap<>();
	private static final HashMap<ResourceLocation, WireType> WireTypeFromIds = new HashMap<>();

	public static @Nullable WireType of(@NotNull Item item)
	{
		if (WireTypes.containsKey(item))
		{
			return WireTypes.get(item);
		}

		return createWireType(ForgeRegistries.ITEMS.getKey(item), item);
	}

	public static @Nullable WireType of(@NotNull ResourceLocation id)
	{
		if (WireTypeFromIds.containsKey(id))
		{
			return WireTypeFromIds.get(id);
		}

		return createWireType(id, ForgeRegistries.ITEMS.getValue(id));
	}

	public long voltage()
	{
		return wireProperties.getVoltage();
	}

	public int amps()
	{
		return wireProperties.getAmperage();
	}

	public int lossPerMetre()
	{
		return wireProperties.getLossPerBlock();
	}

	public int color()
	{
		return insulation.isCable ? 0x34343c : material.getMaterialRGB();
	}

	public float thickness()
	{
		return insulation.thickness * 0.28F;
	}

	public boolean shockHazard()
	{
		return insulation.insulationLevel == -1 && !wireProperties.isSuperconductor() && wireProperties.getLossPerBlock() > 0;
	}

	private static @Nullable WireType createWireType(ResourceLocation id, Item item)
	{
		if (!(item instanceof PipeBlockItem bi) || !(bi.getBlock() instanceof CableBlock cable))
		{
			return null;
		}

		WireType result = new WireType(
			id,
			bi,
			cable.material,
			cable.pipeType,
			cable.createProperties(cable.defaultBlockState(), new ItemStack(item))
		);

		WireTypes.put(item, result);
		WireTypeFromIds.put(id, result);

		return result;
	}
}
