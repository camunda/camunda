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
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.model.MaintenanceEntry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MaintenanceFoldTest {

  private static final String TIMELINE = "tl-b";

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
  void shouldFoldCompactionsBeforeAndAfterRemovals() {
    // given - objects [1-2] [3-4] [5-6] [7-8] [9-10] [11-12], hold at 7
    startAuthority();
    archiveObjects(2, 4, 6, 8, 10, 12);
    archive.archiveSnapshot(snapshot("6-1", 6, 6));
    archive.archiveSnapshot(snapshot("7-1", 7, 7));
    archive.moveHold(7, "7-1");
    archive.compact(1, 4);
    archive.remove(4);
    archive.compact(5, 8);
    archive.compact(9, 12);

    // when
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // then
    assertThat(view.isAuthority()).isTrue();
    assertThat(view.holdIndex()).isEqualTo(7);
    assertThat(view.maintainedObjects().orElseThrow())
        .extracting(LogObjectJson::key)
        .containsExactly(
            "data/00000000000000000005-00000000000000000008-c3.seg",
            "data/00000000000000000009-00000000000000000012-c4.seg");
  }

  @Test
  void shouldFoldFromRootAndAnEntryBasedOnTheRoot() {
    // given
    startAuthority();
    archiveObjects(2, 4, 6);
    archive.archiveSnapshot(snapshot("5-1", 5, 5));
    archive.moveHold(5, "5-1");
    archive.publishRoot();
    archive.remove(2);
    archive.compact(3, 6);

    // when
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // then
    assertThat(view.rootSeq()).isPositive();
    assertThat(view.maintainedObjects().orElseThrow())
        .extracting(LogObjectJson::firstIndex, LogObjectJson::lastIndex)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(3L, 6L));
  }

  @Test
  void shouldFailOnRangeNotStartingAtAnObjectBoundary() {
    // given
    startAuthority();
    archiveObjects(2, 4, 6);
    archive.compact(1, 3);

    // when - then
    assertThatThrownBy(() -> LogArchiveView.read(store, TIMELINE, 1))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("maintenance entry 1")
        .hasMessageContaining("[1, 3]")
        .hasMessageContaining("boundaries");
  }

  @Test
  void shouldFailOnCompactionAboveTheArchivedIndexOfItsBase() {
    // given - the entry is based on a commit before the objects it covers were archived
    startAuthority();
    archiveObjects(2, 4);
    final var object = archive.writeCompactedObject(1, 4);
    archive.writeMaintenance(
        new MaintenanceEntry(
            1, TIMELINE, 1, 1, MaintenanceEntry.Type.COMPACTED, 2, object, null, null));

    // when - then
    assertThatThrownBy(() -> LogArchiveView.read(store, TIMELINE, 1))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("maintenance entry 1")
        .hasMessageContaining("above the archived index 2");
  }

  @Test
  void shouldFailOnRemovalIntoTheHold() {
    // given - hold at 5; removal up to 6 would remove the hold's object
    startAuthority();
    archiveObjects(2, 4, 6);
    archive.archiveSnapshot(snapshot("5-1", 5, 5));
    archive.moveHold(5, "5-1");
    archive.remove(6);

    // when - then
    assertThatThrownBy(() -> LogArchiveView.read(store, TIMELINE, 1))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("maintenance entry 1")
        .hasMessageContaining("restore hold 5");
  }

  @Test
  void shouldFailOnRemovalBasedOnACommitBeforeTheHoldMovedEvenWithARoot() {
    // given - the hold moved after the entry's base; the root is newer than the base
    startAuthority();
    archiveObjects(2, 4, 6);
    archive.remove(2);
    archive.archiveSnapshot(snapshot("5-1", 5, 5));
    archive.moveHold(5, "5-1");
    archive.publishRoot();

    // when - then
    assertThatThrownBy(() -> LogArchiveView.read(store, TIMELINE, 1))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("maintenance entry 1")
        .hasMessageContaining("restore hold 1");
  }

  @Test
  void shouldFailOnRemovalNotEndingAtAnObjectBoundary() {
    // given
    startAuthority();
    archiveObjects(2, 4, 6);
    archive.archiveSnapshot(snapshot("5-1", 5, 5));
    archive.moveHold(5, "5-1");
    archive.remove(3);

    // when - then
    assertThatThrownBy(() -> LogArchiveView.read(store, TIMELINE, 1))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("maintenance entry 1")
        .hasMessageContaining("last index of a current object");
  }

  @Test
  void shouldRefuseRestorePointBelowTheHoldBeforeReadingObjects() {
    // given - snapshots at 2 and 6, hold at 6; a checkpoint at position 5 selects the one at 2
    startAuthority();
    archive.checkpoint(10, 5);
    archive.checkpoint(11, 11);
    archiveObjects(2, 4, 6, 8, 10, 12);
    archive.archiveSnapshot(snapshot("2-1", 2, 2));
    archive.archiveSnapshot(snapshot("6-1", 6, 6));
    archive.moveHold(6, "6-1");
    archive.remove(4);
    final var view = LogArchiveView.read(store, TIMELINE, 1);
    store.calls.clear();

    // when - then
    assertThatThrownBy(() -> LogArchiveRestorePlanner.plan(view, checkpoint(10, 5)))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("is below the restore hold of partition 1");
    assertThat(store.calls).noneMatch(call -> call.contains("data/"));

    // and a restore point above the hold plans the objects from the one holding the hold
    final var plan = LogArchiveRestorePlanner.plan(view, checkpoint(11, 11));
    assertThat(plan.objects()).extracting(LogObjectJson::firstIndex).startsWith(5L);
  }

  @Test
  void shouldIgnoreTheMaintenanceLogOfAClusterTimeline() {
    // given - an invalid entry that would fail the fold of a timeline in mode B
    archive = new TestLogArchive(store, TIMELINE, 1, sourceDirectory, meterRegistry);
    archive.startTimeline(1, "");
    archive.appendAll(1, 2, 3, 4);
    archive.checkpoint(10, 3);
    archive.archive(2);
    archive.archive(4);
    archive.archiveSnapshot(snapshot("2-1", 2, 2));
    archive.writeMaintenance(
        new MaintenanceEntry(1, TIMELINE, 1, 1, MaintenanceEntry.Type.REMOVED, 1, null, 1L, -1L));

    // when
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // then
    assertThat(view.isAuthority()).isFalse();
    assertThat(view.maintainedObjects()).isEmpty();
    assertThat(LogArchiveRestorePlanner.plan(view, checkpoint(10, 3)).objects()).hasSize(2);
  }

  @Test
  void shouldNotChangeAnAuthorityTimelineWithoutMaintenance() {
    // given
    startAuthority();
    archive.checkpoint(10, 3);
    archiveObjects(2, 4);

    // when
    final var view = LogArchiveView.read(store, TIMELINE, 1);

    // then
    assertThat(view.maintainedObjects().orElseThrow()).hasSize(2);
    assertThat(view.hasMovedHold()).isFalse();
    assertThat(LogArchiveRestorePlanner.plan(view, checkpoint(10, 3)).objects()).hasSize(2);
    assertThat(view.holdIndex()).isEqualTo(1);
  }

  @Test
  void shouldPlanABridgedRestoreFromCompactedObjects() {
    // given - the whole timeline compacted into one object, the hold not moved
    startAuthority();
    archive.checkpoint(10, 5);
    archiveObjects(2, 4, 6);
    archive.archiveSnapshot(snapshot("2-1", 2, 2));
    archive.compact(1, 6);

    // when
    final var view = LogArchiveView.read(store, TIMELINE, 1);
    final var plan = LogArchiveRestorePlanner.planBridged(view, checkpoint(10, 5), 1);

    // then
    assertThat(plan.objects())
        .extracting(LogObjectJson::firstIndex, LogObjectJson::lastIndex)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 6L));
  }

  private void startAuthority() {
    archive = new TestLogArchive(store, TIMELINE, 1, sourceDirectory, meterRegistry);
    archive.startAuthorityTimeline(1, "");
    archive.appendAll(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
  }

  private void archiveObjects(final long... lastIndexes) {
    for (final long lastIndex : lastIndexes) {
      archive.archive(lastIndex);
    }
  }

  private static CoveredCheckpointJson checkpoint(final long id, final long position) {
    return TimelineFixture.checkpoint(id, position);
  }

  private static ArchivedSnapshotJson snapshot(
      final String id, final long index, final long position) {
    return new ArchivedSnapshotJson(id, index, 1, position, position, position, position, 1, 1);
  }
}
