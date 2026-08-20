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
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
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
  private final AtomixRecordEntrySupplier entrySupplier;
  private final ConstructableSnapshotStore constructableSnapshotStore;
  private final ConcurrencyControl concurrencyControl;

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
    this.constructableSnapshotStore = requireNonNull(constructableSnapshotStore);
    this.runtimeDirectory = requireNonNull(runtimeDirectory);
    this.zeebeDbFactory = requireNonNull(zeebeDbFactory);
    this.entrySupplier = requireNonNull(entrySupplier);
    this.positionSupplierFactory = requireNonNull(positionSupplierFactory);
    this.concurrencyControl = requireNonNull(concurrencyControl);

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
    }

    constructableSnapshotStore
        .getLatestSnapshot()
        .ifPresent(snapshot -> recoverFromSnapshot(future, snapshot));
    openDb(future);
  }

  private void recoverFromSnapshot(
      final ActorFuture<ZeebeDb> future, final PersistedSnapshot snapshot) {
    LOG.debug("Recovering state from available snapshot: {}", snapshot);

    try (final var db = zeebeDbFactory.openSnapshotOnlyDb(snapshot.getPath().toFile())) {
      db.createSnapshot(runtimeDirectory.toFile());
    } catch (final Exception e) {
      future.completeExceptionally(
          new ZeebeDbException(
              String.format("Failed to recover from snapshot %s", snapshot.getId()), e));
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
      nextSnapshotId = tryFindNextSnapshotId(lowerBoundSnapshotPosition);
    } catch (final NoEntryAtSnapshotPosition e) {
      future.completeExceptionally(e);
      return;
    }

    final var transientSnapshot =
        constructableSnapshotStore.newTransientSnapshot(
            nextSnapshotId.index,
            nextSnapshotId.term,
            nextSnapshotId.processedPosition,
            nextSnapshotId.exportedPosition,
            forceSnapshot);

    if (transientSnapshot.isLeft()) {
      future.completeExceptionally(transientSnapshot.getLeft());
    } else {
      takeSnapshot(transientSnapshot.get(), future);
    }
  }

  private NextSnapshotId tryFindNextSnapshotId(final long lastProcessedPosition)
      throws NoEntryAtSnapshotPosition {
    final var exportedPosition = positionSupplier.getLowestExportedPosition();
    final var backupPosition = positionSupplier.getHighestBackupPosition();
    if (exportedPosition == -1 || backupPosition == -1 || lastProcessedPosition == 0) {
      final var latestSnapshot = constructableSnapshotStore.getLatestSnapshot();
      if (latestSnapshot.isPresent()) {
        // re-use index and term from the latest snapshot to ensure that the records from there are
        // not compacted until they get exported and backed up.
        final var persistedSnapshot = latestSnapshot.get();
        return new NextSnapshotId(
            persistedSnapshot.getIndex(), persistedSnapshot.getTerm(), lastProcessedPosition, 0);
      }

      return new NextSnapshotId(0, 0, lastProcessedPosition, 0);
    }

    final var snapshotPosition =
        Math.min(Math.min(exportedPosition, backupPosition), lastProcessedPosition);
    final var logEntry = entrySupplier.getPreviousIndexedEntry(snapshotPosition);

    if (logEntry.isPresent()) {
      return new NextSnapshotId(
          logEntry.get().index(), logEntry.get().term(), lastProcessedPosition, exportedPosition);
    }

    // No log entry for snapshot position - try to use the index and term of the last snapshot to
    // take new one
    final var latestSnapshot = constructableSnapshotStore.getLatestSnapshot();
    if (latestSnapshot.isPresent()) {
      LOG.warn(
          "No log entry for next snapshot position {}, using index and term from previous snapshot",
          snapshotPosition);
      return new NextSnapshotId(
          latestSnapshot.get().getIndex(),
          latestSnapshot.get().getTerm(),
          lastProcessedPosition,
          exportedPosition);
    }

    throw new NoEntryAtSnapshotPosition(
        String.format(
            "Failed to take snapshot. Expected to find an indexed entry for determined snapshot position %d (processedPosition = %d, exportedPosition=%d) or previous snapshot, but found neither.",
            snapshotPosition, lastProcessedPosition, exportedPosition));
  }

  @SuppressWarnings("rawtypes")
  private void openDb(final ActorFuture<ZeebeDb> future) {
    try {
      if (db == null) {
        db = zeebeDbFactory.createDb(runtimeDirectory.toFile());
        positionSupplier = positionSupplierFactory.apply(db);
        LOG.debug("Opened database from '{}'.", runtimeDirectory);
        future.complete(db);
      }
    } catch (final Exception error) {
      future.completeExceptionally(new RuntimeException("Failed to open database", error));
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

  private record NextSnapshotId(
      long index, long term, long processedPosition, long exportedPosition) {}
}
