package com.jonnyp0g.rummage.client;

import java.util.Collection;
import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Which containers are currently highlighted, and for how long. */
public final class HighlightManager {
	/** How long a highlight stays up after a search, in milliseconds. */
	private static final long DURATION_MS = 60_000L;

	private static List<BlockPos> positions = List.of();
	private static ClientLevel level;
	private static long expiresAt;

	private HighlightManager() {
	}

	public static void set(ClientLevel clientLevel, Collection<BlockPos> newPositions) {
		positions = List.copyOf(newPositions);
		level = clientLevel;
		expiresAt = System.currentTimeMillis() + DURATION_MS;
	}

	public static void clear() {
		positions = List.of();
		level = null;
	}

	/** Positions to draw right now. Drops itself when it expires or the player changes world. */
	public static List<BlockPos> active(ClientLevel currentLevel) {
		if (positions.isEmpty()) {
			return List.of();
		}

		if (currentLevel != level || System.currentTimeMillis() > expiresAt) {
			clear();
			return List.of();
		}

		return positions;
	}
}
