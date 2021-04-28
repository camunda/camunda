/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.impl;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.zeebe.snapshots.PersistedSnapshot;
import io.zeebe.snapshots.TransientSnapshot;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.agrona.IoUtil;
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

  @Before
  public void before() throws IOException {
    final var root = temporaryFolder.newFolder("snapshots").toPath();
    snapshotsDir = root.resolve(PENDING_DIRECTORY);
    pendingSnapshotsDir = root.resolve(SNAPSHOT_DIRECTORY);
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
  public void shouldLoadExistingSnapshot() {
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
  public void shouldLoadLatestSnapshotWhenMoreThanOneExistsAndDeleteOlder() {
    // given
    final List<FileBasedSnapshotMetadata> snapshots = new ArrayList<>();
    snapshots.add(new FileBasedSnapshotMetadata(1, 1, 1, 1));
    snapshots.add(new FileBasedSnapshotMetadata(10, 1, 10, 10));
    snapshots.add(new FileBasedSnapshotMetadata(2, 1, 2, 2));

    // We can't use FileBasedSnapshotStore to create multiple snapshot as it always delete the
    // previous snapshot during normal execution. However, due to errors or crashes during
    // persisting a snapshot, it might end up with more than one snapshot directory on disk.
    snapshots.forEach(
        snapshotId -> {
          try {
            final var snapshot = snapshotsDir.resolve(snapshotId.getSnapshotIdAsString()).toFile();
            assertThat(snapshot.mkdir()).isTrue();
            createSnapshotDir(snapshot.toPath());
            final var checksum = SnapshotChecksum.calculate(snapshot.toPath());
            SnapshotChecksum.persist(snapshot.toPath(), checksum);
          } catch (final IOException e) {
            fail("Failed to create directory", e);
          }
        });

    // when
    snapshotStore.close();
    snapshotStore = createStore(snapshotsDir, pendingSnapshotsDir);

    // then
    assertThat(snapshotStore.getCurrentSnapshotIndex()).isEqualTo(10L);
    final var latestSnapshotPath =
        snapshotStore.getLatestSnapshot().map(PersistedSnapshot::getPath).orElseThrow();
    assertThat(snapshotsDir)
        .as("The older snapshots should have been deleted")
        .isDirectoryNotContaining(p -> !p.equals(latestSnapshotPath));
  }

  @Test
  public void shouldNotLoadCorruptedSnapshot() throws Exception {
    // given
    final var persistedSnapshot = takeTransientSnapshot().persist().join();

    corruptSnapshot(persistedSnapshot);

    // when
    snapshotStore.close();
    snapshotStore = createStore(snapshotsDir, pendingSnapshotsDir);

    // then
    assertThat(snapshotStore.getLatestSnapshot()).isEmpty();
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

  private void corruptSnapshot(final PersistedSnapshot persistedSnapshot) throws IOException {
    final var corruptedFile =
        persistedSnapshot.getPath().resolve(SNAPSHOT_CONTENT_FILE_NAME).toFile();
    try (final RandomAccessFile file = new RandomAccessFile(corruptedFile, "rw")) {
      file.writeLong(12346L);
    }
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
    final var transientSnapshot = snapshotStore.newTransientSnapshot(1, 1, 1, 0).orElseThrow();
    transientSnapshot.take(this::createSnapshotDir);
    return transientSnapshot;
  }

  private FileBasedSnapshotStore createStore(final Path snapshotDir, final Path pendingDir) {
    final var store =
        new FileBasedSnapshotStore(1, 1, new SnapshotMetrics(1 + "-" + 1), snapshotDir, pendingDir);
    IoUtil.ensureDirectoryExists(snapshotDir.toFile(), "Snapshot directory");
    IoUtil.ensureDirectoryExists(pendingSnapshotsDir.toFile(), "Pending snapshot directory");
    scheduler.submitActor(store).join();

    return store;
  }
}
