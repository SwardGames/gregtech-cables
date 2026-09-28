package com.sward.gtwires;

import com.sward.gtwires.core.CableGeometry;
import com.sward.gtwires.items.SpoolItem;
import com.sward.gtwires.network.WireNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = GregTechWires.ID)
public final class WireEvents
{
	public static final TagKey<Item> CUTTERS = TagKey.create(Registries.ITEM, GregTechWires.id("wire_cutters"));

	public static boolean isCutter(ItemStack stack)
	{
		return stack.is(CUTTERS);
	}

	public static boolean isSpool(ItemStack stack, boolean canBeEmpty)
	{
		if (!stack.is(GregTechWires.SPOOL.get()))
		{
			return false;
		}

		if (!canBeEmpty)
		{
			return SpoolItem.type(stack) != null;
		}

		return true;
	}

	@SubscribeEvent
	public static void login(PlayerEvent.PlayerLoggedInEvent e)
	{
		sync(e);
	}

	@SubscribeEvent
	public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e)
	{
		sync(e);
	}

	@SubscribeEvent
	public static void respawn(PlayerEvent.PlayerRespawnEvent e)
	{
		sync(e);
	}

	private static void sync(PlayerEvent e)
	{
		if (e.getEntity() instanceof ServerPlayer p)
		{
			WireNetwork.get(p.serverLevel()).sync(p);
		}
	}

	@SubscribeEvent
	public static void contacts(TickEvent.LevelTickEvent e)
	{
		if (e.phase == TickEvent.Phase.END && e.level instanceof ServerLevel level)
		{
			WireNetwork.get(level).shockPlayers();
		}
	}

	public static double unobstructedReach(net.minecraft.world.entity.player.Player player)
	{
		double reach = Math.min(64, player.getAttributeValue(ForgeMod.BLOCK_REACH.get()));
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));

		BlockHitResult block = player.level()
			.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

		return block.getType() == HitResult.Type.MISS ? reach : Math.min(reach, eye.distanceTo(block.getLocation()));
	}

	public static void cut(ServerPlayer player, long id)
	{
		if (!isCutter(player.getMainHandItem()) || !player.mayBuild() || player.isSpectator())
		{
			return;
		}

		WireNetwork net = WireNetwork.get(player.serverLevel());
		WireNetwork.Link link = net.link(id);

		if (link == null)
		{
			return;
		}

		WireType wire = WireType.of(link.wire());
		double radius = (wire == null ? 0.06 : wire.thickness() / 2) + 0.08;

		if (!Double.isFinite(CableGeometry.hit(
			Vec3.atCenterOf(link.a()), Vec3.atCenterOf(link.b()), player.getEyePosition(),
			player.getLookAngle(), unobstructedReach(player), radius
		)))
		{
			return;
		}

		if (!player.serverLevel().mayInteract(player, link.a()) || !player.serverLevel().mayInteract(player, link.b()))
		{
			return;
		}

		if (!net.recover(player, List.of(link), true))
		{
			SpoolItem.message(player, "no_space");
			return;
		}

		player.getMainHandItem().hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
		SpoolItem.message(player, "recovered");
	}
}
