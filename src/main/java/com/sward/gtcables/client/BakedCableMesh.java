package com.sward.gtcables.client;

import com.ibm.icu.impl.Assert;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sward.gtcables.graph.CableGeometry;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BakedCableMesh
{
	private final int vertexCount;
	private final float[] vertexData;
	private final float[] uvData;
	private final float[] normalData;
	private final int[] ringPosData;

	public final AABB bounds;

	public BakedCableMesh(CableGeometry.Mesh mesh, @Nullable TextureAtlasSprite sprite)
	{
		IntList ringPosData = new IntArrayList();
		FloatList vertexData = new FloatArrayList();
		FloatList uvData = new FloatArrayList();
		FloatList normalData = new FloatArrayList();

		double radius = 16 * mesh.radius;

		int ringCount = mesh.size() - 1;

		for (int ring = 0; ring < ringCount; ring++)
		{
			double start = mesh.distance(ring);
			double end = mesh.distance(ring + 1);
			double cursor = start;

			BlockPos pos = BlockPos.containing(mesh.centre(ring));

			// Split at metre boundaries so atlas UVs repeat continuously, without stretching or bleeding into adjacent sprites.
			while (cursor < end - 1e-10D)
			{
				double tile = Math.floor(cursor + 1e-9D);
				double next = Math.min(end, tile + 1);
				double t0 = (cursor - start) / (end - start);
				double t1 = (next - start) / (end - start);

				float u0 = sprite == null ? 0 : sprite.getU(8D - radius);
				float u1 = sprite == null ? 1 : sprite.getU(8D + radius);

				float v0 = sprite == null ? 0 : sprite.getV(16 * Math.max(0, Math.min(1, cursor - tile)));
				float v1 = sprite == null ? 1 : sprite.getV(16 * Math.max(0, Math.min(1, next - tile)));

				ringPosData.add(pos.getX());
				ringPosData.add(pos.getY());
				ringPosData.add(pos.getZ());

				for (int face = 0; face < 4; face++)
				{
					int j = (face + 1) % 4;

					Vec3 a = interpolate(mesh.corner(ring, face), mesh.corner(ring + 1, face), t0);
					Vec3 b = interpolate(mesh.corner(ring, j), mesh.corner(ring + 1, j), t0);
					Vec3 c = interpolate(mesh.corner(ring, j), mesh.corner(ring + 1, j), t1);
					Vec3 d = interpolate(mesh.corner(ring, face), mesh.corner(ring + 1, face), t1);
					Vec3 n0 = mesh.normal(ring, face).lerp(mesh.normal(ring + 1, face), t0).normalize();
					Vec3 n1 = mesh.normal(ring, face).lerp(mesh.normal(ring + 1, face), t1).normalize();

					// a
					vertexData.add((float)a.x);
					vertexData.add((float)a.y);
					vertexData.add((float)a.z);

					uvData.add(u0);
					uvData.add(v0);

					normalData.add((float)n0.x);
					normalData.add((float)n0.y);
					normalData.add((float)n0.z);

					// b
					vertexData.add((float)b.x);
					vertexData.add((float)b.y);
					vertexData.add((float)b.z);

					uvData.add(u1);
					uvData.add(v0);

					normalData.add((float)n0.x);
					normalData.add((float)n0.y);
					normalData.add((float)n0.z);

					// c
					vertexData.add((float)c.x);
					vertexData.add((float)c.y);
					vertexData.add((float)c.z);

					uvData.add(u1);
					uvData.add(v1);

					normalData.add((float)n1.x);
					normalData.add((float)n1.y);
					normalData.add((float)n1.z);

					// d
					vertexData.add((float)d.x);
					vertexData.add((float)d.y);
					vertexData.add((float)d.z);

					uvData.add(u0);
					uvData.add(v1);

					normalData.add((float)n1.x);
					normalData.add((float)n1.y);
					normalData.add((float)n1.z);
				}

				cursor = next;
			}
		}

		int vertexCount = uvData.size() / 2;

		// Assert that the data is correct
		Assert.assrt((vertexCount % 16) == 0);
		Assert.assrt(vertexData.size() == vertexCount * 3);
		Assert.assrt(uvData.size() == vertexCount * 2);
		Assert.assrt(normalData.size() == vertexCount * 3);
		Assert.assrt(ringPosData.size() == (vertexCount / 16) * 3);

		this.vertexCount = vertexCount;
		this.vertexData = vertexData.toFloatArray();
		this.uvData = uvData.toFloatArray();
		this.normalData = normalData.toFloatArray();

		this.ringPosData = ringPosData.toIntArray();

		this.bounds = mesh.bounds;
	}

	public void render(VertexConsumer out, PoseStack.Pose pose, ClientLevel level, int color, boolean textured, boolean inverted)
	{
		final int r = FastColor.ARGB32.red(color);
		final int g = FastColor.ARGB32.green(color);
		final int b = FastColor.ARGB32.blue(color);
		final int a = FastColor.ARGB32.alpha(color);

		if (textured)
		{
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
			int light = 0;

			for (int i = 0; i < this.vertexCount / 16; ++i)
			{
				int posX = this.ringPosData[i * 3 + 0];
				int posY = this.ringPosData[i * 3 + 1];
				int posZ = this.ringPosData[i * 3 + 2];

				if (pos.getX() != posX || pos.getY() != posY || pos.getZ() != posZ)
				{
					pos.setX(posX);
					pos.setY(posY);
					pos.setZ(posZ);

					light = LevelRenderer.getLightColor(level, pos);
				}

				for (int j = 0; j < 4; ++j)
				{
					for (int k = 0; k < 4; ++k)
					{
						int v = i * 16 + j * 4 + (inverted ? 3 - k : k);

						out.vertex(pose.pose(), this.vertexData[v * 3 + 0],
								this.vertexData[v * 3 + 1], this.vertexData[v * 3 + 2])
							.color(r, g, b, a)
							.uv(this.uvData[v * 2 + 0], this.uvData[v * 2 + 1])
							.overlayCoords(OverlayTexture.NO_OVERLAY)
							.uv2(light)
							.normal(pose.normal(), this.normalData[v * 3 + 0],
								this.normalData[v * 3 + 1], this.normalData[v * 3 + 2])
							.endVertex();
					}
				}
			}
		}
		else
		{
			for (int i = 0; i < this.vertexCount / 4; ++i)
			{
				for (int j = 0; j < 4; ++j)
				{
					int v = i * 4 + (inverted ? 3 - j : j);

					out.vertex(pose.pose(), this.vertexData[v * 3 + 0],
							this.vertexData[v * 3 + 1], this.vertexData[v * 3 + 2])
						.color(r, g, b, a)
						.normal(pose.normal(), this.normalData[v * 3 + 0],
							this.normalData[v * 3 + 1], this.normalData[v * 3 + 2])
						.endVertex();
				}
			}
		}
	}

	private static @NotNull Vec3 interpolate(@NotNull Vec3 a, @NotNull Vec3 b, double t)
	{
		return t <= 0 ? a : t >= 1 ? b : a.lerp(b, t);
	}
}
