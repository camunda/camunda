/*
 * Copyright © 2020  camunda services GmbH (info@camunda.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package io.atomix.raft.snapshot.impl;

import io.atomix.raft.snapshot.PersistedSnapshot;
import io.atomix.raft.snapshot.TransientSnapshot;
import io.zeebe.util.FileUtil;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.slf4j.Logger;

/**
 * Represents a pending snapshot, that is a snapshot in the process of being written and has not yet
 * been committed to the store.
 */
public final class FileBasedTransientSnapshot implements TransientSnapshot {
  private static final Logger LOGGER = new ZbLogger(FileBasedTransientSnapshot.class);

  private final Path directory;
  private final FileBasedSnapshotStore snapshotStore;
  private final FileBasedSnapshotMetadata metadata;

  FileBasedTransientSnapshot(
      final FileBasedSnapshotMetadata metadata,
      final Path directory,
      final FileBasedSnapshotStore snapshotStore) {
    this.metadata = metadata;
    this.snapshotStore = snapshotStore;
    this.directory = directory;
  }

  @Override
  public boolean take(final Predicate<Path> takeSnapshot) {
    final var snapshotMetrics = snapshotStore.getSnapshotMetrics();
    boolean failed;

    try (final var ignored = snapshotMetrics.startTimer()) {
      try {
        failed = !takeSnapshot.test(getPath());
      } catch (final Exception exception) {
        LOGGER.warn("Catched unexpected exception on taking snapshot ({})", metadata, exception);
        failed = true;
      }
    }

    if (failed) {
      abort();
    }

    return !failed;
  }

  @Override
  public void abort() {
    try {
      LOGGER.debug("DELETE dir {}", directory);
      FileUtil.deleteFolder(directory);
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete pending snapshot {}", this, e);
    }
  }

  @Override
  public PersistedSnapshot persist() {
    return snapshotStore.newSnapshot(metadata, directory);
  }

  public Path getPath() {
    return directory;
  }

  @Override
  public String toString() {
    return "FileBasedTransientSnapshot{"
        + "directory="
        + directory
        + ", snapshotStore="
        + snapshotStore
        + ", metadata="
        + metadata
        + '}';
  }
}
