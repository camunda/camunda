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

import io.camunda.zeebe.backup.logarchive.model.ArchivedSnapshotJson;
import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.RootFile;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LogArchiveRestorePlannerTest {

  private static final String TIMELINE = "tl-plan";

  @TempDir Path sourceDirectory;
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final InMemoryLogArchiveStore store = new InMemoryLogArchiveStore();
  private TestLogArchive archive;

  @AfterEach
  void tearDown() {
    if (archive != null) {
      archive.close();
    }
  }

  @Test
  void shouldSelectNewestSnapshotWithAllPositionsBelowCheckpoint() {
    // given
    startArchive(1);
    archive.appendAll(1, 2, 3, 4, 5, 6, 7, 8);
    archive.checkpoint(10, 6);
    archive.archive(3);
    archive.archive(6);
    archive.archive(8);
    final var usable = snapshot("2-1-2-2-1", 2, 2, 2, 2);
    archive.archiveSnapshot(usable);
    archive.archiveSnapshot(snapshot("4-1-6-4-1", 4, 6, 4, 4)); // processed position not below
    archive.archiveSnapshot(snapshot("5-1-5-6-1", 5, 5, 6, 5)); // follow-up event not below
    archive.archiveSnapshot(snapshot("5-1-5-5-2", 5, 5, 5, 7)); // max exported not below
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // when
    final var plan = LogArchiveRestorePlanner.plan(view, checkpoint(view, 10));

    // then
    assertThat(plan.snapshot()).hasValue(usable);
    assertThat(plan.objects())
        .extracting(o -> o.firstIndex() + "-" + o.lastIndex())
        .containsExactly("1-3", "4-6");
  }

  @Test
  void shouldStartAtObjectContainingSnapshotIndexAcrossSealedChunks() {
    // given
    startArchive(1);
    archive.appendAll(1, 2, 3, 4, 5, 6, 7, 8);
    archive.checkpoint(10, 7);
    archive.archive(2);
    archive.archive(4);
    archive.archive(6);
    archive.seal(2);
    archive.archive(8);
    archive.archiveSnapshot(snapshot("4-1-4-4-1", 4, 4, 4, 4));
    archive.publishRoot();
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // when
    final var plan = LogArchiveRestorePlanner.plan(view, checkpoint(view, 10));

    // then
    assertThat(plan.firstIndex()).isEqualTo(3);
    assertThat(plan.objects())
        .extracting(o -> o.firstIndex() + "-" + o.lastIndex())
        .containsExactly("3-4", "5-6", "7-8");
  }

  @Test
  void shouldStartAtFirstObjectWhenSnapshotIndexIsJustBelowIt() {
    // given
    startArchive(5);
    archive.resetSource(5);
    archive.appendAll(5, 6, 7);
    archive.checkpoint(10, 6);
    archive.archive(7);
    archive.archiveSnapshot(snapshot("4-1-4-4-1", 4, 4, 4, 4));
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // when
    final var plan = LogArchiveRestorePlanner.plan(view, checkpoint(view, 10));

    // then
    assertThat(plan.firstIndex()).isEqualTo(5);
    assertThat(plan.snapshotIndex()).isEqualTo(4);
  }

  @Test
  void shouldFailWhenNoObjectCoversSnapshotIndex() {
    // given
    startArchive(5);
    archive.resetSource(5);
    archive.appendAll(5, 6, 7);
    archive.checkpoint(10, 6);
    archive.archive(7);
    archive.archiveSnapshot(snapshot("3-1-3-3-1", 3, 3, 3, 3));
    final var view = LogArchiveView.read(store, TIMELINE, 1);
    final var checkpoint = checkpoint(view, 10);

    // when - then
    assertThatThrownBy(() -> LogArchiveRestorePlanner.plan(view, checkpoint))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("no archived object covers the snapshot index 3");
  }

  @Test
  void shouldFailWithoutSnapshotWhenArchiveDoesNotStartAtFirstIndex() {
    // given
    startArchive(5);
    archive.resetSource(5);
    archive.appendAll(5, 6, 7);
    archive.checkpoint(10, 6);
    archive.archive(7);
    final var view = LogArchiveView.read(store, TIMELINE, 1);
    final var checkpoint = checkpoint(view, 10);

    // when - then
    assertThatThrownBy(() -> LogArchiveRestorePlanner.plan(view, checkpoint))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("there is no snapshot below checkpoint 10")
        .hasMessageContaining("starts at index 5, not 1");
  }

  @Test
  void shouldFailOnGapInObjectRanges() {
    // given
    startArchive(1);
    archive.appendAll(1, 2, 3, 4, 5, 6);
    archive.checkpoint(10, 6);
    archive.archive(2);
    archive.archive(4);
    archive.archive(6);
    final long rootSeq = archive.publishRoot();
    removeOpenObjectFromRoot(rootSeq, 1);
    final var view = LogArchiveView.read(store, TIMELINE, 1);
    final var checkpoint = checkpoint(view, 10);

    // when - then
    assertThatThrownBy(() -> LogArchiveRestorePlanner.plan(view, checkpoint))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("gap in the archived index range")
        .hasMessageContaining("ends at index 2 but")
        .hasMessageContaining("starts at index 5");
  }

  @Test
  void shouldFailWhenCheckpointIsBeyondArchivedPosition() {
    // given
    startArchive(1);
    archive.appendAll(1, 2);
    archive.archive(2);
    final var view = LogArchiveView.read(store, TIMELINE, 1);
    final var checkpoint = new CoveredCheckpointJson(10, 3, "MARKER");

    // when - then
    assertThatThrownBy(() -> LogArchiveRestorePlanner.plan(view, checkpoint))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("is not covered by the archived position 2");
  }

  private void startArchive(final long startIndex) {
    archive = new TestLogArchive(store, TIMELINE, 1, sourceDirectory, meterRegistry);
    archive.startTimeline(startIndex, "");
  }

  private void removeOpenObjectFromRoot(final long rootSeq, final int objectIndex) {
    final var key = archive.prefix() + LogArchiveKeys.rootKey(rootSeq);
    final var root = LogArchiveJson.read(store.get(key), RootFile.class);
    final var openObjects = new ArrayList<>(root.openObjects());
    openObjects.remove(objectIndex);
    store.replace(
        key,
        LogArchiveJson.write(
            new RootFile(
                root.formatVersion(),
                root.timelineId(),
                root.parentTimelineId(),
                root.partitionId(),
                root.seq(),
                root.startIndex(),
                root.archivedIndex(),
                root.archivedPosition(),
                root.chunks(),
                openObjects,
                root.openCheckpoints(),
                root.snapshots())));
  }

  private static CoveredCheckpointJson checkpoint(final LogArchiveView view, final long id) {
    return view.findCheckpoint(id).orElseThrow();
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
