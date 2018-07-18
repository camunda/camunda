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
package io.zeebe.logstreams.rocksdb;

import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.state.SnapshotController;
import io.zeebe.logstreams.state.SnapshotMetadata;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.rocksdb.Checkpoint;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;

/** Controls how snapshot/recovery operations are performed on a RocksDBController */
public class RocksDBSnapshotController implements SnapshotController {
  private static final Logger LOG = Loggers.ROCKSDB_LOGGER;

  private final RocksDBController controller;
  private final RocksDBStorage storage;

  public RocksDBSnapshotController(
      final RocksDBController controller, final RocksDBStorage storage) {
    this.controller = controller;
    this.storage = storage;
  }

  @Override
  public void takeSnapshot(final SnapshotMetadata metadata) throws Exception {
    if (!controller.isOpened()) {
      throw new IllegalStateException("cannot take snapshot of closed RocksDB");
    }

    final File snapshotDir = storage.getSnapshotDirectoryFor(metadata);
    try (Checkpoint checkpoint = Checkpoint.create(controller.getDb())) {
      checkpoint.createCheckpoint(snapshotDir.getAbsolutePath());
    }
  }

  @Override
  public SnapshotMetadata recover(long commitPosition, int term, Predicate<SnapshotMetadata> filter)
      throws Exception {
    final File runtimeDirectory = storage.getRuntimeDirectory();
    final List<SnapshotMetadata> snapshots = storage.listRecoverable(commitPosition);
    SnapshotMetadata recoveredMetadata = null;

    if (!snapshots.isEmpty()) {
      recoveredMetadata =
          snapshots
              .stream()
              .sorted(Comparator.reverseOrder())
              .filter(filter)
              .findFirst()
              .orElse(null);
    }

    if (runtimeDirectory.exists()) {
      controller.delete(runtimeDirectory);
    }

    if (recoveredMetadata != null) {
      final File snapshotPath = storage.getSnapshotDirectoryFor(recoveredMetadata);
      copySnapshot(runtimeDirectory, snapshotPath);

      controller.open(runtimeDirectory, true);
    } else {
      recoveredMetadata = SnapshotMetadata.createInitial(term);
      controller.open(runtimeDirectory, false);
    }

    return recoveredMetadata;
  }

  @Override
  public void purgeAllExcept(final SnapshotMetadata metadata) throws Exception {
    final List<SnapshotMetadata> others = storage.list(s -> !s.equals(metadata));

    for (final SnapshotMetadata other : others) {
      controller.delete(storage.getSnapshotDirectoryFor(other));
      LOG.trace("Purged snapshot {}", other);
    }
  }

  private void copySnapshot(File runtimeDirectory, File snapshotPath) throws Exception {
    try {
      final RocksDB snapshotDB = controller.open(snapshotPath, true);
      try (Checkpoint checkpoint = Checkpoint.create(snapshotDB)) {
        checkpoint.createCheckpoint(runtimeDirectory.getAbsolutePath());
      }
    } finally {
      controller.close();
    }
  }
}
