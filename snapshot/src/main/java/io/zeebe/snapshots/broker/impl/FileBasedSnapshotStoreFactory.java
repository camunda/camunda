/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.snapshots.broker.impl;

import io.zeebe.snapshots.broker.ConstructableSnapshotStore;
import io.zeebe.snapshots.broker.SnapshotStoreSupplier;
import io.zeebe.snapshots.raft.PersistedSnapshotStore;
import io.zeebe.snapshots.raft.ReceivableSnapshotStore;
import io.zeebe.snapshots.raft.ReceivableSnapshotStoreFactory;
import io.zeebe.util.sched.ActorScheduler;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.agrona.IoUtil;

/**
 * Loads existing snapshots in memory, cleaning out old and/or invalid snapshots if present.
 *
 * <p>The current load strategy is to lookup all files directly under the {@code
 * SNAPSHOTS_DIRECTORY}, try to extract {@link FileBasedSnapshotMetadata} from them, and if not
 * possible skip them (and print out a warning).
 *
 * <p>The metadata extraction is done by parsing the directory name using '%d-%d-%d-%d', where in
 * order we expect: index, term, timestamp, and position.
 */
public final class FileBasedSnapshotStoreFactory
    implements SnapshotStoreSupplier, ReceivableSnapshotStoreFactory {
  public static final String SNAPSHOTS_DIRECTORY = "snapshots";
  public static final String PENDING_DIRECTORY = "pending";

  private final Map<String, FileBasedSnapshotStore> partitionSnapshotStores = new HashMap();
  private final ActorScheduler actorScheduler;

  public FileBasedSnapshotStoreFactory(final ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
  }

  @Override
  public ReceivableSnapshotStore createReceivableSnapshotStore(
      final Path root, final String partitionName) {
    final var snapshotDirectory = root.resolve(SNAPSHOTS_DIRECTORY);
    final var pendingDirectory = root.resolve(PENDING_DIRECTORY);

    IoUtil.ensureDirectoryExists(snapshotDirectory.toFile(), "Snapshot directory");
    IoUtil.ensureDirectoryExists(pendingDirectory.toFile(), "Pending snapshot directory");

    return partitionSnapshotStores.computeIfAbsent(
        partitionName,
        p -> createAndOpenNewSnapshotStore(partitionName, snapshotDirectory, pendingDirectory));
  }

  private FileBasedSnapshotStore createAndOpenNewSnapshotStore(
      final String partitionName, final Path snapshotDirectory, final Path pendingDirectory) {
    final var snapshotStore =
        new FileBasedSnapshotStore(
            new SnapshotMetrics(partitionName), snapshotDirectory, pendingDirectory);
    actorScheduler.submitActor(snapshotStore).join();
    return snapshotStore;
  }

  @Override
  public ConstructableSnapshotStore getConstructableSnapshotStore(final String partitionName) {
    return partitionSnapshotStores.get(partitionName);
  }

  @Override
  public ReceivableSnapshotStore getReceivableSnapshotStore(final String partitionName) {
    return partitionSnapshotStores.get(partitionName);
  }

  @Override
  public PersistedSnapshotStore getPersistedSnapshotStore(final String partitionName) {
    return partitionSnapshotStores.get(partitionName);
  }
}
