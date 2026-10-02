package com.sward.gtcables.blocks;

import com.gregtechceu.gtceu.api.blockentity.IPaintable;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

@MethodsReturnNonnullByDefault
public enum ConnectorColor implements StringRepresentable
{
	UNPAINTED(null),
	WHITE(DyeColor.WHITE),
	ORANGE(DyeColor.ORANGE),
	MAGENTA(DyeColor.MAGENTA),
	LIGHT_BLUE(DyeColor.LIGHT_BLUE),
	YELLOW(DyeColor.YELLOW),
	LIME(DyeColor.LIME),
	PINK(DyeColor.PINK),
	GRAY(DyeColor.GRAY),
	LIGHT_GRAY(DyeColor.LIGHT_GRAY),
	CYAN(DyeColor.CYAN),
	PURPLE(DyeColor.PURPLE),
	BLUE(DyeColor.BLUE),
	BROWN(DyeColor.BROWN),
	GREEN(DyeColor.GREEN),
	RED(DyeColor.RED),
	BLACK(DyeColor.BLACK);

	@Nullable
	public final DyeColor dye;

	ConnectorColor(@Nullable DyeColor dye)
	{
		this.dye = dye;
	}


	@Override
	public String getSerializedName()
	{
		return this.dye == null ? "unpainted" : this.dye.getSerializedName();
	}

	public int rgb()
	{
		return this.dye != null ? this.dye.getMapColor().col : -1;
	}

	public static ConnectorColor fromRgb(int rgb)
	{
		if (rgb == IPaintable.UNPAINTED_COLOR)
		{
			return UNPAINTED;
		}

		for (ConnectorColor color : values())
		{
			if (color.rgb() == rgb)
			{
				return color;
			}
		}

		return UNPAINTED;
	}
}
