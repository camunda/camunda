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

import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.spi.SnapshotController;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.rocksdb.Checkpoint;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;

/** Controls how snapshot/recovery operations are performed on a StateController */
public class StateSnapshotController implements SnapshotController {
  private static final Logger LOG = Loggers.ROCKSDB_LOGGER;

  private final StateController controller;
  private final StateStorage storage;

  public StateSnapshotController(final StateController controller, final StateStorage storage) {
    this.controller = controller;
    this.storage = storage;
  }

  @Override
  public void takeSnapshot(final StateSnapshotMetadata metadata) throws Exception {
    if (!controller.isOpened()) {
      throw new IllegalStateException("cannot take snapshot of closed RocksDB");
    }

    if (exists(metadata)) {
      return;
    }

    final File snapshotDir = storage.getSnapshotDirectoryFor(metadata);
    try (Checkpoint checkpoint = Checkpoint.create(controller.getDb())) {
      checkpoint.createCheckpoint(snapshotDir.getAbsolutePath());
    }
  }

  @Override
  public StateSnapshotMetadata recover(
      long commitPosition, int term, Predicate<StateSnapshotMetadata> filter) throws Exception {
    final File runtimeDirectory = storage.getRuntimeDirectory();
    final List<StateSnapshotMetadata> snapshots = storage.listRecoverable(commitPosition);
    StateSnapshotMetadata recoveredMetadata = null;

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
      recoveredMetadata = StateSnapshotMetadata.createInitial(term);
      controller.open(runtimeDirectory, false);
    }

    return recoveredMetadata;
  }

  @Override
  public void purgeAll(Predicate<StateSnapshotMetadata> matcher) throws Exception {
    final List<StateSnapshotMetadata> others = storage.list(matcher);

    for (final StateSnapshotMetadata other : others) {
      controller.delete(storage.getSnapshotDirectoryFor(other));
      LOG.trace("Purged snapshot {}", other);
    }
  }

  private boolean exists(final StateSnapshotMetadata metadata) {
    return storage.getSnapshotDirectoryFor(metadata).exists();
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
