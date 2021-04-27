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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.snapshots.ConstructableSnapshotStore;
import io.zeebe.snapshots.PersistedSnapshotListener;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileBasedTransientSnapshotTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public ActorSchedulerRule scheduler = new ActorSchedulerRule();

  private ConstructableSnapshotStore persistedSnapshotStore;
  private Path snapshotsDir;
  private Path pendingSnapshotsDir;

  @Before
  public void before() {
    final FileBasedSnapshotStoreFactory factory =
        new FileBasedSnapshotStoreFactory(scheduler.get(), 1);
    final int partitionId = 1;
    final File root = temporaryFolder.getRoot();

    factory.createReceivableSnapshotStore(root.toPath(), partitionId);
    persistedSnapshotStore = factory.getConstructableSnapshotStore(partitionId);

    snapshotsDir =
        temporaryFolder
            .getRoot()
            .toPath()
            .resolve(FileBasedSnapshotStoreFactory.SNAPSHOTS_DIRECTORY);
    pendingSnapshotsDir =
        temporaryFolder.getRoot().toPath().resolve(FileBasedSnapshotStoreFactory.PENDING_DIRECTORY);
  }

  @Test
  public void shouldNotCreateDirForTakeTransientSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;

    // when
    persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0);

    // then
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();
    assertThat(snapshotsDir.toFile()).isEmptyDirectory();
  }

  @Test
  public void shouldBeAbleToAbortNotStartedSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0);

    // when
    transientSnapshot.orElseThrow().abort();

    // then
    assertThat(snapshotsDir.toFile()).isEmptyDirectory();
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();
  }

  @Test
  public void shouldTakeTransientSnapshot() {
    // given
    final var index = 1L;
    final var term = 2L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 3, 4).orElseThrow();

    // when
    transientSnapshot.take(this::createSnapshotDir).join();

    // then
    assertThat(snapshotsDir.toFile()).isEmptyDirectory();
    final var snapshotDirs = pendingSnapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).hasSize(1);

    final var pendingSnapshotDir = snapshotDirs[0];
    final FileBasedSnapshotMetadata pendingSnapshotId =
        FileBasedSnapshotMetadata.ofFileName(pendingSnapshotDir.getName()).orElseThrow();
    assertThat(pendingSnapshotId.getIndex()).isEqualTo(1);
    assertThat(pendingSnapshotId.getTerm()).isEqualTo(2);
    assertThat(pendingSnapshotId.getProcessedPosition()).isEqualTo(3);
    assertThat(pendingSnapshotId.getExportedPosition()).isEqualTo(4);
    assertThat(pendingSnapshotDir.listFiles())
        .extracting(File::getName)
        .containsExactlyInAnyOrder("file1.txt", "CHECKSUM");
  }

  @Test
  public void shouldAbortAndDeleteTransientSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0);
    transientSnapshot.orElseThrow().take(this::createSnapshotDir).join();

    // when
    transientSnapshot.get().abort().join();

    // then
    assertThat(snapshotsDir.toFile()).isEmptyDirectory();
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();
  }

  @Test
  public void shouldPurgePendingOnStore() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0);
    transientSnapshot.orElseThrow().take(this::createSnapshotDir);

    // when
    persistedSnapshotStore.purgePendingSnapshots().join();

    // then
    assertThat(snapshotsDir.toFile()).isEmptyDirectory();
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();
  }

  @Test
  public void shouldNotDeletePersistedSnapshotOnPurge() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(this::createSnapshotDir);
    final var persistSnapshot = transientSnapshot.persist().join();

    // when
    persistedSnapshotStore.purgePendingSnapshots().join();

    // then
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();
    final var snapshotDirs = snapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).hasSize(1);

    final var pendingSnapshotDir = snapshotDirs[0];
    assertThat(pendingSnapshotDir).hasName(persistSnapshot.getId());
    assertThat(pendingSnapshotDir.listFiles())
        .extracting(File::getName)
        .containsExactlyInAnyOrder("file1.txt", "CHECKSUM");
  }

  @Test
  public void shouldCommitTakenSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0);
    transientSnapshot.orElseThrow().take(this::createSnapshotDir);

    // when
    final var persistedSnapshot = transientSnapshot.get().persist().join();

    // then
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();

    final var snapshotDirs = snapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(committedSnapshotDir).hasName(persistedSnapshot.getId());
    assertThat(committedSnapshotDir.listFiles())
        .extracting(File::getName)
        .containsExactlyInAnyOrder("file1.txt", "CHECKSUM");
  }

  @Test
  public void shouldReplaceSnapshotOnNextSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var oldTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    oldTransientSnapshot.take(this::createSnapshotDir);
    oldTransientSnapshot.persist().join();

    // when
    final var newSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, 1, 0).orElseThrow();
    newSnapshot.take(this::createSnapshotDir);
    newSnapshot.persist().join();

    // then
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();

    final var snapshotDirs = snapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(
            FileBasedSnapshotMetadata.ofFileName(committedSnapshotDir.getName())
                .orElseThrow()
                .getIndex())
        .isEqualTo(2);
    assertThat(committedSnapshotDir.listFiles())
        .extracting(File::getName)
        .containsExactlyInAnyOrder("file1.txt", "CHECKSUM");
  }

  @Test
  public void shouldRemovePendingSnapshotOnCommittingSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var oldTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    oldTransientSnapshot.take(this::createSnapshotDir);

    // when
    final var newSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, 1, 0).orElseThrow();
    newSnapshot.take(this::createSnapshotDir);
    newSnapshot.persist().join();

    // then
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();

    final var snapshotDirs = snapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(
            FileBasedSnapshotMetadata.ofFileName(committedSnapshotDir.getName())
                .orElseThrow()
                .getIndex())
        .isEqualTo(2);
    assertThat(committedSnapshotDir.listFiles())
        .extracting(File::getName)
        .containsExactlyInAnyOrder("file1.txt", "CHECKSUM");
  }

  @Test
  public void shouldNotRemovePendingSnapshotOnCommittingSnapshotWhenHigher() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var oldTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, 1, 0).orElseThrow();
    oldTransientSnapshot.take(this::createSnapshotDir);

    // when
    final var newSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    newSnapshot.take(this::createSnapshotDir);
    final var newSnapshotId = newSnapshot.persist().join().getId();

    // then
    final var pendingSnapshotDirs = pendingSnapshotsDir.toFile().listFiles();
    assertThat(pendingSnapshotDirs).hasSize(1);

    final var pendingSnapshotDir = pendingSnapshotDirs[0];
    assertThat(
            FileBasedSnapshotMetadata.ofFileName(pendingSnapshotDir.getName())
                .orElseThrow()
                .getIndex())
        .isEqualTo(2);
    assertThat(pendingSnapshotDir.listFiles())
        .extracting(File::getName)
        .containsExactlyInAnyOrder("file1.txt", "CHECKSUM");

    final var snapshotDirs = snapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(committedSnapshotDir).hasName(newSnapshotId);
    assertThat(committedSnapshotDir.listFiles())
        .extracting(File::getName)
        .containsExactlyInAnyOrder("file1.txt", "CHECKSUM");
  }

  @Test
  public void shouldCleanUpPendingDirOnFailingTakeSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var oldTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();

    // when
    oldTransientSnapshot
        .take(
            path -> {
              try {
                FileUtil.ensureDirectoryExists(path);
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
              return false;
            })
        .join();

    // then
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();
    assertThat(snapshotsDir.toFile()).isEmptyDirectory();
  }

  @Test
  public void shouldCleanUpPendingDirOnException() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var oldTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();

    // when
    assertThatThrownBy(
            () ->
                oldTransientSnapshot
                    .take(
                        path -> {
                          try {
                            FileUtil.ensureDirectoryExists(path);
                            throw new RuntimeException("EXPECTED");
                          } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                          }
                        })
                    .join())
        .hasCauseInstanceOf(RuntimeException.class);

    // then
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();
    assertThat(snapshotsDir.toFile()).isEmptyDirectory();
  }

  @Test
  public void shouldNotifyListenersOnNewSnapshot() {
    // given
    final var listener = mock(PersistedSnapshotListener.class);
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    persistedSnapshotStore.addSnapshotListener(listener);
    transientSnapshot.take(this::createSnapshotDir).join();

    // when
    final var persistedSnapshot = transientSnapshot.persist().join();

    // then
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();

    verify(listener, times(1)).onNewSnapshot(persistedSnapshot);
  }

  @Test
  public void shouldNotNotifyListenersOnNewSnapshotWhenDeregistered() {
    // given
    final var listener = mock(PersistedSnapshotListener.class);
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    persistedSnapshotStore.addSnapshotListener(listener);
    persistedSnapshotStore.removeSnapshotListener(listener);
    transientSnapshot.take(this::createSnapshotDir).join();

    // when
    final var persistedSnapshot = transientSnapshot.persist().join();

    // then
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();

    verify(listener, times(0)).onNewSnapshot(persistedSnapshot);
  }

  @Test
  public void shouldNotTakeSnapshotIfIdAlreadyExists() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var processedPosition = 2;
    final var exporterPosition = 3;
    final var transientSnapshot =
        persistedSnapshotStore
            .newTransientSnapshot(index, term, processedPosition, exporterPosition)
            .orElseThrow();
    transientSnapshot.take(this::createSnapshotDir).join();
    // when
    transientSnapshot.persist().join();

    // then
    assertThat(
            persistedSnapshotStore.newTransientSnapshot(
                index, term, processedPosition, exporterPosition))
        .isEmpty();
  }

  @Test
  public void shouldNotPersistDeletedPendingSnapshot() {
    final var index = 1L;
    final var term = 0L;
    final var processedPosition = 2;
    final var exporterPosition = 3;
    final var transientSnapshot =
        persistedSnapshotStore
            .newTransientSnapshot(index, term, processedPosition, exporterPosition)
            .orElseThrow();
    transientSnapshot.take(this::createSnapshotDir).join();

    // when
    persistedSnapshotStore.purgePendingSnapshots().join();
    final var persisted = transientSnapshot.persist();

    // then
    assertThatThrownBy(persisted::join)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Snapshot is not valid");
  }

  @Test
  public void shouldNotPersistSnapshotWithNoDirectoryCreated() {
    final var index = 1L;
    final var term = 0L;
    final var processedPosition = 2;
    final var exporterPosition = 3;
    final var transientSnapshot =
        persistedSnapshotStore
            .newTransientSnapshot(index, term, processedPosition, exporterPosition)
            .orElseThrow();
    transientSnapshot.take(p -> true).join();

    // when
    final var persisted = transientSnapshot.persist();

    // then
    assertThatThrownBy(persisted::join)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Snapshot is not valid");
  }

  @Test
  public void shouldNotPersistSnapshotWithEmptyDirectory() {
    final var index = 1L;
    final var term = 0L;
    final var processedPosition = 2;
    final var exporterPosition = 3;
    final var transientSnapshot =
        persistedSnapshotStore
            .newTransientSnapshot(index, term, processedPosition, exporterPosition)
            .orElseThrow();
    transientSnapshot.take(p -> p.toFile().mkdir()).join();

    // when
    final var persisted = transientSnapshot.persist();

    // then
    assertThatThrownBy(persisted::join)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Snapshot is not valid");
  }

  @Test
  public void shouldPersistIdempotently() {
    // given
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(1L, 2L, 3, 4).orElseThrow();
    transientSnapshot.take(this::createSnapshotDir).join();
    final var firstSnapshot = transientSnapshot.persist().join();
    assertSnapshotWasMoved();

    // when
    final var secondSnapshot = transientSnapshot.persist().join();

    // then
    assertThat(firstSnapshot).isEqualTo(secondSnapshot);
    assertSnapshotWasMoved();
  }

  private void assertSnapshotWasMoved() {
    assertThat(pendingSnapshotsDir.toFile()).isEmptyDirectory();
    final var snapshotDirs = snapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).hasSize(1);
    final var snapshotDir = snapshotDirs[0];
    assertThat(snapshotDir.listFiles())
        .extracting(File::getName)
        .containsExactlyInAnyOrder("file1.txt", "CHECKSUM");
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
