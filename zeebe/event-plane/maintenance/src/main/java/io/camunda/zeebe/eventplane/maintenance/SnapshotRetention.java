/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.history.HistoryLineage.Stretch;
import io.camunda.zeebe.eventplane.snapshot.SnapshotArchive;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes a log's snapshots that are older than the retention period, but always keeps the newest
 * one that a restore along the current path can start from, so that the log stays restorable. A
 * snapshot goes descriptor first, so that no reader finds a descriptor without its files; files
 * that a stopped upload or deletion left without a descriptor go once they are all older than the
 * retention period.
 */
final class SnapshotRetention {

  /** The bound of a log without a snapshot to keep: it holds back no segment. */
  static final long NO_BOUND = Long.MAX_VALUE;

  private static final Logger LOG = LoggerFactory.getLogger(SnapshotRetention.class);

  private static final Comparator<SnapshotDescriptor> NEWEST_LAST =
      Comparator.comparingLong(SnapshotDescriptor::index)
          .thenComparingLong(SnapshotDescriptor::processedPosition)
          .thenComparing(SnapshotDescriptor::snapshotId);

  private final ObjectStore store;

  SnapshotRetention(final ObjectStore store) {
    this.store = store;
  }

  /**
   * Applies retention to the log's snapshots.
   *
   * @param path the current history's path through the log
   * @param threshold what was uploaded before it is older than the retention period
   * @return the first index that a kept snapshot needs, {@link #NO_BOUND} if none, and the count of
   *     snapshots deleted
   */
  Result run(final LogId log, final List<Stretch> path, final Instant threshold)
      throws IOException {
    final var listing = SnapshotArchive.list(store, log.cluster(), log.tenant(), log.partition());
    final var newestOnPath =
        listing.complete().stream()
            .filter(snapshot -> snapshot.servesPath(path))
            .max(NEWEST_LAST)
            .orElse(null);

    long bound = NO_BOUND;
    int deleted = 0;
    for (final var snapshot : listing.complete()) {
      final boolean onPath = snapshot.servesPath(path);
      if (snapshot.createdAt().isBefore(threshold) && !snapshot.equals(newestOnPath)) {
        SnapshotArchive.delete(
            store, log.cluster(), log.tenant(), log.partition(), snapshot.snapshotId());
        LOG.info(
            "Deleted snapshot {} of {} at index {}, created at {}",
            snapshot.snapshotId(),
            log,
            snapshot.index(),
            snapshot.createdAt());
        deleted++;
      } else if (onPath) {
        bound = Math.min(bound, snapshot.index() + 1);
      }
    }
    for (final var snapshotId : listing.withoutDescriptor()) {
      if (deleteIfOld(log, snapshotId, threshold)) {
        deleted++;
      }
    }
    return new Result(bound, deleted);
  }

  /** Deletes the files of a snapshot without descriptor, once every one is old. */
  private boolean deleteIfOld(final LogId log, final String snapshotId, final Instant threshold)
      throws IOException {
    final var objects =
        SnapshotArchive.objects(store, log.cluster(), log.tenant(), log.partition(), snapshotId);
    final boolean old =
        !objects.isEmpty()
            && objects.stream().map(StoredObject::lastModified).allMatch(threshold::isAfter);
    if (old) {
      SnapshotArchive.deleteFiles(store, log.cluster(), log.tenant(), log.partition(), snapshotId);
      LOG.info("Deleted the files of snapshot {} of {}, which has no descriptor", snapshotId, log);
    }
    return old;
  }

  /**
   * @param bound the first index that a kept snapshot of the path needs: the oldest one's index + 1
   * @param deleted the snapshots deleted
   */
  record Result(long bound, int deleted) {}
}
