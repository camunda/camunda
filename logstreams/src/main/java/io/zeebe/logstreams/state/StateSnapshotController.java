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
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;

/** Controls how snapshot/recovery operations are performed */
public class StateSnapshotController implements SnapshotController {
  private static final Logger LOG = Loggers.ROCKSDB_LOGGER;

  private final StateStorage storage;
  private final ZeebeDbFactory zeebeDbFactory;
  private ZeebeDb db;

  public StateSnapshotController(final ZeebeDbFactory rocksDbFactory, final StateStorage storage) {
    zeebeDbFactory = rocksDbFactory;
    this.storage = storage;
  }

  @Override
  public void takeSnapshot(final StateSnapshotMetadata metadata) {
    if (db == null) {
      throw new IllegalStateException("Cannot create snapshot of not open database.");
    }

    if (exists(metadata)) {
      return;
    }

    final File snapshotDir = storage.getSnapshotDirectoryFor(metadata);
    db.createSnapshot(snapshotDir);
  }

  @Override
  public StateSnapshotMetadata recover(
      long commitPosition, int term, Predicate<StateSnapshotMetadata> filter) throws Exception {
    final List<StateSnapshotMetadata> snapshots = storage.listRecoverable(commitPosition);
    return extractMostRecentSnapshot(snapshots, term, filter);
  }

  @Override
  public StateSnapshotMetadata recoverFromLatestSnapshot() throws Exception {
    return extractMostRecentSnapshot(storage.list(), 0, m -> true);
  }

  private StateSnapshotMetadata extractMostRecentSnapshot(
      List<StateSnapshotMetadata> snapshots, int term, Predicate<StateSnapshotMetadata> filter)
      throws Exception {
    final File runtimeDirectory = storage.getRuntimeDirectory();
    StateSnapshotMetadata recoveredMetadata = null;

    if (!snapshots.isEmpty()) {
      recoveredMetadata =
          snapshots.stream()
              .sorted(Comparator.reverseOrder())
              .filter(filter)
              .findFirst()
              .orElse(null);
    }

    if (runtimeDirectory.exists()) {
      FileUtil.deleteFolder(runtimeDirectory.getAbsolutePath());
    }

    if (recoveredMetadata != null) {
      final File snapshotPath = storage.getSnapshotDirectoryFor(recoveredMetadata);
      FileUtil.copySnapshot(runtimeDirectory, snapshotPath);
    } else {
      recoveredMetadata = StateSnapshotMetadata.createInitial(term);
    }

    return recoveredMetadata;
  }

  @Override
  public ZeebeDb openDb() {
    db = zeebeDbFactory.createDb(storage.getRuntimeDirectory());
    return db;
  }

  @Override
  public void purgeAll(Predicate<StateSnapshotMetadata> matcher) throws Exception {
    final List<StateSnapshotMetadata> others = storage.list(matcher);

    for (final StateSnapshotMetadata other : others) {
      FileUtil.deleteFolder(storage.getSnapshotDirectoryFor(other).getAbsolutePath());
      LOG.trace("Purged snapshot {}", other);
    }
  }

  private boolean exists(final StateSnapshotMetadata metadata) {
    return storage.getSnapshotDirectoryFor(metadata).exists();
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
