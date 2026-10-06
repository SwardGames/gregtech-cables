package com.sward.gtcables.items;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.sward.gtcables.CablesConfig;
import com.sward.gtcables.blocks.ConnectorBlock;
import com.sward.gtcables.CableType;
import com.sward.gtcables.graph.CableGeometry;
import com.sward.gtcables.graph.CableGraph;
import com.sward.gtcables.graph.CableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SpoolItem extends Item
{
	public SpoolItem()
	{
		super(new Properties().stacksTo(64));
	}

	@Override
	public int getMaxStackSize(ItemStack stack)
	{
		return length(stack) == 0 ? 64 : 1;
	}

	public static int transferable(int source, int target)
	{
		return Math.min(source, CablesConfig.spoolCapacity() - target);
	}

	public static int removableItems(int length, int stackLimit)
	{
		return Math.min(length / 100, stackLimit);
	}

	public static int length(ItemStack stack)
	{
		return stack.hasTag()
			? Math.max(0, Math.min(CablesConfig.spoolCapacity(), stack.getTag().getInt("LengthCm")))
			: 0;
	}

	public static ResourceLocation type(ItemStack stack)
	{
		return stack.hasTag() ? ResourceLocation.tryParse(stack.getTag().getString("Wire")) : null;
	}

	public static void set(ItemStack stack, ResourceLocation cableType, int length)
	{
		CompoundTag tag = stack.getOrCreateTag();

		if (length <= 0 || cableType == null)
		{
			tag.remove("Wire");
			tag.remove("LengthCm");
		}
		else
		{
			tag.putString("Wire", cableType.toString());
			tag.putInt("LengthCm", Math.min(length, CablesConfig.spoolCapacity()));
		}

		tag.remove("Start");
		tag.remove("Dimension");

		if (tag.isEmpty())
		{
			stack.setTag(null);
		}
	}

	private static boolean insert(ItemStack spool, ItemStack wire)
	{
		CableType cableType = CableType.of(wire.getItem());

		if (cableType == null || length(spool) > CablesConfig.spoolCapacity() - 100 ||
			(length(spool) > 0 && !cableType.id().equals(type(spool))))
		{
			return false;
		}

		int count = Math.min(wire.getCount(), (CablesConfig.spoolCapacity() - length(spool)) / 100);

		set(spool, cableType.id(), length(spool) + count * 100);

		wire.shrink(count);

		return true;
	}

	private static boolean wind(ItemStack spool, ItemStack wire, Player player)
	{
		if (spool.getCount() == 1)
		{
			return insert(spool, wire);
		}

		if (length(spool) != 0)
		{
			return false;
		}

		int free = player.getInventory().getFreeSlot();

		if (free < 0)
		{
			return false;
		}

		ItemStack single = spool.copyWithCount(1);

		if (!insert(single, wire))
		{
			return false;
		}

		spool.shrink(1);

		player.getInventory().setItem(free, single);
		player.getInventory().setChanged();

		return true;
	}

	private static ItemStack extract(ItemStack spool)
	{
		ResourceLocation id = type(spool);

		if (id == null || !ForgeRegistries.ITEMS.containsKey(id))
		{
			return ItemStack.EMPTY;
		}

		Item item = ForgeRegistries.ITEMS.getValue(id);

		if (item == null)
		{
			return ItemStack.EMPTY;
		}

		int n = removableItems(length(spool), new ItemStack(item).getMaxStackSize());

		if (n == 0)
		{
			return ItemStack.EMPTY;
		}

		ItemStack out = new ItemStack(item, n);

		set(spool, id, length(spool) - n * 100);

		return out;
	}

	@Override
	public boolean overrideOtherStackedOnMe(
		@NotNull ItemStack spool,
		@NotNull ItemStack carried,
		@NotNull Slot slot,
		@NotNull ClickAction action,
		@NotNull Player player,
		@NotNull SlotAccess access
	)
	{
		if (action != ClickAction.SECONDARY || !slot.allowModification(player))
		{
			return false;
		}

		if (carried.isEmpty())
		{
			if (length(spool) == 0 || spool.getCount() != 1)
			{
				return false;
			}

			access.set(extract(spool));

			return true;
		}

		return wind(spool, carried, player);
	}

	@Override
	public boolean overrideStackedOnOther(
		@NotNull ItemStack spool,
		@NotNull Slot slot,
		@NotNull ClickAction action,
		@NotNull Player player
	)
	{
		if (action != ClickAction.SECONDARY || !slot.allowModification(player))
		{
			return false;
		}

		// Also support carrying a spool and right clicking a cable stack.
		if (!slot.getItem().isEmpty())
		{
			return wind(spool, slot.getItem(), player);
		}

		if (spool.getCount() != 1)
		{
			return false;
		}

		ItemStack copy = spool.copy();
		ItemStack out = extract(copy);

		if (out.isEmpty() || !slot.mayPlace(out) || slot.getMaxStackSize(out) < out.getCount())
		{
			return false;
		}

		slot.set(out);
		spool.setTag(copy.getTag());

		return true;
	}

	@Override
	public @NotNull InteractionResultHolder<ItemStack> use(
		@NotNull Level level,
		@NotNull Player player,
		@NotNull InteractionHand hand
	)
	{
		if (!player.isShiftKeyDown())
		{
			return InteractionResultHolder.pass(player.getItemInHand(hand));
		}

		ItemStack spool = player.getItemInHand(hand);
		CompoundTag tag = spool.getOrCreateTag();

		if (level.isClientSide)
		{
			return InteractionResultHolder.success(player.getItemInHand(hand));
		}

		tag.remove("Start");
		tag.remove("Dimension");

		if (tag.isEmpty())
		{
			spool.setTag(null);
		}

		message(player, "selection_cleared");

		return InteractionResultHolder.consume(player.getItemInHand(hand));

	}

	@Override
	public @NotNull InteractionResult useOn(UseOnContext context)
	{
		Level level = context.getLevel();
		Player player = context.getPlayer();

		if (player == null)
		{
			return InteractionResult.PASS;
		}

		if (!(player.isShiftKeyDown() || level.getBlockState(context.getClickedPos())
			.getBlock() instanceof ConnectorBlock))
		{
			return InteractionResult.PASS;
		}

		if (level.isClientSide)
		{
			return InteractionResult.SUCCESS;
		}

		ItemStack spool = context.getItemInHand();
		CompoundTag tag = spool.getOrCreateTag();

		if (spool.getCount() != 1 && length(spool) > 0)
		{
			return InteractionResult.FAIL;
		}

		BlockPos end = context.getClickedPos();

		if (player.isShiftKeyDown())
		{
			tag.remove("Start");
			tag.remove("Dimension");

			if (tag.isEmpty())
			{
				spool.setTag(null);
			}

			message(player, "selection_cleared");

			return InteractionResult.CONSUME;
		}

		ResourceLocation type = type(spool);

		if (length(spool) == 0 || type == null)
		{
			if (tag.isEmpty())
			{
				spool.setTag(null);
			}

			message(player, "empty");

			return InteractionResult.CONSUME;
		}

		if (!tag.contains("Start") || !tag.getString("Dimension").equals(level.dimension().location().toString()))
		{
			tag.putLong("Start", end.asLong());
			tag.putString("Dimension", level.dimension().location().toString());
			message(player, "selected");

			return InteractionResult.CONSUME;
		}

		BlockPos start = BlockPos.of(tag.getLong("Start"));

		CableNetwork network = CableNetwork.get((ServerLevel) level);

		if (!network.hasConnector(start))
		{
			tag.remove("Start");
			message(player, "missing_start");

			return InteractionResult.CONSUME;
		}

		if (!level.mayInteract(player, start) || !level.mayInteract(player, end) || !player.mayBuild())
		{
			return InteractionResult.FAIL;
		}

		if (start.equals(end))
		{
			message(player, "same_connector");

			return InteractionResult.CONSUME;
		}

		int cm = CableGraph.lengthCm(start, end);

		if (cm > CablesConfig.connectionMaxLength())
		{
			message(player, "too_long");

			return InteractionResult.CONSUME;
		}

		if (cm > length(spool))
		{
			message(player, "insufficient_wire");

			return InteractionResult.CONSUME;
		}

		CableType cableType = CableType.of(type);

		if (cableType == null)
		{
			message(player, "invalid_wire");

			return InteractionResult.CONSUME;
		}

		switch (CablesConfig.cableIntersectionTest())
		{
			case LINE ->
			{
				if (CableGeometry.lineObstructed(
					level,
					network.getConnectorPosition(start),
					network.getConnectorPosition(end)
				))
				{
					message(player, "cable_obstructed");

					return InteractionResult.CONSUME;
				}
			}
			case CABLE ->
			{
				if (CableGeometry.cableObstructed(
					level,
					network.getConnectorPosition(start),
					network.getConnectorPosition(end)
				))
				{
					message(player, "cable_obstructed");

					return InteractionResult.CONSUME;
				}
			}
		}

		if (!network.connect(start, end, cableType, cm))
		{
			message(player, "duplicate");

			return InteractionResult.CONSUME;
		}

		set(spool, cableType.id(), length(spool) - cm);

		message(player, "connected");

		return InteractionResult.CONSUME;
	}

	@Override
	public @NotNull Component getName(@NotNull ItemStack stack)
	{
		ResourceLocation type = type(stack);

		if (type == null)
		{
			return Component.translatable("item.gtcables.spool.empty");
		}

		Item wireItem = ForgeRegistries.ITEMS.getValue(type);

		if (wireItem == null)
		{
			return Component.translatable("item.gtcables.spool.filled", Component.literal("invalid"));
		}

		return Component.translatable("item.gtcables.spool.filled", wireItem.getName(new ItemStack(wireItem)));
	}

	public static void message(Player player, String key)
	{
		player.displayClientMessage(Component.translatable("message.gtcables." + key), true);
	}

	@Override
	public boolean isBarVisible(@NotNull ItemStack stack)
	{
		return length(stack) > 0;
	}

	@Override
	public int getBarWidth(@NotNull ItemStack stack)
	{
		return Math.round(13f * length(stack) / CablesConfig.spoolCapacity());
	}

	@Override
	public int getBarColor(@NotNull ItemStack stack)
	{
		return 0xd79849;
	}

	@Override
	public void appendHoverText(
		@NotNull ItemStack stack,
		Level level,
		List<Component> tooltip,
		@NotNull TooltipFlag flag
	)
	{
		tooltip.add(Component.translatable(
			"tooltip.gtcables.length",
			String.format(java.util.Locale.ROOT, "%.2f", length(stack) / 100D),
			String.format(java.util.Locale.ROOT, "%.2f", CablesConfig.spoolCapacity() / 100D)
		));

		ResourceLocation id = type(stack);

		if (id != null)
		{
			CableType cableType = CableType.of(id);

			if (cableType != null)
			{
				int tier = GTUtil.getTierByVoltage(cableType.voltage());

				WireProperties wireProperties = cableType.wireProperties();

				if (wireProperties.isSuperconductor())
				{
					tooltip.add(Component.translatable("gtceu.cable.superconductor", GTValues.VN[tier]));
				}

				tooltip.add(Component.translatable(
					"gtceu.cable.voltage",
					FormattingUtil.formatNumbers(wireProperties.getVoltage()), GTValues.VNF[tier]
				));

				tooltip.add(Component.translatable(
					"gtceu.cable.amperage",
					FormattingUtil.formatNumbers(wireProperties.getAmperage())
				));

				tooltip.add(Component.translatable(
					"gtceu.cable.loss_per_block",
					FormattingUtil.formatNumbers(wireProperties.getLossPerBlock())
				));
			}
		}

		if (stack.hasTag() && stack.getTag().contains("Start"))
		{
			tooltip.add(Component.translatable(
				"tooltip.gtcables.selected",
				BlockPos.of(stack.getTag().getLong("Start")).toShortString()
			));
		}
	}
}
