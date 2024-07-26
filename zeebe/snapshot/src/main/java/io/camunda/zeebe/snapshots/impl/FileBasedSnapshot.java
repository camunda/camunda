/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotChunkReader;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.SnapshotMetadata;
import io.camunda.zeebe.snapshots.SnapshotReservation;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBasedSnapshot implements PersistedSnapshot {
  // version currently hardcoded, could be used for backwards compatibility
  private static final int VERSION = 1;
  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedSnapshot.class);

  private final Path directory;
  private final Path checksumFile;
  private final long checksum;
  private final FileBasedSnapshotId snapshotId;
  private final SnapshotMetadata metadata;
  private final Consumer<FileBasedSnapshot> onSnapshotDeleted;

  private final Set<FileBasedSnapshotReservation> reservations = new HashSet<>();
  private final ConcurrencyControl actor;

  private boolean deleted = false;

  FileBasedSnapshot(
      final Path directory,
      final Path checksumFile,
      final long checksum,
      final FileBasedSnapshotId snapshotId,
      final SnapshotMetadata metadata,
      final Consumer<FileBasedSnapshot> onSnapshotDeleted,
      final ConcurrencyControl actor) {
    this.directory = directory;
    this.checksumFile = checksumFile;
    this.checksum = checksum;
    this.snapshotId = snapshotId;
    this.metadata = metadata;
    this.onSnapshotDeleted = onSnapshotDeleted;
    this.actor = actor;
  }

  public FileBasedSnapshotId getSnapshotId() {
    return snapshotId;
  }

  public Path getDirectory() {
    return directory;
  }

  @Override
  public int version() {
    return VERSION;
  }

  @Override
  public long getIndex() {
    return snapshotId.getIndex();
  }

  @Override
  public long getTerm() {
    return snapshotId.getTerm();
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
  public Path getPath() {
    return getDirectory();
  }

  @Override
  public Path getChecksumPath() {
    return checksumFile;
  }

  @Override
  public long getCompactionBound() {
    return getIndex();
  }

  @Override
  public String getId() {
    return snapshotId.getSnapshotIdAsString();
  }

  @Override
  public long getChecksum() {
    return checksum;
  }

  @Override
  public SnapshotMetadata getMetadata() {
    return metadata;
  }

  @Override
  public ActorFuture<SnapshotReservation> reserve() {
    final CompletableActorFuture<SnapshotReservation> snapshotLocked =
        new CompletableActorFuture<>();
    actor.run(
        () -> {
          if (!deleted) {
            final FileBasedSnapshotReservation reservation = new FileBasedSnapshotReservation(this);
            reservations.add(reservation);
            snapshotLocked.complete(reservation);
          } else {
            snapshotLocked.completeExceptionally(
                new SnapshotNotFoundException(
                    String.format(
                        "Expected to reserve snapshot %s, but snapshot is deleted.", getId())));
          }
        });
    return snapshotLocked;
  }

  void delete() {
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

    deleted = true;
    onSnapshotDeleted.accept(this);
  }

  @Override
  public int hashCode() {
    int result = getDirectory().hashCode();
    result = 31 * result + checksumFile.hashCode();
    result = 31 * result + (int) (getChecksum() ^ (getChecksum() >>> 32));
    result = 31 * result + getSnapshotId().hashCode();
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
    return getSnapshotId().equals(that.getSnapshotId());
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
        + ", snapshotId="
        + snapshotId
        + ", metadata="
        + metadata
        + '}';
  }

  boolean isReserved() {
    return !reservations.isEmpty();
  }

  ActorFuture<Void> removeReservation(final FileBasedSnapshotReservation reservation) {
    return actor.call(
        () -> {
          reservations.remove(reservation);
          return null;
        });
  }
}
