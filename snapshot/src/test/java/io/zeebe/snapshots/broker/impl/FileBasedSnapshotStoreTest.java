/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.snapshots.broker.impl;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.ActorScheduler;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileBasedSnapshotStoreTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private Path snapshotsDir;
  private Path pendingSnapshotsDir;
  private FileBasedSnapshotStoreFactory factory;
  private File root;
  private String partitionName;

  @Before
  public void before() {
    factory = new FileBasedSnapshotStoreFactory(createActorScheduler());
    partitionName = "1";
    root = temporaryFolder.getRoot();

    factory.createReceivableSnapshotStore(root.toPath(), partitionName);

    snapshotsDir =
        temporaryFolder
            .getRoot()
            .toPath()
            .resolve(FileBasedSnapshotStoreFactory.SNAPSHOTS_DIRECTORY);
    pendingSnapshotsDir =
        temporaryFolder.getRoot().toPath().resolve(FileBasedSnapshotStoreFactory.PENDING_DIRECTORY);
  }

  private ActorScheduler createActorScheduler() {
    final var actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
    return actorScheduler;
  }

  @Test
  public void shouldCreateSubFoldersOnCreatingDirBasedStore() {
    // given

    // when + then
    assertThat(snapshotsDir).exists();
    Assertions.assertThat(pendingSnapshotsDir).exists();
  }

  @Test
  public void shouldDeleteStore() {
    // given

    // when
    factory.getPersistedSnapshotStore(partitionName).delete().join();

    // then
    assertThat(pendingSnapshotsDir).doesNotExist();
    assertThat(snapshotsDir).doesNotExist();
  }

  @Test
  public void shouldLoadExistingSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        factory
            .getConstructableSnapshotStore(partitionName)
            .newTransientSnapshot(index, term, 1, 0)
            .orElseThrow();
    transientSnapshot.take(this::createSnapshotDir);
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var snapshotStore =
        new FileBasedSnapshotStoreFactory(createActorScheduler())
            .createReceivableSnapshotStore(root.toPath(), partitionName);

    // then
    final var currentSnapshotIndex = snapshotStore.getCurrentSnapshotIndex();
    assertThat(currentSnapshotIndex).isEqualTo(1L);
    assertThat(snapshotStore.getLatestSnapshot()).get().isEqualTo(persistedSnapshot);
  }

  @Test
  public void shouldLoadLatestSnapshotWhenMoreThanOneExistsAndDeleteOlder() throws IOException {
    // given
    final var timeStamp = WallClockTimestamp.from(System.currentTimeMillis());
    final List<FileBasedSnapshotMetadata> snapshots = new ArrayList<>();
    snapshots.add(new FileBasedSnapshotMetadata(1, 1, timeStamp, 1, 1));
    snapshots.add(new FileBasedSnapshotMetadata(10, 1, timeStamp, 10, 10));
    snapshots.add(new FileBasedSnapshotMetadata(2, 1, timeStamp, 2, 2));

    // We can't use FileBasedSnapshotStore to create multiple snapshot as it always delete the
    // previous snapshot during normal execution. However, due to errors or crashes during
    // persisting a snapshot, it might end up with more than one snapshot directory on disk.
    snapshots.forEach(
        snapshotId -> {
          try {
            snapshotsDir.resolve(snapshotId.getSnapshotIdAsString()).toFile().createNewFile();
          } catch (final IOException e) {
            fail("Failed to create directory", e);
          }
        });

    // when
    final var snapshotStore =
        new FileBasedSnapshotStoreFactory(createActorScheduler())
            .createReceivableSnapshotStore(root.toPath(), partitionName);

    // then
    final var currentSnapshotIndex = snapshotStore.getCurrentSnapshotIndex();
    assertThat(currentSnapshotIndex).isEqualTo(10L);
    // should delete older snapshots
    assertThat(snapshotsDir.toFile().list()).hasSize(1);
    assertThat(snapshotsDir.toFile().list())
        .containsExactly(snapshotStore.getLatestSnapshot().get().getId());
  }

  private boolean createSnapshotDir(final Path path) {
    try {
      FileUtil.ensureDirectoryExists(path);
      Files.write(
          path.resolve("file1.txt"),
          "This is the content".getBytes(),
          CREATE_NEW,
          StandardOpenOption.WRITE);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
  }
}
