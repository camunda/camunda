/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.storage.snapshot.SnapshotStore;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.zeebe.broker.clustering.atomix.storage.AtomixRecordEntrySupplier;
import io.zeebe.logstreams.state.Snapshot;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.logstreams.state.SnapshotMetrics;
import io.zeebe.logstreams.state.SnapshotStorage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import org.agrona.IoUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class AtomixSnapshotStorageTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path pendingDirectory;
  private SnapshotStore store;
  private AtomixSnapshotStorage snapshotStorage;
  private AtomixRecordEntrySupplier entrySupplier;

  @Before
  public void setUp() throws Exception {
    final var snapshotsDirectory = temporaryFolder.newFolder("snapshots").toPath();
    final var raftPendingDirectory = temporaryFolder.getRoot().toPath().resolve("pending");
    pendingDirectory = temporaryFolder.newFolder("pushed-pending").toPath();
    entrySupplier = mock(AtomixRecordEntrySupplier.class);
    store =
        new DbSnapshotStore(
            snapshotsDirectory, raftPendingDirectory, new ConcurrentSkipListMap<>());
  }

  @After
  public void tearDown() {
    Optional.ofNullable(snapshotStorage).ifPresent(SnapshotStorage::close);
    Optional.ofNullable(store).ifPresent(SnapshotStore::close);
  }

  @Test
  public void shouldGetPendingSnapshotForPositions() {
    // given
    final var storage = newStorage();
    when(entrySupplier.getIndexedEntry(1)).thenReturn(Optional.of(newEntry(1)));
    when(entrySupplier.getIndexedEntry(2)).thenReturn(Optional.of(newEntry(2)));

    // when
    final var first = storage.getPendingSnapshotFor(1).orElseThrow();
    final var second = storage.getPendingSnapshotFor(2).orElseThrow();

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
    final var storage = newStorage();
    when(entrySupplier.getIndexedEntry(1)).thenReturn(Optional.empty());

    // when
    final var snapshot = storage.getPendingSnapshotFor(1);

    // then
    assertThat(snapshot).isEmpty();
  }

  @Test
  public void shouldGetPendingDirectoryForId() {
    // given
    final var id = "1-1-1";
    final var storage = newStorage();

    // when
    final var directory = storage.getPendingDirectoryFor(id).orElseThrow();

    // then
    assertThat(directory).doesNotExist();
    assertThat(directory.getParent()).isEqualTo(pendingDirectory);
    assertThat(directory.getFileName().toString()).isEqualTo(id);
  }

  @Test
  public void shouldDeleteOrphanedPendingSnapshotsOnNewSnapshot() {
    // given
    final var storage = newStorage();
    final var toDelete = pendingDirectory.resolve("1-1-1");
    final var snapshotDirectory = pendingDirectory.resolve("2-2-2");
    final var toKeep = pendingDirectory.resolve("3-3-3");
    IoUtil.ensureDirectoryExists(toDelete.toFile(), "to delete directory");
    IoUtil.ensureDirectoryExists(snapshotDirectory.toFile(), "snapshot directory");
    IoUtil.ensureDirectoryExists(toKeep.toFile(), "to keep directory");

    // when
    storage.commitSnapshot(snapshotDirectory);

    // then
    assertThat(toDelete).doesNotExist();
    assertThat(toKeep).exists();
  }

  @Test
  public void shouldDeleteOrphanedPendingSnapshotsEvenIfOneIsNotASnapshot() {
    // given
    final var storage = newStorage();
    // given
    final var orphanedSnapshots =
        List.of(pendingDirectory.resolve("1-1-1"), pendingDirectory.resolve("2-2-2"));
    final var snapshotDirectory = pendingDirectory.resolve("3-3-3");
    final var evilFolder = pendingDirectory.resolve("not a snapshot");
    orphanedSnapshots.forEach(p -> IoUtil.ensureDirectoryExists(p.toFile(), ""));
    IoUtil.ensureDirectoryExists(evilFolder.toFile(), "not a snapshot folder");
    IoUtil.ensureDirectoryExists(snapshotDirectory.toFile(), "to keep directory");

    // when
    storage.commitSnapshot(snapshotDirectory);

    // then
    orphanedSnapshots.forEach(s -> assertThat(s).doesNotExist());
    assertThat(evilFolder).exists();
  }

  @Test
  public void shouldReturnEmptyIfIdIsNotMetadata() {
    // given
    final var id = "foo";
    final var storage = newStorage();

    // when
    final var directory = storage.getPendingDirectoryFor(id);

    // then
    assertThat(directory).isEmpty();
  }

  @Test
  public void shouldCommitPendingSnapshot() throws IOException {
    // given
    final var storage = newStorage();

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
    final var storage = newStorage();

    // when
    final var snapshot = newCommittedSnapshot(1);

    // then
    assertThat(storage.getLatestSnapshot())
        .isPresent()
        .map(Snapshot::getCompactionBound)
        .hasValue(snapshot.getCompactionBound());
  }

  @Test
  public void shouldNotifyDeletionListenersOnMaxSnapshotCount() throws IOException {
    // given
    final var listener = mock(SnapshotDeletionListener.class);
    final var storage = newStorage();
    storage.addDeletionListener(listener);

    // when the first snapshot then try to delete snapshots older than first
    final var first = newCommittedSnapshot(1);
    verify(listener).onSnapshotsDeleted(eq(first));

    // when the second snapshot then all snapshots up to that snapshot are deleted
    final var second = newCommittedSnapshot(2);
    verify(listener).onSnapshotsDeleted(eq(second));
    assertThat(storage.getSnapshots()).hasSize(1).containsExactly(second);
  }

  private Snapshot newPendingSnapshot(final long position) {
    when(entrySupplier.getIndexedEntry(position)).thenReturn(Optional.of(newEntry(position)));
    return snapshotStorage.getPendingSnapshotFor(position).orElseThrow();
  }

  private Snapshot newCommittedSnapshot(final long position) throws IOException {
    final var snapshot = newPendingSnapshot(position);
    Files.createDirectories(snapshot.getPath());
    snapshotStorage.commitSnapshot(snapshot.getPath());

    return snapshotStorage.getLatestSnapshot().orElseThrow();
  }

  private AtomixSnapshotStorage newStorage() {
    final var runtimeDirectory = temporaryFolder.getRoot().toPath().resolve("runtime");
    snapshotStorage =
        new AtomixSnapshotStorage(
            runtimeDirectory, pendingDirectory, store, entrySupplier, new SnapshotMetrics(0));
    return snapshotStorage;
  }

  private Indexed<ZeebeEntry> newEntry(final long index) {
    return new Indexed<>(
        index, new ZeebeEntry(1, System.currentTimeMillis(), 1, 1, ByteBuffer.allocate(1)), 0);
  }
}
