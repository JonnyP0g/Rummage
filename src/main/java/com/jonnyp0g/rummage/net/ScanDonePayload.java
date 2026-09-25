package com.jonnyp0g.rummage.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.jonnyp0g.rummage.Rummage;

/** Server -> client: the scan with this id has been fully sent. */
public record ScanDonePayload(int scanId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ScanDonePayload> TYPE = new CustomPacketPayload.Type<>(Rummage.id("scan_done"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ScanDonePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ScanDonePayload::scanId,
			ScanDonePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
