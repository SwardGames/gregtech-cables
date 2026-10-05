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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
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
	private static final class Visual
		{
			private final Cable cable;
			private final BakedCableMesh mesh;
			private final BakedCableMesh outlineMesh;

			public int highlightColor;
			public double highlightTimeout;

			private Visual(Cable cable, BakedCableMesh mesh, BakedCableMesh outlineMesh)
			{
				this.cable = cable;
				this.mesh = mesh;
				this.outlineMesh = outlineMesh;
			}

			public static Visual create(Cable cable)
			{
				Vec3 aPos = GRAPH.getConnectorPosition(cable.a);
				Vec3 bPos = GRAPH.getConnectorPosition(cable.b);

				float width = cable.cableType.thickness();

				return new Visual(
					cable,
					new BakedCableMesh(
						new CableGeometry.Mesh(aPos, bPos, width / 2),
						CableAppearance.get(cable.cableType.id()).sprite()
					),
					new BakedCableMesh(
						new CableGeometry.Mesh(aPos, bPos, width / 2 + 0.015D),
						null
					)
				);
			}

			public Cable cable()
			{
				return this.cable;
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

				var that = (Visual) obj;

				return Objects.equals(this.cable, that.cable) &&
					Objects.equals(this.mesh, that.mesh) &&
					Objects.equals(this.outlineMesh, that.outlineMesh);
			}

			@Override
			public int hashCode()
			{
				return this.cable.hashCode();
			}

			@Override
			public String toString()
			{
				return "Visual[cable=" + this.cable + ']';
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

	public static CableGraph graph()
	{
		return GRAPH;
	}

	public static ResourceLocation dimension()
	{
		return dimension;
	}

	public static void highlightCable(Cable cable, int color)
	{
		Visual visual = CABLE_VISUALS.get(cable);

		if (visual == null)
		{
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		visual.highlightColor = color & 0xFFFFFF;
		visual.highlightTimeout = (double)mc.level.getGameTime() + mc.getFrameTime() + 10D;
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
		ProfilerFiller profiler = mc.getProfiler();

		profiler.push("gtcables_render");

		try
		{
			Vec3 camera = event.getCamera().getPosition();
			int renderDistance = CablesConfig.renderDistance();
			double radius = renderDistance == 0 ? mc.options.getEffectiveRenderDistance() * 16D : renderDistance;

			double time = (double)mc.level.getGameTime() + mc.getFrameTime();

			// Bounds used for rendering. Note that it has an infinite height.
			AABB renderBounds = new AABB(camera, camera).inflate(radius, Double.POSITIVE_INFINITY, radius);

			PoseStack poses = event.getPoseStack();

			MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

			profiler.push("gtcables_render_cables");

			try
			{
				renderCables(event, time, buffers, camera, renderBounds, poses);
			}
			finally
			{
				profiler.pop();
			}

			ItemStack mainHandItem = mc.player.getMainHandItem();
			ItemStack offHandItem = mc.player.getOffhandItem();

			profiler.push("gtcables_render_highlights");

			try
			{
				CableHitResult hit = GRAPH.clip(mc.player);

				if (hit != null)
				{
					Visual visual = CABLE_VISUALS.get(hit.cable());

					if (visual != null)
					{
						if (!renderHeldItemCableHighlight(visual, mainHandItem, buffers, camera, poses))
						{
							renderHeldItemCableHighlight(visual, offHandItem, buffers, camera, poses);
						}
					}
				}
			}
			finally
			{
				profiler.pop();
			}

			profiler.push("gtcables_visualize_spools");

			try
			{
				if (CableTools.isSpool(mainHandItem, false))
				{
					visualizeSpool(mainHandItem, mc, buffers, camera, poses);
				}

				if (CableTools.isSpool(offHandItem, false))
				{
					visualizeSpool(offHandItem, mc, buffers, camera, poses);
				}
			}
			finally
			{
				profiler.pop();
			}
		}
		finally
		{
			profiler.pop();
		}
	}

	public static @Nullable CableHitResult targetCable()
	{
		return GRAPH.clip(Minecraft.getInstance().player);
	}

	static void clearCableVisuals()
	{
		CABLE_VISUALS.clear();
	}

	private static void renderCables(
		RenderLevelStageEvent event,
		double time,
		MultiBufferSource.BufferSource buffers,
		Vec3 camera,
		AABB renderBounds,
		PoseStack poses
	)
	{
		VertexConsumer out = buffers.getBuffer(RENDER_TYPE);

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
		GRAPH.forEachCableInBounds(renderBounds, c -> CABLE_VISUALS.computeIfAbsent(c, Visual::create));

		ClientLevel level = Minecraft.getInstance().level;

		ObjectList<Visual> highlightedVisuals = new ObjectArrayList<>();

		// Finally, render all the cable visuals.
		for (Visual visual : CABLE_VISUALS.values())
		{
			if (!event.getFrustum().isVisible(visual.mesh.bounds))
			{
				continue;
			}

			Cable cable = visual.cable();
			CableAppearance appearance = CableAppearance.get(cable.cableType.id());

			visual.mesh.render(
				out,
				poses,
				level,
				camera,
				(cable.color != UNPAINTED_COLOR) ? cable.color : appearance.color(),
				true,
				false
			);

			if (time < visual.highlightTimeout)
			{
				highlightedVisuals.add(visual);
			}
		}

		buffers.endBatch(RENDER_TYPE);

		if (!highlightedVisuals.isEmpty())
		{
			VertexConsumer outline = buffers.getBuffer(CableHighlight.TYPE);

			for (Visual visual : highlightedVisuals)
			{
				int alpha = Mth.clamp((int)((visual.highlightTimeout - time) / 20 / 0.25D * 255), 0, 255);

				visual.outlineMesh.render(
					outline,
					poses,
					null,
					camera,
					visual.highlightColor | alpha << 24,
					false,
					true
				);
			}

			buffers.endBatch(CableHighlight.TYPE);
		}
	}

	private static boolean renderHeldItemCableHighlight(
		Visual visual,
		ItemStack stack,
		MultiBufferSource.BufferSource buffers,
		Vec3 camera,
		PoseStack poses
	)
	{
		if (CableTools.isCutter(stack))
		{
			VertexConsumer outline = buffers.getBuffer(CableHighlight.TYPE);

			visual.outlineMesh.render(
				outline,
				poses,
				null,
				camera,
				0xFFFFFFFF,
				false,
				true
			);

			buffers.endBatch(CableHighlight.TYPE);

			return true;
		}

		ImmutablePair<DyeColor, ColorSprayBehaviour> spray = CableTools.getSprayCan(stack);

		if (spray != null)
		{
			VertexConsumer outline = buffers.getBuffer(CableHighlight.TYPE);

			visual.outlineMesh.render(
				outline,
				poses,
				null,
				camera,
				spray.left == null ? 0x7FBFBFBF : (spray.left.getMapColor().col | 0xFF000000),
				false,
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
		Vec3 camera,
		PoseStack poses
	)
	{
		CompoundTag tag = mainHandItem.getTag();

		if (tag == null)
		{
			return;
		}

		ResourceLocation wireTypeId = SpoolItem.type(mainHandItem);
		CableType cableType = wireTypeId == null ? null : CableType.of(wireTypeId);

		// Ensure that the spool has a cable
		if (cableType == null)
		{
			return;
		}

		// Ensure that the spools dimension matches the current dimension
		if (!tag.getString("Dimension").equals(dimension.toString()))
		{
			return;
		}

		// Ensure that the spool has a start point
		if (!tag.contains("Start"))
		{
			return;
		}

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
			else
			{
				switch (CablesConfig.cableIntersectionTest())
				{
					case LINE ->
					{
						if (CableGeometry.lineObstructed(
							mc.level,
							GRAPH.getConnectorPosition(start),
							GRAPH.getConnectorPosition(endBlockPos)
						))
						{
							isValid = false;
						}
					}
					case CABLE ->
					{
						if (CableGeometry.cableObstructed(
							mc.level,
							GRAPH.getConnectorPosition(start),
							GRAPH.getConnectorPosition(endBlockPos)
						))
						{
							isValid = false;
						}
					}
				}
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

		// The maximum visualization length.
		// This is 16 more than the max connection length to show an invalid connection when slightly exceeding that length
		long maxLength = CablesConfig.connectionMaxLength() / 100 + 16;

		if (end.distanceToSqr(startPos) <= maxLength * maxLength)
		{
			float width = cableType.thickness();

			BakedCableMesh mesh = new BakedCableMesh(new CableGeometry.Mesh(startPos, end, width / 2), null);

			VertexConsumer outline = buffers.getBuffer(CableHighlight.TYPE);

			mesh.render(
				outline,
				poses,
				null,
				camera,
				isValid ? hasConnector ? GREEN : WHITE : RED,
				false,
				false
			);

			buffers.endBatch(CableHighlight.TYPE);
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
