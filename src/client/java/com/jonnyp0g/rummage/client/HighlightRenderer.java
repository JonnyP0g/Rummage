package com.jonnyp0g.rummage.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import com.jonnyp0g.rummage.Rummage;

/**
 * Draws a translucent, pulsing box over every highlighted container, visible through walls.
 *
 * This is the most version-sensitive file in the mod: it follows the "Rendering in the World" example
 * from the Fabric docs (custom render pipeline, extraction phase then drawing phase). If Fabric API or
 * Minecraft change the level rendering API again, this is the only file that should need touching.
 */
public final class HighlightRenderer {
	// Same as vanilla's debug filled box, but with the depth test removed so it shows through walls.
	private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Rummage.id("pipeline/highlight_through_walls"))
			.withDepthStencilState(Optional.empty())
			.build()
	);

	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static final StagedVertexBuffer STAGED_BUFFER = new StagedVertexBuffer(() -> "Rummage Highlight Buffer", RenderType.SMALL_BUFFER_SIZE);

	private static final float R = 1.0f;
	private static final float G = 0.8f;
	private static final float B = 0.1f;
	/** Grow the box slightly so it doesn't z-fight with the block faces. */
	private static final float PADDING = 0.005f;

	/** Immutable snapshot taken in the extraction phase and read in the drawing phase. */
	private record BoxState(int x, int y, int z, float alpha) {
	}

	private static volatile List<BoxState> extracted = List.of();

	private HighlightRenderer() {
	}

	public static void init() {
		LevelExtractionEvents.END_EXTRACTION.register(HighlightRenderer::extract);

		// END_MAIN runs after the main pass has closed its GPU render passes.
		// Terrain callbacks run with a pass open and cannot upload or open another pass.
		LevelRenderEvents.END_MAIN.register(HighlightRenderer::draw);

		// Free the GPU buffer when the game shuts down (the Fabric docs do this with a GameRenderer#close mixin).
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> STAGED_BUFFER.close());
	}

	// ------------------------------------------------------------------------------------------
	// Extraction phase: read game state, build immutable render state
	// ------------------------------------------------------------------------------------------

	private static void extract(LevelExtractionContext context) {
		List<BlockPos> active = HighlightManager.active(Minecraft.getInstance().level);

		if (active.isEmpty()) {
			extracted = List.of();
			return;
		}

		// Gentle pulse so highlights stand out against a wall of chests.
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 250.0);
		float alpha = 0.25f + 0.25f * pulse;

		List<BoxState> states = new ArrayList<>(active.size());

		for (BlockPos pos : active) {
			states.add(new BoxState(pos.getX(), pos.getY(), pos.getZ(), alpha));
		}

		extracted = List.copyOf(states);
	}

	// ------------------------------------------------------------------------------------------
	// Drawing phase
	// ------------------------------------------------------------------------------------------

	private static void draw(LevelRenderContext context) {
		List<BoxState> boxes = extracted;

		if (boxes.isEmpty()) {
			return;
		}

		RenderPipeline pipeline = FILLED_THROUGH_WALLS;
		VertexFormat formatBinding = pipeline.getVertexFormatBinding(0);

		assert formatBinding != null;

		PrimitiveTopology primitive = pipeline.getPrimitiveTopology();
		StagedVertexBuffer.Draw draw = STAGED_BUFFER.appendDraw(formatBinding, primitive, primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);

		PoseStack matrices = context.poseStack();
		Vec3 camera = context.levelState().cameraRenderState.pos;

		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);

		VertexConsumer builder = STAGED_BUFFER.getVertexBuilder(draw);

		for (BoxState box : boxes) {
			renderFilledBox(matrices.last().pose(), builder,
					box.x() - PADDING, box.y() - PADDING, box.z() - PADDING,
					box.x() + 1 + PADDING, box.y() + 1 + PADDING, box.z() + 1 + PADDING,
					R, G, B, box.alpha());
		}

		matrices.popPose();

		STAGED_BUFFER.upload();

		StagedVertexBuffer.ExecuteInfo info = STAGED_BUFFER.getExecuteInfo(draw);

		if (info != null) {
			execute(Minecraft.getInstance(), info, pipeline);
		}

		STAGED_BUFFER.endFrame();
	}

	private static void renderFilledBox(Matrix4fc positionMatrix, VertexConsumer buffer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float red, float green, float blue, float alpha) {
		// Front face
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);

		// Back face
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);

		// Left face
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

		// Right face
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

		// Top face
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

		// Bottom face
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
	}

	private static void execute(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = mainTarget.getColorTextureView();

		assert colorTexture != null;

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> Rummage.MOD_ID + " highlight", colorTexture, Optional.empty(), mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
			renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));

			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);

			renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
			renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());

			// The base vertex is the starting index when the data was copied into the vertex buffer divided by vertex size.
			renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
		}
	}
}
