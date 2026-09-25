package com.jonnyp0g.rummage.net;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A container block (chest, barrel, shulker box) and a summary of what is inside it. */
public record ContainerData(BlockPos pos, List<ItemData> items) {
	public static final StreamCodec<RegistryFriendlyByteBuf, ContainerData> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ContainerData::pos,
			ItemData.CODEC.apply(ByteBufCodecs.list()), ContainerData::items,
			ContainerData::new
	);
}
