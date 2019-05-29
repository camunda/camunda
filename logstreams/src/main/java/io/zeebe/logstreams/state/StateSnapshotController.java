/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.state;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.impl.delete.DeletionService;
import io.zeebe.logstreams.impl.delete.NoopDeletionService;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.spi.ValidSnapshotListener;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;

/** Controls how snapshot/recovery operations are performed */
public class StateSnapshotController implements SnapshotController, ValidSnapshotListener {
  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;

  private static final String ERROR_MSG_ENSURING_MAX_SNAPSHOT_COUNT =
      "Unexpected exception occurred on ensuring maximum snapshot count.";

  private final StateStorage storage;
  private final ZeebeDbFactory zeebeDbFactory;
  private ZeebeDb db;
  private final ReplicationController replicationController;
  private DeletionService deletionService = new NoopDeletionService();
  private final int maxSnapshotCount;

  public StateSnapshotController(final ZeebeDbFactory rocksDbFactory, final StateStorage storage) {
    this(rocksDbFactory, storage, new NoneSnapshotReplication(), 1);
  }

  public StateSnapshotController(
      final ZeebeDbFactory rocksDbFactory, final StateStorage storage, int maxSnapshotCount) {
    this(rocksDbFactory, storage, new NoneSnapshotReplication(), maxSnapshotCount);
  }

  public StateSnapshotController(
      ZeebeDbFactory zeebeDbFactory,
      StateStorage storage,
      SnapshotReplication replication,
      int maxSnapshotCount) {
    this.storage = storage;
    this.zeebeDbFactory = zeebeDbFactory;
    this.maxSnapshotCount = maxSnapshotCount;
    this.replicationController = new ReplicationController(replication, storage, this);
  }

  @Override
  public void takeSnapshot(long lowerBoundSnapshotPosition) {
    if (db == null) {
      throw new IllegalStateException("Cannot create snapshot of not open database.");
    }

    final File snapshotDir = storage.getSnapshotDirectoryFor(lowerBoundSnapshotPosition);
    db.createSnapshot(snapshotDir);
  }

  @Override
  public void takeTempSnapshot() {
    if (db == null) {
      throw new IllegalStateException("Cannot create snapshot of not open database.");
    }

    final File snapshotDir = storage.getTempSnapshotDirectory();
    LOG.debug("Take temporary snapshot and write into {}.", snapshotDir.getAbsolutePath());
    db.createSnapshot(snapshotDir);
  }

  @Override
  public void moveValidSnapshot(long lowerBoundSnapshotPosition) throws IOException {
    if (db == null) {
      throw new IllegalStateException("Cannot create snapshot of not open database.");
    }

    final File previousLocation = storage.getTempSnapshotDirectory();
    if (!previousLocation.exists()) {
      throw new IllegalStateException(
          String.format(
              "Temporary snapshot directory %s does not exist.",
              previousLocation.getAbsolutePath()));
    }

    final File snapshotDir = storage.getSnapshotDirectoryFor(lowerBoundSnapshotPosition);
    if (snapshotDir.exists()) {
      return;
    }

    LOG.debug(
        "Snapshot is valid. Move snapshot from {} to {}.",
        previousLocation.getAbsolutePath(),
        snapshotDir.getAbsolutePath());

    Files.move(previousLocation.toPath(), snapshotDir.toPath());
    onNewValidSnapshot();
  }

  public void replicateLatestSnapshot(Consumer<Runnable> executor) {
    final List<File> snapshots = storage.listByPositionDesc();

    if (snapshots != null && !snapshots.isEmpty()) {
      final File latestSnapshotDirectory = snapshots.get(0);
      LOG.debug("Start replicating latest snapshot {}", latestSnapshotDirectory.toPath());
      final long snapshotPosition = Long.parseLong(latestSnapshotDirectory.getName());

      final File[] files = latestSnapshotDirectory.listFiles();
      for (File snapshotChunkFile : files) {
        executor.accept(
            () -> {
              LOG.debug("Replicate snapshot chunk {}", snapshotChunkFile.toPath());
              replicationController.replicate(snapshotPosition, files.length, snapshotChunkFile);
            });
      }
    }
  }

  public void consumeReplicatedSnapshots() {
    replicationController.consumeReplicatedSnapshots();
  }

  public void setDeletionService(DeletionService deletionService) {
    this.deletionService = deletionService;
  }

  @Override
  public long recover() throws Exception {
    final File runtimeDirectory = storage.getRuntimeDirectory();

    if (runtimeDirectory.exists()) {
      FileUtil.deleteFolder(runtimeDirectory.getAbsolutePath());
    }

    final List<File> snapshots = storage.listByPositionDesc();
    LOG.debug("Available snapshots: {}", snapshots);

    long lowerBoundSnapshotPosition = -1;

    final Iterator<File> snapshotIterator = snapshots.iterator();
    while (snapshotIterator.hasNext() && lowerBoundSnapshotPosition < 0) {
      final File snapshotDirectory = snapshotIterator.next();

      FileUtil.copySnapshot(runtimeDirectory, snapshotDirectory);

      try {
        // open database to verify that the snapshot is recoverable
        openDb();

        LOG.debug("Recovered state from snapshot '{}'", snapshotDirectory);

        lowerBoundSnapshotPosition = Long.parseLong(snapshotDirectory.getName());

      } catch (Exception e) {
        FileUtil.deleteFolder(runtimeDirectory.getAbsolutePath());

        if (snapshotIterator.hasNext()) {
          LOG.warn(
              "Failed to open snapshot '{}'. Delete this snapshot and try the previous one.",
              snapshotDirectory,
              e);
          FileUtil.deleteFolder(snapshotDirectory.getAbsolutePath());

        } else {
          LOG.error(
              "Failed to open snapshot '{}'. No snapshots available to recover from. Manual action is required.",
              snapshotDirectory,
              e);
          throw new RuntimeException("Failed to recover from snapshots", e);
        }
      }
    }

    return lowerBoundSnapshotPosition;
  }

  @Override
  public ZeebeDb openDb() {
    if (db == null) {
      final File runtimeDirectory = storage.getRuntimeDirectory();
      db = zeebeDbFactory.createDb(runtimeDirectory);
      LOG.debug("Opened database from '{}'.", runtimeDirectory.toPath());
    }

    return db;
  }

  @Override
  public void onNewValidSnapshot() {
    try {
      ensureMaxSnapshotCount();
    } catch (IOException e) {
      LOG.error(ERROR_MSG_ENSURING_MAX_SNAPSHOT_COUNT, e);
    }

    if (getValidSnapshotsCount() >= maxSnapshotCount) {
      deletionService.delete(getPositionToDelete(maxSnapshotCount));
    }
  }

  public void ensureMaxSnapshotCount() throws IOException {
    final List<File> snapshots = storage.listByPositionAsc();
    if (snapshots.size() > maxSnapshotCount) {
      final int oldestValidSnapshotIndex = snapshots.size() - maxSnapshotCount;
      LOG.debug(
          "Ensure max snapshot count {}, will delete {} snapshot(s).",
          maxSnapshotCount,
          oldestValidSnapshotIndex);

      final List<File> snapshotsToRemove = snapshots.subList(0, oldestValidSnapshotIndex);

      for (final File snapshot : snapshotsToRemove) {
        FileUtil.deleteFolder(snapshot.toPath());
        LOG.debug("Purged snapshot {}", snapshot);
      }

      cleanUpTemporarySnapshots(snapshots, oldestValidSnapshotIndex);
    } else {
      LOG.debug(
          "Tried to ensure max snapshot count {}, nothing to do snapshot count is {}.",
          maxSnapshotCount,
          snapshots.size());
    }
  }

  /**
   * Removes orphaned snapshots, where the replication was not completed successfully. Uses the
   * oldest valid snapshot position to decide which snapshot can be removed.
   *
   * @param snapshots the list of valid snapshot directories
   * @param oldestValidSnapshotIndex the index of the oldest valid snapshot
   * @throws IOException can be thrown on deletion
   */
  private void cleanUpTemporarySnapshots(List<File> snapshots, int oldestValidSnapshotIndex)
      throws IOException {
    final File oldestValidSnapshot = snapshots.get(oldestValidSnapshotIndex);
    final long oldestValidSnapshotPosition = Long.parseLong(oldestValidSnapshot.getName());
    LOG.debug(
        "Search for orphaned snapshots below oldest valid snapshot position {}",
        oldestValidSnapshotPosition);

    final List<File> tmpDirectoriesBelowPosition =
        storage.findTmpDirectoriesBelowPosition(oldestValidSnapshotPosition);
    for (final File notCompletedSnapshot : tmpDirectoriesBelowPosition) {
      FileUtil.deleteFolder(notCompletedSnapshot.toPath());
      LOG.debug("Delete not completed (orphaned) snapshot {}", notCompletedSnapshot);
    }
  }

  public long getPositionToDelete(int maxSnapshotCount) {
    return storage.listByPositionDesc().stream()
        .skip(maxSnapshotCount - 1)
        .findFirst()
        .map(f -> Long.parseLong(f.getName()))
        .orElse(-1L);
  }

  @Override
  public int getValidSnapshotsCount() {
    return storage.list().size();
  }

  @Override
  public long getLastValidSnapshotPosition() {
    return storage.listByPositionDesc().stream()
        .map(File::getName)
        .mapToLong(Long::parseLong)
        .findFirst()
        .orElse(-1L);
  }

  @Override
  public File getLastValidSnapshotDirectory() {
    final List<File> snapshots = storage.listByPositionDesc();
    if (snapshots != null) {
      return snapshots.get(0);
    }
    return null;
  }

  @Override
  public File getSnapshotDirectoryFor(long snapshotId) {
    return storage.getSnapshotDirectoryFor(snapshotId);
  }

  @Override
  public void close() throws Exception {
    if (db != null) {
      db.close();
      final File runtimeDirectory = storage.getRuntimeDirectory();
      LOG.debug("Closed database from '{}'.", runtimeDirectory.toPath());
      db = null;
    }
  }

  public boolean isDbOpened() {
    return db != null;
  }
}
