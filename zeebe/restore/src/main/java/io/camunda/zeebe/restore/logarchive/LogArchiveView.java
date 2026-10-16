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
import io.camunda.zeebe.backup.logarchive.model.IndexChunkFile;
import io.camunda.zeebe.backup.logarchive.model.IndexChunkJson;
import io.camunda.zeebe.backup.logarchive.model.LatestHint;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.model.RootFile;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The folded view of one partition of one timeline in the log archive: the newest root plus every
 * commit entry published after it (spec section 13, step 1).
 *
 * <p>Sealed index chunks are only described by the view; their files are read on demand with {@link
 * #readChunk(IndexChunkJson)} and cached.
 */
public final class LogArchiveView {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveView.class);

  private final LogArchiveStore store;
  private final String timelineId;
  private final int partitionId;
  private final String parentTimelineId;
  private final long startIndex;
  private final long archivedIndex;
  private final long archivedPosition;
  private final long rootSeq;
  private final long lastSeq;
  private final List<IndexChunkJson> chunks;
  private final List<LogObjectJson> openObjects;
  private final List<ArchivedSnapshotJson> snapshots;
  private final List<CoveredCheckpointJson> checkpoints;
  private final Map<Long, IndexChunkFile> chunkFiles = new HashMap<>();

  private LogArchiveView(final LogArchiveStore store, final Folder folder) {
    this.store = store;
    timelineId = folder.timelineId;
    partitionId = folder.partitionId;
    parentTimelineId = folder.parentTimelineId;
    startIndex = folder.startIndex;
    archivedIndex = folder.archivedIndex;
    archivedPosition = folder.archivedPosition;
    rootSeq = folder.rootSeq;
    lastSeq = folder.lastSeq;
    chunks = List.copyOf(folder.chunks);
    openObjects = List.copyOf(folder.openObjects);
    snapshots = List.copyOf(folder.snapshots);
    checkpoints = List.copyOf(folder.checkpoints.values());
  }

  /**
   * Reads and folds the archive of one partition: {@code _latest.json}, the root it points to (or
   * the highest root if the hint is missing), then the commit entries from {@code rootSeq + 1}
   * until the first missing one. If there is no root at all, the commit entries are folded from seq
   * 1.
   *
   * @throws LogArchiveRestoreException if the partition has no archive or the archive is
   *     inconsistent
   */
  public static LogArchiveView read(
      final LogArchiveStore store, final String timelineId, final int partitionId) {
    final var prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);
    final var root = readRoot(store, prefix, timelineId, partitionId);
    final var folder =
        root.map(Folder::fromRoot).orElseGet(() -> Folder.empty(timelineId, partitionId));

    long seq = folder.lastSeq + 1;
    while (true) {
      final var bytes = store.getIfExists(prefix + LogArchiveKeys.commitKey(seq)).join();
      if (bytes.isEmpty()) {
        break;
      }
      folder.apply(LogArchiveJson.read(bytes.get(), CommitEntry.class), seq);
      seq++;
    }

    if (folder.lastSeq == 0) {
      throw new LogArchiveRestoreException(
          "No log archive found for partition %d of timeline '%s' (no root and no commit entry)"
              .formatted(partitionId, timelineId));
    }
    if (folder.startIndex < 0) {
      throw new LogArchiveRestoreException(
          "Log archive of partition %d of timeline '%s' has no start index"
              .formatted(partitionId, timelineId));
    }
    return new LogArchiveView(store, folder);
  }

  private static Optional<RootFile> readRoot(
      final LogArchiveStore store,
      final String prefix,
      final String timelineId,
      final int partitionId) {
    final var latest = store.getIfExists(prefix + LogArchiveKeys.latestKey()).join();
    if (latest.isPresent()) {
      final var hint = LogArchiveJson.read(latest.get(), LatestHint.class);
      final var root = store.getIfExists(prefix + LogArchiveKeys.rootKey(hint.rootSeq())).join();
      if (root.isPresent()) {
        return Optional.of(validRoot(root.get(), hint.rootSeq(), timelineId, partitionId));
      }
      LOG.warn(
          "Root {} named by _latest.json of partition {} is missing, falling back to listing",
          hint.rootSeq(),
          partitionId);
    }

    final var rootKeys = store.list(prefix + "root/").join();
    if (rootKeys.isEmpty()) {
      return Optional.empty();
    }
    final var highest =
        rootKeys.stream().map(LogArchiveKeys::seqFromRootKey).max(Long::compare).orElseThrow();
    final var root =
        store
            .getIfExists(prefix + LogArchiveKeys.rootKey(highest))
            .join()
            .orElseThrow(
                () ->
                    new LogArchiveRestoreException(
                        "Listed root %d of partition %d disappeared"
                            .formatted(highest, partitionId)));
    return Optional.of(validRoot(root, highest, timelineId, partitionId));
  }

  private static RootFile validRoot(
      final byte[] bytes, final long seq, final String timelineId, final int partitionId) {
    final var root = LogArchiveJson.read(bytes, RootFile.class);
    if (root.seq() != seq
        || root.partitionId() != partitionId
        || !root.timelineId().equals(timelineId)) {
      throw new LogArchiveRestoreException(
          "Root %d of partition %d of timeline '%s' describes seq %d of partition %d of timeline '%s'"
              .formatted(
                  seq, partitionId, timelineId, root.seq(), root.partitionId(), root.timelineId()));
    }
    return root;
  }

  public String timelineId() {
    return timelineId;
  }

  public int partitionId() {
    return partitionId;
  }

  /** The parent timeline, empty for an original timeline. */
  public String parentTimelineId() {
    return parentTimelineId;
  }

  public long startIndex() {
    return startIndex;
  }

  /** F: the last archived journal index. */
  public long archivedIndex() {
    return archivedIndex;
  }

  /** F: the highest archived position, -1 if no application entry is archived yet. */
  public long archivedPosition() {
    return archivedPosition;
  }

  /** Seq of the root the view started from, 0 if there was none. */
  public long rootSeq() {
    return rootSeq;
  }

  /** Seq of the last commit entry folded into the view (or the root's seq). */
  public long lastSeq() {
    return lastSeq;
  }

  /** All sealed index chunks, in chunk order. */
  public List<IndexChunkJson> chunks() {
    return chunks;
  }

  /** All objects not yet sealed into a chunk, in index order. */
  public List<LogObjectJson> openObjects() {
    return openObjects;
  }

  /** All archived snapshots, in seq order. */
  public List<ArchivedSnapshotJson> snapshots() {
    return snapshots;
  }

  /**
   * Recently covered checkpoints: the root's open checkpoints plus the checkpoints of the commit
   * entries folded after the root, in id order. Older checkpoints are in the sealed chunk files.
   */
  public List<CoveredCheckpointJson> checkpoints() {
    return checkpoints;
  }

  String partitionPrefix() {
    return LogArchiveKeys.partitionPrefix(timelineId, partitionId);
  }

  LogArchiveStore store() {
    return store;
  }

  /**
   * Reads a sealed chunk file and verifies it against its description.
   *
   * @throws LogArchiveRestoreException if the file is missing or does not match the description
   */
  public IndexChunkFile readChunk(final IndexChunkJson chunk) {
    final var cached = chunkFiles.get(chunk.chunkNo());
    if (cached != null) {
      return cached;
    }
    final var key = partitionPrefix() + chunk.key();
    final var bytes =
        store
            .getIfExists(key)
            .join()
            .orElseThrow(
                () ->
                    new LogArchiveRestoreException(
                        "Sealed index chunk %s is missing".formatted(key)));
    if (!sha256Hex(bytes).equals(chunk.sha256())) {
      throw new LogArchiveRestoreException(
          "Index chunk %s does not match its sha256 %s".formatted(key, chunk.sha256()));
    }
    final var file = LogArchiveJson.read(bytes, IndexChunkFile.class);
    final var objects = file.objects();
    if (file.chunkNo() != chunk.chunkNo()
        || file.partitionId() != partitionId
        || !file.timelineId().equals(timelineId)
        || objects.size() != chunk.objectCount()
        || objects.isEmpty()
        || objects.getFirst().firstIndex() != chunk.firstIndex()
        || objects.getLast().lastIndex() != chunk.lastIndex()) {
      throw new LogArchiveRestoreException(
          "Index chunk %s does not match its description %s".formatted(key, chunk));
    }
    chunkFiles.put(chunk.chunkNo(), file);
    return file;
  }

  /**
   * Finds a covered checkpoint by id: first in the recent checkpoints, then in the sealed chunk
   * whose checkpoint id range includes it.
   */
  public Optional<CoveredCheckpointJson> findCheckpoint(final long checkpointId) {
    for (final var checkpoint : checkpoints) {
      if (checkpoint.checkpointId() == checkpointId) {
        return Optional.of(checkpoint);
      }
    }
    for (final var chunk : chunks) {
      if (chunk.firstCheckpointId() >= 0
          && chunk.firstCheckpointId() <= checkpointId
          && checkpointId <= chunk.lastCheckpointId()) {
        final var found =
            readChunk(chunk).checkpoints().stream()
                .filter(c -> c.checkpointId() == checkpointId)
                .findFirst();
        if (found.isPresent()) {
          return found;
        }
      }
    }
    return Optional.empty();
  }

  /**
   * All covered checkpoints, newest id first: the recent ones, then those of the sealed chunks from
   * the newest chunk backwards. Chunk files are read lazily as the stream is consumed.
   */
  public Stream<CoveredCheckpointJson> checkpointsNewestFirst() {
    final var recent =
        checkpoints.stream()
            .sorted(Comparator.comparingLong(CoveredCheckpointJson::checkpointId).reversed());
    final var sealed =
        chunks.reversed().stream()
            .filter(chunk -> chunk.lastCheckpointId() >= 0)
            .flatMap(
                chunk ->
                    readChunk(chunk).checkpoints().stream()
                        .sorted(
                            Comparator.comparingLong(CoveredCheckpointJson::checkpointId)
                                .reversed()));
    return Stream.concat(recent, sealed).distinct();
  }

  @Override
  public String toString() {
    return "LogArchiveView[timeline=%s, partition=%d, startIndex=%d, F=(%d, %d), rootSeq=%d, lastSeq=%d, chunks=%d, openObjects=%d, snapshots=%d, checkpoints=%d]"
        .formatted(
            timelineId,
            partitionId,
            startIndex,
            archivedIndex,
            archivedPosition,
            rootSeq,
            lastSeq,
            chunks.size(),
            openObjects.size(),
            snapshots.size(),
            checkpoints.size());
  }

  static String sha256Hex(final byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Mutable fold state; applies commit entries the same way the processor applies events. */
  private static final class Folder {
    private final String timelineId;
    private final int partitionId;
    private String parentTimelineId = "";
    private long startIndex = -1;
    private long archivedIndex = -1;
    private long archivedPosition = -1;
    private long rootSeq;
    private long lastSeq;
    private final List<IndexChunkJson> chunks = new ArrayList<>();
    private final List<LogObjectJson> openObjects = new ArrayList<>();
    private final List<ArchivedSnapshotJson> snapshots = new ArrayList<>();
    private final Map<Long, CoveredCheckpointJson> checkpoints = new LinkedHashMap<>();

    private Folder(final String timelineId, final int partitionId) {
      this.timelineId = timelineId;
      this.partitionId = partitionId;
    }

    static Folder empty(final String timelineId, final int partitionId) {
      return new Folder(timelineId, partitionId);
    }

    static Folder fromRoot(final RootFile root) {
      final var folder = new Folder(root.timelineId(), root.partitionId());
      folder.parentTimelineId = Objects.requireNonNullElse(root.parentTimelineId(), "");
      folder.startIndex = root.startIndex();
      folder.archivedIndex = root.archivedIndex();
      folder.archivedPosition = root.archivedPosition();
      folder.rootSeq = root.seq();
      folder.lastSeq = root.seq();
      folder.chunks.addAll(root.chunks());
      folder.openObjects.addAll(root.openObjects());
      folder.snapshots.addAll(root.snapshots());
      root.openCheckpoints().forEach(folder::addCheckpoint);
      return folder;
    }

    void apply(final CommitEntry entry, final long expectedSeq) {
      if (entry.seq() != expectedSeq
          || entry.partitionId() != partitionId
          || !entry.timelineId().equals(timelineId)) {
        throw inconsistent(
            "commit entry %d describes seq %d of partition %d of timeline '%s'"
                .formatted(expectedSeq, entry.seq(), entry.partitionId(), entry.timelineId()));
      }
      switch (entry.type()) {
        case TIMELINE_STARTED -> applyTimelineStarted(entry);
        case LOG_ARCHIVED -> applyLogArchived(entry);
        case SNAPSHOT_ARCHIVED -> applySnapshotArchived(entry);
        case INDEX_SEALED -> applyIndexSealed(entry);
        case null, default -> throw inconsistent("unknown commit entry type " + entry.type());
      }
      lastSeq = expectedSeq;
    }

    private void applyTimelineStarted(final CommitEntry entry) {
      if (entry.seq() != 1 || entry.startIndex() == null) {
        throw inconsistent("TIMELINE_STARTED at seq %d".formatted(entry.seq()));
      }
      startIndex = entry.startIndex();
      parentTimelineId = Objects.requireNonNullElse(entry.parentTimelineId(), "");
      archivedIndex = startIndex - 1;
      archivedPosition = -1;
    }

    private void applyLogArchived(final CommitEntry entry) {
      requireStarted(entry);
      long expectedFirstIndex = archivedIndex + 1;
      for (final var object : entry.objects()) {
        if (object.firstIndex() != expectedFirstIndex || object.lastIndex() < object.firstIndex()) {
          throw inconsistent(
              "commit entry %d archives object %s, expected it to start at index %d"
                  .formatted(entry.seq(), object.key(), expectedFirstIndex));
        }
        openObjects.add(object);
        expectedFirstIndex = object.lastIndex() + 1;
      }
      if (entry.archivedIndex() != expectedFirstIndex - 1) {
        throw inconsistent(
            "commit entry %d has archivedIndex %d, but its objects end at %d"
                .formatted(entry.seq(), entry.archivedIndex(), expectedFirstIndex - 1));
      }
      archivedIndex = entry.archivedIndex();
      archivedPosition = entry.archivedPosition();
      entry.checkpoints().forEach(this::addCheckpoint);
    }

    private void applySnapshotArchived(final CommitEntry entry) {
      requireStarted(entry);
      final var snapshot = entry.snapshot();
      if (snapshot == null) {
        throw inconsistent("SNAPSHOT_ARCHIVED entry %d has no snapshot".formatted(entry.seq()));
      }
      if (snapshots.stream().noneMatch(s -> s.id().equals(snapshot.id()))) {
        snapshots.add(snapshot);
      }
    }

    private void applyIndexSealed(final CommitEntry entry) {
      requireStarted(entry);
      final var chunk = entry.chunk();
      if (chunk == null) {
        throw inconsistent("INDEX_SEALED entry %d has no chunk".formatted(entry.seq()));
      }
      final int count = chunk.objectCount();
      if (count <= 0
          || openObjects.size() < count
          || openObjects.getFirst().firstIndex() != chunk.firstIndex()
          || openObjects.get(count - 1).lastIndex() != chunk.lastIndex()) {
        throw inconsistent(
            "INDEX_SEALED entry %d seals %s, which does not match the open objects"
                .formatted(entry.seq(), chunk));
      }
      openObjects.subList(0, count).clear();
      chunks.add(chunk);
    }

    private void addCheckpoint(final CoveredCheckpointJson checkpoint) {
      checkpoints.putIfAbsent(checkpoint.checkpointId(), checkpoint);
    }

    private void requireStarted(final CommitEntry entry) {
      if (startIndex < 0) {
        throw inconsistent(
            "commit entry %d (%s) precedes TIMELINE_STARTED".formatted(entry.seq(), entry.type()));
      }
    }

    private LogArchiveRestoreException inconsistent(final String detail) {
      return new LogArchiveRestoreException(
          "Inconsistent log archive of partition %d of timeline '%s': %s"
              .formatted(partitionId, timelineId, detail));
    }
  }
}
