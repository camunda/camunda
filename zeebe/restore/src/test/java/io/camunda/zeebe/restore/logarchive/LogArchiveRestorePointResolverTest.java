/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LogArchiveRestorePointResolverTest {

  private static final String TIMELINE = "tl-resolve";

  @TempDir Path sourceDirectory1;
  @TempDir Path sourceDirectory2;
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final InMemoryLogArchiveStore store = new InMemoryLogArchiveStore();
  private TestLogArchive partition1;
  private TestLogArchive partition2;

  @BeforeEach
  void setUp() {
    partition1 = new TestLogArchive(store, TIMELINE, 1, sourceDirectory1, meterRegistry);
    partition2 = new TestLogArchive(store, TIMELINE, 2, sourceDirectory2, meterRegistry);
    partition1.startTimeline(1, "");
    partition2.startTimeline(1, "");
  }

  @AfterEach
  void tearDown() {
    partition1.close();
    partition2.close();
  }

  @Test
  void shouldResolveLatestToNewestCheckpointCoveredByAllPartitions() {
    // given
    partition1.appendAll(1, 2, 3, 4);
    partition1.checkpoint(10, 2);
    partition1.checkpoint(20, 4);
    partition1.archive(4);

    partition2.appendAll(5, 6, 7, 8);
    partition2.checkpoint(10, 6);
    partition2.checkpoint(20, 9); // not archived yet on partition 2
    partition2.archive(4);

    // when
    final var restorePoint = LogArchiveRestorePointResolver.resolve(views(), Optional.empty());

    // then
    assertThat(restorePoint.checkpointId()).isEqualTo(10);
    assertThat(restorePoint.checkpoint(1).checkpointPosition()).isEqualTo(2);
    assertThat(restorePoint.checkpoint(2).checkpointPosition()).isEqualTo(6);
  }

  @Test
  void shouldResolveLatestThroughSealedChunks() {
    // given
    partition1.appendAll(1, 2, 3);
    partition1.checkpoint(10, 2);
    partition1.checkpoint(20, 3);
    partition1.archive(3);

    partition2.appendAll(5, 6, 7);
    partition2.checkpoint(10, 6);
    partition2.checkpoint(20, 7);
    partition2.archive(3);
    partition2.seal(1);
    partition2.publishRoot(); // the root lists no recent checkpoint anymore

    // when
    final var restorePoint = LogArchiveRestorePointResolver.resolve(views(), Optional.empty());

    // then
    assertThat(restorePoint.checkpointId()).isEqualTo(20);
    assertThat(restorePoint.checkpoint(2).checkpointPosition()).isEqualTo(7);
  }

  @Test
  void shouldResolveGivenCheckpointFromSealedChunk() {
    // given
    partition1.appendAll(1, 2);
    partition1.checkpoint(10, 1);
    partition1.archive(2);
    partition1.seal(1);
    partition1.publishRoot();
    partition1.appendAll(3);
    partition1.checkpoint(20, 3);
    partition1.archive(3);

    partition2.appendAll(5, 6);
    partition2.checkpoint(10, 5);
    partition2.checkpoint(20, 6);
    partition2.archive(2);

    // when
    final var restorePoint = LogArchiveRestorePointResolver.resolve(views(), Optional.of(10L));

    // then
    assertThat(restorePoint.checkpointId()).isEqualTo(10);
    assertThat(restorePoint.checkpoint(1).checkpointPosition()).isEqualTo(1);
    assertThat(restorePoint.checkpoint(2).checkpointPosition()).isEqualTo(5);
  }

  @Test
  void shouldFailWhenGivenCheckpointIsNotCoveredByEveryPartition() {
    // given
    partition1.appendAll(1, 2);
    partition1.checkpoint(10, 2);
    partition1.archive(2);

    partition2.appendAll(5, 6);
    partition2.checkpoint(10, 7); // beyond F of partition 2
    partition2.archive(2);
    final var views = views();

    // when - then
    assertThatThrownBy(() -> LogArchiveRestorePointResolver.resolve(views, Optional.of(10L)))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("Checkpoint 10 is not covered by the log archive of partition 2");
  }

  @Test
  void shouldFailWhenNoCheckpointIsCommonToAllPartitions() {
    // given
    partition1.appendAll(1, 2);
    partition1.checkpoint(10, 2);
    partition1.archive(2);

    partition2.appendAll(5, 6);
    partition2.checkpoint(11, 6);
    partition2.archive(2);
    final var views = views();

    // when - then
    assertThatThrownBy(() -> LogArchiveRestorePointResolver.resolve(views, Optional.empty()))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("No checkpoint is covered by the log archives of all partitions");
  }

  private Map<Integer, LogArchiveView> views() {
    return Map.of(
        1, LogArchiveView.read(store, TIMELINE, 1), 2, LogArchiveView.read(store, TIMELINE, 2));
  }
}
