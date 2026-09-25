package com.jonnyp0g.rummage.net;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Summary of one kind of item inside a container.
 *
 * @param itemId     registry id, e.g. "minecraft:diamond_sword"
 * @param count      total count of this exact kind of item in the container
 * @param customName the player-given name, or "" if the item has none
 * @param inShulker  true if the item was found inside a shulker box (or other item container) in the container
 * @param enchants   stored enchantments (enchanted books only), sorted by id
 */
public record ItemData(String itemId, int count, String customName, boolean inShulker, List<EnchantData> enchants) {
	public static final StreamCodec<RegistryFriendlyByteBuf, ItemData> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, ItemData::itemId,
			ByteBufCodecs.VAR_INT, ItemData::count,
			ByteBufCodecs.STRING_UTF8, ItemData::customName,
			ByteBufCodecs.BOOL, ItemData::inShulker,
			EnchantData.CODEC.apply(ByteBufCodecs.list()), ItemData::enchants,
			ItemData::new
	);
}
