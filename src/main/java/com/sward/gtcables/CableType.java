package com.sward.gtcables;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.api.item.PipeBlockItem;
import com.gregtechceu.gtceu.common.block.CableBlock;
import com.gregtechceu.gtceu.common.pipelike.cable.Insulation;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;

@FieldsAreNonnullByDefault
@ParametersAreNonnullByDefault
public record CableType(
	ResourceLocation id,
	PipeBlockItem item,
	Material material,
	Insulation insulation,
	WireProperties wireProperties
)
{
	private static final HashMap<Item, CableType> Types = new HashMap<>();
	private static final HashMap<ResourceLocation, CableType> TypeFromIds = new HashMap<>();

	public static @Nullable CableType of(Item item)
	{
		if (Types.containsKey(item))
		{
			return Types.get(item);
		}

		ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);

		if (id == null)
		{
			return null;
		}

		return createCableType(id, item);
	}

	public static @Nullable CableType of(ResourceLocation id)
	{
		if (TypeFromIds.containsKey(id))
		{
			return TypeFromIds.get(id);
		}

		return createCableType(id, ForgeRegistries.ITEMS.getValue(id));
	}

	public long voltage()
	{
		return this.wireProperties.getVoltage();
	}

	public int amps()
	{
		return this.wireProperties.getAmperage();
	}

	public int lossPerMetre()
	{
		return this.wireProperties.getLossPerBlock();
	}

	public int color()
	{
		return this.insulation.isCable ? 0x34343c : this.material.getMaterialRGB();
	}

	public float thickness()
	{
		return this.insulation.thickness * 0.28F;
	}

	public boolean shockHazard()
	{
		return this.insulation.insulationLevel == -1 && !this.wireProperties.isSuperconductor() && this.wireProperties.getLossPerBlock() > 0;
	}

	private static @Nullable CableType createCableType(ResourceLocation id, @Nullable Item item)
	{
		if (!(item instanceof PipeBlockItem bi) || !(bi.getBlock() instanceof CableBlock cable))
		{
			return null;
		}

		CableType result = new CableType(
			id,
			bi,
			cable.material,
			cable.pipeType,
			cable.createProperties(cable.defaultBlockState(), new ItemStack(item))
		);

		Types.put(item, result);
		TypeFromIds.put(id, result);

		return result;
	}
}
