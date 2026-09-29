package com.sward.gtcables.util;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

public final class PlayerHelper
{
	public static double unobstructedReach(net.minecraft.world.entity.player.Player player)
	{
		double reach = Math.min(64, player.getAttributeValue(ForgeMod.BLOCK_REACH.get()));

		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));

		BlockHitResult block = player.level()
			.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

		return block.getType() == HitResult.Type.MISS ? reach : Math.min(reach, eye.distanceTo(block.getLocation()));
	}
}
