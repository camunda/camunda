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
import io.zeebe.logstreams.state.SnapshotMetadata;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;

/** Handles how snapshots/databases are stored on the file system. */
public class RocksDBStorage {
  private static final Logger LOG = Loggers.ROCKSDB_LOGGER;

  private static final String SEPARATOR = "_";
  private final File runtimeDirectory;
  private final File snapshotsDirectory;

  public RocksDBStorage(final File runtimeDirectory, final File snapshotsDirectory) {
    this.runtimeDirectory = runtimeDirectory;
    this.snapshotsDirectory = snapshotsDirectory;
  }

  public File getRuntimeDirectory() {
    return runtimeDirectory;
  }

  public File getSnapshotsDirectory() {
    return snapshotsDirectory;
  }

  public File getSnapshotDirectoryFor(final SnapshotMetadata metadata) {
    if (metadata == null) {
      throw new NullPointerException();
    }

    final String path =
        String.format(
            "%d%s%d%s%d",
            metadata.getLastSuccessfulProcessedEventPosition(),
            SEPARATOR,
            metadata.getLastWrittenEventPosition(),
            SEPARATOR,
            metadata.getLastWrittenEventTerm());

    return new File(snapshotsDirectory, path);
  }

  public SnapshotMetadata getSnapshotMetadata(final File folder) {
    if (folder == null) {
      throw new NullPointerException();
    }

    if (folder.exists() && !folder.isDirectory()) {
      throw new IllegalArgumentException("given file is not a directory");
    }

    final String name = folder.getName();
    final String[] parts = name.split(SEPARATOR, 3);

    return new SnapshotMetadata(
        Long.parseLong(parts[0]),
        Long.parseLong(parts[1]),
        Integer.parseInt(parts[2]),
        folder.exists());
  }

  public List<SnapshotMetadata> list() {
    return list(s -> true);
  }

  public List<SnapshotMetadata> listRecoverable(long lastSuccessfulProcessedEventPosition) {
    return list(s -> s.getLastWrittenEventPosition() <= lastSuccessfulProcessedEventPosition);
  }

  public List<SnapshotMetadata> list(Predicate<SnapshotMetadata> filter) {
    final File[] snapshotFolders = snapshotsDirectory.listFiles();
    final List<SnapshotMetadata> snapshots = new ArrayList<>();

    if (snapshotFolders == null || snapshotFolders.length == 0) {
      return snapshots;
    }

    for (final File folder : snapshotFolders) {
      if (folder.isDirectory()) {
        try {
          final SnapshotMetadata metadata = getSnapshotMetadata(folder);

          if (filter.test(metadata)) {
            snapshots.add(metadata);
          }
        } catch (final Exception ex) {
          LOG.warn("error listing snapshot {}", folder.getAbsolutePath());
        }
      }
    }

    return snapshots;
  }
}
