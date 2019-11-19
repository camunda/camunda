/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.state;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.util.FileUtil;
import io.zeebe.util.LangUtil;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/** Controls how snapshot/recovery operations are performed */
public class StateSnapshotController implements SnapshotController {
  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;

  private final SnapshotStorage storage;
  private final ZeebeDbFactory zeebeDbFactory;
  private final ReplicationController replicationController;
  private ZeebeDb db;

  public StateSnapshotController(
      final ZeebeDbFactory rocksDbFactory, final SnapshotStorage storage) {
    this(rocksDbFactory, storage, new NoneSnapshotReplication());
  }

  public StateSnapshotController(
      final ZeebeDbFactory zeebeDbFactory,
      final SnapshotStorage storage,
      final SnapshotReplication replication) {
    this.storage = storage;
    this.zeebeDbFactory = zeebeDbFactory;
    this.replicationController = new ReplicationController(replication, storage);
  }

  @Override
  public void takeSnapshot(final long lowerBoundSnapshotPosition) {
    final var snapshotDir = storage.getPendingDirectoryFor(lowerBoundSnapshotPosition);
    createSnapshot(snapshotDir);
    storage.commitSnapshot(snapshotDir);
  }

  @Override
  public void takeTempSnapshot(final long lowerBoundSnapshotPosition) {
    final var snapshotDir = storage.getPendingDirectoryFor(lowerBoundSnapshotPosition);
    createSnapshot(snapshotDir);
  }

  @Override
  public void commitSnapshot(final long lowerBoundSnapshotPosition) throws IOException {
    Objects.requireNonNull(db, "Cannot commit snapshot for a closed database");
    storage.commitSnapshot(storage.getPendingDirectoryFor(lowerBoundSnapshotPosition));
  }

  @Override
  public void replicateLatestSnapshot(final Consumer<Runnable> executor) {
    final var maybeLatest = storage.getLatestSnapshot();
    if (maybeLatest.isPresent()) {
      final var latestSnapshotDirectory = maybeLatest.get().getPath();
      LOG.debug("Start replicating latest snapshot {}", latestSnapshotDirectory);

      try {
        final var files = Files.list(latestSnapshotDirectory).collect(Collectors.toList());
        for (final var file : files) {
          executor.accept(
              () -> {
                LOG.trace("Replicate snapshot chunk {}", file);
                replicationController.replicate(
                    latestSnapshotDirectory.getFileName().toString(), files.size(), file.toFile());
              });
        }
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Override
  public void consumeReplicatedSnapshots() {
    replicationController.consumeReplicatedSnapshots();
  }

  @Override
  public long recover() throws Exception {
    final var runtimeDirectory = storage.getRuntimeDirectory();

    if (Files.exists(runtimeDirectory)) {
      FileUtil.deleteFolder(runtimeDirectory);
    }

    final var snapshots = storage.getSnapshots();
    LOG.debug("Available snapshots: {}", snapshots);

    long lowerBoundSnapshotPosition = -1;

    final var snapshotIterator = snapshots.iterator();
    while (snapshotIterator.hasNext() && lowerBoundSnapshotPosition < 0) {
      final var snapshot = snapshotIterator.next();

      FileUtil.copySnapshot(runtimeDirectory, snapshot.getPath());

      try {
        // open database to verify that the snapshot is recoverable
        openDb();
        LOG.debug("Recovered state from snapshot '{}'", snapshot);
        lowerBoundSnapshotPosition = snapshot.getPosition();
      } catch (final Exception e) {
        FileUtil.deleteFolder(runtimeDirectory);

        if (snapshotIterator.hasNext()) {
          LOG.warn(
              "Failed to open snapshot '{}'. Delete this snapshot and try the previous one.",
              snapshot,
              e);
          FileUtil.deleteFolder(snapshot.getPath());

        } else {
          LOG.error(
              "Failed to open snapshot '{}'. No snapshots available to recover from. Manual action is required.",
              snapshot,
              e);
          LangUtil.rethrowUnchecked(e);
        }
      }
    }

    return lowerBoundSnapshotPosition;
  }

  @Override
  public ZeebeDb openDb() {
    if (db == null) {
      final var runtimeDirectory = storage.getRuntimeDirectory();
      db = zeebeDbFactory.createDb(runtimeDirectory.toFile());
      LOG.debug("Opened database from '{}'.", runtimeDirectory);
    }

    return db;
  }

  @Override
  public long getPositionToDelete(final int maxSnapshotCount) {
    return storage
        .getSnapshots()
        .map(Snapshot::getPosition)
        .sorted(Comparator.reverseOrder())
        .skip(maxSnapshotCount - 1L)
        .findFirst()
        .orElse(-1L);
  }

  @Override
  public int getValidSnapshotsCount() {
    return (int) storage.getSnapshots().count();
  }

  @Override
  public long getLastValidSnapshotPosition() {
    return storage.getLatestSnapshot().map(Snapshot::getPosition).orElse(-1L);
  }

  @Override
  public File getLastValidSnapshotDirectory() {
    return storage.getLatestSnapshot().map(Snapshot::getPath).map(Path::toFile).orElse(null);
  }

  @Override
  public void close() throws Exception {
    if (db != null) {
      db.close();
      final var runtimeDirectory = storage.getRuntimeDirectory();
      LOG.debug("Closed database from '{}'.", runtimeDirectory);
      db = null;
    }
  }

  public boolean isDbOpened() {
    return db != null;
  }

  private void createSnapshot(final Path snapshotDir) {
    Objects.requireNonNull(db, "Cannot take snapshot of closed database");
    LOG.debug("Take temporary snapshot and write into {}.", snapshotDir);
    db.createSnapshot(snapshotDir.toFile());
  }
}
