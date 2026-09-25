package com.jonnyp0g.rummage;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jonnyp0g.rummage.net.ContainersPayload;
import com.jonnyp0g.rummage.net.ScanDonePayload;
import com.jonnyp0g.rummage.net.ScanRequestPayload;
import com.jonnyp0g.rummage.server.ContainerScanner;

/**
 * Common entrypoint. Runs on both the client and the (dedicated or integrated) server.
 */
public class Rummage implements ModInitializer {
	public static final String MOD_ID = "rummage";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Payload types must be registered on both sides before they can be sent or received.
		PayloadTypeRegistry.serverboundPlay().register(ScanRequestPayload.TYPE, ScanRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ContainersPayload.TYPE, ContainersPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScanDonePayload.TYPE, ScanDonePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ScanRequestPayload.TYPE, ContainerScanner::handle);

		LOGGER.info("Rummage loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
