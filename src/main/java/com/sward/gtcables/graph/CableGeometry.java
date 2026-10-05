package com.sward.gtcables.graph;

import com.sward.gtcables.GregTechCables;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

/**
 * Catenary shared by client rendering and authoritative server picking.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class CableGeometry
{
	private CableGeometry()
	{
	}

	/**
	 * One continuous, planar sweep. Adjacent faces reuse the exact same mitered corner vertices.
	 */
	public static final class Mesh
	{
		public final AABB bounds;
		public final double radius;

		private final Vec3[] points;
		private final Vec3[] across;
		private final Vec3[] up;
		private final Vec3[][] rings;
		private final double[] distances;

		public Mesh(Vec3 a, Vec3 b, double radius)
		{
			int count = segments(a, b) + 1;

			this.points = new Vec3[count];
			this.across = new Vec3[count];
			this.up = new Vec3[count];
			this.rings = new Vec3[count][4];
			this.distances = new double[count];

			Vec3 delta = b.subtract(a);
			double horizontal = Math.hypot(delta.x, delta.z);

			// A catenary lies in one vertical plane; a fixed binormal never flips at steep slopes.
			Vec3 u = horizontal > 1e-6D
				? new Vec3(-delta.z / horizontal, 0, delta.x / horizontal)
				: new Vec3(0, 0, delta.y >= 0 ? -1 : 1);

			for (int i = 0; i < count; i++)
			{
				this.points[i] = point(a, b, i / (double) (count - 1));

				if (i > 0)
				{
					this.distances[i] = this.distances[i - 1] + this.points[i].distanceTo(this.points[i - 1]);
				}
			}

			AABB total = new AABB(a, b);

			for (int i = 0; i < count; i++)
			{
				Vec3 incoming = this.points[i].subtract(this.points[Math.max(0, i - 1)]).normalize();
				Vec3 outgoing = this.points[Math.min(count - 1, i + 1)].subtract(this.points[i]).normalize();
				Vec3 tangent = i == 0 ? outgoing : i == count - 1 ? incoming : incoming.add(outgoing).normalize();
				Vec3 v = tangent.cross(u).normalize();

				double miter = i == 0 || i == count - 1 ? 1 : 1 / Math.max(0.5D, tangent.dot(incoming));

				this.across[i] = u;
				this.up[i] = v;

				Vec3 ru = u.scale(radius);
				Vec3 rv = v.scale(radius * miter);

				this.rings[i][0] = this.points[i].add(ru).add(rv);
				this.rings[i][1] = this.points[i].subtract(ru).add(rv);
				this.rings[i][2] = this.points[i].subtract(ru).subtract(rv);
				this.rings[i][3] = this.points[i].add(ru).subtract(rv);

				for (Vec3 corner : this.rings[i])
				{
					total = total.minmax(new AABB(corner, corner));
				}
			}

			this.bounds = total.inflate(1e-9D);
			this.radius = radius;
		}

		public int size()
		{
			return this.points.length;
		}

		public Vec3 centre(int i)
		{
			return this.points[i];
		}

		public Vec3 corner(int ring, int corner)
		{
			return this.rings[ring][corner];
		}

		public double distance(int i)
		{
			return this.distances[i];
		}

		public Vec3 normal(int ring, int side)
		{
			return switch (side)
			{
				case 0 -> this.up[ring];
				case 1 -> this.across[ring].scale(-1);
				case 2 -> this.up[ring].scale(-1);
				default -> this.across[ring];
			};
		}
	}

	/**
	 * Contact uses the same welded sweep as rendering, including the mitered joins.
	 */
	public static final class ContactShape
	{
		private final Segment[] segments;
		private final AABB bounds;

		public ContactShape(Vec3 a, Vec3 b, double radius)
		{
			Mesh mesh = new Mesh(a, b, radius);
			int count = mesh.size() - 1;

			this.segments = new Segment[count];

			for (int i = 0; i < count; i++)
			{
				Vec3[] vertices = new Vec3[8];

				for (int j = 0; j < 4; j++)
				{
					vertices[j] = mesh.corner(i, j);
					vertices[j + 4] = mesh.corner(i + 1, j);
				}

				this.segments[i] = new Segment(vertices);
			}

			this.bounds = mesh.bounds;
		}

		public boolean touches(AABB body)
		{
			if (!this.bounds.intersects(body))
			{
				return false;
			}

			for (Segment segment : this.segments)
			{
				if (segment.touches(body))
				{
					return true;
				}
			}

			return false;
		}
	}

	private static final Vec3[] WORLD_AXES = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};

	private static final class Segment
	{
		private final AABB bounds;
		private final Vec3[] axes;
		private final double[] min;
		private final double[] max;

		Segment(Vec3[] verts)
		{
			AABB box = new AABB(verts[0], verts[0]);

			for (Vec3 vertex : verts)
			{
				box = box.minmax(new AABB(vertex, vertex));
			}

			this.bounds = box.inflate(1e-9D);

			List<Vec3> separating = new ArrayList<>(List.of(WORLD_AXES));

			// Face normals and all edge/world-axis crosses: exact SAT for a convex sweep segment vs AABB.
			for (int[] face : new int[][]{
				{0, 1, 2, 3},
				{4, 7, 6, 5},
				{0, 4, 5, 1},
				{1, 5, 6, 2},
				{2, 6, 7, 3},
				{3, 7, 4, 0}
			})
			{
				separating.add(verts[face[1]].subtract(verts[face[0]]).cross(verts[face[2]].subtract(verts[face[0]])));

				for (int j = 0; j < 4; j++)
				{
					Vec3 edge = verts[face[(j + 1) % 4]].subtract(verts[face[j]]);

					for (Vec3 axis : WORLD_AXES)
					{
						separating.add(edge.cross(axis));
					}
				}
			}

			List<Vec3> unique = new ArrayList<>();

			for (Vec3 candidate : separating)
			{
				if (candidate.lengthSqr() > 1e-18D)
				{
					Vec3 axis = candidate.normalize();

					if (unique.stream().noneMatch(existing -> Math.abs(existing.dot(axis)) > 1 - 1e-10D))
					{
						unique.add(axis);
					}
				}
			}

			this.axes = unique.toArray(Vec3[]::new);
			this.min = new double[this.axes.length];
			this.max = new double[this.axes.length];

			for (int i = 0; i < this.axes.length; i++)
			{
				this.min[i] = Double.POSITIVE_INFINITY;
				this.max[i] = Double.NEGATIVE_INFINITY;

				for (Vec3 vertex : verts)
				{
					double value = vertex.dot(this.axes[i]);
					this.min[i] = Math.min(this.min[i], value);
					this.max[i] = Math.max(this.max[i], value);
				}
			}
		}

		boolean touches(AABB body)
		{
			if (!this.bounds.intersects(body))
			{
				return false;
			}

			Vec3 centre = body.getCenter();
			Vec3 half = new Vec3(body.getXsize() / 2, body.getYsize() / 2, body.getZsize() / 2);

			for (int i = 0; i < this.axes.length; i++)
			{
				Vec3 axis = this.axes[i];
				double value = centre.dot(axis);
				double radius = half.x * Math.abs(axis.x) + half.y * Math.abs(axis.y) + half.z * Math.abs(axis.z);

				if (value + radius < this.min[i] - 1e-9D || value - radius > this.max[i] + 1e-9D)
				{
					return false;
				}
			}

			return true;
		}
	}

	/// Returns how many segments are in a cable going from a to b.
	/// This will be at least 16 to ensure a smooth curve
	///
	/// @param a The cable's first point
	/// @param b The cable's second point
	/// @return How many segments are in the cable
	public static int segments(Vec3 a, Vec3 b)
	{
		return Math.max(16, (int) Math.ceil(a.distanceTo(b)));
	}

	/// Finds the position of the cable at the given distance from a to b
	///
	/// @param a The cable's first point
	/// @param b The cable's second point
	/// @param t The normalized distance from a to b
	/// @return The position at t
	public static Vec3 point(Vec3 a, Vec3 b, double t)
	{
		Vec3 delta = b.subtract(a);
		double horizontal = Math.hypot(delta.x, delta.z);

		if (horizontal < 1e-6D)
		{
			return a.lerp(b, t);
		}

//		double tension = Math.max(2, Math.pow(horizontal, 1.1D) * 1.5D);
		double tension = Math.max(2, horizontal * 1.5D);
		double ratio = delta.y / (2 * tension * Math.sinh(horizontal / (2 * tension)));
		double asinh = Math.copySign(Math.log(Math.abs(ratio) + Math.hypot(ratio, 1)), ratio);
		double shift = horizontal / 2 - tension * asinh;
		double y = tension * (Math.cosh((t * horizontal - shift) / tension) - Math.cosh(shift / tension));

		return new Vec3(a.x + delta.x * t, a.y + y, a.z + delta.z * t);
	}

	/// Calculates the hit distance to the given cable, or infinity if no hit is found
	///
	/// @param a         The cable's first point
	/// @param b         The cable's second point
	/// @param start     The start position
	/// @param direction The direction (must be unit)
	/// @param reach     How far to check
	/// @param radius    The radius of the cable
	/// @return The hit distance, or infinity if not found
	public static double hit(Vec3 a, Vec3 b, Vec3 start, Vec3 direction, double reach, double radius)
	{
		int n = segments(a, b);
		Vec3 previous = a;
		double best = Double.POSITIVE_INFINITY;

		for (int i = 1; i <= n; i++)
		{
			Vec3 next = point(a, b, i / (double) n);
			Vec3 edge = next.subtract(previous);
			Vec3 offset = start.subtract(previous);

			double ee = edge.lengthSqr();
			double de = direction.dot(edge);
			double doff = direction.dot(offset);
			double eo = edge.dot(offset);
			double divisor = ee - de * de;
			double along = divisor > 1e-12D ? (eo - de * doff) / divisor : 0;

			along = Math.max(0, Math.min(1, along));

			double ray = Math.max(0, Math.min(reach, previous.add(edge.scale(along)).subtract(start).dot(direction)));

			along = ee < 1e-12D
				? 0
				: Math.max(0, Math.min(1, start.add(direction.scale(ray)).subtract(previous).dot(edge) / ee));

			if (start.add(direction.scale(ray)).distanceToSqr(previous.add(edge.scale(along))) <= radius * radius)
			{
				best = Math.min(best, ray);
			}

			previous = next;
		}

		return best;
	}

	/// Calculates the bounds of the cable connecting the two given points.
	///
	/// @param a       The first point
	/// @param b       The second point
	/// @param padding How much padding to give the bounds
	/// @return The cables bounding box
	public static AABB bounds(Vec3 a, Vec3 b, double padding)
	{
		return bounds(a.x, a.y, a.z, b.x, b.y, b.z, padding);
	}

	/// Calculates the bounds of the cable connecting the two given points.
	///
	/// @param ax      The first point's x coordinate
	/// @param ay      The first point's y coordinate
	/// @param az      The first point's z coordinate
	/// @param bx      The second point's x coordinate
	/// @param by      The second point's y coordinate
	/// @param bz      The second point's z coordinate
	/// @param padding How much padding to give the bounds
	/// @return The cables bounding box
	public static AABB bounds(double ax, double ay, double az, double bx, double by, double bz, double padding)
	{
		double minX = Math.min(ax, bx);
		double minY = Math.min(ay, by);
		double minZ = Math.min(az, bz);
		double maxX = Math.max(ax, bx);
		double maxY = Math.max(ay, by);
		double maxZ = Math.max(az, bz);

		double horizontal = Math.hypot(maxX - minX, maxZ - minZ);

		if (horizontal > 1e-6D)
		{
			double tension = Math.max(2, horizontal * 1.5D);

			double ratio = (maxY - minY) / (2 * tension * Math.sinh(horizontal / (2 * tension)));

			double asinh = Math.copySign(Math.log(Math.abs(ratio) + Math.hypot(ratio, 1)), ratio);

			double shift = horizontal / 2 - tension * asinh;

			if (shift > 0 && shift < horizontal)
			{
				minY = Math.min(minY, minY + tension * (1 - Math.cosh(shift / tension)));
			}
		}

		return new AABB(
			minX - padding,
			minY - padding,
			minZ - padding,
			maxX + padding,
			maxY + padding,
			maxZ + padding
		);
	}

	/// Determines if the line between the two points would intersect any blocks not tagged 'gtcables:cable_passthrough'
	///
	/// @param level The level the cable is in
	/// @param a     The first point
	/// @param b     The second point
	/// @return If the cable hits a block
	public static boolean lineObstructed(Level level, Vec3 a, Vec3 b)
	{
		ClipContext clipContext = new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null)
		{
			@Override
			public VoxelShape getBlockShape(BlockState pBlockState, BlockGetter pLevel, BlockPos pPos)
			{
				if (pBlockState.is(GregTechCables.CABLE_PASSTHROUGH))
				{
					return Shapes.empty();
				}

				return super.getBlockShape(pBlockState, pLevel, pPos);
			}
		};

		return level.clip(clipContext).getType() == HitResult.Type.BLOCK;
	}

	/// Determines if a cable connecting two points would intersect any blocks not tagged 'gtcables:cable_passthrough'
	///
	/// @param level The level the cable is in
	/// @param a     The first connector position
	/// @param b     The second connector position
	/// @return If the cable hits a block
	public static boolean cableObstructed(Level level, Vec3 a, Vec3 b)
	{
		int segments = CableGeometry.segments(a, b);

		Vec3 prev = a;

		for (int i = 1; i <= segments; i++)
		{
			Vec3 next = CableGeometry.point(a, b, (double) i / segments);

			if (lineObstructed(level, prev, next))
			{
				return true;
			}

			prev = next;
		}

		return false;
	}

	public static boolean cableIntersects(VoxelShape shape, BlockPos blockPos, Vec3 a, Vec3 b)
	{
		AABB blockBounds = new AABB(blockPos).inflate(1e-7D);

		int segments = CableGeometry.segments(a, b);

		Vec3 prev = a;

		for (int i = 1; i <= segments; i++)
		{
			Vec3 next = CableGeometry.point(a, b, (double) i / segments);

			if (blockBounds.intersects(new AABB(prev, next)) && shape.clip(prev, next, blockPos) != null)
			{
				return true;
			}

			prev = next;
		}

		return false;
	}
}
