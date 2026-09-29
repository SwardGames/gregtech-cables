package com.sward.gtcables.graph;

import com.sward.gtcables.CableType;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

@FieldsAreNonnullByDefault
public final class Cable
{
	public final long id;
	public final BlockPos a;
	public final BlockPos b;
	public final CableType cableType;
	public final int lengthCm;

	public final AABB bounds;
	public final long aId;
	public final long bId;
	public final long spanLoss;

	public int color;

	public Cable(long id, @NotNull BlockPos a, @NotNull BlockPos b, @NotNull CableType cableType, int lengthCm, int color)
	{
		if (a == b || lengthCm < 1)
		{
			throw new IllegalArgumentException("Invalid cable");
		}

		this.id = id;
		this.a = a;
		this.b = b;
		this.cableType = cableType;
		this.lengthCm = lengthCm;

		double x0 = Math.min(a.getX(), b.getX()) + 0.5D;
		double y0 = Math.min(a.getY(), b.getY()) + 0.5D;
		double z0 = Math.min(a.getZ(), b.getZ()) + 0.5D;

		double x1 = Math.max(a.getX(), b.getX()) + 0.5D;
		double y1 = Math.max(a.getY(), b.getY()) + 0.5D;
		double z1 = Math.max(a.getZ(), b.getZ()) + 0.5D;

		this.bounds = CableGeometry.bounds(x0, y0, z0, x1, y1, z1, 0.5D);

		this.aId = a.asLong();
		this.bId = b.asLong();
		this.spanLoss = (long) lengthCm * cableType.lossPerMetre();

		this.color = color;
	}

	public long other(long node)
	{
		return node == this.aId ? this.bId : this.aId;
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
		return Long.hashCode(this.id);
	}

	@Override
	public String toString()
	{
		return "Cable[" +
			"id=" + this.id + ", " +
			"a=" + this.a + ", " +
			"b=" + this.b + ", " +
			"aId=" + this.aId + ", " +
			"bId=" + this.bId + ", " +
			"lengthCm=" + this.lengthCm + ", " +
			"cableType=" + this.cableType + ", " +
			"color=" + this.color + ']';
	}

	public long id()
	{
		return this.id;
	}
}
