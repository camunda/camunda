/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.model.ArchivedSnapshotJson;
import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import io.camunda.zeebe.backup.logarchive.model.HandoverJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.model.TimelineFile;
import java.util.List;

/**
 * Writes the commit entries of one partition of a timeline without any log data, for tests that
 * only resolve and plan. Every entry's position equals its index.
 */
final class TimelineFixture {

  /**
   * The checkpoint timestamps of the tests: checkpoint {@code id} is taken at minute {@code id}.
   */
  static final long EPOCH_MILLIS = 1_760_000_000_000L;

  private final InMemoryLogArchiveStore store;
  private final String timelineId;
  private final int partitionId;
  private final String prefix;
  private long archivedIndex;
  private long nextSeq = 1;

  private TimelineFixture(
      final InMemoryLogArchiveStore store,
      final String timelineId,
      final int partitionId,
      final long startIndex,
      final String parentTimelineId) {
    this.store = store;
    this.timelineId = timelineId;
    this.partitionId = partitionId;
    prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);
    archivedIndex = startIndex - 1;
    commit(CommitEntry.Type.TIMELINE_STARTED, startIndex, parentTimelineId, List.of(), List.of());
  }

  /** Starts a timeline the way the publisher does: commit entry and registry file. */
  static TimelineFixture start(
      final InMemoryLogArchiveStore store,
      final String timelineId,
      final int partitionId,
      final long startIndex,
      final String parentTimelineId) {
    final var fixture =
        startWithoutRegistry(store, timelineId, partitionId, startIndex, parentTimelineId);
    store
        .put(
            fixture.prefix + LogArchiveKeys.timelineKey(),
            LogArchiveJson.write(
                new TimelineFile(
                    LogArchiveJson.FORMAT_VERSION,
                    timelineId,
                    partitionId,
                    parentTimelineId,
                    startIndex,
                    1,
                    -1,
                    -1,
                    -1)))
        .join();
    return fixture;
  }

  /** Starts a timeline of an archive written before the registry file existed. */
  static TimelineFixture startWithoutRegistry(
      final InMemoryLogArchiveStore store,
      final String timelineId,
      final int partitionId,
      final long startIndex,
      final String parentTimelineId) {
    return new TimelineFixture(store, timelineId, partitionId, startIndex, parentTimelineId);
  }

  static long timestamp(final long checkpointId) {
    return EPOCH_MILLIS + checkpointId * 60_000;
  }

  /** A covered checkpoint taken at minute {@code id}. */
  static CoveredCheckpointJson checkpoint(final long id, final long position) {
    return new CoveredCheckpointJson(id, position, "SCHEDULED_BACKUP", timestamp(id));
  }

  /** Archives one object up to {@code lastIndex}, covering the given checkpoints. */
  TimelineFixture archive(final long lastIndex, final CoveredCheckpointJson... checkpoints) {
    final long firstIndex = archivedIndex + 1;
    final var object =
        new LogObjectJson(
            LogArchiveKeys.dataKey(firstIndex, lastIndex, 1),
            1,
            firstIndex,
            lastIndex,
            firstIndex,
            lastIndex,
            (int) (lastIndex - firstIndex + 1),
            1,
            "sha");
    archivedIndex = lastIndex;
    commit(CommitEntry.Type.LOG_ARCHIVED, null, null, List.of(object), List.of(checkpoints));
    return this;
  }

  /** Archives a snapshot at the index whose positions all equal the index. */
  TimelineFixture snapshot(final long index) {
    final var snapshot =
        new ArchivedSnapshotJson(
            index + "-1-" + index + "-" + index, index, 1, index, index, index, index, 1, 1);
    write(
        new CommitEntry(
            LogArchiveJson.FORMAT_VERSION,
            timelineId,
            partitionId,
            nextSeq,
            CommitEntry.Type.SNAPSHOT_ARCHIVED,
            0,
            0,
            archivedIndex,
            archivedIndex,
            null,
            null,
            List.of(),
            List.of(),
            snapshot,
            null));
    return this;
  }

  TimelineFixture handover(final long checkpointId, final long position) {
    write(
        new CommitEntry(
            LogArchiveJson.FORMAT_VERSION,
            timelineId,
            partitionId,
            nextSeq,
            CommitEntry.Type.HANDOVER,
            0,
            0,
            archivedIndex,
            archivedIndex,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            null,
            new HandoverJson(checkpointId, position)));
    return this;
  }

  LogArchiveView view() {
    return LogArchiveView.read(store, timelineId, partitionId);
  }

  private void commit(
      final CommitEntry.Type type,
      final Long startIndex,
      final String parentTimelineId,
      final List<LogObjectJson> objects,
      final List<CoveredCheckpointJson> checkpoints) {
    write(
        new CommitEntry(
            LogArchiveJson.FORMAT_VERSION,
            timelineId,
            partitionId,
            nextSeq,
            type,
            0,
            0,
            archivedIndex,
            type == CommitEntry.Type.TIMELINE_STARTED ? -1 : archivedIndex,
            startIndex,
            parentTimelineId,
            objects,
            checkpoints,
            null,
            null));
  }

  private void write(final CommitEntry entry) {
    store.put(prefix + LogArchiveKeys.commitKey(nextSeq), LogArchiveJson.write(entry)).join();
    nextSeq++;
  }
}
