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
import io.atomix.raft.snapshot.SnapshotChunkReader;
import io.atomix.raft.snapshot.SnapshotId;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.util.FileUtil;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;

public final class FileBasedSnapshot implements PersistedSnapshot {
  // version currently hardcoded, could be used for backwards compatibility
  private static final int VERSION = 1;
  private static final Logger LOGGER = new ZbLogger(FileBasedSnapshot.class);

  private final Path directory;
  private final FileBasedSnapshotMetadata metadata;

  FileBasedSnapshot(final Path directory, final FileBasedSnapshotMetadata metadata) {
    this.directory = directory;
    this.metadata = metadata;
  }

  public FileBasedSnapshotMetadata getMetadata() {
    return metadata;
  }

  public Path getDirectory() {
    return directory;
  }

  @Override
  public WallClockTimestamp getTimestamp() {
    return metadata.getTimestamp();
  }

  @Override
  public int version() {
    return VERSION;
  }

  @Override
  public long getIndex() {
    return metadata.getIndex();
  }

  @Override
  public long getTerm() {
    return metadata.getTerm();
  }

  @Override
  public SnapshotChunkReader newChunkReader() {
    try {
      return new FileBasedSnapshotChunkReader(directory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() {
    // nothing to be done
  }

  @Override
  public void delete() {
    if (!Files.exists(directory)) {
      return;
    }

    try {
      FileUtil.deleteFolder(directory);
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete snapshot {}", this, e);
    }
  }

  @Override
  public Path getPath() {
    return getDirectory();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getDirectory(), getMetadata());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final FileBasedSnapshot that = (FileBasedSnapshot) o;
    return getDirectory().equals(that.getDirectory()) && getMetadata().equals(that.getMetadata());
  }

  @Override
  public String toString() {
    return "DbSnapshot{" + "directory=" + directory + ", metadata=" + metadata + '}';
  }

  @Override
  public long getCompactionBound() {
    return getIndex();
  }

  @Override
  public SnapshotId getId() {
    return metadata;
  }
}
