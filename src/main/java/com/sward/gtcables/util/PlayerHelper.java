package com.sward.gtcables.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeMod;

public final class PlayerHelper
{
	public static double unobstructedReach(Player player)
	{
		double reach = Math.min(64, player.getAttributeValue(ForgeMod.BLOCK_REACH.get()));

		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getLookAngle().scale(reach));

		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
			player,
			start,
			end,
			new AABB(start, end).inflate(1D),
			entity -> !entity.isSpectator() && entity.isPickable(),
			reach * reach
		);

		BlockHitResult blockHit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

		if (entityHit != null)
		{
			reach = Math.min(reach, start.distanceTo(entityHit.getLocation()));
		}

		if (blockHit.getType() != HitResult.Type.MISS)
		{
			reach = Math.min(reach, start.distanceTo(blockHit.getLocation()));
		}

		return reach;
	}
}
