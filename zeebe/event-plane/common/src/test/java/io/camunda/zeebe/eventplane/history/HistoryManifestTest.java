/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class HistoryManifestTest {

  private static final UUID A = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID B = UUID.fromString("00000000-0000-4000-8000-000000000002");
  private static final Instant NOON = Instant.parse("2026-10-19T12:00:00Z");

  @Test
  void shouldRejectWhatTheDesignRejects() {
    // when, then
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new HistoryManifest(2, "cluster-1", "default", B, NOON, Map.of(1, Base.NOTHING)))
        .withMessage("Expected manifest version 1, but got 2");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HistoryManifest(1, " ", "default", B, NOON, Map.of(1, Base.NOTHING)))
        .withMessageContaining("to name its cluster and physical tenant");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new HistoryManifest(1, "c", "", B, NOON, Map.of(1, Base.NOTHING)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> manifest(B, Map.of()))
        .withMessage("Expected a history of at least one partition, but got none");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> manifest(B, Map.of(0, Base.NOTHING)))
        .withMessage("Expected a partition of 1 or more, but got 0");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> manifest(B, Map.of(1, new Base(B, 900, 400))))
        .withMessageContaining("to take over another history in partition 1, but got itself");
  }

  @Test
  void shouldRejectABaseThatIsNeitherACheckpointNorNothing() {
    // when, then
    assertThatIllegalArgumentException().isThrownBy(() -> new Base(A, 0, -1));
    assertThatIllegalArgumentException().isThrownBy(() -> new Base(null, 0, 0));
    assertThatIllegalArgumentException().isThrownBy(() -> new Base(A, 900, 0));
    assertThatIllegalArgumentException().isThrownBy(() -> new Base(null, 900, 400));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Base(A, 900, 0))
        .withMessageContaining("but got source " + A + ", index 900 and position 0");
  }

  @Test
  void shouldNotChangeItsPartitionsAfterwards() {
    // given
    final var partitions = new HashMap<Integer, Base>();
    partitions.put(1, Base.NOTHING);
    final var manifest = manifest(A, partitions);

    // when
    partitions.put(2, Base.NOTHING);

    // then
    assertThat(manifest.partitions()).containsOnlyKeys(1);
    assertThatThrownBy(() -> manifest.partitions().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldKeepItsPartitionsInNumericalOrder() {
    // given
    final var partitions = new HashMap<Integer, Base>();
    for (final int partition : List.of(10, 2, 1, 100, 3)) {
      partitions.put(partition, Base.NOTHING);
    }

    // when
    final var manifest = manifest(A, partitions);

    // then
    assertThat(manifest.partitions().keySet()).containsExactly(1, 2, 3, 10, 100);
  }

  @Test
  void shouldOrderTheCurrentHistoryLastByTimeThenByIdAsText() {
    // given ids that UUID.compareTo, comparing signed numbers, orders the other way round
    final var low = UUID.fromString("7fffffff-ffff-4fff-8fff-ffffffffffff");
    final var high = UUID.fromString("80000000-0000-4000-8000-000000000000");
    final var later =
        new HistoryManifest(1, "c", "t", A, NOON.plusSeconds(1), Map.of(1, Base.NOTHING));

    // when
    final var ordered =
        List.of(
                later,
                manifest(high, Map.of(1, Base.NOTHING)),
                manifest(low, Map.of(1, Base.NOTHING)))
            .stream()
            .sorted(HistoryManifest.CURRENT_LAST)
            .toList();

    // then
    assertThat(ordered).extracting(HistoryManifest::historyId).containsExactly(low, high, A);
  }

  private static HistoryManifest manifest(final UUID id, final Map<Integer, Base> partitions) {
    return new HistoryManifest(1, "cluster-1", "default", id, NOON, partitions);
  }
}
