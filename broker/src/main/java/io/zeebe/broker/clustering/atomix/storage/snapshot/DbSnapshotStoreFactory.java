/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.SnapshotStore;
import io.atomix.protocols.raft.storage.snapshot.SnapshotStoreFactory;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.agrona.IoUtil;
import org.slf4j.Logger;

/**
 * Loads existing snapshots in memory, cleaning out old and/or invalid snapshots if present.
 *
 * <p>The current load strategy is to lookup all files directly under the {@code
 * SNAPSHOTS_DIRECTORY}, try to extract {@link DbSnapshotMetadata} from them, and if not possible
 * skip them (and print out a warning).
 *
 * <p>The metadata extraction is done by parsing the directory name using '%d-%d-%d-%d', where in
 * order we expect: index, term, timestamp, and position.
 */
public class DbSnapshotStoreFactory implements SnapshotStoreFactory {
  static final String SNAPSHOTS_DIRECTORY = "snapshots";
  static final String PENDING_DIRECTORY = "pending";
  private static final Logger LOGGER = new ZbLogger(DbSnapshotStoreFactory.class);

  private final int maxSnapshotCount;

  public DbSnapshotStoreFactory(final int maxSnapshotCount) {
    this.maxSnapshotCount = maxSnapshotCount;
  }

  @Override
  public SnapshotStore createSnapshotStore(final Path root, final String prefix) {
    final var snapshots = new ConcurrentSkipListMap<DbSnapshotId, DbSnapshot>();
    final var snapshotDirectory = root.resolve(SNAPSHOTS_DIRECTORY);
    final var pendingDirectory = root.resolve(PENDING_DIRECTORY);

    IoUtil.ensureDirectoryExists(snapshotDirectory.toFile(), "Snapshot directory");
    IoUtil.ensureDirectoryExists(pendingDirectory.toFile(), "Pending snapshot directory");

    loadSnapshots(snapshotDirectory, snapshots);

    return new DbSnapshotStore(snapshotDirectory, pendingDirectory, maxSnapshotCount, snapshots);
  }

  private void loadSnapshots(
      final Path snapshotDirectory, final NavigableMap<DbSnapshotId, DbSnapshot> snapshots) {
    try (var stream = Files.newDirectoryStream(snapshotDirectory)) {
      for (final var path : stream) {
        collectSnapshot(snapshots, path);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void collectSnapshot(
      final NavigableMap<DbSnapshotId, DbSnapshot> snapshots, final Path path) {
    final var maybeMeta = DbSnapshotMetadata.ofPath(path);
    if (maybeMeta.isPresent()) {
      final var metadata = maybeMeta.get();
      snapshots.put(metadata, new DbSnapshot(path, metadata));
    } else {
      LOGGER.warn("Expected snapshot file format to be %d-%d-%d-%d, but was {}", path);
    }
  }
}
