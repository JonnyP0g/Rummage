package com.jonnyp0g.rummage.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.jonnyp0g.rummage.net.ContainerData;
import com.jonnyp0g.rummage.net.ContainersPayload;
import com.jonnyp0g.rummage.net.EnchantData;
import com.jonnyp0g.rummage.net.ItemData;
import com.jonnyp0g.rummage.net.ScanDonePayload;
import com.jonnyp0g.rummage.net.ScanRequestPayload;

/**
 * Server half of Rummage. Walks the chunks that are already loaded around a player, summarises the
 * contents of every storage container in them and streams the result back to that player.
 *
 * Only already-loaded chunks are touched (getChunkNow never loads anything), so a scan can't
 * cause chunk generation or loading.
 */
public final class ContainerScanner {
	/**
	 * Clientbound custom payloads are capped at 1 MiB. Batches are cut by number of item entries
	 * rather than number of containers so a room full of packed shulker boxes can't blow the limit.
	 */
	private static final int MAX_ENTRIES_PER_PACKET = 3000;

	private ContainerScanner() {
	}

	/** Which block entities count as "storage containers". Add more types here (hoppers, barrels of your own mod, ...). */
	private static boolean isStorage(BlockEntity blockEntity) {
		return blockEntity instanceof Container
				&& (blockEntity instanceof ChestBlockEntity // includes trapped chests
				|| blockEntity instanceof BarrelBlockEntity
				|| blockEntity instanceof ShulkerBoxBlockEntity);
	}

	public static void handle(ScanRequestPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		ServerLevel level = player.level();

		int maxRadius = level.getServer().getPlayerList().getViewDistance();
		int radius = Mth.clamp(payload.radiusChunks(), 1, Math.max(1, maxRadius));

		BlockPos origin = player.blockPosition();
		int centerX = SectionPos.blockToSectionCoord(origin.getX());
		int centerZ = SectionPos.blockToSectionCoord(origin.getZ());

		List<ContainerData> batch = new ArrayList<>();
		int batchEntries = 0;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(centerX + dx, centerZ + dz);

				if (chunk == null) {
					continue; // not loaded on the server, skip
				}

				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!isStorage(blockEntity)) {
						continue;
					}

					List<ItemData> items = summarise((Container) blockEntity);

					if (items.isEmpty()) {
						continue;
					}

					batch.add(new ContainerData(blockEntity.getBlockPos(), items));
					batchEntries += items.size();

					if (batchEntries >= MAX_ENTRIES_PER_PACKET) {
						ServerPlayNetworking.send(player, new ContainersPayload(payload.scanId(), List.copyOf(batch)));
						batch.clear();
						batchEntries = 0;
					}
				}
			}
		}

		if (!batch.isEmpty()) {
			ServerPlayNetworking.send(player, new ContainersPayload(payload.scanId(), List.copyOf(batch)));
		}

		ServerPlayNetworking.send(player, new ScanDonePayload(payload.scanId()));
	}

	// ------------------------------------------------------------------------------------------
	// Summarising
	// ------------------------------------------------------------------------------------------

	/** Running total for one kind of item. Items only merge when id, name, enchantments and location all match. */
	private static final class Accumulator {
		final String itemId;
		final String customName;
		final boolean inShulker;
		final List<EnchantData> enchants;
		int count;

		Accumulator(String itemId, String customName, boolean inShulker, List<EnchantData> enchants) {
			this.itemId = itemId;
			this.customName = customName;
			this.inShulker = inShulker;
			this.enchants = enchants;
		}
	}

	private static List<ItemData> summarise(Container container) {
		Map<String, Accumulator> totals = new LinkedHashMap<>();

		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			collect(container.getItem(slot), false, totals);
		}

		List<ItemData> result = new ArrayList<>(totals.size());

		for (Accumulator total : totals.values()) {
			result.add(new ItemData(total.itemId, total.count, total.customName, total.inShulker, total.enchants));
		}

		return result;
	}

	private static void collect(ItemStack stack, boolean inShulker, Map<String, Accumulator> totals) {
		if (stack.isEmpty()) {
			return;
		}

		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

		// Custom (anvil / name tag) names. Items still match their normal item id/name on the client too.
		Component customComponent = stack.get(DataComponents.CUSTOM_NAME);
		String customName = customComponent == null ? "" : customComponent.getString();

		List<EnchantData> enchants = storedEnchantments(stack);

		String key = itemId + '\u0000' + customName + '\u0000' + inShulker + '\u0000' + enchants;
		totals.computeIfAbsent(key, k -> new Accumulator(itemId, customName, inShulker, enchants)).count += stack.getCount();

		// Shulker boxes (and any other item carrying a container component): look inside.
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);

		if (contents != null) {
			for (var template : contents.nonEmptyItems()) {
				collect(template.create(), true, totals);
			}
		}
	}

	/** Enchantments stored *on an enchanted book* (not enchantments applied to tools/armour). */
	private static List<EnchantData> storedEnchantments(ItemStack stack) {
		ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);

		if (stored == null || stored.isEmpty()) {
			return List.of();
		}

		List<EnchantData> result = new ArrayList<>();

		for (Holder<Enchantment> enchantment : stored.keySet()) {
			result.add(new EnchantData(enchantment.getRegisteredName(), stored.getLevel(enchantment)));
		}

		result.sort(Comparator.comparing(EnchantData::id));
		return result;
	}
}
