/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.RaftStorage;
import io.atomix.protocols.raft.storage.snapshot.SnapshotStore;
import io.atomix.protocols.raft.storage.snapshot.SnapshotStoreFactory;
import io.atomix.utils.time.WallClockTimestamp;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.agrona.IoUtil;
import org.agrona.collections.Long2ObjectHashMap;

/** Loads existing snapshots in memory, cleaning out old and/or invalid snapshots if present. */
public class DbSnapshotStoreFactory implements SnapshotStoreFactory {
  static final String SNAPSHOTS_DIRECTORY = "snapshots";
  static final String PENDING_DIRECTORY = "pending";

  @Override
  public SnapshotStore createSnapshotStore(final RaftStorage storage) {
    final var root = storage.directory().toPath();
    final var snapshots = new Long2ObjectHashMap<DbSnapshot>();
    final var snapshotDirectory = root.resolve(SNAPSHOTS_DIRECTORY);
    final var pendingDirectory = root.resolve(PENDING_DIRECTORY);

    IoUtil.ensureDirectoryExists(snapshotDirectory.toFile(), "Snapshot directory");
    IoUtil.ensureDirectoryExists(pendingDirectory.toFile(), "Pending snapshot directory");

    try (var stream =
        Files.newDirectoryStream(
            snapshotDirectory, path -> path.endsWith(DbSnapshotStore.SNAPSHOT_EXTENSION))) {
      for (final var path : stream) {
        final var metadata = parseMetadata(path);
        snapshots.put(metadata.getIndex(), new DbSnapshot(path, metadata));
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return new DbSnapshotStore(snapshotDirectory, pendingDirectory, snapshots);
  }

  /**
   * Parses snapshot metadata from the directory name, expecting a given format.
   *
   * <p>TODO: should we remove snapshots which cannot parse, or just output a warning?
   *
   * @param path path to a potential snapshot directory
   * @return parsed snapshot metadata
   */
  private DbSnapshotMetadata parseMetadata(final Path path) {
    final var filename =
        path.getFileName().toString().substring(0, DbSnapshotStore.SNAPSHOT_EXTENSION.length());
    final var parts = filename.split("-", 4);
    // TODO: better error handling
    return new DbSnapshotMetadata(
        Long.parseLong(parts[0]),
        Long.parseLong(parts[1]),
        new WallClockTimestamp(Long.parseLong(parts[2])),
        Long.parseLong(parts[3]));
  }
}
