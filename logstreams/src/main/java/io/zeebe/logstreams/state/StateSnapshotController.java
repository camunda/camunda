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
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;

/** Controls how snapshot/recovery operations are performed */
public class StateSnapshotController implements SnapshotController {
  private static final Logger LOG = Loggers.ROCKSDB_LOGGER;

  private final StateStorage storage;
  private final ZeebeDbFactory zeebeDbFactory;
  private ZeebeDb db;

  public StateSnapshotController(final ZeebeDbFactory rocksDbFactory, final StateStorage storage) {
    this.zeebeDbFactory = rocksDbFactory;
    this.storage = storage;
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
  }

  @Override
  public long recover() throws Exception {
    final List<File> snapshots = storage.list();
    return extractMostRecentSnapshot(snapshots);
  }

  private long extractMostRecentSnapshot(List<File> snapshots) throws Exception {
    final File runtimeDirectory = storage.getRuntimeDirectory();
    File snapshotDirectory = null;

    if (!snapshots.isEmpty()) {
      snapshotDirectory = snapshots.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    if (runtimeDirectory.exists()) {
      FileUtil.deleteFolder(runtimeDirectory.getAbsolutePath());
    }

    long lowerBoundSnapshotPosition = -1;
    if (snapshotDirectory != null) {
      lowerBoundSnapshotPosition = Long.parseLong(snapshotDirectory.getName());
      FileUtil.copySnapshot(runtimeDirectory, snapshotDirectory);
    }

    return lowerBoundSnapshotPosition;
  }

  @Override
  public ZeebeDb openDb() {
    db = zeebeDbFactory.createDb(storage.getRuntimeDirectory());
    return db;
  }

  @Override
  public void ensureMaxSnapshotCount(int maxSnapshotCount) throws Exception {
    final List<String> snapshots = storage.listSorted();
    if (snapshots.size() > maxSnapshotCount) {
      LOG.debug(
          "Ensure max snapshot count {}, will delete {} snapshot(s).",
          maxSnapshotCount,
          snapshots.size() - maxSnapshotCount);

      final List<String> snapshotsToRemove =
          snapshots.subList(0, snapshots.size() - maxSnapshotCount);

      for (final String snapshot : snapshotsToRemove) {
        FileUtil.deleteFolder(snapshot);
        LOG.debug("Purged snapshot {}", snapshot);
      }
    } else {
      LOG.debug(
          "Tried to ensure max snapshot count {}, nothing to do snapshot count is {}.",
          maxSnapshotCount,
          snapshots.size());
    }
  }

  @Override
  public void close() throws Exception {
    if (db != null) {
      db.close();
      db = null;
    }
  }

  public boolean isDbOpened() {
    return db != null;
  }
}
