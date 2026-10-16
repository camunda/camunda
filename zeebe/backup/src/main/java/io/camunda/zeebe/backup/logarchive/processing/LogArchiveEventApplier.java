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
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
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
  private final DbCheckpointMetadataState checkpointMetadataState;

  LogArchiveEventApplier(
      final int partitionId,
      final DbLogArchiveState state,
      final DbCheckpointMetadataState checkpointMetadataState) {
    this.partitionId = partitionId;
    this.state = state;
    this.checkpointMetadataState = checkpointMetadataState;
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
    state.clearAll();
    final var meta =
        new LogArchiveMeta()
            .setTimelineId(event.getTimelineId())
            .setParentTimelineId(event.getParentTimelineId())
            .setStartIndex(event.getStartIndex())
            .setArchivedIndex(event.getStartIndex() - 1)
            .setNextSeq(event.getSeq() + 1);
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

    final var covered = collectCoveredCheckpoints(meta);
    final var attached = attach(objects, covered);
    for (int i = 0; i < objects.size(); i++) {
      state.insertOpenObject(objects.get(i), attached.get(i));
    }

    if (event.getCheckpoints().isEmpty()) {
      covered.forEach(event::addCheckpoint);
    }
    meta.setNextSeq(event.getSeq() + 1);
    store(meta, event);
  }

  /**
   * Walks the checkpoints in id order past the last covered one, up to F. Checkpoints before the
   * first archived position are skipped; either way the walk moves on past them. Advances {@code
   * lastCoveredCheckpointId} in the given meta.
   */
  private List<CoveredCheckpoint> collectCoveredCheckpoints(final LogArchiveMeta meta) {
    final var covered = new ArrayList<CoveredCheckpoint>();
    final long archivedPosition = meta.getArchivedPosition();
    final long firstArchivedPosition = meta.getFirstArchivedPosition();
    final long[] lastCovered = {meta.getLastCoveredCheckpointId()};

    checkpointMetadataState.visitCheckpointsAfter(
        meta.getLastCoveredCheckpointId(),
        checkpoint -> {
          if (checkpoint.checkpointPosition() > archivedPosition) {
            return false;
          }
          lastCovered[0] = checkpoint.checkpointId();
          if (checkpoint.checkpointPosition() >= firstArchivedPosition) {
            covered.add(
                new CoveredCheckpoint()
                    .setCheckpointId(checkpoint.checkpointId())
                    .setCheckpointPosition(checkpoint.checkpointPosition())
                    .setCheckpointType(checkpoint.checkpointType()));
          }
          return true;
        });

    meta.setLastCoveredCheckpointId(lastCovered[0]);
    return covered;
  }

  /**
   * Attaches each covered checkpoint to the first object of this event that reaches its position,
   * so that a chunk's checkpoints are a function of its objects alone. Checkpoints can be deleted
   * from the CHECKPOINTS column family later, the attached copies cannot.
   */
  private static List<List<CoveredCheckpoint>> attach(
      final List<LogObjectValue> objects, final List<CoveredCheckpoint> covered) {
    final var attached = new ArrayList<List<CoveredCheckpoint>>();
    objects.forEach(object -> attached.add(new ArrayList<>()));
    for (final var checkpoint : covered) {
      for (int i = 0; i < objects.size(); i++) {
        final long lastPosition = objects.get(i).getLastPosition();
        if (lastPosition >= 0 && lastPosition >= checkpoint.getCheckpointPosition()) {
          attached.get(i).add(checkpoint);
          break;
        }
      }
    }
    return attached;
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
