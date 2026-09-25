package com.jonnyp0g.rummage.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One enchantment on an enchanted book: registry id (e.g. "minecraft:mending") and level. */
public record EnchantData(String id, int level) {
	public static final StreamCodec<RegistryFriendlyByteBuf, EnchantData> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, EnchantData::id,
			ByteBufCodecs.VAR_INT, EnchantData::level,
			EnchantData::new
	);
}
