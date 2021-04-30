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

import io.zeebe.snapshots.TransientSnapshot;
import io.zeebe.test.util.asserts.DirectoryAssert;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    snapshotsDir = root.resolve(SNAPSHOT_DIRECTORY);
    pendingSnapshotsDir = root.resolve(PENDING_DIRECTORY);
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
    try (final var channel =
        FileChannel.open(
            persistedSnapshot.getChecksumFile(),
            StandardOpenOption.WRITE,
            StandardOpenOption.DSYNC)) {
      channel.write(ByteBuffer.allocate(Long.BYTES).putLong(0, 0xCAFEL));
    }

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
    final var transientSnapshot = store.newTransientSnapshot(index, 1, 1, 0).orElseThrow();
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
