package com.jonnyp0g.rummage.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.jonnyp0g.rummage.Rummage;
import com.jonnyp0g.rummage.net.ContainersPayload;
import com.jonnyp0g.rummage.net.ScanDonePayload;

public class RummageClient implements ClientModInitializer {
	private static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Rummage.id("main"));

		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.rummage.open",
				InputConstants.Type.KEYBOARD,
				InputConstants.KEY_R,
				category
		));

		// Key mappings only accumulate clicks while no screen is open, so no extra "is a screen open" check is needed.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.player != null) {
					client.gui.setScreen(new RummageScreen());
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ContainersPayload.TYPE, (payload, context) -> ContainerIndex.accept(payload));
		ClientPlayNetworking.registerGlobalReceiver(ScanDonePayload.TYPE, (payload, context) -> ContainerIndex.finish(payload.scanId()));

		HighlightRenderer.init();
	}
}
