/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.processing;

import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.protocol.impl.record.value.management.CoveredCheckpoint;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.LogObjectValue;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Applies log archive events to the replicated state. The very same code runs while processing a
 * command and while replaying its event, so leader and followers end up with identical state (I3,
 * I4).
 *
 * <p>Besides the state, applying an event also completes it: the fields that are derived from state
 * (F after applying, the covered checkpoints) are written into the event record if they are not
 * there yet. When replaying, they are already there and are equal to what is derived again.
 *
 * <p>The event's seq is taken from the record, never from state: the processor assigns it while
 * processing, and the log carries it to replay.
 */
final class LogArchiveEventApplier {

  private final int partitionId;
  private final DbLogArchiveState state;

  LogArchiveEventApplier(final int partitionId, final DbLogArchiveState state) {
    this.partitionId = partitionId;
    this.state = state;
  }

  /**
   * Applies the event.
   *
   * @param recordPosition the position written into the commit entry, see {@link
   *     LogArchiveRecordsProcessor#recordPositionOf}
   * @param recordTimestamp the timestamp written into the commit entry, see {@link
   *     LogArchiveRecordsProcessor#NO_RECORD_TIMESTAMP}
   * @return the commit entry JSON that was stored in the outbox, null for events without a seq
   */
  byte @Nullable [] apply(
      final LogArchiveIntent intent,
      final LogArchiveRecord event,
      final long recordPosition,
      final long recordTimestamp) {
    switch (intent) {
      case TIMELINE_STARTED -> applyTimelineStarted(event);
      case LOG_ARCHIVED -> applyLogArchived(event);
      case SNAPSHOT_ARCHIVED -> applySnapshotArchived(event);
      case INDEX_SEALED -> applyIndexSealed(event);
      case HANDOVER_COMPLETED -> applyHandoverCompleted(event);
      case LOG_MERGED -> applyLogMerged(event);
      case LOG_RELEASED -> applyLogReleased(event);
      case PUBLISHED -> {
        applyPublished(event);
        return null;
      }
      default ->
          throw new IllegalArgumentException("Expected a log archive event, but got " + intent);
    }

    final var json =
        LogArchiveJsonMapper.commitEntryJson(
            partitionId, intent, event, recordPosition, recordTimestamp);
    state.insertOutbox(event.getSeq(), json);
    return json;
  }

  private void applyTimelineStarted(final LogArchiveRecord event) {
    // The handover belongs to the branch, not to the timeline: a new timeline after a restore past
    // H or a fork after a rollback continues a branch whose old backups have already ended. A
    // restore to before H brings back a state without a handover, so nothing is carried then.
    final var previous = state.meta();
    final long handoverCheckpointId = previous.getHandoverCheckpointId();
    final long handoverPosition = previous.getHandoverPosition();
    // The backup checkpoints are kept for the same reason: they belong to the branch's history,
    // which the new timeline continues. After a restore the state only holds the ones up to the
    // restore point anyway.
    state.clearAll();
    final var meta =
        new LogArchiveMeta()
            .setTimelineId(event.getTimelineId())
            .setParentTimelineId(event.getParentTimelineId())
            .setStartIndex(event.getStartIndex())
            .setArchivedIndex(event.getStartIndex() - 1)
            .setMergedIndex(event.getStartIndex() - 1)
            .setReleasedIndex(event.getStartIndex() - 1)
            .setNextSeq(event.getSeq() + 1)
            .setHandoverCheckpointId(handoverCheckpointId)
            .setHandoverPosition(handoverPosition);
    store(meta, event);
  }

  private void applyLogArchived(final LogArchiveRecord event) {
    final var meta = state.meta();
    final var objects = event.getObjects();

    meta.setArchivedIndex(objects.getLast().getLastIndex());
    for (final var object : objects) {
      if (object.getFirstPosition() < 0) {
        continue;
      }
      if (meta.getFirstArchivedPosition() < 0) {
        meta.setFirstArchivedPosition(object.getFirstPosition());
      }
      meta.setArchivedPosition(Math.max(meta.getArchivedPosition(), object.getLastPosition()));
    }

    final var attached = new ArrayList<List<CoveredCheckpoint>>();
    objects.forEach(object -> attached.add(new ArrayList<>()));
    final var covered = coverPendingCheckpoints(meta, objects, attached);
    for (int i = 0; i < objects.size(); i++) {
      state.insertOpenObject(objects.get(i), attached.get(i));
      state.insertUnmergedObject(objects.get(i));
    }

    if (event.getCheckpoints().isEmpty()) {
      covered.forEach(event::addCheckpoint);
    }
    meta.setNextSeq(event.getSeq() + 1);
    store(meta, event);
  }

  /**
   * Takes the pending checkpoints up to F in id order and removes them from the pending ones.
   * Checkpoints taken before the first archived position are dropped. Every other one is attached
   * to the object of this event that contains its position, so that a chunk's checkpoints are a
   * function of its objects alone, and it is returned as covered.
   *
   * <p>A checkpoint is pending from its {@code CREATED} event on. Commands are processed in log
   * order, so that event always precedes the {@code LOG_ARCHIVED} event that archives the
   * checkpoint's position: a pending checkpoint up to F lies in an object of this event. If not, an
   * invariant is broken, and this fails instead of silently not listing the checkpoint.
   */
  private List<CoveredCheckpoint> coverPendingCheckpoints(
      final LogArchiveMeta meta,
      final List<LogObjectValue> objects,
      final List<List<CoveredCheckpoint>> attached) {
    final var covered = new ArrayList<CoveredCheckpoint>();
    for (final var checkpoint : state.pendingCheckpointsUpTo(meta.getArchivedPosition())) {
      state.deletePendingCheckpoint(checkpoint.getCheckpointId());
      final long position = checkpoint.getCheckpointPosition();
      if (position < meta.getFirstArchivedPosition()) {
        continue;
      }
      final int objectIndex = indexOfObjectContaining(objects, position);
      if (objectIndex < 0) {
        throw new IllegalStateException(
            "Expected checkpoint %d at position %d to lie in an object of this LOG_ARCHIVED event, but it lies in none of %s (first archived position %d, archived position %d); its CREATED event must precede the event archiving its position"
                .formatted(
                    checkpoint.getCheckpointId(),
                    position,
                    objects.stream()
                        .map(
                            object ->
                                "[%d..%d]"
                                    .formatted(object.getFirstPosition(), object.getLastPosition()))
                        .toList(),
                    meta.getFirstArchivedPosition(),
                    meta.getArchivedPosition()));
      }
      attached.get(objectIndex).add(checkpoint);
      if (checkpoint.getCheckpointType().shouldCreateBackup()) {
        state.insertBackupCheckpoint(checkpoint);
      }
      covered.add(checkpoint);
    }
    return covered;
  }

  private static int indexOfObjectContaining(
      final List<LogObjectValue> objects, final long position) {
    for (int i = 0; i < objects.size(); i++) {
      final var object = objects.get(i);
      if (object.getFirstPosition() >= 0
          && object.getFirstPosition() <= position
          && position <= object.getLastPosition()) {
        return i;
      }
    }
    return -1;
  }

  private void applySnapshotArchived(final LogArchiveRecord event) {
    final var meta = state.meta();
    state.insertSnapshot(event.getSeq(), event.getSnapshot());
    meta.setNextSeq(event.getSeq() + 1);
    store(meta, event);
  }

  private void applyIndexSealed(final LogArchiveRecord event) {
    final var meta = state.meta();
    final var chunk = event.getChunk();
    state
        .firstOpenObjects((int) chunk.getObjectCount())
        .forEach(open -> state.deleteOpenObject(open.object().getFirstIndex()));
    state.insertChunk(chunk);
    meta.setNextChunkNo(chunk.getChunkNo() + 1).setNextSeq(event.getSeq() + 1);
    store(meta, event);
  }

  private void applyHandoverCompleted(final LogArchiveRecord event) {
    final var meta = state.meta();
    meta.setHandoverCheckpointId(event.getHandoverCheckpointId())
        .setHandoverPosition(event.getHandoverPosition())
        .setNextSeq(event.getSeq() + 1);
    store(meta, event);
  }

  /**
   * Replaces the archived objects of the merged range by the merged object and moves M to its last
   * index. M only gains a position if the merged object holds application entries.
   */
  private void applyLogMerged(final LogArchiveRecord event) {
    final var meta = state.meta();
    final var merged = event.getObjects().getFirst();
    state.deleteUnmergedObjectsUpTo(merged.getLastIndex());
    state.insertMergedObject(merged);
    meta.setMergedIndex(merged.getLastIndex());
    if (merged.getLastPosition() >= 0) {
      meta.setMergedPosition(merged.getLastPosition());
    }
    meta.setNextSeq(event.getSeq() + 1);
    store(meta, event);
    event.setMergedIndex(meta.getMergedIndex()).setMergedPosition(meta.getMergedPosition());
  }

  /**
   * Hands every merged object up to the proposed index over to the event plane: the cluster drops
   * its rows. R only gains a position if a released object holds application entries.
   */
  private void applyLogReleased(final LogArchiveRecord event) {
    final var meta = state.meta();
    final long releasedIndex = event.getReleasedIndex();
    for (final var object : state.mergedObjects()) {
      if (object.getLastIndex() <= releasedIndex && object.getLastPosition() >= 0) {
        meta.setReleasedPosition(object.getLastPosition());
      }
    }
    state.deleteMergedObjectsUpTo(releasedIndex);
    meta.setReleasedIndex(releasedIndex).setNextSeq(event.getSeq() + 1);
    store(meta, event);
    event.setReleasedIndex(meta.getReleasedIndex()).setReleasedPosition(meta.getReleasedPosition());
  }

  private void applyPublished(final LogArchiveRecord event) {
    final var meta = state.meta();
    state.deleteOutboxUpTo(event.getPublishedSeq());
    meta.setLastPublishedSeq(event.getPublishedSeq());
    store(meta, event);
  }

  private void store(final LogArchiveMeta meta, final LogArchiveRecord event) {
    state.setMeta(meta);
    event.setArchivedIndex(meta.getArchivedIndex()).setArchivedPosition(meta.getArchivedPosition());
  }
}
