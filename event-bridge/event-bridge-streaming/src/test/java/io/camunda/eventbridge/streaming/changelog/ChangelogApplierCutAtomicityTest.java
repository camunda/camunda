/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.state.TestColumnFamilies;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cut-atomic apply (event-bridge-streaming ADR 0009 decision 6): records are buffered until their
 * cut's offset-marker arrives, then the whole cut becomes visible as one unit. A writer crashing
 * mid-publish leaves a torn tail — records with no marker yet — which must stay invisible rather
 * than half-applied (a half-applied cut would be missing the dedup/position bookkeeping its marker
 * carries, and a later correct cut folding on top would double-merge it after promotion).
 */
final class ChangelogApplierCutAtomicityTest {

  private static final String TOPIC = "cut-atomicity-changelog";
  private static final int PARTITION = 0;

  @TempDir private Path storeDir;

  @Test
  void shouldHideATornTailUntilItsMarkerArrivesThenApplyTheWholeCutAtomically() throws Exception {
    final var broker = new FakeChangelogBroker();

    try (var provider =
        RocksDbStateStoreProvider.<TestColumnFamilies>open(
            storeDir.toFile(), new SimpleMeterRegistry())) {
      final long[] persistedPosition = {ChangelogApplier.NO_POSITION};
      final long[] persistedSourceOffset = {-1L};
      final var applier =
          ChangelogApplier.singleColumnFamily(
              broker.client,
              TOPIC,
              PARTITION,
              provider,
              TestColumnFamilies.CELLS,
              () -> persistedPosition[0],
              cut -> {
                persistedSourceOffset[0] = cut.sourceOffset();
                persistedPosition[0] = cut.changelogPosition();
              });

      // given — a torn tail: two data rows published with no trailing marker (a writer that
      // crashed mid-publish)
      final byte[] keyA = {0, 0, 0, 1};
      final byte[] keyB = {0, 0, 0, 2};
      broker.appendRaw(
          List.of(new byte[][] {keyA, "a".getBytes()}, new byte[][] {keyB, "b".getBytes()}));

      // when — the applier polls the torn tail
      final int appliedFromTornTail = applier.pollOnce();

      // then — nothing from the incomplete cut is visible, and nothing was reported persisted
      assertThat(appliedFromTornTail).isZero();
      assertThat(rowCount(provider)).isZero();
      assertThat(persistedPosition[0]).isEqualTo(ChangelogApplier.NO_POSITION);

      // when — the marker for that same cut finally arrives
      final List<Long> markerPositions =
          broker.appendRaw(
              List.<byte[][]>of(
                  new byte[][] {ChangelogMarker.KEY, ChangelogMarker.encodeValue(42L)}));
      final int appliedAfterMarker = applier.pollOnce();

      // then — the whole cut becomes visible in one atomic step: both rows, together
      assertThat(appliedAfterMarker).isEqualTo(1);
      assertThat(rowCount(provider)).isEqualTo(2);
      assertThat(persistedSourceOffset[0]).isEqualTo(42L);
      assertThat(persistedPosition[0]).isEqualTo(markerPositions.get(markerPositions.size() - 1));
    }
  }

  private static int rowCount(final RocksDbStateStoreProvider<TestColumnFamilies> provider) {
    final var store =
        provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
    final int[] count = {0};
    store.forEach((k, v) -> count[0]++);
    return count[0];
  }
}
