/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.broker.partitioning.PartitionRestartSnapshots.Outcome;
import io.camunda.zeebe.broker.partitioning.PartitionRestartSnapshots.PartitionSnapshot;
import io.camunda.zeebe.broker.partitioning.PartitionRestartSnapshots.SnapshotPartition;
import io.camunda.zeebe.broker.partitioning.PartitionRestartSnapshots.TakenSnapshot;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

final class PartitionRestartSnapshotsTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final PartitionRestartSnapshots snapshots =
      new PartitionRestartSnapshots(Duration.ofMillis(200));

  @Test
  void shouldReportEveryPartitionsSnapshot() {
    // given
    final var taken = new TakenSnapshot(42, 1024);

    // when
    final var reports =
        take(
            partition(1, () -> CompletableFuture.completedFuture(taken)),
            partition(2, () -> CompletableFuture.completedFuture(null)));

    // then
    assertThat(reports)
        .extracting(PartitionSnapshot::partition, PartitionSnapshot::outcome)
        .containsExactly(tuple(1, Outcome.TAKEN), tuple(2, Outcome.SKIPPED));
    assertThat(reports.getFirst().snapshot()).isEqualTo(taken);
  }

  @Test
  void shouldGoAheadWhenASnapshotFails() {
    // when
    final var reports =
        take(
            partition(
                1,
                () ->
                    CompletableFuture.failedFuture(
                        new IllegalStateException("NoEntryAtSnapshotPosition"))));

    // then
    assertThat(reports.getFirst().outcome()).isEqualTo(Outcome.FAILED);
    assertThat(reports.getFirst().failure()).contains("NoEntryAtSnapshotPosition");
  }

  @Test
  void shouldGoAheadWhenASnapshotTakesTooLong() {
    // when
    final var reports = take(partition(1, CompletableFuture::new));

    // then
    assertThat(reports.getFirst().outcome()).isEqualTo(Outcome.TIMED_OUT);
  }

  @Test
  void shouldGoAheadWhenAPartitionCannotBeAsked() {
    // when
    final var reports =
        take(
            partition(
                1,
                () -> {
                  throw new IllegalStateException("closed");
                }));

    // then
    assertThat(reports.getFirst().outcome()).isEqualTo(Outcome.FAILED);
  }

  private List<PartitionSnapshot> take(final SnapshotPartition... partitions) {
    final var reports = snapshots.take(List.of(partitions));
    assertThat(reports).succeedsWithin(TIMEOUT);
    return reports.join();
  }

  private static SnapshotPartition partition(
      final int id, final Supplier<CompletableFuture<@Nullable TakenSnapshot>> snapshot) {
    return new SnapshotPartition() {
      @Override
      public int partition() {
        return id;
      }

      @Override
      public CompletableFuture<@Nullable TakenSnapshot> forceSnapshot() {
        return snapshot.get();
      }
    };
  }
}
