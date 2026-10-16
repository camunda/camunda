/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.format.LogArchiveFrame;
import io.camunda.zeebe.backup.logarchive.format.LogObjectEncoder;
import io.camunda.zeebe.backup.logarchive.model.ArchivedSnapshotJson;
import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import io.camunda.zeebe.backup.logarchive.model.IndexChunkFile;
import io.camunda.zeebe.backup.logarchive.model.IndexChunkJson;
import io.camunda.zeebe.backup.logarchive.model.LatestHint;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.model.RootFile;
import io.camunda.zeebe.backup.logarchive.model.SnapshotDescriptorFile;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Builds the log archive of one partition the way the processor and the leader components would:
 * entries are appended to a real source journal, read back and archived as encoded log objects with
 * commit entries, chunks and roots.
 */
final class TestLogArchive implements AutoCloseable {

  static final String JOURNAL_NAME = "raft-partition-partition-1";
  static final long TERM = 3;

  private final InMemoryLogArchiveStore store;
  private final String timelineId;
  private final int partitionId;
  private final String prefix;
  private final SegmentedJournal sourceJournal;

  private long startIndex = -1;
  private long archivedIndex;
  private long archivedPosition = -1;
  private long firstArchivedPosition = -1;
  private long nextSeq = 1;
  private long nextChunkNo = 1;
  private final List<LogObjectJson> openObjects = new ArrayList<>();
  private final List<IndexChunkJson> chunks = new ArrayList<>();
  private final List<ArchivedSnapshotJson> snapshots = new ArrayList<>();
  private final List<CoveredCheckpointJson> openCheckpoints = new ArrayList<>();
  private final TreeMap<Long, Long> pendingCheckpoints = new TreeMap<>();

  TestLogArchive(
      final InMemoryLogArchiveStore store,
      final String timelineId,
      final int partitionId,
      final Path sourceDirectory,
      final MeterRegistry meterRegistry) {
    this.store = store;
    this.timelineId = timelineId;
    this.partitionId = partitionId;
    prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);
    sourceJournal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(sourceDirectory.toFile())
            .withName(JOURNAL_NAME)
            .withMetaStore(new InMemory())
            .build();
  }

  String prefix() {
    return prefix;
  }

  /** Appends an application entry (asqn >= 0) or a non-application entry (asqn = -1). */
  long append(final long asqn) {
    final var data =
        ("entry-" + asqn + "-" + sourceJournal.getLastIndex()).getBytes(StandardCharsets.UTF_8);
    return sourceJournal.append(asqn, new DirectBufferWriter(new UnsafeBuffer(data))).index();
  }

  /** Appends application entries with the given asqns. */
  void appendAll(final long... asqns) {
    for (final long asqn : asqns) {
      append(asqn);
    }
  }

  /** Resets the source journal so that the next entry gets the given index. */
  void resetSource(final long nextIndex) {
    sourceJournal.reset(nextIndex);
  }

  /** All entries of the source journal, as frames. */
  List<LogArchiveFrame> sourceFrames() {
    return frames(sourceJournal.getFirstIndex(), sourceJournal.getLastIndex());
  }

  void startTimeline(final long startIndex, final String parentTimelineId) {
    this.startIndex = startIndex;
    archivedIndex = startIndex - 1;
    commit(
        CommitEntry.Type.TIMELINE_STARTED,
        startIndex,
        parentTimelineId,
        List.of(),
        List.of(),
        null,
        null);
  }

  /** A checkpoint taken at the given position; covered once it is archived. */
  void checkpoint(final long checkpointId, final long position) {
    pendingCheckpoints.put(checkpointId, position);
  }

  /** Archives all source entries after F up to and including {@code lastIndex} as one object. */
  LogObjectJson archive(final long lastIndex) {
    final var frames = frames(archivedIndex + 1, lastIndex);
    final var encoded = LogObjectEncoder.encode(timelineId, partitionId, TERM, frames);
    final var header = encoded.header();
    final var object =
        new LogObjectJson(
            LogArchiveKeys.dataKey(header.firstIndex(), header.lastIndex(), TERM),
            TERM,
            header.firstIndex(),
            header.lastIndex(),
            header.firstPosition(),
            header.lastPosition(),
            header.entryCount(),
            encoded.bytes().length,
            encoded.sha256());
    store.put(prefix + object.key(), encoded.bytes()).join();

    openObjects.add(object);
    archivedIndex = object.lastIndex();
    if (object.lastPosition() >= 0) {
      archivedPosition = object.lastPosition();
      if (firstArchivedPosition < 0) {
        firstArchivedPosition = object.firstPosition();
      }
    }
    final var covered = new ArrayList<CoveredCheckpointJson>();
    while (!pendingCheckpoints.isEmpty()
        && pendingCheckpoints.firstEntry().getValue() <= archivedPosition) {
      final var entry = pendingCheckpoints.pollFirstEntry();
      covered.add(new CoveredCheckpointJson(entry.getKey(), entry.getValue(), "MARKER"));
    }
    openCheckpoints.addAll(covered);
    commit(CommitEntry.Type.LOG_ARCHIVED, null, null, List.of(object), covered, null, null);
    return object;
  }

  /** Records an archived snapshot without uploading any file. */
  void archiveSnapshot(final ArchivedSnapshotJson snapshot) {
    snapshots.add(snapshot);
    commit(CommitEntry.Type.SNAPSHOT_ARCHIVED, null, null, List.of(), List.of(), snapshot, null);
  }

  /** Uploads the files and descriptor of a persisted snapshot and records it as archived. */
  ArchivedSnapshotJson uploadSnapshot(final PersistedSnapshot snapshot) {
    final var files = snapshot.files();
    files.forEach(
        (name, path) ->
            store.putFile(prefix + LogArchiveKeys.snapshotFileKey(snapshot.getId(), name), path));
    final var metadata = snapshot.getMetadata();
    final var archived =
        new ArchivedSnapshotJson(
            snapshot.getId(),
            snapshot.getIndex(),
            snapshot.getTerm(),
            metadata.processedPosition(),
            metadata.minExportedPosition(),
            metadata.lastFollowupEventPosition(),
            metadata.maxExportedPosition(),
            files.size(),
            snapshot.getTotalSizeInBytes());
    final var descriptor =
        new SnapshotDescriptorFile(
            archived.id(),
            archived.index(),
            archived.term(),
            archived.processedPosition(),
            archived.exportedPosition(),
            archived.lastFollowupEventPosition(),
            archived.maxExportedPosition(),
            archived.fileCount(),
            archived.sizeBytes(),
            List.copyOf(files.keySet()));
    store
        .put(
            prefix + LogArchiveKeys.snapshotDescriptorKey(snapshot.getId()),
            LogArchiveJson.write(descriptor))
        .join();
    archiveSnapshot(archived);
    return archived;
  }

  /** Seals the first {@code objectCount} open objects into an index chunk. */
  IndexChunkJson seal(final int objectCount) {
    final var objects = List.copyOf(openObjects.subList(0, objectCount));
    final long firstPosition =
        objects.stream()
            .mapToLong(LogObjectJson::firstPosition)
            .filter(p -> p >= 0)
            .min()
            .orElse(-1);
    final long lastPosition =
        objects.stream().mapToLong(LogObjectJson::lastPosition).max().orElse(-1);
    final var checkpoints =
        openCheckpoints.stream().filter(c -> c.checkpointPosition() <= lastPosition).toList();
    final var chunkFile =
        new IndexChunkFile(
            LogArchiveJson.FORMAT_VERSION,
            timelineId,
            partitionId,
            nextChunkNo,
            objects,
            checkpoints);
    final var chunkJson = LogArchiveJson.write(chunkFile);
    final var chunk =
        new IndexChunkJson(
            nextChunkNo,
            LogArchiveKeys.indexKey(objects.getFirst().firstIndex(), objects.getLast().lastIndex()),
            objects.getFirst().firstIndex(),
            objects.getLast().lastIndex(),
            firstPosition,
            lastPosition,
            objectCount,
            checkpoints.isEmpty() ? -1 : checkpoints.getFirst().checkpointId(),
            checkpoints.isEmpty() ? -1 : checkpoints.getLast().checkpointId(),
            LogArchiveView.sha256Hex(chunkJson));
    store.put(prefix + chunk.key(), chunkJson).join();

    openObjects.subList(0, objectCount).clear();
    openCheckpoints.removeAll(checkpoints);
    chunks.add(chunk);
    nextChunkNo++;
    commit(CommitEntry.Type.INDEX_SEALED, null, null, List.of(), List.of(), null, chunk);
    return chunk;
  }

  /** Writes the root for the last seq and points {@code _latest.json} at it. */
  long publishRoot() {
    final long seq = nextSeq - 1;
    final var root =
        new RootFile(
            LogArchiveJson.FORMAT_VERSION,
            timelineId,
            "",
            partitionId,
            seq,
            startIndex,
            archivedIndex,
            archivedPosition,
            chunks,
            openObjects,
            openCheckpoints,
            snapshots);
    store.put(prefix + LogArchiveKeys.rootKey(seq), LogArchiveJson.write(root)).join();
    store
        .put(
            prefix + LogArchiveKeys.latestKey(),
            LogArchiveJson.write(
                new LatestHint(LogArchiveJson.FORMAT_VERSION, timelineId, partitionId, seq)))
        .join();
    return seq;
  }

  private void commit(
      final CommitEntry.Type type,
      final Long startIndex,
      final String parentTimelineId,
      final List<LogObjectJson> objects,
      final List<CoveredCheckpointJson> checkpoints,
      final ArchivedSnapshotJson snapshot,
      final IndexChunkJson chunk) {
    final long seq = nextSeq++;
    final var entry =
        new CommitEntry(
            LogArchiveJson.FORMAT_VERSION,
            timelineId,
            partitionId,
            seq,
            type,
            1000 + seq,
            1_700_000_000_000L + seq,
            archivedIndex,
            archivedPosition,
            startIndex,
            parentTimelineId,
            objects,
            checkpoints,
            snapshot,
            chunk);
    store.put(prefix + LogArchiveKeys.commitKey(seq), LogArchiveJson.write(entry)).join();
  }

  private List<LogArchiveFrame> frames(final long fromIndex, final long toIndex) {
    final var frames = new ArrayList<LogArchiveFrame>();
    try (final var reader = sourceJournal.openReader()) {
      reader.seek(fromIndex);
      while (reader.hasNext()) {
        final var record = reader.next();
        if (record.index() > toIndex) {
          break;
        }
        final var serialized = new byte[record.serializedRecord().capacity()];
        record.serializedRecord().getBytes(0, serialized);
        final long position = record.asqn() >= 0 ? record.asqn() : -1;
        frames.add(
            new LogArchiveFrame(
                record.index(),
                TERM,
                record.asqn(),
                position,
                position,
                record.checksum(),
                serialized));
      }
    }
    return frames;
  }

  @Override
  public void close() {
    sourceJournal.close();
  }
}
