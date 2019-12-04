/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.protocols.raft.storage.snapshot.Snapshot;
import io.atomix.utils.time.WallClockTimestamp;
import java.util.ArrayList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DbSnapshotStoreFactoryTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void shouldCreateDirectoriesIfNotExist() {
    // given
    final var root = temporaryFolder.getRoot().toPath();
    final var factory = new DbSnapshotStoreFactory();

    // when
    final var store = factory.createSnapshotStore(root, "ignored");

    // then
    assertThat(root.resolve(DbSnapshotStoreFactory.SNAPSHOTS_DIRECTORY)).exists().isDirectory();
    assertThat(root.resolve(DbSnapshotStoreFactory.PENDING_DIRECTORY)).exists().isDirectory();
    assertThat(store.getSnapshots()).isEmpty();
  }

  @Test
  public void shouldLoadExistingSnapshots() {
    // given
    final var root = temporaryFolder.getRoot().toPath();
    final var factory = new DbSnapshotStoreFactory();
    final var originalStore = (DbSnapshotStore) factory.createSnapshotStore(root, "ignored");
    final var firstSnapshot = newCommittedSnapshot(originalStore, 1);
    final var secondSnapshot = newCommittedSnapshot(originalStore, 2);

    // when
    final var store = factory.createSnapshotStore(root, "ignored");

    // then
    final var snapshots = new ArrayList<Snapshot>(store.getSnapshots());
    assertThat(snapshots).hasSize(2).containsExactly(firstSnapshot, secondSnapshot);
  }

  private Snapshot newCommittedSnapshot(final DbSnapshotStore store, final long index) {
    final var directory =
        store
            .getPath()
            .resolveSibling(DbSnapshotStoreFactory.PENDING_DIRECTORY)
            .resolve(String.format("%d-1-1-1", index));
    store.newPendingSnapshot(index, 1, WallClockTimestamp.from(1), directory).commit();
    return store.getCurrentSnapshot();
  }
}
