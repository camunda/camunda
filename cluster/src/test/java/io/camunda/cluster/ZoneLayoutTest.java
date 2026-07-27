/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

final class ZoneLayoutTest {

  // ── effectiveSlot ──────────────────────────────────────────────────────────
  private static final List<String> DUAL_ZONES = List.of("zone-a", "zone-b");

  @Test
  void shouldReturnNodeIdxForBareMember() {
    // given / when / then
    assertThat(ZoneLayout.effectiveSlot(null, 3, DUAL_ZONES)).hasValue(3);
  }

  @Test
  void shouldReturnEmptyForZonedMemberNotInZoneOrder() {
    assertThat(ZoneLayout.effectiveSlot("zone-c", 0, DUAL_ZONES)).isEqualTo(OptionalInt.empty());
  }

  static Stream<List<String>> zoneOrders() {
    return Stream.of(
        List.of("zone-a"), List.of("zone-a", "zone-b"), List.of("zone-a", "zone-b", "zone-c"));
  }

  @ParameterizedTest(name = "zones={0}")
  @MethodSource("zoneOrders")
  void shouldPreserveRoundRobinSlotInvariant(final List<String> zoneOrder) {
    // zoned member (zone=zones[r], localNodeIdx=n) must occupy the same slot as the bare member it
    // replaced (nodeIdx = n * zoneCount + r).
    final int zoneCount = zoneOrder.size();
    for (int localNodeIdx = 0; localNodeIdx < 4; localNodeIdx++) {
      for (int rank = 0; rank < zoneCount; rank++) {
        final int bareNodeIdx = localNodeIdx * zoneCount + rank;
        final var bareSlot = ZoneLayout.effectiveSlot(null, bareNodeIdx, zoneOrder);
        final var zonedSlot =
            ZoneLayout.effectiveSlot(zoneOrder.get(rank), localNodeIdx, zoneOrder);

        assertThat(zonedSlot)
            .describedAs(
                "zoned (zone=%s, localIdx=%d) should map to same slot as bare nodeIdx=%d",
                zoneOrder.get(rank), localNodeIdx, bareNodeIdx)
            .isEqualTo(bareSlot);
      }
    }
  }

  // ── zoneRankForBareNodeIdx ─────────────────────────────────────────────────

  @ParameterizedTest(name = "nodeIdx={0}, zoneCount={1} -> rank={2}")
  @CsvSource(
      textBlock =
          """
          0, 2, 0
          1, 2, 1
          2, 2, 0
          3, 2, 1
          0, 3, 0
          1, 3, 1
          2, 3, 2
          3, 3, 0
          4, 3, 1
          """)
  void shouldReturnZoneRankForBareNodeIdx(
      final int nodeIdx, final int zoneCount, final int expectedRank) {
    assertThat(ZoneLayout.zoneRankForBareNodeIdx(nodeIdx, zoneCount)).isEqualTo(expectedRank);
  }

  // ── localNodeIdxForBareNodeIdx ─────────────────────────────────────────────

  @ParameterizedTest(name = "nodeIdx={0}, zoneCount={1} -> localIdx={2}")
  @CsvSource(
      textBlock =
          """
          0, 2, 0
          1, 2, 0
          2, 2, 1
          3, 2, 1
          0, 3, 0
          1, 3, 0
          2, 3, 0
          3, 3, 1
          4, 3, 1
          5, 3, 1
          """)
  void shouldReturnLocalNodeIdxForBareNodeIdx(
      final int nodeIdx, final int zoneCount, final int expectedLocalIdx) {
    assertThat(ZoneLayout.localNodeIdxForBareNodeIdx(nodeIdx, zoneCount))
        .isEqualTo(expectedLocalIdx);
  }
}
