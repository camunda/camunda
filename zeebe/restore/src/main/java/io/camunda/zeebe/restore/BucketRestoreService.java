/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.eventplane.history.Histories;
import io.camunda.zeebe.eventplane.history.HistoryLineage.Stretch;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.segment.EntryReader;
import io.camunda.zeebe.eventplane.segment.Footer;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.snapshot.SnapshotArchive;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor;
import io.camunda.zeebe.eventplane.store.NoSuchObjectException;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.restore.BucketRestoreException.LogEndsBeforeTarget;
import io.camunda.zeebe.restore.BucketRestoreException.MissingEntry;
import io.camunda.zeebe.restore.BucketRestoreException.NoHistory;
import io.camunda.zeebe.restore.BucketRestoreException.NothingToRestore;
import io.camunda.zeebe.restore.BucketRestoreException.UnexpectedEntry;
import io.camunda.zeebe.restore.PartitionRestoreService.RestoredCheckpoint;
import io.camunda.zeebe.restore.PathSegments.PathSegment;
import io.camunda.zeebe.snapshots.SnapshotFileInfoProvider;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restores a partition from the Event Plane's bucket instead of a backup: an archived snapshot, and
 * the journal after it from the segments, byte for byte, as segment entries are the journal's own.
 *
 * <p>It follows the tenant's current history: the path of histories that leads to it through the
 * partition. It takes the newest snapshot on that path whose positions are all at or before the
 * target, resets the journal to the entry after the snapshot's index, which Raft needs as the
 * journal's first entry at the latest, and appends every entry of the path from there, up to the
 * first entry with a record past the target. If the restored journal doesn't reach every position
 * the snapshot relies on, it takes the next older snapshot; without any, it restores the journal
 * from index 1.
 */
public final class BucketRestoreService {

  /** The target that restores everything the bucket holds. */
  public static final long LATEST = Long.MAX_VALUE;

  private static final Logger LOG = LoggerFactory.getLogger(BucketRestoreService.class);
  private static final int RELISTS = 3;
  private static final Comparator<SnapshotDescriptor> NEWEST_FIRST =
      Comparator.comparingLong(SnapshotDescriptor::index)
          .thenComparingLong(SnapshotDescriptor::processedPosition)
          .reversed();

  private final ObjectStore store;
  private final LogId log;
  private final RaftPartition partition;
  private final Path rootDirectory;
  private final int brokerId;
  private final SnapshotFileInfoProvider fileInfoProvider;
  private final MeterRegistry meterRegistry;

  /**
   * @param store the Event Plane's store
   * @param cluster the cluster id in the store's object names
   * @param partition the partition to restore, whose group is the tenant in the object names
   */
  public BucketRestoreService(
      final ObjectStore store,
      final String cluster,
      final RaftPartition partition,
      final int brokerId,
      final SnapshotFileInfoProvider fileInfoProvider,
      final MeterRegistry meterRegistry) {
    this.store = store;
    log = new LogId(cluster, partition.id().group(), partition.id().number());
    this.partition = partition;
    rootDirectory = partition.dataDirectory().toPath();
    this.brokerId = brokerId;
    this.fileInfoProvider = fileInfoProvider;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Restores the partition into its data directory, which should be empty.
   *
   * @param target the position to restore to: the last restored record's is at most this, and the
   *     next entry has a record past it; {@link #LATEST} for all the bucket holds
   * @return the current history, which the restore took over, the snapshot, and the checkpoint
   * @throws BucketRestoreException if the bucket doesn't hold what the restore needs
   */
  public RestoredFromBucket restore(final long target) throws IOException, FlushException {
    final var lineage = Histories.load(store, log.cluster(), log.tenant());
    final var current =
        lineage.current(log.cluster(), log.tenant()).orElseThrow(() -> new NoHistory(log));
    final var path = lineage.path(current.historyId(), log.partition());
    if (path.isEmpty()) {
      throw new NoHistory(log);
    }
    final var segments = new PathSegments(store, log, path);
    segments.refresh();
    final var candidates = candidates(path, target);
    LOG.info(
        "Restoring partition {} from the bucket {} in history {}, to position {}, from one of {} snapshots",
        log.partition(),
        store.root(),
        current.historyId(),
        target == LATEST ? "latest" : target,
        candidates.size());

    try (final var journal =
        SegmentedJournal.builder(partition.getMeterRegistry())
            .withDirectory(rootDirectory.toFile())
            .withName(partition.name())
            .withMetaStore(new InMemory())
            .build()) {
      for (final var snapshot : candidates) {
        final var appended = append(journal, segments, snapshot.index() + 1, target);
        if (appended.position() >= snapshot.requiredPosition()) {
          journal.flush();
          install(snapshot);
          return restored(current.historyId(), snapshot.snapshotId(), appended);
        }
        LOG.info(
            "Not restoring from snapshot {}, which relies on position {}, as the log reaches {} only",
            snapshot.snapshotId(),
            snapshot.requiredPosition(),
            appended.position());
      }
      final var appended = append(journal, segments, 1, target);
      if (appended.position() < 1) {
        throw new NothingToRestore(log.partition(), target);
      }
      journal.flush();
      return restored(current.historyId(), null, appended);
    }
  }

  /** The snapshots a restore along the path can start from, by the target, newest first. */
  private List<SnapshotDescriptor> candidates(final List<Stretch> path, final long target)
      throws IOException {
    return SnapshotArchive.list(store, log).complete().stream()
        .filter(snapshot -> snapshot.servesPath(path))
        .filter(snapshot -> snapshot.requiredPosition() <= target)
        .sorted(NEWEST_FIRST)
        .toList();
  }

  /**
   * Resets the journal to {@code first}, and appends the path's entries from there, until the first
   * entry with a record past the target or the bucket's end.
   */
  private Appended append(
      final Journal journal, final PathSegments segments, final long first, final long target)
      throws IOException {
    journal.reset(first);
    long next = first;
    long position = Entry.NO_POSITION;
    int relists = 0;
    while (true) {
      final var segment = segments.holding(next);
      if (segment.isEmpty()) {
        break;
      }
      final List<Entry> entries;
      try {
        entries = read(segment.get(), next);
      } catch (final NoSuchObjectException e) {
        // a segment can disappear while a reader reads, when maintenance replaces it
        if (++relists > RELISTS) {
          throw e;
        }
        segments.refresh();
        continue;
      }
      for (final var entry : entries) {
        if (entry.hasRecords() && entry.lastPosition() > target) {
          return new Appended(next - 1, position);
        }
        journal.append(entry.checksum(), entry.serializedRecord());
        if (entry.hasRecords()) {
          position = entry.lastPosition();
        }
        next++;
      }
    }
    if (next == first) {
      throw new MissingEntry(log.partition(), first);
    }
    if (target != LATEST && position < target) {
      throw new LogEndsBeforeTarget(log.partition(), target, next - 1, position);
    }
    return new Appended(next - 1, position);
  }

  /** The segment's entries from {@code next} up to the last that serves the path. */
  private List<Entry> read(final PathSegment segment, final long next) throws IOException {
    final var content =
        store.get(segment.key()).orElseThrow(() -> new NoSuchObjectException(segment.key()));
    final var footer = Footer.read(ByteBuffer.wrap(content));
    final var entries = new ArrayList<Entry>();
    long expected = next;
    try (final var reader =
        new EntryReader(new ByteArrayInputStream(content, 0, (int) footer.bodyLength()))) {
      for (var entry = reader.next();
          entry != null && expected <= segment.lastServed();
          entry = reader.next()) {
        if (entry.index() < next) {
          continue;
        }
        if (entry.index() != expected) {
          throw new UnexpectedEntry(log.partition(), segment.key(), expected, entry.index());
        }
        entries.add(entry);
        expected++;
      }
    }
    if (expected <= segment.lastServed()) {
      throw new UnexpectedEntry(log.partition(), segment.key(), expected, -1);
    }
    return entries;
  }

  /** Downloads the snapshot's files, and moves them into the partition's snapshot store. */
  private void install(final SnapshotDescriptor snapshot) throws IOException {
    final var download =
        rootDirectory.resolve(
            "restoring-partition" + log.partition() + "-snapshot-" + snapshot.snapshotId());
    try {
      final var files = SnapshotArchive.download(store, log, snapshot, download);
      @SuppressWarnings("resource")
      final var snapshotStore =
          new FileBasedSnapshotStore(
              brokerId, partition.id(), rootDirectory, fileInfoProvider, meterRegistry);
      snapshotStore.restore(snapshot.snapshotId(), files);
    } finally {
      FileUtil.deleteFolderIfExists(download);
    }
  }

  private RestoredFromBucket restored(
      final UUID history, final @Nullable String snapshotId, final Appended appended) {
    LOG.info(
        "Restored partition {} from the bucket, from snapshot {}, up to index {}, position {}",
        log.partition(),
        snapshotId,
        appended.lastIndex(),
        appended.position());
    return new RestoredFromBucket(
        history, snapshotId, new RestoredCheckpoint(appended.lastIndex(), appended.position()));
  }

  /**
   * What a restore from the bucket restored.
   *
   * @param history the tenant's current history, whose journal the restore took over
   * @param snapshotId the snapshot it installed, or null if it restored the journal from index 1
   * @param checkpoint the last restored entry, and the last record's position at or before it
   */
  public record RestoredFromBucket(
      UUID history, @Nullable String snapshotId, RestoredCheckpoint checkpoint) {}

  /** The journal's last appended index, and the last record's position, or -1 if none. */
  private record Appended(long lastIndex, long position) {}
}
