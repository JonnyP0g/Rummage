package com.jonnyp0g.rummage.net;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.jonnyp0g.rummage.Rummage;

/** Server -> client: a batch of container summaries. A scan produces one or more of these. */
public record ContainersPayload(int scanId, List<ContainerData> containers) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ContainersPayload> TYPE = new CustomPacketPayload.Type<>(Rummage.id("containers"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ContainersPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ContainersPayload::scanId,
			ContainerData.CODEC.apply(ByteBufCodecs.list()), ContainersPayload::containers,
			ContainersPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
