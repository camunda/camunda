/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.processing;

import io.camunda.zeebe.backup.logarchive.model.ArchivedSnapshotJson;
import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import io.camunda.zeebe.backup.logarchive.model.IndexChunkFile;
import io.camunda.zeebe.backup.logarchive.model.IndexChunkJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.model.RootFile;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState.OpenObject;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.protocol.impl.record.value.management.ArchivedSnapshot;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.ArchivedSnapshotValue;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.CoveredCheckpointValue;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.IndexChunkValue;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue.LogObjectValue;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Pure mappings from protocol records and replicated state to the JSON model of the bucket objects.
 * The same event record always maps to the same bytes (I4), so any two leaders write identical
 * commit entries.
 */
public final class LogArchiveJsonMapper {

  private LogArchiveJsonMapper() {}

  /**
   * Maps an event of the processor to its commit entry.
   *
   * @param eventIntent one of {@code TIMELINE_STARTED}, {@code LOG_ARCHIVED}, {@code
   *     SNAPSHOT_ARCHIVED}, {@code INDEX_SEALED}
   * @param event the event record as written to the log, including seq, archivedIndex,
   *     archivedPosition and the covered checkpoints
   * @param recordPosition see {@link LogArchiveRecordsProcessor#recordPositionOf}
   * @param recordTimestamp see {@link LogArchiveRecordsProcessor#NO_RECORD_TIMESTAMP}
   * @throws IllegalArgumentException if the intent has no commit entry
   */
  public static CommitEntry commitEntry(
      final int partitionId,
      final LogArchiveIntent eventIntent,
      final LogArchiveRecord event,
      final long recordPosition,
      final long recordTimestamp) {
    final var type = typeOf(eventIntent);
    final boolean timeline = type == CommitEntry.Type.TIMELINE_STARTED;
    final boolean logArchived = type == CommitEntry.Type.LOG_ARCHIVED;
    return new CommitEntry(
        LogArchiveJson.FORMAT_VERSION,
        event.getTimelineId(),
        partitionId,
        event.getSeq(),
        type,
        recordPosition,
        recordTimestamp,
        event.getArchivedIndex(),
        event.getArchivedPosition(),
        timeline ? event.getStartIndex() : null,
        timeline ? event.getParentTimelineId() : null,
        logArchived
            ? event.getObjects().stream().map(LogArchiveJsonMapper::logObject).toList()
            : List.of(),
        logArchived
            ? event.getCheckpoints().stream().map(LogArchiveJsonMapper::checkpoint).toList()
            : List.of(),
        type == CommitEntry.Type.SNAPSHOT_ARCHIVED ? snapshot(event.getSnapshot()) : null,
        type == CommitEntry.Type.INDEX_SEALED ? chunk(event.getChunk()) : null);
  }

  /** Same as {@link #commitEntry}, serialized. */
  public static byte[] commitEntryJson(
      final int partitionId,
      final LogArchiveIntent eventIntent,
      final LogArchiveRecord event,
      final long recordPosition,
      final long recordTimestamp) {
    return LogArchiveJson.write(
        commitEntry(partitionId, eventIntent, event, recordPosition, recordTimestamp));
  }

  /** The root for {@code seq}, built from the state as of applying that seq. */
  public static RootFile rootFile(
      final int partitionId,
      final LogArchiveMeta meta,
      final long seq,
      final List<IndexChunk> chunks,
      final List<OpenObject> openObjects,
      final List<ArchivedSnapshot> snapshots) {
    return new RootFile(
        LogArchiveJson.FORMAT_VERSION,
        meta.getTimelineId(),
        meta.getParentTimelineId(),
        partitionId,
        seq,
        meta.getStartIndex(),
        meta.getArchivedIndex(),
        meta.getArchivedPosition(),
        chunks.stream().map(LogArchiveJsonMapper::chunk).toList(),
        openObjects.stream().map(open -> logObject(open.object())).toList(),
        openObjects.stream()
            .flatMap(open -> open.checkpoints().stream())
            .map(LogArchiveJsonMapper::checkpoint)
            .toList(),
        snapshots.stream().map(LogArchiveJsonMapper::snapshot).toList());
  }

  /**
   * The index chunk file for the given objects, which must be the first open objects in index
   * order.
   */
  public static IndexChunkFile indexChunkFile(
      final int partitionId,
      final String timelineId,
      final long chunkNo,
      final List<OpenObject> objects) {
    return new IndexChunkFile(
        LogArchiveJson.FORMAT_VERSION,
        timelineId,
        partitionId,
        chunkNo,
        objects.stream().map(open -> logObject(open.object())).toList(),
        objects.stream()
            .flatMap(open -> open.checkpoints().stream())
            .map(LogArchiveJsonMapper::checkpoint)
            .toList());
  }

  /**
   * Builds the seal request for the given objects, which must be the first open objects in index
   * order: the chunk fields derived from them, and the chunk file JSON including its sha256.
   */
  public static SealRequest sealRequest(
      final int partitionId,
      final String timelineId,
      final long chunkNo,
      final List<OpenObject> objects) {
    if (objects.isEmpty()) {
      throw new IllegalArgumentException("Cannot seal a chunk without objects");
    }
    final var json =
        LogArchiveJson.write(indexChunkFile(partitionId, timelineId, chunkNo, objects));
    final var first = objects.getFirst().object();
    final var last = objects.getLast().object();
    final var checkpoints = objects.stream().flatMap(open -> open.checkpoints().stream()).toList();

    long firstPosition = -1;
    long lastPosition = -1;
    for (final var open : objects) {
      final var object = open.object();
      if (object.getFirstPosition() >= 0) {
        if (firstPosition < 0) {
          firstPosition = object.getFirstPosition();
        }
        lastPosition = object.getLastPosition();
      }
    }

    final var chunk =
        new IndexChunk()
            .setChunkNo(chunkNo)
            .setKey(LogArchiveKeys.indexKey(first.getFirstIndex(), last.getLastIndex()))
            .setFirstIndex(first.getFirstIndex())
            .setLastIndex(last.getLastIndex())
            .setFirstPosition(firstPosition)
            .setLastPosition(lastPosition)
            .setObjectCount(objects.size())
            .setFirstCheckpointId(
                checkpoints.isEmpty() ? -1 : checkpoints.getFirst().getCheckpointId())
            .setLastCheckpointId(
                checkpoints.isEmpty() ? -1 : checkpoints.getLast().getCheckpointId())
            .setSha256(sha256Hex(json));
    return new SealRequest(chunk, json);
  }

  public static LogObjectJson logObject(final LogObjectValue object) {
    return new LogObjectJson(
        object.getKey(),
        object.getTerm(),
        object.getFirstIndex(),
        object.getLastIndex(),
        object.getFirstPosition(),
        object.getLastPosition(),
        (int) object.getEntryCount(),
        object.getSizeBytes(),
        object.getSha256());
  }

  public static CoveredCheckpointJson checkpoint(final CoveredCheckpointValue checkpoint) {
    return new CoveredCheckpointJson(
        checkpoint.getCheckpointId(),
        checkpoint.getCheckpointPosition(),
        checkpoint.getCheckpointType().name());
  }

  public static ArchivedSnapshotJson snapshot(final ArchivedSnapshotValue snapshot) {
    return new ArchivedSnapshotJson(
        snapshot.getId(),
        snapshot.getIndex(),
        snapshot.getTerm(),
        snapshot.getProcessedPosition(),
        snapshot.getExportedPosition(),
        snapshot.getLastFollowupEventPosition(),
        snapshot.getMaxExportedPosition(),
        (int) snapshot.getFileCount(),
        snapshot.getSizeBytes());
  }

  public static IndexChunkJson chunk(final IndexChunkValue chunk) {
    return new IndexChunkJson(
        chunk.getChunkNo(),
        chunk.getKey(),
        chunk.getFirstIndex(),
        chunk.getLastIndex(),
        chunk.getFirstPosition(),
        chunk.getLastPosition(),
        (int) chunk.getObjectCount(),
        chunk.getFirstCheckpointId(),
        chunk.getLastCheckpointId(),
        chunk.getSha256());
  }

  private static CommitEntry.Type typeOf(final LogArchiveIntent intent) {
    return switch (intent) {
      case TIMELINE_STARTED -> CommitEntry.Type.TIMELINE_STARTED;
      case LOG_ARCHIVED -> CommitEntry.Type.LOG_ARCHIVED;
      case SNAPSHOT_ARCHIVED -> CommitEntry.Type.SNAPSHOT_ARCHIVED;
      case INDEX_SEALED -> CommitEntry.Type.INDEX_SEALED;
      default ->
          throw new IllegalArgumentException("Intent %s has no commit entry".formatted(intent));
    };
  }

  private static String sha256Hex(final byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
