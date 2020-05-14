/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.atomix.raft.impl.zeebe.snapshot.DbSnapshot;
import io.atomix.raft.impl.zeebe.snapshot.DbSnapshotId;
import io.atomix.raft.impl.zeebe.snapshot.DbSnapshotStore;
import io.atomix.raft.storage.snapshot.Snapshot;
import io.atomix.raft.storage.snapshot.SnapshotListener;
import io.atomix.raft.storage.snapshot.SnapshotStore;
import io.atomix.utils.time.WallClockTimestamp;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.agrona.IoUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DbSnapshotStoreTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path snapshotsDirectory;
  private Path pendingDirectory;
  private DbSnapshotStore store;

  @Before
  public void setUp() throws Exception {
    snapshotsDirectory = temporaryFolder.newFolder("snapshots").toPath();
    pendingDirectory = temporaryFolder.newFolder("pending").toPath();
  }

  @After
  public void tearDown() {
    Optional.ofNullable(store).ifPresent(SnapshotStore::close);
  }

  @Test
  public void shouldNotifyListenersOnNewSnapshot() {
    // given
    final var directory = pendingDirectory.resolve("1-1-1-1");
    final var listener = mock(SnapshotListener.class);
    final var store = newStore(new ConcurrentSkipListMap<>());
    store.addListener(listener);
    IoUtil.ensureDirectoryExists(directory.toFile(), "snapshot directory");

    // when
    final var snapshot = store.newSnapshot(1, 1, WallClockTimestamp.from(1), directory);

    // then
    verify(listener, times(1)).onNewSnapshot(eq(snapshot), eq(store));
  }

  @Test
  public void shouldDeleteStore() {
    // given
    final var store = newStore(new ConcurrentSkipListMap<>());

    // when
    store.delete();

    // then
    assertThat(pendingDirectory).doesNotExist();
    assertThat(snapshotsDirectory).doesNotExist();
  }

  @Test
  public void shouldReturnExistingIfSnapshotAlreadyExists() {
    // given
    final var directory = pendingDirectory.resolve("1-1-1-1");
    final var store = newStore(new ConcurrentSkipListMap<>());
    IoUtil.ensureDirectoryExists(directory.toFile(), "snapshot directory");

    // when
    final var snapshot = store.newSnapshot(1, 1, WallClockTimestamp.from(1), directory);
    final var existing =
        store.newSnapshot(
            snapshot.index(),
            snapshot.term(),
            snapshot.timestamp(),
            pendingDirectory.resolve("1-1-1-1"));

    // then
    assertThat(existing).isEqualTo(snapshot);
  }

  @Test
  public void shouldDeleteOrphanedPendingSnapshots() throws IOException {
    // given
    final var pendingSnapshots =
        List.of(pendingDirectory.resolve("1-1-1"), pendingDirectory.resolve("2-2-2"));
    final var store = newStore(new ConcurrentSkipListMap<>());
    pendingSnapshots.forEach(p -> IoUtil.ensureDirectoryExists(p.toFile(), ""));

    // when
    store.purgePendingSnapshots();

    // then
    pendingSnapshots.forEach(p -> assertThat(p).doesNotExist());
  }

  @Test
  public void shouldDeleteOlderSnapshotsOnPurge() {
    // given
    final var toDeleteDirectory = pendingDirectory.resolve("1-1-1-1");
    final var snapshotDirectory = pendingDirectory.resolve("2-2-2-2");
    final var store = newStore(new ConcurrentSkipListMap<>());
    IoUtil.ensureDirectoryExists(toDeleteDirectory.toFile(), "snapshot directory");
    IoUtil.ensureDirectoryExists(snapshotDirectory.toFile(), "snapshot directory");

    // when
    store.newSnapshot(1, 1, WallClockTimestamp.from(1), toDeleteDirectory);
    final var snapshot = store.newSnapshot(2, 2, WallClockTimestamp.from(2), snapshotDirectory);
    store.purgeSnapshots(snapshot);

    // then
    assertThat(toDeleteDirectory).doesNotExist();
    assertThat(snapshot.getPath()).exists();
    assertThat((Collection<Snapshot>) store.getSnapshots()).containsOnly(snapshot);
  }

  private DbSnapshotStore newStore(
      final ConcurrentNavigableMap<DbSnapshotId, DbSnapshot> snapshots) {
    store = new DbSnapshotStore(snapshotsDirectory, pendingDirectory, snapshots);
    return store;
  }
}
