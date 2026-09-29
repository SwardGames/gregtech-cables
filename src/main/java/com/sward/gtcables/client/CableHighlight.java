package com.sward.gtcables.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Reversed geometry + back-face culling gives an inverted-hull outline.
 * POSITION_COLOR is untextured and unlit; depth testing prevents x-ray highlighting.
 */
final class CableHighlight extends RenderType
{
	static final RenderType TYPE = create(
		"gtcables_cutter_outline",
		DefaultVertexFormat.POSITION_COLOR,
		VertexFormat.Mode.QUADS,
		256,
		false,
		false,
		CompositeState.builder()
			.setShaderState(POSITION_COLOR_SHADER).setCullState(CULL)
			.setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
			.createCompositeState(false)
	);

	private CableHighlight(
		String name,
		VertexFormat format,
		VertexFormat.Mode mode,
		int bufferSize,
		boolean crumbling,
		boolean sort,
		Runnable setup,
		Runnable clear
	)
	{
		super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
	}
}
