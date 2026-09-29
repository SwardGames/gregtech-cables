package com.sward.gtcables.client;

import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.gregtechceu.gtceu.common.item.ColorSprayBehaviour;
import com.mojang.blaze3d.vertex.*;
import com.sward.gtcables.*;
import com.sward.gtcables.graph.Cable;
import com.sward.gtcables.graph.CableGeometry;
import com.sward.gtcables.graph.CableGraph;
import com.sward.gtcables.graph.CableHitResult;
import com.sward.gtcables.items.SpoolItem;
import com.sward.gtcables.network.clientbound.*;
import com.sward.gtcables.util.CableTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.gregtechceu.gtceu.api.blockentity.IPaintable.UNPAINTED_COLOR;

@Mod.EventBusSubscriber(modid = GregTechCables.ID, value = Dist.CLIENT)
public final class ClientCableNetwork
{
	private record Visual(Cable cable, float width, CableGeometry.Mesh mesh, CableGeometry.Mesh outlineMesh)
	{
		public static Visual create(Cable cable)
		{
			Vec3 aPos = GRAPH.getConnectorPosition(cable.a);
			Vec3 bPos = GRAPH.getConnectorPosition(cable.b);

			float width = cable.cableType.thickness();

			return new Visual(
				cable,
				width,
				new CableGeometry.Mesh(aPos, bPos, width / 2),
				new CableGeometry.Mesh(aPos, bPos, width / 2 + 0.015D)
			);
		}
	}

	// The render type used to render wires
	private static final RenderType RENDER_TYPE = RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS);

	// Used to render normal connection highlights
	private static final int WHITE = 0xFFFFFFFF;

	// Used to render valid connection highlights
	private static final int GREEN = 0xFF77FF55;

	// Used to render invalid connection highlights
	private static final int RED = 0xFFFF5544;

	// The client-side cable graph
	private static final CableGraph GRAPH = new CableGraph();

	// All wires which have a baked mesh
	private static final Map<Cable, Visual> CABLE_VISUALS = new LinkedHashMap<>();

	// The current dimension that the client information is for
	private static ResourceLocation dimension;


	@SubscribeEvent
	public static void logout(ClientPlayerNetworkEvent.LoggingOut e)
	{
		GRAPH.clear();
		CABLE_VISUALS.clear();
		dimension = null;
	}

	private static boolean ready()
	{
		Minecraft mc = Minecraft.getInstance();

		return mc.level != null && mc.player != null && mc.level.dimension().location().equals(dimension);
	}

	public static void useWireCutters(PlayerInteractEvent e)
	{
		CableHitResult hit = GRAPH.clip(e.getEntity());

		if (hit != null)
		{
			e.setCanceled(true);
			e.setCancellationResult(InteractionResult.SUCCESS);

			Minecraft mc = Minecraft.getInstance();

			Vec3 position = mc.player.position();

			mc.level.playLocalSound(
				position.x,
				position.y,
				position.z,
				GTSoundEntries.WIRECUTTER_TOOL.getMainEvent(),
				SoundSource.BLOCKS,
				0.7F,
				1.1F,
				false
			);
		}
	}

	public static void useSprayCan(
		@NotNull PlayerInteractEvent e,
		@Nullable DyeColor color,
		@NotNull ColorSprayBehaviour behaviour
	)
	{
		CableHitResult hit = GRAPH.clip(e.getEntity());

		if (hit != null)
		{
			e.setCanceled(true);
			e.setCancellationResult(InteractionResult.SUCCESS);

			Minecraft mc = Minecraft.getInstance();

			Vec3 position = mc.player.position();

			mc.level.playLocalSound(
				position.x,
				position.y,
				position.z,
				GTSoundEntries.SPRAY_CAN_TOOL.getMainEvent(),
				SoundSource.BLOCKS,
				0.7F,
				1.1F,
				false
			);
		}
	}

	@SubscribeEvent
	public static void render(RenderLevelStageEvent event)
	{
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !ready())
		{
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		Vec3 camera = event.getCamera().getPosition();
		int renderDistance = CablesConfig.connectionMaxLength();
		double radius = renderDistance == 0 ? mc.options.getEffectiveRenderDistance() * 16D : renderDistance;

		// Bounds used for rendering. Note that it has an infinite height.
		AABB renderBounds = new AABB(camera, camera).inflate(radius, Double.POSITIVE_INFINITY, radius);

		// First, remove all wires which are now out of range
		List<Cable> entriesToCull = new ArrayList<>(16);

		for (Cable cable : CABLE_VISUALS.keySet())
		{
			if (!cable.bounds.intersects(renderBounds))
			{
				entriesToCull.add(cable);
			}
		}

		for (Cable cable : entriesToCull)
		{
			CABLE_VISUALS.remove(cable);
		}

		// Second, add missing wires
		GRAPH.forEachOverlap(renderBounds, c -> CABLE_VISUALS.computeIfAbsent(c, Visual::create));

		PoseStack poses = event.getPoseStack();

		poses.pushPose();
		poses.translate(-camera.x, -camera.y, -camera.z);

		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		VertexConsumer out = buffers.getBuffer(RENDER_TYPE);

		for (Visual visual : CABLE_VISUALS.values())
		{
			if (!event.getFrustum().isVisible(visual.mesh.bounds()))
			{
				continue;
			}

			float width = 16 * visual.width;

			Cable cable = visual.cable();
			CableAppearance.Appearance appearance = CableAppearance.get(cable.cableType.id());

			sides(
				out,
				poses.last(),
				visual.mesh,
				appearance.sprite(),
				(cable.color != UNPAINTED_COLOR) ? cable.color : appearance.color(),
				width,
				false
			);
		}

		buffers.endBatch(RENDER_TYPE);

		ItemStack mainHandItem = mc.player.getMainHandItem();
		ItemStack offHandItem = mc.player.getOffhandItem();

		CableHitResult hit = GRAPH.clip(mc.player);

		if (hit != null)
		{
			Visual visual = CABLE_VISUALS.get(hit.cable());

			if (visual != null)
			{
				if (!renderHeldItemCableHighlight(visual, mainHandItem, buffers, poses))
				{
					renderHeldItemCableHighlight(visual, offHandItem, buffers, poses);
				}
			}
		}

		if (CableTools.isSpool(mainHandItem, false))
		{
			visualizeSpool(mainHandItem, mc, buffers, poses);
		}

		if (CableTools.isSpool(offHandItem, false))
		{
			visualizeSpool(offHandItem, mc, buffers, poses);
		}

		poses.popPose();
	}

	public static @Nullable CableHitResult targetCable()
	{
		return GRAPH.clip(Minecraft.getInstance().player);
	}

	private static boolean renderHeldItemCableHighlight(
		Visual visual,
		ItemStack stack,
		MultiBufferSource.BufferSource buffers,
		PoseStack poses
	)
	{
		if (CableTools.isCutter(stack))
		{
			VertexConsumer outline = buffers.getBuffer(CableHighlight.TYPE);

			sides(
				outline,
				poses.last(),
				visual.outlineMesh,
				null,
				0xFFFFFFFF,
				16 * visual.width,
				true
			);

			buffers.endBatch(CableHighlight.TYPE);

			return true;
		}

		ImmutablePair<DyeColor, ColorSprayBehaviour> spray = CableTools.getSprayCan(stack);

		if (spray != null)
		{
			VertexConsumer outline = buffers.getBuffer(CableHighlight.TYPE);

			sides(
				outline,
				poses.last(),
				visual.outlineMesh,
				null,
				spray.left == null ? 0x7FBFBFBF : spray.left.getMapColor().col,
				16 * visual.width,
				true
			);

			buffers.endBatch(CableHighlight.TYPE);

			return true;
		}

		return false;
	}

	private static void visualizeSpool(
		ItemStack mainHandItem,
		Minecraft mc,
		MultiBufferSource.BufferSource buffers,
		PoseStack poses
	)
	{
		CompoundTag tag = mainHandItem.getTag();
		ResourceLocation wireTypeId = SpoolItem.type(mainHandItem);
		CableType cableType = wireTypeId == null ? null : CableType.of(wireTypeId);

		if (tag != null && tag.contains("Start") && cableType != null)
		{
			long startId = tag.getLong("Start");
			BlockPos start = BlockPos.of(startId);
			Vec3 startPos = GRAPH.getConnectorPosition(start);

			boolean hasConnector = false;
			boolean isValid = true;
			Vec3 end;

			if (mc.hitResult instanceof BlockHitResult hit &&
				hit.getType() == HitResult.Type.BLOCK &&
				mc.level.getBlockState(hit.getBlockPos()).is(GregTechCables.CONNECTOR.get()))
			{
				BlockPos endBlockPos = hit.getBlockPos();

				hasConnector = true;
				end = GRAPH.getConnectorPosition(endBlockPos);

				if (start.equals(endBlockPos))
				{
					isValid = false;
				}

				int cm = CableGraph.lengthCm(start, endBlockPos);

				if (cm > CablesConfig.connectionMaxLength() || cm > SpoolItem.length(mainHandItem))
				{
					isValid = false;
				}
				else if (GRAPH.hasCable(startId, endBlockPos.asLong()))
				{
					isValid = false;
				}
			}
			else
			{
				end = mc.hitResult.getLocation();

				double dx = end.x - start.getX();
				double dy = end.y - start.getY();
				double dz = end.z - start.getZ();

				double cm = Math.sqrt(dx * dx + dy * dy + dz * dz) * 100;

				if (cm > CablesConfig.connectionMaxLength() || cm > SpoolItem.length(mainHandItem))
				{
					isValid = false;
				}
			}

			if (end.distanceToSqr(startPos) <= 272D * 272D)
			{
				float width = cableType.thickness();

				CableGeometry.Mesh mesh = new CableGeometry.Mesh(startPos, end, width / 2);

				VertexConsumer outline = buffers.getBuffer(CableHighlight.TYPE);
				sides(
					outline,
					poses.last(),
					mesh,
					null,
					isValid
						? hasConnector ? GREEN : WHITE
						: RED,
					16 * width,
					false
				);
				buffers.endBatch(CableHighlight.TYPE);
			}
		}
	}

	private static @NotNull Vec3 interpolate(@NotNull Vec3 a, @NotNull Vec3 b, double t)
	{
		return t <= 0 ? a : t >= 1 ? b : a.lerp(b, t);
	}

	private static void sides(
		VertexConsumer out,
		PoseStack.Pose pose,
		CableGeometry.Mesh mesh,
		TextureAtlasSprite sprite,
		int color,
		float width,
		boolean inverted
	)
	{
		ClientLevel level = Minecraft.getInstance().level;

		for (int ring = 0; ring < mesh.size() - 1; ring++)
		{
			double start = mesh.distance(ring);
			double end = mesh.distance(ring + 1);
			double cursor = start;

			int light = inverted
				? LightTexture.FULL_BRIGHT
				: LevelRenderer.getLightColor(level, BlockPos.containing(mesh.centre(ring)));

			// Split at metre boundaries so atlas UVs repeat continuously, without stretching or bleeding into adjacent sprites.
			while (cursor < end - 1e-10D)
			{
				double tile = Math.floor(cursor + 1e-9D);
				double next = Math.min(end, tile + 1);
				double t0 = (cursor - start) / (end - start);
				double t1 = (next - start) / (end - start);

				float u0 = sprite == null ? 0 : sprite.getU(8D - width / 2D);
				float u1 = sprite == null ? 1 : sprite.getU(8D + width / 2D);

				float v0 = sprite == null ? 0 : sprite.getV(16 * Math.max(0, Math.min(1, cursor - tile)));
				float v1 = sprite == null ? 1 : sprite.getV(16 * Math.max(0, Math.min(1, next - tile)));

				for (int face = 0; face < 4; face++)
				{
					int j = (face + 1) % 4;

					Vec3 a = interpolate(mesh.corner(ring, face), mesh.corner(ring + 1, face), t0);
					Vec3 b = interpolate(mesh.corner(ring, j), mesh.corner(ring + 1, j), t0);
					Vec3 c = interpolate(mesh.corner(ring, j), mesh.corner(ring + 1, j), t1);
					Vec3 d = interpolate(mesh.corner(ring, face), mesh.corner(ring + 1, face), t1);
					Vec3 n0 = mesh.normal(ring, face).lerp(mesh.normal(ring + 1, face), t0).normalize();
					Vec3 n1 = mesh.normal(ring, face).lerp(mesh.normal(ring + 1, face), t1).normalize();

					quad(
						out,
						pose,
						new Vec3[]{a, b, c, d},
						new Vec3[]{n0, n0, n1, n1},
						color,
						light,
						u0,
						u1,
						v0,
						v1,
						inverted
					);
				}

				cursor = next;
			}
		}
	}

	private static void quad(
		VertexConsumer out,
		PoseStack.Pose pose,
		Vec3[] points,
		Vec3[] normals,
		int color,
		int light,
		float u0,
		float u1,
		float v0,
		float v1,
		boolean inverted
	)
	{
		for (int k = 0; k < 4; k++)
		{
			int i = inverted ? 3 - k : k;

			Vec3 point = points[i];
			Vec3 normal = normals[i];

			if (inverted)
			{
				out.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
					.color((color >> 16) & 255, (color >> 8) & 255, color & 255, 255)
					.endVertex();
			}
			else
			{
				out.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
					.color((color >> 16) & 255, (color >> 8) & 255, color & 255, 255)
					.uv(i == 0 || i == 3 ? u0 : u1, i < 2 ? v0 : v1)
					.overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
					.normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
			}
		}
	}

	private static void burnEffects(BlockPos a, BlockPos b)
	{
		if (!ready())
		{
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		Vec3 aPos = Vec3.atCenterOf(a);
		Vec3 bPos = Vec3.atCenterOf(b);

		int samples = Math.min(256, Math.max(2, 5 * (int) Math.ceil(aPos.distanceTo(bPos))));

		Vec3 nearest = null;
		double bestDistance = 32 * 32;

		for (int i = 0; i <= samples; i++)
		{
			Vec3 point = CableGeometry.point(aPos, bPos, i / (double) samples);

			double distance = mc.player.distanceToSqr(point);

			if (distance > 64 * 64 || !mc.level.hasChunkAt(BlockPos.containing(point)))
			{
				continue;
			}

			mc.level.addParticle(ParticleTypes.FLAME, point.x, point.y, point.z, 0D, 0.015D, 0D);
			mc.level.addParticle(ParticleTypes.LARGE_SMOKE, point.x, point.y, point.z, 0D, 0.04D, 0D);

			if (distance < bestDistance)
			{
				bestDistance = distance;
				nearest = point;
			}
		}

		if (nearest != null)
		{
			mc.level.playLocalSound(
				nearest.x,
				nearest.y,
				nearest.z,
				SoundEvents.FIRE_EXTINGUISH,
				SoundSource.BLOCKS,
				0.7F,
				1.1F,
				false
			);
		}
	}

	/*
	 * PACKET HANDLERS
	 */

	public static void syncGraph(SyncGraphPacket packet)
	{
		GRAPH.clear();
		CABLE_VISUALS.clear();
		dimension = packet.dimension();

		for (SyncGraphPacket.ConnectorData node : packet.connectors())
		{
			GRAPH.addConnector(node.id(), node.direction());
		}

		for (SyncGraphPacket.CableData cable : packet.cables())
		{
			addCable(cable.id(), cable.a(), cable.b(), cable.wireType(), cable.color());
		}
	}

	public static void addConnectorPacket(AddConnectorPacket packet)
	{
		if (!packet.dimension().equals(dimension))
		{
			GregTechCables.LOGGER.error(
				"Received add node packet for dimension {} in dimension {}.",
				packet.dimension(),
				dimension
			);

			return;
		}

		GRAPH.addConnector(packet.id(), packet.direction());
	}

	public static void addCablePacket(AddCablePacket packet)
	{
		if (!packet.dimension().equals(dimension))
		{
			GregTechCables.LOGGER.error(
				"Received add cable packet for dimension {} in dimension {}.",
				packet.dimension(),
				dimension
			);

			return;
		}

		addCable(packet.id(), packet.a(), packet.b(), packet.wireType(), packet.color());
	}

	public static void removeConnectorPacket(RemoveConnectorPacket packet)
	{
		if (!packet.dimension().equals(dimension))
		{
			GregTechCables.LOGGER.error(
				"Received remove node packet for dimension {} in dimension {}.",
				packet.dimension(),
				dimension
			);

			return;
		}

		GRAPH.removeConnector(packet.id());
	}

	public static void removeCablePacket(RemoveCablePacket packet)
	{
		if (!packet.dimension().equals(dimension))
		{
			GregTechCables.LOGGER.error(
				"Received remove cable packet for dimension {} in dimension {}.",
				packet.dimension(),
				dimension
			);

			return;
		}

		removeCable(packet.id(), packet.burned());
	}

	public static void cableColorChangedPacket(CableColorChangedPacket packet)
	{
		if (!packet.dimension().equals(dimension))
		{
			GregTechCables.LOGGER.error(
				"Received cable color changed packet for dimension {} in dimension {}.",
				packet.dimension(),
				dimension
			);

			return;
		}

		Cable cable = GRAPH.getCable(packet.id());

		cable.color = packet.color();
	}

	private static void addCable(long id, BlockPos a, BlockPos b, ResourceLocation wireType, int color)
	{
		CableType cableType = CableType.of(wireType);

		if (cableType == null)
		{
			GregTechCables.LOGGER.error("Failed to find WireType for id '{}'. The cable will be discarded.", wireType);

			return;
		}

		GRAPH.addCable(id, a, b, a.asLong(), b.asLong(), cableType, CableGraph.lengthCm(a, b), color);
	}

	private static void removeCable(long id, boolean burned)
	{
		Cable cable = GRAPH.removeCable(id);

		if (cable != null)
		{
			CABLE_VISUALS.remove(cable);

			if (burned)
			{
				burnEffects(cable.a, cable.b);
			}
		}
	}
}
