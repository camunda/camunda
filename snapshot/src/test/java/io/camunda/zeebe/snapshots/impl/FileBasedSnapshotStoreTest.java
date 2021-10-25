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

import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.test.util.asserts.DirectoryAssert;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileBasedSnapshotStoreTest {
  private static final String SNAPSHOT_DIRECTORY = "snapshots";
  private static final String PENDING_DIRECTORY = "pending";

  private static final String SNAPSHOT_CONTENT_FILE_NAME = "file1.txt";

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
    snapshotStore = createStore(snapshotsDir, pendingSnapshotsDir);
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
    snapshotStore = createStore(snapshotsDir, pendingSnapshotsDir);

    // then
    assertThat(snapshotStore.getCurrentSnapshotIndex()).isEqualTo(1L);
    assertThat(snapshotStore.getLatestSnapshot()).hasValue(persistedSnapshot);
  }

  @Test
  public void shouldLoadLatestSnapshotWhenMoreThanOneExistsAndDeleteOlder() throws IOException {
    // given
    final FileBasedSnapshotStore otherStore = createStore(snapshotsDir, pendingSnapshotsDir);
    final var olderSnapshot = takeTransientSnapshot(1L, otherStore).persist().join();
    final var newerSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(2L, snapshotStore).persist().join();

    // when
    assertThat(snapshotsDir)
        .asInstanceOf(DirectoryAssert.factory())
        .as("ensure both the older and newer snapshots exist")
        .isDirectoryContainingAllOf(olderSnapshot.getPath(), newerSnapshot.getPath());
    snapshotStore.close();
    snapshotStore = createStore(snapshotsDir, pendingSnapshotsDir);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).hasValue(newerSnapshot);
    assertThat(snapshotsDir)
        .asInstanceOf(DirectoryAssert.factory())
        .as("the older snapshots should have been deleted")
        .isDirectoryContainingExactly(newerSnapshot.getPath(), newerSnapshot.getChecksumFile());
  }

  @Test
  public void shouldNotLoadCorruptedSnapshot() throws Exception {
    // given
    final var persistedSnapshot = (FileBasedSnapshot) takeTransientSnapshot().persist().join();
    SnapshotChecksum.persist(persistedSnapshot.getChecksumFile(), new SfvChecksum(0xCAFEL));

    // when
    snapshotStore.close();
    snapshotStore = createStore(snapshotsDir, pendingSnapshotsDir);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).isEmpty();
  }

  @Test
  public void shouldDeleteSnapshotWithoutChecksumFile() throws IOException {
    // given
    final var persistedSnapshot = (FileBasedSnapshot) takeTransientSnapshot().persist().join();
    Files.delete(persistedSnapshot.getChecksumFile());

    // when
    snapshotStore.close();
    snapshotStore = createStore(snapshotsDir, pendingSnapshotsDir);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).isEmpty();
    assertThat(persistedSnapshot.getDirectory()).doesNotExist();
  }

  @Test
  public void shouldDeleteOlderSnapshotsWithCorruptChecksums() throws IOException {
    // given
    final var otherStore = createStore(snapshotsDir, pendingSnapshotsDir);
    final var corruptOlderSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(1, otherStore).persist().join();
    SnapshotChecksum.persist(corruptOlderSnapshot.getChecksumFile(), new SfvChecksum(0xCAFEL));

    final var newerSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(2, snapshotStore).persist().join();

    // when
    snapshotStore.close();
    snapshotStore = createStore(snapshotsDir, pendingSnapshotsDir);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).hasValue(newerSnapshot);
    assertThat(newerSnapshot.getDirectory()).exists();
    assertThat(newerSnapshot.getChecksumFile()).exists();
    assertThat(corruptOlderSnapshot.getDirectory()).doesNotExist();
    assertThat(corruptOlderSnapshot.getChecksumFile()).doesNotExist();
  }

  @Test
  public void shouldDeleteOlderSnapshotsWithMissingChecksums() throws IOException {
    // given
    final var otherStore = createStore(snapshotsDir, pendingSnapshotsDir);
    final var corruptOlderSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(1, otherStore).persist().join();
    Files.delete(corruptOlderSnapshot.getChecksumFile());

    final var newerSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(2, snapshotStore).persist().join();

    // when
    snapshotStore.close();
    snapshotStore = createStore(snapshotsDir, pendingSnapshotsDir);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).get().isEqualTo(newerSnapshot);
    assertThat(newerSnapshot.getDirectory()).exists();
    assertThat(newerSnapshot.getChecksumFile()).exists();
    assertThat(corruptOlderSnapshot.getDirectory()).doesNotExist();
    assertThat(corruptOlderSnapshot.getChecksumFile()).doesNotExist();
  }

  @Test
  public void shouldDeleteCorruptSnapshotsWhenAddingNewSnapshot() throws IOException {
    // given
    final var olderSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(1, snapshotStore).persist().join();
    final var otherStore = createStore(snapshotsDir, pendingSnapshotsDir);

    // when - corrupting old snapshot and adding new valid snapshot
    SnapshotChecksum.persist(olderSnapshot.getChecksumFile(), new SfvChecksum(0xCAFEL));
    final var newerSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(2, otherStore).persist().join();

    // then -- valid snapshot is unaffected and corrupt snapshot is deleted
    assertThat(otherStore.getLatestSnapshot()).get().isEqualTo(newerSnapshot);
    assertThat(newerSnapshot.getDirectory()).exists();
    assertThat(newerSnapshot.getChecksumFile()).exists();
    assertThat(olderSnapshot.getDirectory()).doesNotExist();
    assertThat(olderSnapshot.getChecksumFile()).doesNotExist();
  }

  @Test
  public void shouldNotDeleteCorruptSnapshotWithoutValidSnapshot() throws IOException {
    // given
    final var otherStore = createStore(snapshotsDir, pendingSnapshotsDir);
    final var corruptSnapshot =
        (FileBasedSnapshot) takeTransientSnapshot(1, otherStore).persist().join();
    SnapshotChecksum.persist(corruptSnapshot.getChecksumFile(), new SfvChecksum(0xCAFEL));

    // when
    snapshotStore.close();
    snapshotStore = createStore(snapshotsDir, pendingSnapshotsDir);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).isEmpty();
    assertThat(corruptSnapshot.getDirectory()).exists();
    assertThat(corruptSnapshot.getChecksumFile()).exists();
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
  public void shouldCopySnapshot() {
    // given
    final var persistedSnapshot = (FileBasedSnapshot) takeTransientSnapshot().persist().join();
    final var target = rootDirectory.resolve("runtime");

    // when
    snapshotStore.copySnapshot(persistedSnapshot, target).join();

    // then
    assertThat(target).isNotEmptyDirectory();
    assertThat(target.toFile().list())
        .containsExactlyInAnyOrder(persistedSnapshot.getDirectory().toFile().list());
  }

  @Test
  public void shouldCompleteWithExceptionWhenCannotCopySnapshot() throws IOException {
    // given
    final var persistedSnapshot = (FileBasedSnapshot) takeTransientSnapshot().persist().join();
    final var target = rootDirectory.resolve("runtime");
    target.toFile().createNewFile();

    // when
    final var result = snapshotStore.copySnapshot(persistedSnapshot, target);

    // then - should fail because targetDirectory already exists
    assertThatThrownBy(result::join).hasCauseInstanceOf(FileAlreadyExistsException.class);
  }

  @Test
  public void shouldCompleteWithExceptionWhenCopyingIfSnapshotDoesNotExists() {
    // given
    final var persistedSnapshot = (FileBasedSnapshot) takeTransientSnapshot().persist().join();
    final var target = rootDirectory.resolve("runtime");

    // when
    persistedSnapshot.delete();
    final var result = snapshotStore.copySnapshot(persistedSnapshot, target);

    // then
    assertThatThrownBy(result::join).hasCauseInstanceOf(FileNotFoundException.class);
  }

  private boolean createSnapshotDir(final Path path) {
    try {
      FileUtil.ensureDirectoryExists(path);
      Files.write(
          path.resolve(SNAPSHOT_CONTENT_FILE_NAME),
          "This is the content".getBytes(),
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

  private FileBasedSnapshotStore createStore(final Path snapshotDir, final Path pendingDir)
      throws IOException {
    final var store =
        new FileBasedSnapshotStore(1, 1, new SnapshotMetrics(1 + "-" + 1), snapshotDir, pendingDir);
    FileUtil.ensureDirectoryExists(snapshotDir);
    FileUtil.ensureDirectoryExists(pendingSnapshotsDir);
    scheduler.submitActor(store).join();

    return store;
  }
}
