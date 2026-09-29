package com.sward.gtcables.graph;

import com.sward.gtcables.CableType;
import com.sward.gtcables.CablesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public final class Cable
{
	public final long id;
	public final BlockPos a;
	public final BlockPos b;
	public final @NotNull CableType cableType;
	public final int lengthCm;

	public final AABB bounds;
	public final long aId;
	public final long bId;
	public final long spanLoss;

	public int color;

	public Cable(long id, @NotNull BlockPos a, @NotNull BlockPos b, @NotNull CableType cableType, int lengthCm, int color)
	{
		if (a == b || lengthCm < 1 || lengthCm > CablesConfig.connectionMaxLength() || cableType == null)
		{
			throw new IllegalArgumentException("Invalid cable");
		}

		this.id = id;
		this.a = a;
		this.b = b;
		this.cableType = cableType;
		this.lengthCm = lengthCm;

		int x0 = Math.min(a.getX(), b.getX());
		int y0 = Math.min(a.getY(), b.getY());
		int z0 = Math.min(a.getZ(), b.getZ());

		int x1 = Math.max(a.getX(), b.getX()) + 1;
		int y1 = Math.max(a.getY(), b.getY()) + 1;
		int z1 = Math.max(a.getZ(), b.getZ()) + 1;

		this.bounds = new AABB(x0, y0, z0, x1, y1, z1);

		this.aId = a.asLong();
		this.bId = b.asLong();
		this.spanLoss = (long) lengthCm * cableType.lossPerMetre();

		this.color = color;
	}

	public long other(long node)
	{
		return node == aId ? bId : aId;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj == this)
		{
			return true;
		}

		if (obj == null || obj.getClass() != this.getClass())
		{
			return false;
		}

		Cable that = (Cable) obj;

		return this.id == that.id;
	}

	@Override
	public int hashCode()
	{
		return Long.hashCode(id);
	}

	@Override
	public String toString()
	{
		return "Wire[" +
			"id=" + id + ", " +
			"a=" + a + ", " +
			"b=" + b + ", " +
			"aId=" + aId + ", " +
			"bId=" + bId + ", " +
			"lengthCm=" + lengthCm + ", " +
			"wireType=" + cableType + ", " +
			"color=" + color + ']';
	}

	public long id()
	{
		return id;
	}
}
