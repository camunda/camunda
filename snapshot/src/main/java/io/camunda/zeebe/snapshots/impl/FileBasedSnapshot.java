/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import io.zeebe.snapshots.PersistedSnapshot;
import io.zeebe.snapshots.SnapshotChunkReader;
import io.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBasedSnapshot implements PersistedSnapshot {
  // version currently hardcoded, could be used for backwards compatibility
  private static final int VERSION = 1;
  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedSnapshot.class);

  private final Path directory;
  private final Path checksumFile;
  private final long checksum;
  private final FileBasedSnapshotMetadata metadata;

  FileBasedSnapshot(
      final Path directory,
      final Path checksumFile,
      final long checksum,
      final FileBasedSnapshotMetadata metadata) {
    this.directory = directory;
    this.checksumFile = checksumFile;
    this.checksum = checksum;
    this.metadata = metadata;
  }

  public FileBasedSnapshotMetadata getMetadata() {
    return metadata;
  }

  public Path getDirectory() {
    return directory;
  }

  public Path getChecksumFile() {
    return checksumFile;
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
      return new FileBasedSnapshotChunkReader(directory, checksum);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void delete() {
    // the checksum, as a mark file, should be deleted first
    try {
      Files.deleteIfExists(checksumFile);
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete snapshot checksum file {}", checksumFile, e);
    }

    try {
      FileUtil.deleteFolderIfExists(directory);
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete snapshot {}", directory, e);
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
  public long getChecksum() {
    return checksum;
  }

  @Override
  public void close() {
    // nothing to be done
  }

  @Override
  public int hashCode() {
    int result = getDirectory().hashCode();
    result = 31 * result + checksumFile.hashCode();
    result = 31 * result + (int) (getChecksum() ^ (getChecksum() >>> 32));
    result = 31 * result + getMetadata().hashCode();
    return result;
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

    if (getChecksum() != that.getChecksum()) {
      return false;
    }
    if (!getDirectory().equals(that.getDirectory())) {
      return false;
    }
    if (!checksumFile.equals(that.checksumFile)) {
      return false;
    }
    return getMetadata().equals(that.getMetadata());
  }

  @Override
  public String toString() {
    return "FileBasedSnapshot{"
        + "directory="
        + directory
        + ", checksumFile="
        + checksumFile
        + ", checksum="
        + checksum
        + ", metadata="
        + metadata
        + '}';
  }
}
