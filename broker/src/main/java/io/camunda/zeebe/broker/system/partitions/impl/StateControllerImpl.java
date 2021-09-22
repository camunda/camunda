/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
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

  public StateControllerImpl(
      @SuppressWarnings("rawtypes") final ZeebeDbFactory zeebeDbFactory,
      final ConstructableSnapshotStore constructableSnapshotStore,
      final Path runtimeDirectory,
      final AtomixRecordEntrySupplier entrySupplier,
      @SuppressWarnings("rawtypes") final ToLongFunction<ZeebeDb> exporterPositionSupplier) {
    this.constructableSnapshotStore = constructableSnapshotStore;
    this.runtimeDirectory = runtimeDirectory;
    this.zeebeDbFactory = zeebeDbFactory;
    this.exporterPositionSupplier = exporterPositionSupplier;
    this.entrySupplier = entrySupplier;
  }

  @Override
  public Optional<TransientSnapshot> takeTransientSnapshot(final long lowerBoundSnapshotPosition) {
    if (!isDbOpened()) {
      LOG.warn(
          "Expected to take snapshot for last processed position {}, but database was closed.",
          lowerBoundSnapshotPosition);
      return Optional.empty();
    }

    final long exportedPosition = exporterPositionSupplier.applyAsLong(openDb());
    final long snapshotPosition =
        determineSnapshotPosition(lowerBoundSnapshotPosition, exportedPosition);
    final var optionalIndexed = entrySupplier.getPreviousIndexedEntry(snapshotPosition);
    if (optionalIndexed.isEmpty()) {
      LOG.warn(
          "Failed to take snapshot. Expected to find an indexed entry for determined snapshot position {} (processedPosition = {}, exportedPosition={}), but found no matching indexed entry which contains this position.",
          snapshotPosition,
          lowerBoundSnapshotPosition,
          exportedPosition);
      return Optional.empty();
    }

    final var snapshotIndexedEntry = optionalIndexed.get();
    final Optional<TransientSnapshot> transientSnapshot =
        constructableSnapshotStore.newTransientSnapshot(
            snapshotIndexedEntry.index(),
            snapshotIndexedEntry.term(),
            lowerBoundSnapshotPosition,
            exportedPosition);

    transientSnapshot.ifPresent(this::takeSnapshot);

    return transientSnapshot;
  }

  @Override
  public ActorFuture<Void> recover() {
    try {
      FileUtil.deleteFolderIfExists(runtimeDirectory);
    } catch (final IOException e) {
      return CompletableActorFuture.completedExceptionally(e);
    }

    final var optLatestSnapshot = constructableSnapshotStore.getLatestSnapshot();
    if (optLatestSnapshot.isPresent()) {
      final var snapshot = optLatestSnapshot.get();
      LOG.debug("Recovering state from available snapshot: {}", snapshot);
      return constructableSnapshotStore.copySnapshot(snapshot, runtimeDirectory);
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  @SuppressWarnings("rawtypes")
  public ZeebeDb openDb() {
    if (db == null) {
      db = zeebeDbFactory.createDb(runtimeDirectory.toFile());
      LOG.debug("Opened database from '{}'.", runtimeDirectory);
    }

    return db;
  }

  @Override
  public void closeDb() throws Exception {
    if (db != null) {
      final var dbToClose = db;
      db = null;
      dbToClose.close();

      LOG.debug("Closed database from '{}'.", runtimeDirectory);
    }

    FileUtil.deleteFolderIfExists(runtimeDirectory);
  }

  @Override
  public int getValidSnapshotsCount() {
    return constructableSnapshotStore.getLatestSnapshot().isPresent() ? 1 : 0;
  }

  @Override
  public void close() throws Exception {
    closeDb();
  }

  boolean isDbOpened() {
    return db != null;
  }

  private ActorFuture<Boolean> takeSnapshot(final TransientSnapshot snapshot) {
    return snapshot.take(
        snapshotDir -> {
          if (db == null) {
            LOG.error("Expected to take a snapshot, but no database was opened");
            return false;
          }

          LOG.debug("Taking temporary snapshot into {}.", snapshotDir);
          try {
            db.createSnapshot(snapshotDir.toFile());
          } catch (final Exception e) {
            LOG.error("Failed to create snapshot of runtime database", e);
            return false;
          }

          return true;
        });
  }

  private long determineSnapshotPosition(
      final long lowerBoundSnapshotPosition, final long exportedPosition) {
    final long snapshotPosition = Math.min(exportedPosition, lowerBoundSnapshotPosition);
    LOG.trace(
        "Based on lowest exporter position '{}' and last processed position '{}', determined '{}' as snapshot position.",
        exportedPosition,
        lowerBoundSnapshotPosition,
        snapshotPosition);
    return snapshotPosition;
  }
}
