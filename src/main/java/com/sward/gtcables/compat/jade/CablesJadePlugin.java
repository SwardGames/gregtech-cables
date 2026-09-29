package com.sward.gtcables.compat.jade;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.sward.gtcables.*;
import com.sward.gtcables.blocks.ConnectorBlock;
import com.sward.gtcables.client.ClientCableNetwork;
import com.sward.gtcables.graph.Cable;
import com.sward.gtcables.graph.CableHitResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.blockentity.IPaintable.UNPAINTED_COLOR;

/**
 * Jade discovers this class only when installed. No production class depends on Jade.
 */
@WailaPlugin
public final class CablesJadePlugin implements IWailaPlugin
{
	private static final String MARKER = "gtcables:cable";

	@Override
	public void registerClient(IWailaClientRegistration registration)
	{
		registration.registerBlockComponent(Provider.INSTANCE, ConnectorBlock.class);
		registration.markAsClientFeature(Provider.INSTANCE.getUid());
		registration.addRayTraceCallback((hit, accessor, original) ->
		{
			CableHitResult hitResult = ClientCableNetwork.targetCable();

			if (hitResult == null)
			{
				return accessor;
			}

			Minecraft mc = Minecraft.getInstance();

			if (hit != null && hit.getType() != HitResult.Type.MISS &&
				mc.player.getEyePosition().distanceTo(hit.getLocation()) + 0.001D < hitResult.distance())
			{
				return accessor;
			}

			CompoundTag data = new CompoundTag();

			data.putLong(MARKER, hitResult.cable().id);

			// Synthetic accessor located on the cable, not at either (possibly unloaded) endpoint.
			// Its immutable stats already arrived in our link sync; Jade needs no server request.
			return registration.blockAccessor().level(mc.level).player(mc.player)
				.hit(new BlockHitResult(hitResult.hit(), Direction.UP, BlockPos.containing(hitResult.hit()), false))
				.blockState(GregTechCables.CONNECTOR.get().defaultBlockState())
				.fakeBlock(new ItemStack(hitResult.cable().cableType.item()))
				.serverData(data).serverConnected(false).showDetails(registration.isShowDetailsPressed()).build();
		});
	}

	public static List<Component> description(CableHitResult hitResult, boolean details)
	{
		Cable cable = hitResult.cable();
		CableType cableType = cable.cableType;

		ArrayList<Component> lines = new java.util.ArrayList<>();

		lines.add(Component.translatable(
			"jade.gtcables.voltage",
			GTValues.VNF[GTUtil.getTierByVoltage(cableType.voltage())]
		));

		lines.add(Component.translatable("jade.gtcables.amperage", cableType.amps()));

		lines.add(Component.translatable("jade.gtcables.length", decimal(cable.lengthCm, 2)));

		if (cable.color != UNPAINTED_COLOR)
		{
			lines.add(
				Component.translatable("gtceu.top.stained", String.format("#%06X", cable.color))
					.withStyle(style -> style.withColor(cable.color))
			);
		}

		if (details)
		{
			lines.add(Component.translatable("jade.gtcables.endpoint_a", cable.a.toShortString()));
			lines.add(Component.translatable("jade.gtcables.endpoint_b", cable.b.toShortString()));
			lines.add(Component.translatable(
				"jade.gtcables.span_loss",
				decimal((long) cable.lengthCm * cableType.lossPerMetre(), 2)
			));
		}

		return lines;
	}

	private static String decimal(long value, int scale)
	{
		return BigDecimal.valueOf(value, scale).stripTrailingZeros().toPlainString();
	}

	private enum Provider implements IBlockComponentProvider
	{
		INSTANCE;

		@Override
		public ResourceLocation getUid()
		{
			return GregTechCables.id("wire_data");
		}

		@Override
		public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
		{
			if (!accessor.getServerData().contains(MARKER))
			{
				return;
			}

			CableHitResult hitResult = ClientCableNetwork.targetCable();

			if (hitResult == null || hitResult.cable().id != accessor.getServerData().getLong(MARKER))
			{
				return;
			}

			description(hitResult, accessor.showDetails()).forEach(tooltip::add);
		}
	}
}
