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
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.snapshot.SnapshotArchive;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.restore.BucketRestoreException.NoHistory;
import io.camunda.zeebe.restore.BucketRestoreException.NothingToRestore;
import io.camunda.zeebe.restore.PartitionRestoreService.RestoredCheckpoint;
import io.camunda.zeebe.snapshots.SnapshotFileInfoProvider;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Path;
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
  public static final long LATEST = PathEntries.LATEST;

  private static final Logger LOG = LoggerFactory.getLogger(BucketRestoreService.class);
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
    final var entries = new PathEntries(store, log, segments);
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
        final var appended = entries.append(journal, snapshot.index() + 1, target);
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
      final var appended = entries.append(journal, 1, target);
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
      final UUID history, final @Nullable String snapshotId, final PathEntries.Appended appended) {
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
}
