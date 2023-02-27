/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.broker.system.partitions.NoEntryAtSnapshotPosition;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.SnapshotException.StateClosedException;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;

/** Controls how snapshot/recovery operations are performed */
public class StateControllerImpl implements StateController {

  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;

  private final Path runtimeDirectory;

  @SuppressWarnings("rawtypes")
  private final ZeebeDbFactory zeebeDbFactory;

  @SuppressWarnings("rawtypes")
  private final ToLongFunction<ZeebeDb> exporterPositionSupplier;

  private final AtomixRecordEntrySupplier entrySupplier;

  @SuppressWarnings("rawtypes")
  private ZeebeDb db;

  private final ConstructableSnapshotStore constructableSnapshotStore;
  private final ConcurrencyControl concurrencyControl;

  public StateControllerImpl(
      @SuppressWarnings("rawtypes") final ZeebeDbFactory zeebeDbFactory,
      final ConstructableSnapshotStore constructableSnapshotStore,
      final Path runtimeDirectory,
      final AtomixRecordEntrySupplier entrySupplier,
      @SuppressWarnings("rawtypes") final ToLongFunction<ZeebeDb> exporterPositionSupplier,
      final ConcurrencyControl concurrencyControl) {
    this.constructableSnapshotStore = constructableSnapshotStore;
    this.runtimeDirectory = runtimeDirectory;
    this.zeebeDbFactory = zeebeDbFactory;
    this.exporterPositionSupplier = exporterPositionSupplier;
    this.entrySupplier = entrySupplier;
    this.concurrencyControl = concurrencyControl;
  }

  @Override
  public ActorFuture<TransientSnapshot> takeTransientSnapshot(
      final long lowerBoundSnapshotPosition) {
    final ActorFuture<TransientSnapshot> future = concurrencyControl.createFuture();
    concurrencyControl.run(() -> takeTransientSnapshotInternal(lowerBoundSnapshotPosition, future));
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

  private void closeDbInternal(final ActorFuture<Void> future) {
    try {
      if (db != null) {
        final var dbToClose = db;
        dbToClose.endTracing();
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

    final var optLatestSnapshot = constructableSnapshotStore.getLatestSnapshot();
    if (optLatestSnapshot.isPresent()) {
      final var snapshot = optLatestSnapshot.get();
      LOG.debug("Recovering state from available snapshot: {}", snapshot);
      constructableSnapshotStore
          .copySnapshot(snapshot, runtimeDirectory)
          .onComplete(
              (ok, error) -> {
                if (error != null) {
                  future.completeExceptionally(
                      new RuntimeException(
                          String.format("Failed to recover from snapshot %s", snapshot.getId()),
                          error));
                } else {
                  openDb(future);
                }
              });
    } else {
      // If there is no snapshot, open empty database
      openDb(future);
    }
  }

  private void takeTransientSnapshotInternal(
      final long lowerBoundSnapshotPosition, final ActorFuture<TransientSnapshot> future) {
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
            nextSnapshotId.exportedPosition);

    if (transientSnapshot.isLeft()) {
      future.completeExceptionally(transientSnapshot.getLeft());
    } else {
      takeSnapshot(transientSnapshot.get(), future);
    }
  }

  private NextSnapshotId tryFindNextSnapshotId(final long lastProcessedPosition)
      throws NoEntryAtSnapshotPosition {
    final var exportedPosition = exporterPositionSupplier.applyAsLong(db);
    if (exportedPosition == -1) {
      final var latestSnapshot = constructableSnapshotStore.getLatestSnapshot();
      if (latestSnapshot.isPresent()) {
        // re-use index and term from the latest snapshot to ensure that the records from there are
        // not compacted until they get exported.
        final var persistedSnapshot = latestSnapshot.get();
        return new NextSnapshotId(
            persistedSnapshot.getIndex(), persistedSnapshot.getTerm(), lastProcessedPosition, 0);
      }

      return new NextSnapshotId(0, 0, lastProcessedPosition, 0);
    }

    final var snapshotPosition = Math.min(exportedPosition, lastProcessedPosition);
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
        LOG.debug("Opened database from '{}'.", runtimeDirectory);
        future.complete(db);
        db.startTracing();
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
