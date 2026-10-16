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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.backup.logarchive.model.ArchivedSnapshotJson;
import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LogArchiveViewTest {

  private static final String TIMELINE = "tl-view";

  @TempDir Path sourceDirectory;
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final InMemoryLogArchiveStore store = new InMemoryLogArchiveStore();
  private TestLogArchive archive;

  @BeforeEach
  void setUp() {
    archive = new TestLogArchive(store, TIMELINE, 1, sourceDirectory, meterRegistry);
    archive.startTimeline(1, "");
  }

  @AfterEach
  void tearDown() {
    archive.close();
  }

  @Test
  void shouldFoldRootAndLaterCommitEntries() {
    // given
    archive.appendAll(1, 2, 3);
    archive.checkpoint(100, 2);
    final var first = archive.archive(3);
    final long rootSeq = archive.publishRoot();

    archive.append(-1);
    archive.appendAll(4, 5);
    archive.checkpoint(101, 5);
    final var second = archive.archive(6);
    final var snapshot = snapshot("1-1-1-1-1", 3, 1, 1, 1);
    archive.archiveSnapshot(snapshot);

    // when
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // then
    assertThat(view.rootSeq()).isEqualTo(rootSeq);
    assertThat(view.lastSeq()).isEqualTo(rootSeq + 2);
    assertThat(view.startIndex()).isEqualTo(1);
    assertThat(view.archivedIndex()).isEqualTo(6);
    assertThat(view.archivedPosition()).isEqualTo(5);
    assertThat(view.openObjects()).containsExactly(first, second);
    assertThat(view.snapshots()).containsExactly(snapshot);
    assertThat(view.checkpoints())
        .extracting(CoveredCheckpointJson::checkpointId, CoveredCheckpointJson::checkpointPosition)
        .containsExactly(tuple(100L, 2L), tuple(101L, 5L));
  }

  @Test
  void shouldFoldAllCommitEntriesWithoutRoot() {
    // given
    archive.appendAll(1, 2);
    final var object = archive.archive(2);

    // when
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // then
    assertThat(view.rootSeq()).isZero();
    assertThat(view.lastSeq()).isEqualTo(2);
    assertThat(view.openObjects()).containsExactly(object);
    assertThat(view.archivedPosition()).isEqualTo(2);
  }

  @Test
  void shouldTakeHighestRootWhenLatestHintIsMissing() {
    // given
    archive.appendAll(1, 2);
    archive.archive(2);
    archive.publishRoot();
    archive.appendAll(3, 4);
    archive.archive(4);
    final long highestRoot = archive.publishRoot();
    store.remove(archive.prefix() + LogArchiveKeys.latestKey());

    // when
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // then
    assertThat(view.rootSeq()).isEqualTo(highestRoot);
    assertThat(view.archivedIndex()).isEqualTo(4);
    assertThat(view.openObjects()).hasSize(2);
  }

  @Test
  void shouldReadOlderCheckpointFromSealedChunk() {
    // given
    archive.appendAll(1, 2, 3);
    archive.checkpoint(100, 2);
    final var sealedObject = archive.archive(3);
    final var chunk = archive.seal(1);
    archive.appendAll(4, 5);
    archive.checkpoint(101, 5);
    archive.archive(5);
    archive.publishRoot();

    // when
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // then
    assertThat(view.chunks()).containsExactly(chunk);
    assertThat(view.checkpoints())
        .extracting(CoveredCheckpointJson::checkpointId)
        .containsOnly(101L);
    assertThat(view.findCheckpoint(100)).hasValue(new CoveredCheckpointJson(100, 2, "MARKER"));
    assertThat(view.findCheckpoint(99)).isEmpty();
    assertThat(view.readChunk(chunk).objects()).containsExactly(sealedObject);
    assertThat(view.checkpointsNewestFirst())
        .extracting(CoveredCheckpointJson::checkpointId)
        .containsExactly(101L, 100L);
  }

  @Test
  void shouldRejectChunkThatDoesNotMatchItsChecksum() {
    // given
    archive.appendAll(1, 2);
    archive.checkpoint(100, 1);
    archive.archive(2);
    final var chunk = archive.seal(1);
    archive.publishRoot();
    store.replace(archive.prefix() + chunk.key(), "{}".getBytes());
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // when - then
    assertThatThrownBy(() -> view.findCheckpoint(100))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("does not match its sha256");
  }

  @Test
  void shouldFailWhenPartitionHasNoArchive() {
    // when - then
    assertThatThrownBy(() -> LogArchiveView.read(store, TIMELINE, 2))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("No log archive found for partition 2");
  }

  @Test
  void shouldFailOnNonContiguousCommitEntry() {
    // given
    archive.appendAll(1, 2);
    final LogObjectJson object = archive.archive(2);
    archive.publishRoot();
    archive.appendAll(3);
    archive.archive(3);
    // the commit entry after the root claims to archive an already archived range
    final var tampered =
        new String(store.get(archive.prefix() + LogArchiveKeys.commitKey(3)))
            .replace("\"firstIndex\":3", "\"firstIndex\":" + object.firstIndex());
    store.replace(archive.prefix() + LogArchiveKeys.commitKey(3), tampered.getBytes());

    // when - then
    assertThatThrownBy(() -> LogArchiveView.read(store, TIMELINE, 1))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("expected it to start at index 3");
  }

  private static ArchivedSnapshotJson snapshot(
      final String id,
      final long index,
      final long processedPosition,
      final long lastFollowupEventPosition,
      final long maxExportedPosition) {
    return new ArchivedSnapshotJson(
        id,
        index,
        1,
        processedPosition,
        processedPosition,
        lastFollowupEventPosition,
        maxExportedPosition,
        1,
        10);
  }
}
