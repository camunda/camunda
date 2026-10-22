/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.snapshot.SnapshotArchive;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes a log's snapshots that no backup log names, once they are older than the retention
 * period. A snapshot that a backup log names is kept whatever its age, as the backup needs it. A
 * snapshot goes descriptor first, so that no reader finds a descriptor without its files; files
 * that a stopped upload or deletion left without a descriptor go once they are all older than the
 * retention period.
 */
final class SnapshotRetention {

  private static final Logger LOG = LoggerFactory.getLogger(SnapshotRetention.class);

  private final ObjectStore store;

  SnapshotRetention(final ObjectStore store) {
    this.store = store;
  }

  /**
   * Applies retention to the log's snapshots.
   *
   * @param named the ids of the snapshots that the log's backup logs name
   * @param threshold what was created or uploaded before it is older than the retention period
   * @return the count of snapshots deleted, those without a descriptor included
   * @throws io.camunda.zeebe.eventplane.snapshot.InvalidSnapshotDescriptorException if a descriptor
   *     isn't valid
   */
  int run(final LogId log, final Set<String> named, final Instant threshold) throws IOException {
    final var listing = SnapshotArchive.list(store, log);
    int deleted = 0;
    int spared = 0;
    for (final var snapshot : listing.complete()) {
      final boolean old = snapshot.createdAt().isBefore(threshold);
      if (old && named.contains(snapshot.snapshotId())) {
        spared++;
      } else if (old) {
        SnapshotArchive.delete(store, log, snapshot.snapshotId());
        LOG.info(
            "Deleted snapshot {} of {} at index {}, created at {}",
            snapshot.snapshotId(),
            log,
            snapshot.index(),
            snapshot.createdAt());
        deleted++;
      }
    }
    if (spared > 0) {
      LOG.info("Kept {} old snapshots of {}, as backups need them", spared, log);
    }
    for (final var snapshotId : listing.withoutDescriptor()) {
      if (deleteIfOld(log, snapshotId, threshold)) {
        deleted++;
      }
    }
    return deleted;
  }

  /** Deletes the files of a snapshot without descriptor, once every one is old. */
  private boolean deleteIfOld(final LogId log, final String snapshotId, final Instant threshold)
      throws IOException {
    final var objects = SnapshotArchive.objects(store, log, snapshotId);
    final boolean old =
        !objects.isEmpty()
            && objects.stream().map(StoredObject::lastModified).allMatch(threshold::isAfter);
    if (old) {
      SnapshotArchive.deleteFiles(store, log, snapshotId);
      LOG.info("Deleted the files of snapshot {} of {}, which has no descriptor", snapshotId, log);
    }
    return old;
  }
}
