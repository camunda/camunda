/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.broker.system.partitions.NoEntryAtSnapshotPosition;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.broker.system.partitions.impl.SnapshotIdFinder.NextSnapshotId;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotException.StateClosedException;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.stream.impl.state.DbLastProcessedPositionState;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import org.agrona.CloseHelper;
import org.slf4j.Logger;

/** Controls how snapshot/recovery operations are performed */
@SuppressWarnings("rawtypes")
public class StateControllerImpl implements StateController {

  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;
  private static final Duration DB_METRICS_EXPORT_INTERVAL = Duration.ofSeconds(5);

  private final Path runtimeDirectory;
  private final ZeebeDbFactory zeebeDbFactory;
  private final Function<ZeebeDb, StatePositionSupplier> positionSupplierFactory;
  private final SnapshotIdFinder snapshotIdFinder;
  private final ConstructableSnapshotStore constructableSnapshotStore;
  private final ConcurrencyControl concurrencyControl;
  private final boolean sharedState;

  private ZeebeDb db;
  private StatePositionSupplier positionSupplier;
  private ScheduledTimer metricsExportTimer;

  public StateControllerImpl(
      final ZeebeDbFactory zeebeDbFactory,
      final ConstructableSnapshotStore constructableSnapshotStore,
      final Path runtimeDirectory,
      final AtomixRecordEntrySupplier entrySupplier,
      final Function<ZeebeDb, StatePositionSupplier> positionSupplierFactory,
      final ConcurrencyControl concurrencyControl) {
    this(
        zeebeDbFactory,
        constructableSnapshotStore,
        runtimeDirectory,
        entrySupplier,
        positionSupplierFactory,
        concurrencyControl,
        false);
  }

  /**
   * @param sharedState whether the snapshots only name a state kept in a shared database, instead
   *     of holding a copy of it. Then recovery checks that the state isn't older than the snapshot,
   *     because the log may no longer have the records in between.
   */
  public StateControllerImpl(
      final ZeebeDbFactory zeebeDbFactory,
      final ConstructableSnapshotStore constructableSnapshotStore,
      final Path runtimeDirectory,
      final AtomixRecordEntrySupplier entrySupplier,
      final Function<ZeebeDb, StatePositionSupplier> positionSupplierFactory,
      final ConcurrencyControl concurrencyControl,
      final boolean sharedState) {
    this.constructableSnapshotStore = requireNonNull(constructableSnapshotStore);
    this.runtimeDirectory = requireNonNull(runtimeDirectory);
    this.zeebeDbFactory = requireNonNull(zeebeDbFactory);
    snapshotIdFinder = new SnapshotIdFinder(entrySupplier, constructableSnapshotStore);
    this.positionSupplierFactory = requireNonNull(positionSupplierFactory);
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.sharedState = sharedState;

    concurrencyControl.execute(this::scheduleDbMetricsExport);
  }

  @Override
  public ActorFuture<TransientSnapshot> takeTransientSnapshot(
      final long lowerBoundSnapshotPosition, final boolean forceSnapshot) {
    final ActorFuture<TransientSnapshot> future = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> takeTransientSnapshotInternal(lowerBoundSnapshotPosition, forceSnapshot, future));
    return future;
  }

  @Override
  public ActorFuture<ZeebeDb> recover() {
    final ActorFuture<ZeebeDb> future = concurrencyControl.createFuture();
    concurrencyControl.run(() -> recoverInternal(future));
    return future;
  }

  @Override
  public ActorFuture<Void> closeDb() {
    final ActorFuture<Void> future = concurrencyControl.createFuture();
    concurrencyControl.run(() -> closeDbInternal(future));
    return future;
  }

  private void scheduleDbMetricsExport() {
    metricsExportTimer =
        concurrencyControl.schedule(DB_METRICS_EXPORT_INTERVAL, this::exportDbMetrics);
  }

  private void exportDbMetrics() {
    if (isDbOpened()) {
      db.exportMetrics();
    }

    scheduleDbMetricsExport();
  }

  private void closeDbInternal(final ActorFuture<Void> future) {
    try {
      CloseHelper.quietClose(metricsExportTimer);

      if (db != null) {
        final var dbToClose = db;
        db = null;
        dbToClose.close();

        LOG.debug("Closed database from '{}'.", runtimeDirectory);
      }

      tryDeletingRuntimeDirectory();
      future.complete(null);
    } catch (final Exception e) {
      future.completeExceptionally(e);
    }
  }

  private void recoverInternal(final ActorFuture<ZeebeDb> future) {
    try {
      FileUtil.deleteFolderIfExists(runtimeDirectory);
    } catch (final IOException e) {
      future.completeExceptionally(
          new RuntimeException(
              "Failed to delete runtime folder. Cannot recover from snapshot.", e));
      return;
    }

    final var latestSnapshot = constructableSnapshotStore.getLatestSnapshot();
    if (latestSnapshot.isPresent() && !recoverFromSnapshot(future, latestSnapshot.get())) {
      return;
    }
    openDb(future, latestSnapshot);
  }

  private boolean recoverFromSnapshot(
      final ActorFuture<ZeebeDb> future, final PersistedSnapshot snapshot) {
    LOG.debug("Recovering state from available snapshot: {}", snapshot);

    try (final var db = zeebeDbFactory.openSnapshotOnlyDb(snapshot.getPath().toFile())) {
      db.createSnapshot(runtimeDirectory.toFile());
      return true;
    } catch (final Exception e) {
      future.completeExceptionally(
          new ZeebeDbException(
              String.format("Failed to recover from snapshot %s", snapshot.getId()), e));
      return false;
    }
  }

  private void takeTransientSnapshotInternal(
      final long lowerBoundSnapshotPosition,
      final boolean forceSnapshot,
      final ActorFuture<TransientSnapshot> future) {
    if (!isDbOpened()) {
      final String error =
          String.format(
              "Expected to take snapshot for last processed position %d, but database was closed.",
              lowerBoundSnapshotPosition);
      future.completeExceptionally(new StateClosedException(error));
      return;
    }

    final NextSnapshotId nextSnapshotId;
    try {
      if (lowerBoundSnapshotPosition == 0 && !forceSnapshot) {
        future.completeExceptionally(
            new IllegalArgumentException(
                "Snapshot can be taken at processed position 0 only if forced."));
        return;
      }
      nextSnapshotId =
          snapshotIdFinder.find(
              lowerBoundSnapshotPosition,
              positionSupplier.getLowestExportedPosition(),
              positionSupplier.getHighestBackupPosition());
    } catch (final NoEntryAtSnapshotPosition e) {
      future.completeExceptionally(e);
      return;
    }

    final var transientSnapshot =
        constructableSnapshotStore.newTransientSnapshot(
            nextSnapshotId.index(),
            nextSnapshotId.term(),
            nextSnapshotId.processedPosition(),
            nextSnapshotId.exportedPosition(),
            forceSnapshot);

    if (transientSnapshot.isLeft()) {
      future.completeExceptionally(transientSnapshot.getLeft());
    } else {
      takeSnapshot(transientSnapshot.get(), future);
    }
  }

  @SuppressWarnings("rawtypes")
  private void openDb(
      final ActorFuture<ZeebeDb> future, final Optional<PersistedSnapshot> recoveredSnapshot) {
    try {
      if (db == null) {
        final var openedDb = zeebeDbFactory.createDb(runtimeDirectory.toFile());
        if (sharedState && recoveredSnapshot.isPresent()) {
          try {
            verifyStateIsNotOlderThan(openedDb, recoveredSnapshot.get());
          } catch (final RuntimeException e) {
            CloseHelper.quietClose(openedDb);
            throw e;
          }
        }
        db = openedDb;
        positionSupplier = positionSupplierFactory.apply(db);
        LOG.debug("Opened database from '{}'.", runtimeDirectory);
        future.complete(db);
      }
    } catch (final UnrecoverableException error) {
      future.completeExceptionally(error);
    } catch (final Exception error) {
      future.completeExceptionally(new RuntimeException("Failed to open database", error));
    }
  }

  /**
   * A shared state only moves forward, so it can't be older than a snapshot that names it, unless
   * its database went back in time, for example after a restore to an earlier point in time. Raft
   * may delete the log up to the snapshot at any time, so the state can't be trusted to catch up by
   * replaying the log: the records between the state and the snapshot may be gone.
   */
  @SuppressWarnings("unchecked")
  private static void verifyStateIsNotOlderThan(
      final ZeebeDb db, final PersistedSnapshot snapshot) {
    final long processedPosition =
        new DbLastProcessedPositionState(db, db.createContext())
            .getLastSuccessfulProcessedRecordPosition();
    final long snapshotPosition = snapshot.getMetadata().processedPosition();
    if (processedPosition < snapshotPosition) {
      throw new UnrecoverableException(
          ("Expected the shared state to be processed up to at least position %d, like snapshot "
                  + "%s, but it is processed only up to position %d. The state doesn't belong to "
                  + "this snapshot and log, for example because its database was restored to an "
                  + "earlier point in time.")
              .formatted(snapshotPosition, snapshot.getId(), processedPosition));
    }
  }

  private void tryDeletingRuntimeDirectory() {
    try {
      FileUtil.deleteFolderIfExists(runtimeDirectory);
    } catch (final Exception e) {
      LOG.debug("Failed to delete runtime directory when closing", e);
    }
  }

  @Override
  public void close() throws Exception {
    closeDb();
  }

  boolean isDbOpened() {
    return db != null;
  }

  private void takeSnapshot(
      final TransientSnapshot snapshot,
      final ActorFuture<TransientSnapshot> transientSnapshotFuture) {
    final var snapshotTaken =
        snapshot.take(
            snapshotDir -> {
              if (db == null) {
                throw new StateClosedException(
                    "Expected to take a snapshot, but no database was opened");
              }

              LOG.debug("Taking temporary snapshot into {}.", snapshotDir);
              db.createSnapshot(snapshotDir.toFile());
            });

    snapshotTaken.onComplete(
        (ok, error) -> {
          if (error != null) {
            transientSnapshotFuture.completeExceptionally(error);
          } else {
            transientSnapshotFuture.complete(snapshot);
          }
        });
  }
}
