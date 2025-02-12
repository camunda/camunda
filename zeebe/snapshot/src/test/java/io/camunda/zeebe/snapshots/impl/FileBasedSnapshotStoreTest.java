/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotNotFoundException;
import io.camunda.zeebe.snapshots.TestChecksumProvider;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.test.util.asserts.DirectoryAssert;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32C;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileBasedSnapshotStoreTest {
  private static final String SNAPSHOT_DIRECTORY = "snapshots";
  private static final String PENDING_DIRECTORY = "pending";

  private static final String SNAPSHOT_CONTENT_FILE_NAME = "file1.txt";
  private static final String SNAPSHOT_CONTENT = "this is the content";
  private static final Integer PARTITION_ID = 1;

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public ActorSchedulerRule scheduler = new ActorSchedulerRule();

  private Path snapshotsDir;
  private Path pendingSnapshotsDir;
  private FileBasedSnapshotStore snapshotStore;
  private Path rootDirectory;

  @Before
  public void before() throws IOException {
    rootDirectory = temporaryFolder.newFolder("snapshots").toPath();
    snapshotsDir = rootDirectory.resolve(SNAPSHOT_DIRECTORY);
    pendingSnapshotsDir = rootDirectory.resolve(PENDING_DIRECTORY);
    snapshotStore = createStore(rootDirectory);
  }

  @Test
  public void shouldCreateDirectoriesIfNotExist() {
    // given
    final var root = temporaryFolder.getRoot().toPath();

    // when
    final var store =
        new FileBasedSnapshotStore(1, root, snapshotPath -> Map.of(), new SimpleMeterRegistry());

    // then
    assertThat(root.resolve(FileBasedSnapshotStore.SNAPSHOTS_DIRECTORY)).exists().isDirectory();
    assertThat(root.resolve(FileBasedSnapshotStore.PENDING_DIRECTORY)).exists().isDirectory();
    assertThat(store.getLatestSnapshot()).isEmpty();
  }

  @Test
  public void shouldDeleteStore() {
    // given

    // when
    snapshotStore.delete().join();

    // then
    assertThat(pendingSnapshotsDir).doesNotExist();
    assertThat(snapshotsDir).doesNotExist();
  }

  @Test
  public void shouldLoadExistingSnapshot() throws IOException {
    // given
    final var persistedSnapshot = takeTransientSnapshot().persist().join();

    // when
    snapshotStore.close();
    snapshotStore = createStore(rootDirectory);

    // then
    assertThat(snapshotStore.getCurrentSnapshotIndex()).isEqualTo(1L);
    assertThat(snapshotStore.getLatestSnapshot()).hasValue(persistedSnapshot);
  }

  @Test
  public void shouldLoadExistingSnapshotWithMetadata() throws IOException {
    // given
    final var persistedSnapshot =
        takeTransientSnapshot().withLastFollowupEventPosition(100L).persist().join();

    // when
    snapshotStore.close();
    snapshotStore = createStore(rootDirectory);

    // then
    assertThat(snapshotStore.getCurrentSnapshotIndex()).isEqualTo(1L);
    assertThat(snapshotStore.getLatestSnapshot()).hasValue(persistedSnapshot);
    final var latestSnapshot = snapshotStore.getLatestSnapshot().orElseThrow();
    assertThat(latestSnapshot.getMetadata()).isEqualTo(persistedSnapshot.getMetadata());
  }

  @Test
  public void shouldLoadLatestSnapshotWhenMoreThanOneExistsAndDeleteOlder() throws IOException {
    // given
    final FileBasedSnapshotStore otherStore = createStore(rootDirectory);
    final var olderSnapshot = takeTransientSnapshot(1L, otherStore).persist().join();
    final var newerSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(2L, snapshotStore).persist().join();

    // when
    assertThat(snapshotsDir)
        .asInstanceOf(DirectoryAssert.factory())
        .as("ensure both the older and newer snapshots exist")
        .isDirectoryContainingAllOf(olderSnapshot.getPath(), newerSnapshot.getPath());
    snapshotStore.close();
    snapshotStore = createStore(rootDirectory);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).hasValue(newerSnapshot);
    assertThat(snapshotsDir)
        .asInstanceOf(DirectoryAssert.factory())
        .as("the older snapshots should have been deleted")
        .isDirectoryContainingExactly(newerSnapshot.getPath(), newerSnapshot.getChecksumPath());
  }

  @Test
  public void shouldNotLoadCorruptedSnapshot() throws Exception {
    // given
    final var persistedSnapshot = (FileBasedSnapshot) takeTransientSnapshot().persist().join();
    SnapshotChecksum.persist(persistedSnapshot.getChecksumPath(), new SfvChecksumImpl(0xCAFEL));

    // when
    snapshotStore.close();
    snapshotStore = createStore(rootDirectory);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).isEmpty();
  }

  @Test
  public void shouldDeleteSnapshotWithoutChecksumFile() throws IOException {
    // given
    final var persistedSnapshot = (FileBasedSnapshot) takeTransientSnapshot().persist().join();
    Files.delete(persistedSnapshot.getChecksumPath());

    // when
    snapshotStore.close();
    snapshotStore = createStore(rootDirectory);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).isEmpty();
    assertThat(persistedSnapshot.getDirectory()).doesNotExist();
  }

  @Test
  public void shouldDeleteOlderSnapshotsWithCorruptChecksums() throws IOException {
    // given
    final var otherStore = createStore(rootDirectory);
    final var corruptOlderSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(1, otherStore).persist().join();
    SnapshotChecksum.persist(corruptOlderSnapshot.getChecksumPath(), new SfvChecksumImpl(0xCAFEL));

    final var newerSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(2, snapshotStore).persist().join();

    // when
    snapshotStore.close();
    snapshotStore = createStore(rootDirectory);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).hasValue(newerSnapshot);
    assertThat(newerSnapshot.getDirectory()).exists();
    assertThat(newerSnapshot.getChecksumPath()).exists();
    assertThat(corruptOlderSnapshot.getDirectory()).doesNotExist();
    assertThat(corruptOlderSnapshot.getChecksumPath()).doesNotExist();
  }

  @Test
  public void shouldDeleteOlderSnapshotsWithMissingChecksums() throws IOException {
    // given
    final var otherStore = createStore(rootDirectory);
    final var corruptOlderSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(1, otherStore).persist().join();
    Files.delete(corruptOlderSnapshot.getChecksumPath());

    final var newerSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(2, snapshotStore).persist().join();

    // when
    snapshotStore.close();
    snapshotStore = createStore(rootDirectory);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).get().isEqualTo(newerSnapshot);
    assertThat(newerSnapshot.getDirectory()).exists();
    assertThat(newerSnapshot.getChecksumPath()).exists();
    assertThat(corruptOlderSnapshot.getDirectory()).doesNotExist();
    assertThat(corruptOlderSnapshot.getChecksumPath()).doesNotExist();
  }

  @Test
  public void shouldDeleteCorruptSnapshotsWhenAddingNewSnapshot() throws IOException {
    // given
    final var olderSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(1, snapshotStore).persist().join();
    final var otherStore = createStore(rootDirectory);

    // when - corrupting old snapshot and adding new valid snapshot
    SnapshotChecksum.persist(olderSnapshot.getChecksumPath(), new SfvChecksumImpl(0xCAFEL));
    final var newerSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(2, otherStore).persist().join();

    // then -- valid snapshot is unaffected and corrupt snapshot is deleted
    assertThat(otherStore.getLatestSnapshot()).get().isEqualTo(newerSnapshot);
    assertThat(newerSnapshot.getDirectory()).exists();
    assertThat(newerSnapshot.getChecksumPath()).exists();
    assertThat(olderSnapshot.getDirectory()).doesNotExist();
    assertThat(olderSnapshot.getChecksumPath()).doesNotExist();
  }

  @Test
  public void shouldNotDeleteCorruptSnapshotWithoutValidSnapshot() throws IOException {
    // given
    final var otherStore = createStore(rootDirectory);
    final var corruptSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(1, otherStore).persist().join();
    SnapshotChecksum.persist(corruptSnapshot.getChecksumPath(), new SfvChecksumImpl(0xCAFEL));

    // when
    snapshotStore.close();
    snapshotStore = createStore(rootDirectory);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).isEmpty();
    assertThat(corruptSnapshot.getDirectory()).exists();
    assertThat(corruptSnapshot.getChecksumPath()).exists();
  }

  @Test
  public void shouldUseChecksumProviderForChecksumsIfSupplied() throws IOException {
    // given
    final Map<String, Long> badChecksums = new HashMap<>();
    badChecksums.put(SNAPSHOT_CONTENT_FILE_NAME, 123L);
    final var testChecksumProvider = new TestChecksumProvider(badChecksums);

    // when
    final var store =
        new FileBasedSnapshotStore(
            1, rootDirectory, testChecksumProvider, new SimpleMeterRegistry());
    scheduler.submitActor(store).join();
    final var takenSnapshot = (FileBasedSnapshot) takeTransientSnapshot(1, store).persist().join();

    // then
    final var persistedChecksums = SnapshotChecksum.read(takenSnapshot.getChecksumPath());
    assertThat(persistedChecksums.getChecksums().get(SNAPSHOT_CONTENT_FILE_NAME)).isEqualTo(123L);
  }

  @Test
  public void shouldPurgePendingSnapshots() {
    // given
    takeTransientSnapshot();

    // when
    snapshotStore.purgePendingSnapshots().join();

    // then
    assertThat(pendingSnapshotsDir).isEmptyDirectory();
  }

  @Test
  public void shouldDeleteSnapshot() {
    // given
    final var persistedSnapshot = (FileBasedSnapshot) takeTransientSnapshot().persist().join();

    // when
    persistedSnapshot.delete();

    // then
    assertThat(persistedSnapshot.getPath()).doesNotExist();
  }

  @Test
  public void shouldNotDeleteReservedSnapshot() {
    // given
    final var reservedSnapshot = takeTransientSnapshot(1, snapshotStore).persist().join();
    reservedSnapshot.reserve().join();

    // when
    final var newSnapshot = takeTransientSnapshot(2, snapshotStore).persist().join();

    // then
    assertThat(snapshotStore.getAvailableSnapshots().join())
        .containsExactlyInAnyOrder(reservedSnapshot, newSnapshot);
    assertThat(reservedSnapshot.getPath()).exists();
    assertThat(newSnapshot.getPath()).exists();
  }

  @Test
  public void shouldNotReserveDeletedSnapshot() throws IOException {
    // given
    final var snapshotToReserve = takeTransientSnapshot(1, snapshotStore).persist().join();

    // when
    takeTransientSnapshot(2, snapshotStore).persist().join();

    // then
    assertThatThrownBy(() -> snapshotToReserve.reserve().join())
        .hasCauseInstanceOf(SnapshotNotFoundException.class);
  }

  @Test
  public void shouldDeleteReleasedSnapshot() {
    // given
    final var reservedSnapshot = takeTransientSnapshot(1, snapshotStore).persist().join();
    final var reservation = reservedSnapshot.reserve().join();

    // when
    reservation.release().join();
    final var newSnapshot = takeTransientSnapshot(3, snapshotStore).persist().join();

    // then
    assertThat(snapshotStore.getAvailableSnapshots().join()).containsExactly(newSnapshot);
    assertThat(reservedSnapshot.getPath()).doesNotExist();
    assertThat(newSnapshot.getPath()).exists();
  }

  @Test
  public void shouldNotDeleteReservedSnapshotUntilAllReservationsAreReleased() {
    // given
    final var reservedSnashot = takeTransientSnapshot(1, snapshotStore).persist().join();

    // first reservation
    final var reservation = reservedSnashot.reserve().join();
    // second reservation
    reservedSnashot.reserve().join();

    // when
    reservation.release();

    final var newSnapshot = takeTransientSnapshot(2, snapshotStore).persist().join();

    // then
    assertThat(snapshotStore.getAvailableSnapshots().join())
        .containsExactlyInAnyOrder(reservedSnashot, newSnapshot);
    assertThat(reservedSnashot.getPath()).exists();
    assertThat(newSnapshot.getPath()).exists();
  }

  @Test
  public void shouldRestartWithAReceivedSnapshot() throws IOException {
    // given
    final Map<String, Long> fileChecksums = new HashMap<>();
    final var fileContentChecksum = new CRC32C();
    fileContentChecksum.update(SNAPSHOT_CONTENT.getBytes(StandardCharsets.UTF_8));
    fileChecksums.put(SNAPSHOT_CONTENT_FILE_NAME, fileContentChecksum.getValue());
    final var receiverStorePath = temporaryFolder.newFolder("receiver").toPath();

    final var store =
        new FileBasedSnapshotStore(
            PARTITION_ID,
            receiverStorePath,
            new TestChecksumProvider(fileChecksums),
            new SimpleMeterRegistry());
    scheduler.submitActor(store);

    // when
    final var persistedSnapshot = takeTransientSnapshot().persist().join();
    final var receivedSnapshot = store.newReceivedSnapshot(persistedSnapshot.getId()).join();
    try (final var reader = persistedSnapshot.newChunkReader()) {
      while (reader.hasNext()) {
        receivedSnapshot.apply(reader.next()).join();
      }
    }

    receivedSnapshot.persist().join();

    // restart store will attempt to update the latest snapshot to the most recent one and check for
    // corruption.
    store.close();

    final var restartedStore =
        new FileBasedSnapshotStore(
            PARTITION_ID,
            receiverStorePath,
            new TestChecksumProvider(fileChecksums),
            new SimpleMeterRegistry());
    scheduler.submitActor(restartedStore).join();

    assertThat(restartedStore.getLatestSnapshot())
        .describedAs(
            "The latest snapshot is not detected as corrupted and should be loaded after restart")
        .hasValueSatisfying(s -> assertThat(s.getId()).isEqualTo(persistedSnapshot.getId()));
  }

  private boolean createSnapshotDir(final Path path) {
    try {
      FileUtil.ensureDirectoryExists(path);
      Files.write(
          path.resolve(SNAPSHOT_CONTENT_FILE_NAME),
          SNAPSHOT_CONTENT.getBytes(),
          CREATE_NEW,
          StandardOpenOption.WRITE);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
  }

  private TransientSnapshot takeTransientSnapshot() {
    return takeTransientSnapshot(1L, snapshotStore);
  }

  private TransientSnapshot takeTransientSnapshot(
      final long index, final FileBasedSnapshotStore store) {
    final var transientSnapshot = store.newTransientSnapshot(index, 1, 1, 0).get();
    transientSnapshot.take(this::createSnapshotDir);
    return transientSnapshot;
  }

  private FileBasedSnapshotStore createStore(final Path root) {
    final var store =
        new FileBasedSnapshotStore(1, root, snapshotPath -> Map.of(), new SimpleMeterRegistry());
    scheduler.submitActor(store).join();

    return store;
  }
}
