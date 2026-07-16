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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Warming (event-bridge-streaming ADR 0009 decision 6): an empty joiner has no persisted changelog
 * position, so it starts tailing from the changelog's start (position 0) — a full cold rebuild
 * bounded by the live keyspace rather than history — and its reported readiness reflects real
 * progress as it catches up, exactly the signal consumer-groups ADR 0006 decision 1's ready-only
 * promotion consults.
 */
final class ChangelogApplierWarmingTest {

  private static final String TOPIC = "warming-changelog";
  private static final int PARTITION = 0;

  @TempDir private Path storeDir;

  @Test
  void shouldWarmFromTheStartAndReportCaughtUpReadinessOnceDrained() throws Exception {
    final var broker = new FakeChangelogBroker();
    final var publisher = new ChangelogPublisher(broker.client, TOPIC, PARTITION);

    // given — the active publishes a few cuts before any standby exists
    publisher.publish(List.of(ChangelogRecord.put(new byte[] {0, 0, 0, 1}, "a".getBytes())), 1L);
    publisher.publish(List.of(ChangelogRecord.put(new byte[] {0, 0, 0, 2}, "b".getBytes())), 2L);

    try (var provider =
        RocksDbStateStoreProvider.<TestColumnFamilies>open(
            storeDir.toFile(), new SimpleMeterRegistry())) {
      final long[] persistedPosition = {ChangelogApplier.NO_POSITION};
      final var applier =
          ChangelogApplier.singleColumnFamily(
              broker.client,
              TOPIC,
              PARTITION,
              provider,
              TestColumnFamilies.CELLS,
              () -> persistedPosition[0],
              cut -> persistedPosition[0] = cut.changelogPosition());

      // then — a cold joiner is maximally behind before its first poll
      assertThat(applier.readiness()).isEqualTo(Long.MAX_VALUE);

      // when — it warms by replaying the changelog from the start (no source fetch, no fold)
      applier.drainToEnd();

      // then — it has applied both cuts and reports caught up
      assertThat(applier.lastAppliedPosition()).isGreaterThanOrEqualTo(0);
      assertThat(applier.readiness()).isZero();

      // and — while the active keeps publishing, a further poll catches the new cut too
      publisher.publish(List.of(ChangelogRecord.put(new byte[] {0, 0, 0, 3}, "c".getBytes())), 3L);
      applier.drainToEnd();
      assertThat(applier.readiness()).isZero();
    }
  }
}
