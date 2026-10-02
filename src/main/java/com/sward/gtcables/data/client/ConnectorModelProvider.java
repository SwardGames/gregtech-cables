package com.sward.gtcables.data.client;

import com.sward.gtcables.GregTechCables;
import com.sward.gtcables.blocks.ConnectorBlock;
import com.sward.gtcables.blocks.ConnectorColor;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.EnumMap;
import java.util.Map;

public class ConnectorModelProvider extends BlockStateProvider
{
	public ConnectorModelProvider(
		PackOutput output,
		ExistingFileHelper existingFileHelper
	)
	{
		super(output, GregTechCables.ID, existingFileHelper);
	}

	@Override
	protected void registerStatesAndModels()
	{
		Block connector = GregTechCables.CONNECTOR.get();

		Map<ConnectorColor, ModelFile> colorModels = new EnumMap<>(ConnectorColor.class);

		for (ConnectorColor color : ConnectorColor.values())
		{
			String name = "connector_" + color.getSerializedName();

			String textureName = color == ConnectorColor.UNPAINTED
				? "connector"
				: name;

			ResourceLocation texture = modLoc("block/" + textureName);

			ModelFile model = models()
				.withExistingParent(name, modLoc("block/connector"))
				.texture("0", texture)
				.texture("particle", texture);

			colorModels.put(color, model);
		}

		getVariantBuilder(connector).forAllStatesExcept(
			state ->
			{
				Direction facing = state.getValue(ConnectorBlock.FACING);

				int rotationX = switch (facing)
				{
					case UP -> 0;
					case DOWN -> 180;
					default -> 270;
				};

				int rotationY = switch (facing)
				{
					case NORTH -> 180;
					case EAST -> 270;
					case WEST -> 90;
					default -> 0;
				};

				return ConfiguredModel.builder()
					.modelFile(colorModels.get(state.getValue(ConnectorBlock.COLOR)))
					.rotationX(rotationX)
					.rotationY(rotationY)
					.build();
			},
			ConnectorBlock.WATERLOGGED
		);

		simpleBlockItem(
			connector,
			colorModels.get(ConnectorColor.UNPAINTED)
		);
	}
}
