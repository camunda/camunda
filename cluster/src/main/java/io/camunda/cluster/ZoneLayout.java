/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster;

import java.util.List;
import java.util.OptionalInt;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Slot-layout arithmetic that relates bare integer member IDs to zone-aware member positions.
 *
 * <p>The invariant: bare member {@code nodeIdx} maps to zone rank {@code nodeIdx % zoneCount} at
 * local position {@code nodeIdx / zoneCount}. A zoned member with local index {@code n} at zone
 * rank {@code r} occupies effective slot {@code n * zoneCount + r}, which equals the original bare
 * {@code nodeIdx}. This coordinate system lets the round-robin distributor and the migration
 * planner agree on member positions without coupling to each other.
 */
@NullMarked
public final class ZoneLayout {

  private ZoneLayout() {}

  /**
   * Returns the effective slot of a member. Bare members ({@code zone == null}) return {@code
   * nodeIdx}. Zoned members return {@code nodeIdx * zoneOrder.size() + rank}. Returns {@link
   * OptionalInt#empty()} when {@code zone} is not present in {@code zoneOrder}.
   */
  public static OptionalInt effectiveSlot(
      final @Nullable String zone, final int nodeIdx, final List<String> zoneOrder) {
    if (zone == null) {
      return OptionalInt.of(nodeIdx);
    }
    final var rank = zoneOrder.indexOf(zone);
    return rank < 0 ? OptionalInt.empty() : OptionalInt.of((nodeIdx * zoneOrder.size()) + rank);
  }

  /**
   * Returns the zone rank (index in zone order) that a bare broker with the given {@code nodeIdx}
   * belongs to. Equivalent to {@code nodeIdx % zoneCount}.
   */
  public static int zoneRankForBareNodeIdx(final int nodeIdx, final int zoneCount) {
    return nodeIdx % zoneCount;
  }

  /**
   * Returns the local node index within its zone for a bare broker with the given {@code nodeIdx}.
   * Equivalent to {@code nodeIdx / zoneCount}.
   */
  public static int localNodeIdxForBareNodeIdx(final int nodeIdx, final int zoneCount) {
    return nodeIdx / zoneCount;
  }
}
