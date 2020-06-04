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
import static org.mockito.Mockito.verify;

import io.atomix.raft.impl.zeebe.snapshot.AtomixRecordEntrySupplier;
import io.atomix.raft.impl.zeebe.snapshot.AtomixSnapshotStorage;
import io.atomix.raft.impl.zeebe.snapshot.DbSnapshotStore;
import io.atomix.raft.impl.zeebe.snapshot.Snapshot;
import io.atomix.raft.impl.zeebe.snapshot.SnapshotDeletionListener;
import io.atomix.raft.impl.zeebe.snapshot.SnapshotMetrics;
import io.atomix.raft.impl.zeebe.snapshot.SnapshotStorage;
import io.atomix.raft.storage.snapshot.SnapshotStore;
import io.atomix.storage.journal.JournalReader.Mode;
import io.zeebe.logstreams.storage.atomix.AtomixRecordEntrySupplierImpl;
import io.zeebe.logstreams.util.AtomixLogStorageRule;
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
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class AtomixSnapshotStorageTest {

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final AtomixLogStorageRule logStorageRule = new AtomixLogStorageRule(temporaryFolder);
  @Rule public final RuleChain chain = RuleChain.outerRule(temporaryFolder).around(logStorageRule);

  private Path pendingDirectory;
  private SnapshotStore store;
  private AtomixSnapshotStorage snapshotStorage;
  private AtomixRecordEntrySupplier entrySupplier;

  @Before
  public void setUp() throws Exception {
    final var snapshotsDirectory = temporaryFolder.newFolder("snapshots").toPath();
    final var raftPendingDirectory = temporaryFolder.getRoot().toPath().resolve("pending");
    pendingDirectory = temporaryFolder.newFolder("pushed-pending").toPath();
    entrySupplier =
        new AtomixRecordEntrySupplierImpl(
            logStorageRule.getIndexMapping(),
            logStorageRule.getRaftLog().openReader(-1, Mode.COMMITS));
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
  public void shouldNotGetPendingSnapshotForNegativePosition() {
    // given
    final var storage = newStorage();
    logStorageRule.appendEntry(ByteBuffer.allocate(1));

    // when
    final var snapshot = storage.getPendingSnapshotFor(-1);
    // then
    assertThat(snapshot).isEmpty();
  }

  @Test
  public void shouldGetPendingSnapshotForPositions() {
    // given
    final var storage = newStorage();
    logStorageRule.appendEntry(ByteBuffer.allocate(1));
    logStorageRule.appendEntry(ByteBuffer.allocate(1));
    logStorageRule.appendEntry(ByteBuffer.allocate(1));

    // when
    final var first = storage.getPendingSnapshotFor(2).orElseThrow();
    final var second = storage.getPendingSnapshotFor(3).orElseThrow();

    // then
    assertThat(first.getPath()).doesNotExist().hasParentRaw(pendingDirectory);
    assertThat(second.getPath()).doesNotExist().hasParentRaw(pendingDirectory);
    assertThat(first.getPath()).isNotEqualTo(second.getPath());
  }

  @Test
  public void shouldReturnNullIfNoEntryForPosition() {
    // given
    final var storage = newStorage();
    logStorageRule.appendEntry(ByteBuffer.allocate(1));

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
    assertThat(directory).doesNotExist().hasParentRaw(pendingDirectory);
    assertThat(directory.getFileName()).hasToString(id);
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
    final var snapshot = newPendingSnapshot(2);
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

  @Test
  public void shouldNotCreatePendingSnapshotIfSnapshotExistsForIndex() throws IOException {
    // given
    final var storage = newStorage();
    logStorageRule.appendEntry(ByteBuffer.allocate(1));
    logStorageRule.appendEntry(ByteBuffer.allocate(1));
    final var snapshot = storage.getPendingSnapshotFor(3).orElseThrow();
    Files.createDirectories(snapshot.getPath());
    storage.commitSnapshot(snapshot.getPath()).orElseThrow();

    // when
    final var newSnapshot = storage.getPendingSnapshotFor(3);

    // then
    assertThat(newSnapshot).isEmpty();
  }

  private Snapshot newPendingSnapshot(final long position) {
    logStorageRule.appendEntry(ByteBuffer.allocate(1));
    logStorageRule.appendEntry(ByteBuffer.allocate(1));
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
}
