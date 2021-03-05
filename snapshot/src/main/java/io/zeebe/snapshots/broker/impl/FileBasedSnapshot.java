/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.broker.impl;

import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.snapshots.raft.PersistedSnapshot;
import io.zeebe.snapshots.raft.SnapshotChunkReader;
import io.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBasedSnapshot implements PersistedSnapshot {
  // version currently hardcoded, could be used for backwards compatibility
  private static final int VERSION = 1;
  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedSnapshot.class);

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
      return new FileBasedSnapshotChunkReader(directory, SnapshotChecksum.read(directory));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
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
  public long getCompactionBound() {
    return getIndex();
  }

  @Override
  public String getId() {
    return metadata.getSnapshotIdAsString();
  }

  @Override
  public void close() {
    // nothing to be done
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
    return "FileBasedSnapshot{" + "directory=" + directory + ", metadata=" + metadata + '}';
  }
}
