package com.jonnyp0g.rummage.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.jonnyp0g.rummage.net.ContainerData;
import com.jonnyp0g.rummage.net.ContainersPayload;
import com.jonnyp0g.rummage.net.EnchantData;
import com.jonnyp0g.rummage.net.ItemData;

/**
 * Client-side copy of what the server told us is in nearby containers, plus the search over it.
 * Everything here runs on the client thread, so no locking.
 */
public final class ContainerIndex {
	/** One kind of item in a container, with everything precomputed for searching and display. */
	public record Entry(String itemId, int count, String customName, boolean inShulker, String label, String searchText) {
	}

	public record Stored(BlockPos pos, List<Entry> entries) {
	}

	/** A container that matched a search. */
	public record Hit(BlockPos pos, List<Entry> matches, int totalCount, double distanceSqr) {
	}

	private static final Map<BlockPos, Stored> CONTAINERS = new HashMap<>();
	private static int currentScanId;
	private static boolean scanning;
	private static int version;

	private ContainerIndex() {
	}

	// ------------------------------------------------------------------------------------------
	// Scan lifecycle
	// ------------------------------------------------------------------------------------------

	/** Clears the index and returns the id to put in the scan request. */
	public static int beginScan() {
		CONTAINERS.clear();
		scanning = true;
		version++;
		return ++currentScanId;
	}

	public static void accept(ContainersPayload payload) {
		if (payload.scanId() != currentScanId) {
			return; // reply to an older scan
		}

		for (ContainerData container : payload.containers()) {
			List<Entry> entries = new ArrayList<>(container.items().size());

			for (ItemData item : container.items()) {
				entries.add(toEntry(item));
			}

			CONTAINERS.put(container.pos(), new Stored(container.pos(), entries));
		}

		version++;
	}

	public static void finish(int scanId) {
		if (scanId == currentScanId) {
			scanning = false;
			version++;
		}
	}

	public static boolean isScanning() {
		return scanning;
	}

	public static int containerCount() {
		return CONTAINERS.size();
	}

	/** Bumps whenever the index changes; screens compare it to know when to redo their search. */
	public static int version() {
		return version;
	}

	// ------------------------------------------------------------------------------------------
	// Search
	// ------------------------------------------------------------------------------------------

	/**
	 * Every space separated word in the query must appear somewhere in an entry's search text
	 * (item id, item name, custom name, enchantment names), case-insensitive. Nearest containers first.
	 */
	public static List<Hit> search(String query, double x, double y, double z) {
		String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");

		if (words.length == 0 || words[0].isEmpty()) {
			return List.of();
		}

		List<Hit> hits = new ArrayList<>();

		for (Stored stored : CONTAINERS.values()) {
			List<Entry> matches = new ArrayList<>();
			int total = 0;

			for (Entry entry : stored.entries()) {
				if (matchesAll(entry.searchText(), words)) {
					matches.add(entry);
					total += entry.count();
				}
			}

			if (!matches.isEmpty()) {
				hits.add(new Hit(stored.pos(), matches, total, stored.pos().distToCenterSqr(x, y, z)));
			}
		}

		hits.sort(Comparator.comparingDouble(Hit::distanceSqr));
		return hits;
	}

	private static boolean matchesAll(String searchText, String[] words) {
		for (String word : words) {
			if (!searchText.contains(word)) {
				return false;
			}
		}

		return true;
	}

	// ------------------------------------------------------------------------------------------
	// Building searchable entries
	// ------------------------------------------------------------------------------------------

	private static Entry toEntry(ItemData data) {
		Identifier id = Identifier.tryParse(data.itemId());
		Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
		String baseName = new ItemStack(item).getHoverName().getString();

		StringBuilder search = new StringBuilder();
		// Every item is always findable by its id and normal name, even when it has a custom name.
		append(search, data.itemId());
		append(search, words(data.itemId()));
		append(search, baseName);
		append(search, data.customName());

		List<String> enchantLabels = new ArrayList<>();

		for (EnchantData enchant : data.enchants()) {
			String name = enchantName(enchant.id());
			String level = levelName(enchant.level());

			append(search, enchant.id());
			append(search, words(enchant.id()));
			append(search, name);
			append(search, level);
			append(search, Integer.toString(enchant.level()));

			enchantLabels.add(level.isEmpty() ? name : name + " " + level);
		}

		String label = data.customName().isEmpty() ? baseName : data.customName() + " (" + baseName + ")";

		if (!enchantLabels.isEmpty()) {
			label += " [" + String.join(", ", enchantLabels) + "]";
		}

		return new Entry(data.itemId(), data.count(), data.customName(), data.inShulker(), label, search.toString().toLowerCase(Locale.ROOT));
	}

	private static void append(StringBuilder builder, String text) {
		if (!text.isEmpty()) {
			builder.append(text).append(' ');
		}
	}

	/** "minecraft:diamond_sword" -> "diamond sword" */
	private static String words(String id) {
		int colon = id.indexOf(':');
		String path = colon >= 0 ? id.substring(colon + 1) : id;
		return path.replace('_', ' ').replace('/', ' ');
	}

	/** Localised enchantment name via the vanilla translation key, e.g. enchantment.minecraft.mending. */
	private static String enchantName(String id) {
		int colon = id.indexOf(':');
		String namespace = colon >= 0 ? id.substring(0, colon) : "minecraft";
		String path = colon >= 0 ? id.substring(colon + 1) : id;
		return translateOr("enchantment." + namespace + "." + path.replace('/', '.'), words(id));
	}

	private static String levelName(int level) {
		return translateOr("enchantment.level." + level, Integer.toString(level));
	}

	private static String translateOr(String key, String fallback) {
		return Language.getInstance().has(key) ? Component.translatable(key).getString() : fallback;
	}
}
