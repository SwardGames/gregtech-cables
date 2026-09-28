package com.sward.gtwires.client;

import com.mojang.blaze3d.vertex.*;
import com.sward.gtwires.*;
import com.sward.gtwires.core.CableGeometry;
import com.sward.gtwires.core.WireGraph;
import com.sward.gtwires.items.SpoolItem;
import com.sward.gtwires.network.*;
import com.sward.gtwires.network.clientbound.*;
import com.sward.gtwires.network.serverbound.CutEdgePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Mod.EventBusSubscriber(modid = GregTechWires.ID, value = Dist.CLIENT)
public final class WireClient
{
	public record ClientWire(
		long id,
		@NotNull BlockPos a,
		@NotNull BlockPos b,
		int lengthCm,
		@NotNull AABB bounds,
		@NotNull WireType type
	)
	{
	}

	private record Visual(ClientWire wire, float width, CableGeometry.Mesh mesh, CableGeometry.Mesh outlineMesh)
	{
		public static Visual create(ClientWire wire)
		{
			Vec3 aPos = getConnectorPosition(wire.a);
			Vec3 bPos = getConnectorPosition(wire.b);

			float width = wire.type.thickness();

			return new Visual(
				wire,
				width,
				new CableGeometry.Mesh(aPos, bPos, width / 2),
				new CableGeometry.Mesh(aPos, bPos, width / 2 + 0.015D)
			);
		}
	}

	public record Target(ClientWire wire, Vec3 hit, double distance)
	{
	}

	private static final Vec3[] OFFSETS = {
		new Vec3(0.50D, 0.25D, 0.50D),
		new Vec3(0.50D, 0.75D, 0.50D),
		new Vec3(0.50D, 0.50D, 0.25D),
		new Vec3(0.50D, 0.50D, 0.75D),
		new Vec3(0.25D, 0.50D, 0.50D),
		new Vec3(0.75D, 0.50D, 0.50D)
	};

	// The render type used to render wires
	private static final RenderType RENDER_TYPE = RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS);

	// Used to render normal connection highlights
	private static final int WHITE = 0xFFFFFFFF;

	// Used to render valid connection highlights
	private static final int GREEN = 0xFF77FF55;

	// Used to render invalid connection highlights
	private static final int RED = 0xFFFF5544;

	// All nodes known to the client
	private static final Map<Long, Direction> NODES = new HashMap<>();

	// All wires known to the client
	private static final Map<Long, ClientWire> WIRES = new LinkedHashMap<>();

	// Maps chunk positions to wires whose AABBs intersect that chunk
	private static final Map<ChunkPos, Set<ClientWire>> WIRES_PER_CHUNK = new HashMap<>();

	// Contains all connector links (both ways)
	private static final Map<BlockPos, Set<BlockPos>> CONNECTIONS = new HashMap<>();

	// All wires which have a baked mesh
	private static final Map<ClientWire, List<ChunkPos>> WIRE_CHUNKS = new LinkedHashMap<>();

	// All wires which have a baked mesh
	private static final Map<ClientWire, Visual> WIRE_VISUALS = new LinkedHashMap<>();

	// The current dimension that the client information is for
	private static ResourceLocation dimension;

	/**
	 * Returns the connector position for the given block position.
	 * Will return the center of the block if it fails to find the node.
	 */
	public static Vec3 getConnectorPosition(BlockPos blockPos)
	{
		long nodeId = blockPos.asLong();

		Vec3 pos = Vec3.atLowerCornerOf(blockPos);

		if (NODES.containsKey(nodeId))
		{
			pos = pos.add(OFFSETS[NODES.get(nodeId).ordinal()]);
		}

		return pos;
	}

	/**
	 * One nearest-wire selection shared by Jade, the outline, and the cutter click.
	 */
	public static Target target()
	{
		if (!ready())
		{
			return null;
		}

		Minecraft mc = Minecraft.getInstance();

		Vec3 eye = mc.player.getEyePosition();
		Vec3 direction = mc.player.getLookAngle();
		double reach = WireEvents.unobstructedReach(mc.player);
		double best = Double.POSITIVE_INFINITY;

		if (mc.hitResult instanceof EntityHitResult entity)
		{
			reach = Math.min(reach, eye.distanceTo(entity.getLocation()));
		}

		Visual selected = null;

		for (Visual visual : WIRE_VISUALS.values())
		{
			if (!visual.mesh.bounds().intersects(new AABB(eye, eye.add(direction.scale(reach)))))
			{
				continue;
			}

			Vec3 aPos = getConnectorPosition(visual.wire.a());
			Vec3 bPos = getConnectorPosition(visual.wire.b());

			double hit = CableGeometry.hit(
				aPos, bPos, eye,
				direction, reach, visual.width / 2 + 0.08D
			);

			if (hit < best)
			{
				best = hit;
				selected = visual;
			}
		}

		return selected == null ? null : new Target(selected.wire, eye.add(direction.scale(best)), best);
	}

	@SubscribeEvent
	public static void logout(ClientPlayerNetworkEvent.LoggingOut e)
	{
		NODES.clear();
		WIRES.clear();
		WIRES_PER_CHUNK.clear();
		CONNECTIONS.clear();
		WIRE_VISUALS.clear();
		dimension = null;
	}

	private static boolean ready()
	{
		Minecraft mc = Minecraft.getInstance();

		return mc.level != null && mc.player != null && mc.level.dimension().location().equals(dimension);
	}

	@SubscribeEvent
	public static void click(InputEvent.InteractionKeyMappingTriggered e)
	{
		if (!e.isUseItem() || !e.isCancelable() || !ready())
		{
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		if (!WireEvents.isCutter(mc.player.getMainHandItem()))
		{
			return;
		}

		Target selected = target();

		if (selected != null)
		{
			e.setCanceled(true);
			e.setSwingHand(true);
			WirePackets.CHANNEL.sendToServer(new CutEdgePacket(selected.wire.id()));
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
		double radius = mc.options.getEffectiveRenderDistance() * 16D;

		// Bounds used for rendering. Note that it has an infinite height.
		AABB renderBounds = new AABB(camera, camera).inflate(radius, Double.POSITIVE_INFINITY, radius);

		// First, remove all wires which are now out of range
		List<ClientWire> entriesToCull = new ArrayList<>(16);

		for (ClientWire wire : WIRE_VISUALS.keySet())
		{
			if (!wire.bounds.intersects(renderBounds))
			{
				entriesToCull.add(wire);
			}
		}

		for (ClientWire wire : entriesToCull)
		{
			WIRE_VISUALS.remove(wire);
		}

		// Second, add missing wires
		int chunkX0 = SectionPos.posToSectionCoord(renderBounds.minX);
		int chunkX1 = SectionPos.posToSectionCoord(renderBounds.maxX);
		int chunkZ0 = SectionPos.posToSectionCoord(renderBounds.minZ);
		int chunkZ1 = SectionPos.posToSectionCoord(renderBounds.maxZ);

		for (int chunkX = chunkX0; chunkX <= chunkX1; ++chunkX)
		{
			for (int chunkZ = chunkZ0; chunkZ <= chunkZ1; ++chunkZ)
			{
				Set<ClientWire> chunkWires = WIRES_PER_CHUNK.get(new ChunkPos(chunkX, chunkZ));

				if (chunkWires != null)
				{
					for (ClientWire wire : chunkWires)
					{
						if (wire.bounds.intersects(renderBounds))
						{
							WIRE_VISUALS.computeIfAbsent(wire, Visual::create);
						}
					}
				}
			}
		}

		PoseStack poses = event.getPoseStack();

		poses.pushPose();
		poses.translate(-camera.x, -camera.y, -camera.z);

		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		VertexConsumer out = buffers.getBuffer(RENDER_TYPE);

		for (Visual visual : WIRE_VISUALS.values())
		{
			if (!event.getFrustum().isVisible(visual.mesh.bounds()))
			{
				continue;
			}

			float width = 16 * visual.width;

			WireAppearance.Appearance appearance = WireAppearance.get(visual.wire().type.id());

			sides(
				out,
				poses.last(),
				visual.mesh,
				appearance.sprite(),
				appearance.color(),
				width,
				false
			);
		}

		buffers.endBatch(RENDER_TYPE);

		ItemStack heldItem = mc.player.getMainHandItem();

		if (WireEvents.isCutter(heldItem))
		{
			Target target = target();
			ClientWire selected = target == null ? null : WIRES.get(target.wire.id());

			if (selected != null)
			{
				Visual visual = WIRE_VISUALS.get(selected);

				if (visual != null)
				{
					VertexConsumer outline = buffers.getBuffer(WireHighlight.TYPE);

					sides(
						outline,
						poses.last(),
						visual.outlineMesh,
						null,
						0xFFFFFFFF,
						16 * selected.type.thickness(),
						true
					);

					buffers.endBatch(WireHighlight.TYPE);
				}
			}
		}

		if (WireEvents.isSpool(heldItem, false))
		{
			CompoundTag tag = heldItem.getTag();
			ResourceLocation wire = SpoolItem.type(heldItem);
			WireType type = wire == null ? null : WireType.of(wire);

			if (tag != null && tag.contains("Start") && type != null)
			{
				BlockPos start = BlockPos.of(tag.getLong("Start"));
				Vec3 startPos = getConnectorPosition(start);

				boolean hasConnector = false;
				boolean isValid = false;
				Vec3 end;

				if (mc.hitResult instanceof BlockHitResult hit &&
					hit.getType() == HitResult.Type.BLOCK &&
					mc.level.getBlockState(hit.getBlockPos()).is(GregTechWires.CONNECTOR.get()))
				{
					BlockPos endBlockPos = hit.getBlockPos();

					hasConnector = true;
					end = getConnectorPosition(endBlockPos);

					isValid = true;

					if (start.equals(endBlockPos))
					{
						isValid = false;
					}

					int cm = WireGraph.lengthCm(start, endBlockPos);

					if (cm > WiresConfig.connectionMaxLength() || cm > SpoolItem.length(heldItem))
					{
						isValid = false;
					}

					Set<BlockPos> startLinks = CONNECTIONS.get(start);

					if (startLinks != null && startLinks.contains(endBlockPos))
					{
						isValid = false;
					}
				}
				else
				{
					end = mc.hitResult.getLocation();
				}

				if (end.distanceToSqr(startPos) <= 272D * 272D)
				{
					float width = type.thickness();

					CableGeometry.Mesh mesh = new CableGeometry.Mesh(startPos, end, width / 2);

					VertexConsumer outline = buffers.getBuffer(WireHighlight.TYPE);
					sides(
						outline,
						poses.last(),
						mesh,
						null,
						hasConnector
							? isValid ? GREEN : RED
							: WHITE,
						16 * width,
						false
					);
					buffers.endBatch(WireHighlight.TYPE);
				}
			}
		}

		poses.popPose();
	}

	private static Vec3 interpolate(Vec3 a, Vec3 b, double t)
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
					.color(255, 255, 255, 255)
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
		NODES.clear();
		WIRES.clear();
		WIRES_PER_CHUNK.clear();
		CONNECTIONS.clear();
		WIRE_VISUALS.clear();
		dimension = packet.dimension();

		for (SyncGraphPacket.NodeData node : packet.nodes())
		{
			addNode(node.id(), node.direction());
		}

		for (SyncGraphPacket.EdgeData edge : packet.edges())
		{
			addEdge(edge.id(), edge.a(), edge.b(), edge.wire());
		}
	}

	public static void addNodePacket(AddNodePacket packet)
	{
		if (!packet.dimension().equals(dimension))
		{
			GregTechWires.LOGGER.error("Received add node packet for dimension {} in dimension {}.", packet.dimension(), dimension);

			return;
		}

		addNode(packet.id(), packet.direction());
	}

	public static void addEdgePacket(AddEdgePacket packet)
	{
		if (!packet.dimension().equals(dimension))
		{
			GregTechWires.LOGGER.error("Received add edge packet for dimension {} in dimension {}.", packet.dimension(), dimension);

			return;
		}

		addEdge(packet.id(), packet.a(), packet.b(), packet.wire());
	}

	public static void removeNodePacket(RemoveNodePacket packet)
	{
		if (!packet.dimension().equals(dimension))
		{
			GregTechWires.LOGGER.error("Received remove node packet for dimension {} in dimension {}.", packet.dimension(), dimension);

			return;
		}

		removeNode(packet.id());
	}

	public static void removeEdgePacket(RemoveEdgePacket packet)
	{
		if (!packet.dimension().equals(dimension))
		{
			GregTechWires.LOGGER.error("Received remove edge packet for dimension {} in dimension {}.", packet.dimension(), dimension);

			return;
		}

		removeEdge(packet.id(), packet.burned());
	}

	public static void resetGraphPacket(ResetGraphPacket packet)
	{
		if (!packet.dimension().equals(dimension))
		{
			GregTechWires.LOGGER.error("Received remove edge packet for dimension {} in dimension {}.", packet.dimension(), dimension);

			return;
		}

		NODES.clear();
		WIRES.clear();
		WIRES_PER_CHUNK.clear();
		CONNECTIONS.clear();
		WIRE_VISUALS.clear();
	}

	private static void addNode(long id, Direction edge)
	{
		NODES.put(id, edge);
	}

	private static void removeNode(long id)
	{
		NODES.remove(id);
	}

	private static void addEdge(long id, BlockPos a, BlockPos b, ResourceLocation wire)
	{
		WireType type = WireType.of(wire);

		if (type == null)
		{
			GregTechWires.LOGGER.error("Failed to find WireType for id '{}'. The wire will be discarded.", wire);

			return;
		}

		ClientWire clientWire = new ClientWire(
			id,
			a,
			b,
			WireGraph.lengthCm(a, b),
			new AABB(
				a.getX(),
				a.getY(),
				a.getZ(),
				b.getX() + 1D,
				b.getY() + 1D,
				b.getZ() + 1D
			),
			type
		);

		// Chunks for spatial hashing
		List<ChunkPos> chunks = new ArrayList<>();

		int aChunkX = SectionPos.blockToSectionCoord(a.getX());
		int bChunkX = SectionPos.blockToSectionCoord(b.getX());
		int aChunkZ = SectionPos.blockToSectionCoord(a.getZ());
		int bChunkZ = SectionPos.blockToSectionCoord(b.getZ());

		int chunkX0 = Math.min(aChunkX, bChunkX);
		int chunkX1 = Math.max(aChunkX, bChunkX);
		int chunkZ0 = Math.min(aChunkZ, bChunkZ);
		int chunkZ1 = Math.max(aChunkZ, bChunkZ);

		for (int chunkX = chunkX0; chunkX <= chunkX1; ++chunkX)
		{
			for (int chunkZ = chunkZ0; chunkZ <= chunkZ1; ++chunkZ)
			{
				ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

				chunks.add(chunkPos);

				WIRES_PER_CHUNK.computeIfAbsent(chunkPos, k -> new HashSet<>()).add(clientWire);
			}
		}

		WIRES.put(id, clientWire);

		CONNECTIONS.computeIfAbsent(a, k -> new HashSet<>()).add(b);
		CONNECTIONS.computeIfAbsent(b, k -> new HashSet<>()).add(a);

		WIRE_CHUNKS.put(clientWire, chunks);
	}

	private static void removeEdge(long id, boolean burned)
	{
		ClientWire wire = WIRES.remove(id);

		if (wire != null)
		{
			WIRE_VISUALS.remove(wire);

			List<ChunkPos> chunks = WIRE_CHUNKS.remove(wire);

			if (chunks != null)
			{
				for (ChunkPos chunk : chunks)
				{
					Set<ClientWire> chunkWires = WIRES_PER_CHUNK.get(chunk);

					chunkWires.remove(wire);
				}
			}

			BlockPos a = wire.a();
			BlockPos b = wire.b();

			Set<BlockPos> aLinks = CONNECTIONS.get(a);

			if (aLinks != null)
			{
				aLinks.remove(b);
			}

			Set<BlockPos> bLinks = CONNECTIONS.get(b);

			if (bLinks != null)
			{
				bLinks.remove(a);
			}

			if (burned)
			{
				burnEffects(a, b);
			}
		}
	}
}
