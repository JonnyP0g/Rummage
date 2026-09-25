package com.jonnyp0g.rummage.client;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.jonnyp0g.rummage.net.ScanRequestPayload;

/**
 * The search bar. Opening it asks the server for a fresh scan; results update live as they arrive and
 * as you type. Enter (or the Highlight button) highlights every matching container and closes the screen.
 */
public class RummageScreen extends Screen {
	private static final int BOX_WIDTH = 240;
	private static final int MAX_LINES = 8;
	private static final int MAX_LABEL_CHARS = 60;

	/** Remembered between openings so the last search is one keypress away. */
	private static String lastQuery = "";

	private EditBox searchBox;
	private String query = "";
	private List<ContainerIndex.Hit> hits = List.of();
	private int seenVersion = -1;
	private boolean scanRequested;
	private boolean serverMissing;

	public RummageScreen() {
		super(Component.translatable("screen.rummage.title"));
	}

	@Override
	protected void init() {
		int x = this.width / 2 - BOX_WIDTH / 2;
		int y = this.height / 5;

		this.searchBox = new EditBox(this.font, x, y, BOX_WIDTH - 64, 20, Component.translatable("screen.rummage.title"));
		this.searchBox.setMaxLength(64);
		this.searchBox.setHint(Component.translatable("screen.rummage.hint"));
		this.searchBox.setValue(this.query.isEmpty() ? lastQuery : this.query);
		this.searchBox.setResponder(text -> {
			this.query = text;
			this.refresh();
		});
		this.addRenderableWidget(this.searchBox);
		this.setInitialFocus(this.searchBox);

		this.addRenderableWidget(Button.builder(Component.translatable("screen.rummage.highlight"), button -> this.confirm())
				.bounds(x + BOX_WIDTH - 60, y, 60, 20)
				.build());

		this.query = this.searchBox.getValue();

		// init() also runs again on window resize, so only ask the server once per opening.
		if (!this.scanRequested) {
			this.scanRequested = true;
			this.requestScan();
		}

		this.refresh();
	}

	private void requestScan() {
		Minecraft client = this.minecraft;

		if (client == null || client.level == null) {
			return;
		}

		if (!ClientPlayNetworking.canSend(ScanRequestPayload.TYPE)) {
			this.serverMissing = true;
			return;
		}

		int scanId = ContainerIndex.beginScan();
		ClientPlayNetworking.send(new ScanRequestPayload(scanId, client.options.getEffectiveRenderDistance()));
	}

	private void refresh() {
		Minecraft client = this.minecraft;

		if (client == null || client.player == null) {
			return;
		}

		this.hits = ContainerIndex.search(this.query, client.player.getX(), client.player.getY(), client.player.getZ());
		this.seenVersion = ContainerIndex.version();
	}

	@Override
	public void tick() {
		super.tick();

		if (ContainerIndex.version() != this.seenVersion) {
			this.refresh();
		}
	}

	private void confirm() {
		Minecraft client = this.minecraft;
		lastQuery = this.query;

		if (client != null && client.level != null && !this.hits.isEmpty()) {
			HighlightManager.set(client.level, this.hits.stream().map(ContainerIndex.Hit::pos).toList());
		} else {
			HighlightManager.clear();
		}

		this.onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			this.confirm();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		// Keep a singleplayer world running so the integrated server can answer the scan request.
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int x = this.width / 2 - BOX_WIDTH / 2;
		int y = this.height / 5 + 26;

		graphics.text(this.font, this.statusLine(), x, y, 0xFFAAAAAA, false);

		int lineY = y + 14;
		int shown = Math.min(MAX_LINES, this.hits.size());

		for (int i = 0; i < shown; i++) {
			graphics.text(this.font, this.describe(this.hits.get(i)), x, lineY, 0xFFFFFFFF, true);
			lineY += 11;
		}

		if (this.hits.size() > shown) {
			String more = Component.translatable("screen.rummage.more", this.hits.size() - shown).getString();
			graphics.text(this.font, more, x, lineY, 0xFFAAAAAA, false);
		}
	}

	private String statusLine() {
		if (this.serverMissing) {
			return Component.translatable("screen.rummage.no_server").getString();
		}

		if (this.query.isBlank()) {
			return Component.translatable(ContainerIndex.isScanning() ? "screen.rummage.scanning" : "screen.rummage.indexed", ContainerIndex.containerCount()).getString();
		}

		if (this.hits.isEmpty()) {
			return Component.translatable(ContainerIndex.isScanning() ? "screen.rummage.scanning" : "screen.rummage.no_results", ContainerIndex.containerCount()).getString();
		}

		int items = this.hits.stream().mapToInt(ContainerIndex.Hit::totalCount).sum();
		return Component.translatable("screen.rummage.summary", this.hits.size(), items).getString();
	}

	private String describe(ContainerIndex.Hit hit) {
		ContainerIndex.Entry first = hit.matches().get(0);

		String label = first.label();

		if (label.length() > MAX_LABEL_CHARS) {
			label = label.substring(0, MAX_LABEL_CHARS - 1) + "…";
		}

		StringBuilder line = new StringBuilder();
		line.append(first.count()).append("x ").append(label);

		if (first.inShulker()) {
			line.append(" (shulker)");
		}

		if (hit.matches().size() > 1) {
			line.append(" +").append(hit.matches().size() - 1);
		}

		line.append("  ").append(hit.pos().toShortString()).append("  ").append((int) Math.sqrt(hit.distanceSqr())).append("m");
		return line.toString();
	}
}
