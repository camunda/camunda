/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.protocols.raft.storage.snapshot.SnapshotStore;
import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.zeebe.broker.clustering.atomix.storage.AtomixRecordEntrySupplier;
import io.zeebe.logstreams.state.Snapshot;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.logstreams.state.SnapshotStorage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AtomixSnapshotStorageTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path snapshotsDirectory;
  private Path pendingDirectory;
  private SnapshotStore store;
  private AtomixSnapshotStorage storage;
  private AtomixRecordEntrySupplier entrySupplier;

  @Before
  public void setUp() throws Exception {
    snapshotsDirectory = temporaryFolder.newFolder("snapshots").toPath();
    pendingDirectory = temporaryFolder.newFolder("pending").toPath();
    entrySupplier = mock(AtomixRecordEntrySupplier.class);
    store =
        new DbSnapshotStore(snapshotsDirectory, pendingDirectory, new ConcurrentSkipListMap<>());
  }

  @After
  public void tearDown() throws Exception {
    Optional.ofNullable(storage).ifPresent(SnapshotStorage::close);
    Optional.ofNullable(store).ifPresent(SnapshotStore::close);
  }

  @Test
  public void shouldGetPendingSnapshotForPositions() {
    // given
    final var storage = newStorage(1);
    when(entrySupplier.getIndexedEntry(1)).thenReturn(Optional.of(newEntry(1)));
    when(entrySupplier.getIndexedEntry(2)).thenReturn(Optional.of(newEntry(2)));

    // when
    final var first = storage.getPendingSnapshotFor(1);
    final var second = storage.getPendingSnapshotFor(2);

    // then
    assertThat(first.getPath()).doesNotExist();
    assertThat(first.getPath().getParent()).isEqualTo(pendingDirectory);
    assertThat(second.getPath()).doesNotExist();
    assertThat(second.getPath().getParent()).isEqualTo(pendingDirectory);
    assertThat(first.getPath()).isNotEqualTo(second.getPath());
  }

  @Test
  public void shouldReturnNullIfNoEntryForPosition() {
    // given
    final var storage = newStorage(1);
    when(entrySupplier.getIndexedEntry(1)).thenReturn(Optional.empty());

    // when
    final var snapshot = storage.getPendingSnapshotFor(1);

    // then
    assertThat(snapshot).isNull();
  }

  @Test
  public void shouldGetPendingDirectoryForId() {
    // given
    final var id = "1-1-1-1";
    final var storage = newStorage(1);

    // when
    final var directory = storage.getPendingDirectoryFor(id);

    // then
    assertThat(directory).doesNotExist();
    assertThat(directory.getParent()).isEqualTo(pendingDirectory);
    assertThat(directory.getFileName().toString()).isEqualTo(id);
  }

  @Test
  public void shouldReturnNullIfIdIsNotMetadata() {
    // given
    final var id = "foo";
    final var storage = newStorage(1);

    // when
    final var directory = storage.getPendingDirectoryFor(id);

    // then
    assertThat(directory).isNull();
  }

  @Test
  public void shouldCommitPendingSnapshot() throws IOException {
    // given
    final var storage = newStorage(1);

    // when
    final var snapshot = newPendingSnapshot(1);
    Files.createDirectories(snapshot.getPath());
    storage.commitSnapshot(snapshot.getPath());

    // then
    assertThat(store.getSnapshots()).hasSize(1);
    assertThat(store.getCurrentSnapshotIndex()).isEqualTo(1);
    assertThat(store.getSnapshot(1))
        .extracting(s -> s.getPath().getFileName())
        .isEqualTo(snapshot.getPath().getFileName());
  }

  @Test
  public void shouldGetLatestSnapshot() throws IOException {
    // given
    final var storage = newStorage(1);

    // when
    final var snapshot = newCommittedSnapshot(1);

    // then
    assertThat(storage.getLatestSnapshot())
        .isPresent()
        .map(Snapshot::getPosition)
        .hasValue(snapshot.getPosition());
  }

  @Test
  public void shouldNotifyDeletionListenersOnMaxSnapshotCount() throws IOException {
    // given
    final var maxCount = 2;
    final var listener = mock(SnapshotDeletionListener.class);
    final var storage = newStorage(maxCount);
    storage.addDeletionListener(listener);

    // when - then
    final var first = newCommittedSnapshot(1);
    verify(listener, never()).onSnapshotDeleted(any());

    // when - then
    final var second = newCommittedSnapshot(2);
    verify(listener, times(1)).onSnapshotDeleted(eq(first));
    assertThat(storage.getSnapshots()).hasSize(maxCount).containsExactly(first, second);
  }

  private Snapshot newPendingSnapshot(final long position) {
    when(entrySupplier.getIndexedEntry(position)).thenReturn(Optional.of(newEntry(position)));
    return storage.getPendingSnapshotFor(position);
  }

  private Snapshot newCommittedSnapshot(final long position) throws IOException {
    final var snapshot = newPendingSnapshot(position);
    Files.createDirectories(snapshot.getPath());
    storage.commitSnapshot(snapshot.getPath());

    return storage.getLatestSnapshot().get();
  }

  private AtomixSnapshotStorage newStorage(final int maxSnapshotsCount) {
    final var runtimeDirectory = temporaryFolder.getRoot().toPath().resolve("runtime");
    storage = new AtomixSnapshotStorage(runtimeDirectory, store, entrySupplier, maxSnapshotsCount);
    return storage;
  }

  private Indexed<ZeebeEntry> newEntry(final long index) {
    return new Indexed<>(
        index, new ZeebeEntry(1, System.currentTimeMillis(), 1, 1, ByteBuffer.allocate(1)), 0);
  }
}
