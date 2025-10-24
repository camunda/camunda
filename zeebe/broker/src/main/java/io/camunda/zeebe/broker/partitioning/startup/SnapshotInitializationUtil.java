/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransfer;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility class for handling bootstrapping a partition via snapshot from partition 1. */
public final class SnapshotInitializationUtil {

  private static final Logger LOG = LoggerFactory.getLogger(SnapshotInitializationUtil.class);

  private SnapshotInitializationUtil() {
    // Utility class
  }

  /**
   * Initializes a partition's snapshot store from a snapshot from partition 1, handling all the
   * necessary validation and cleanup steps. This method is idempotent and can be safely retried
   * after failures at different steps.
   *
   * @param snapshotStore the snapshot store to use
   * @param snapshotTransfer the snapshot transfer service
   * @param concurrencyControl the concurrency control for actor futures
   * @return a future that completes when initialization is finished
   */
  public static ActorFuture<Void> initializeFromSnapshot(
      final FileBasedSnapshotStore snapshotStore,
      final SnapshotTransfer snapshotTransfer,
      final ConcurrencyControl concurrencyControl) {

    return validateAndCleanupExistingSnapshot(snapshotStore, concurrencyControl)
        .andThen(
            ignored -> fetchAndRestoreSnapshot(snapshotTransfer, snapshotStore, concurrencyControl),
            concurrencyControl)
        .andThen(
            ignored -> cleanupBootstrapSnapshot(snapshotStore, concurrencyControl),
            concurrencyControl);
  }

  /**
   * Validates existing snapshots and cleans up bootstrap snapshots if necessary. If a bootstrap
   * snapshot is already present, it might be from a previous failed bootstrap attempt. Since it is
   * not easy to verify if it is the required snapshot, we will delete it so that a new snapshot can
   * be requested. It is not expected to have snapshot not for bootstrap, because this partition has
   * not started yet. So for safety we will fail and manual intervention is required.
   */
  private static ActorFuture<Void> validateAndCleanupExistingSnapshot(
      final FileBasedSnapshotStore snapshotStore, final ConcurrencyControl concurrencyControl) {

    final Optional<PersistedSnapshot> latestSnapshot = snapshotStore.getLatestSnapshot();

    if (latestSnapshot.isEmpty()) {
      LOG.trace("No existing snapshot found, proceeding with initialization");
      return CompletableActorFuture.completed();
    }

    final PersistedSnapshot snapshot = latestSnapshot.get();
    final boolean isBootstrap = snapshot.getMetadata().isBootstrap();

    if (isBootstrap) {
      LOG.info(
          "A bootstrapped snapshot is present, deleting it in order to be able to fetch it again and bootstrap cleanly.");
      return snapshotStore.delete().thenApply(empty -> null, concurrencyControl);
    } else {
      final String errorMessage =
          "Snapshot "
              + snapshot.getId()
              + " is not for bootstrap, aborting bootstrap. Manual intervention is required to successfully bootstrap this partition. Verify why the snapshot is present and if it's safe to do so please delete it.";
      return CompletableActorFuture.completedExceptionally(new IllegalStateException(errorMessage));
    }
  }

  /** Fetches the latest snapshot from the leader and restores it if available. */
  private static ActorFuture<Void> fetchAndRestoreSnapshot(
      final SnapshotTransfer snapshotTransfer,
      final FileBasedSnapshotStore snapshotStore,
      final ConcurrencyControl concurrencyControl) {

    final ActorFuture<PersistedSnapshot> fetchFuture =
        snapshotTransfer.getLatestSnapshot(Protocol.DEPLOYMENT_PARTITION);

    return fetchFuture.andThen(
        snapshot -> {
          if (snapshot == null) {
            LOG.info("Received no snapshot from leader, skipping restore from snapshot");
            return CompletableActorFuture.completed();
          } else {
            LOG.info("Received snapshot {} from leader, restoring from snapshot", snapshot.getId());
            return snapshotStore.restore(snapshot);
          }
        },
        concurrencyControl);
  }

  private static ActorFuture<Void> cleanupBootstrapSnapshot(
      final FileBasedSnapshotStore snapshotStore, final ConcurrencyControl concurrencyControl) {

    return snapshotStore.deleteBootstrapSnapshots();
  }
}
