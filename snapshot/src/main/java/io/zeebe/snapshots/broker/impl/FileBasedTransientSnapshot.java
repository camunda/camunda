/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.snapshots.broker.impl;

import io.zeebe.snapshots.raft.PersistedSnapshot;
import io.zeebe.snapshots.raft.TransientSnapshot;
import io.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a pending snapshot, that is a snapshot in the process of being written and has not yet
 * been committed to the store.
 */
public final class FileBasedTransientSnapshot implements TransientSnapshot {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileBasedTransientSnapshot.class);

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
