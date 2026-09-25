package com.jonnyp0g.rummage.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.jonnyp0g.rummage.Rummage;

/**
 * Client -> server: "tell me what is in the loaded containers around me".
 *
 * @param scanId       chosen by the client, echoed back so stale replies can be ignored
 * @param radiusChunks how far to look; the server clamps this to its own view distance
 */
public record ScanRequestPayload(int scanId, int radiusChunks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ScanRequestPayload> TYPE = new CustomPacketPayload.Type<>(Rummage.id("scan_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ScanRequestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ScanRequestPayload::scanId,
			ByteBufCodecs.VAR_INT, ScanRequestPayload::radiusChunks,
			ScanRequestPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
