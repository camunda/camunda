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
import static org.assertj.core.api.Assertions.assertThatCode;
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
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TransientSnapshotTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public ActorSchedulerRule scheduler = new ActorSchedulerRule();

  private ConstructableSnapshotStore persistedSnapshotStore;
  private Path lastTransientSnapshotPath;

  @Before
  public void before() {
    final FileBasedSnapshotStoreFactory factory =
        new FileBasedSnapshotStoreFactory(scheduler.get(), 1);
    final int partitionId = 1;
    final File root = temporaryFolder.getRoot();

    factory.createReceivableSnapshotStore(root.toPath(), partitionId);
    persistedSnapshotStore = factory.getConstructableSnapshotStore(partitionId);
  }

  @Test
  public void shouldTransientPathContainsMetadata() {
    // given
    final var index = 1L;
    final var term = 2L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 3, 4).orElseThrow();
    final CompletableFuture<Path> transientPath = new CompletableFuture<>();

    // when
    transientSnapshot.take(
        p -> {
          transientPath.complete(p);
          return true;
        });

    // then
    final var path = transientPath.join();
    final var metadata =
        FileBasedSnapshotMetadata.ofFileName(path.getFileName().toString()).orElseThrow();
    assertThat(metadata.getIndex()).isEqualTo(1);
    assertThat(metadata.getTerm()).isEqualTo(2);
    assertThat(metadata.getProcessedPosition()).isEqualTo(3);
    assertThat(metadata.getExportedPosition()).isEqualTo(4);
  }

  @Test
  public void shouldReturnTrueOnSuccessTake() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();

    // when
    final var success =
        transientSnapshot
            .take(
                p -> {
                  try {
                    FileUtil.ensureDirectoryExists(p);
                    Files.createFile(p.resolve("file"));
                    return true;
                  } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                  }
                })
            .join();

    // then
    assertThat(success).isTrue();
  }

  @Test
  public void shouldReturnFalseOnFailureTake() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();

    // when
    final var success = transientSnapshot.take(p -> false).join();

    // then
    assertThat(success).isFalse();
  }

  @Test
  public void shouldCompleteExceptionallyOnExceptionInTake() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();

    // when
    final var success =
        transientSnapshot.take(
            p -> {
              throw new RuntimeException("EXPECTED");
            });

    // then
    assertThatThrownBy(success::join)
        .hasCauseInstanceOf(RuntimeException.class)
        .hasMessageContaining("EXPECTED");
  }

  @Test
  public void shouldFailToPersistWhenTakeDoesnotWrite() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(p -> true).join();

    // when - then
    assertThatThrownBy(() -> transientSnapshot.persist().join())
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldBeAbleToAbortNotStartedSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();

    // when - then
    assertThatCode(transientSnapshot::abort).doesNotThrowAnyException();
  }

  @Test
  public void shouldPersistTransientSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(this::takeSnapshot);

    // when
    final var persistedSnapshot = transientSnapshot.persist().join();

    // then
    assertThat(persistedSnapshot.getIndex()).isEqualTo(1L);
    assertThat(persistedSnapshot.getTerm()).isZero();

    final var snapshotPath = persistedSnapshot.getPath();
    assertThat(snapshotPath).exists().isNotEqualTo(lastTransientSnapshotPath);
    assertThat(lastTransientSnapshotPath).doesNotExist();

    final var committedSnapshotDir = snapshotPath.toFile();
    assertThat(committedSnapshotDir).hasName(persistedSnapshot.getId());
    assertThat(committedSnapshotDir.listFiles())
        .extracting(File::getName)
        .containsExactlyInAnyOrder("file1.txt", "CHECKSUM");
  }

  @Test
  public void shouldDeleteSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(this::takeSnapshot);
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    persistedSnapshot.delete();

    // then
    final var snapshotPath = persistedSnapshot.getPath();
    assertThat(snapshotPath).doesNotExist();
    assertThat(lastTransientSnapshotPath).doesNotExist();
  }

  @Test
  public void shouldNewSnapshotShouldBeLargerThenOlder() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(this::takeSnapshot);
    final var previousSnapshot = transientSnapshot.persist().join();

    // when
    final var newTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, 1, 0).orElseThrow();
    newTransientSnapshot.take(this::takeSnapshot);
    final var persistedSnapshot = newTransientSnapshot.persist().join();

    // then
    assertThat(previousSnapshot.getId()).isLessThan(persistedSnapshot.getId());
  }

  @Test
  public void shouldReplacePersistedTransientSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(this::takeSnapshot);
    final var previousSnapshot = transientSnapshot.persist().join();

    // when
    final var newTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, 1, 0).orElseThrow();
    newTransientSnapshot.take(this::takeSnapshot);
    final var persistedSnapshot = newTransientSnapshot.persist().join();

    // then
    assertThat(previousSnapshot.getPath()).doesNotExist();

    final var snapshotPath = persistedSnapshot.getPath();
    assertThat(snapshotPath).exists().isNotEqualTo(lastTransientSnapshotPath);

    final var committedSnapshotDir = snapshotPath.toFile();
    assertThat(committedSnapshotDir).hasName(persistedSnapshot.getId());
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
    oldTransientSnapshot.take(this::takeSnapshot).join();
    final var oldTransientSnapshotPath = lastTransientSnapshotPath;

    // when
    final var newSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, 1, 0).orElseThrow();
    newSnapshot.take(this::takeSnapshot).join();
    final var persistedSnapshot = newSnapshot.persist().join();

    // then
    assertThat(lastTransientSnapshotPath).doesNotExist();
    assertThat(oldTransientSnapshotPath).doesNotExist();
    assertThat(persistedSnapshot.getPath()).exists();
  }

  @Test
  public void shouldNotRemovePendingSnapshotOnCommittingSnapshotWhenHigher() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var newTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, 1, 0).orElseThrow();
    newTransientSnapshot.take(this::takeSnapshot).join();
    final var oldTransientSnapshotPath = lastTransientSnapshotPath;

    // when
    final var oldSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    oldSnapshot.take(this::takeSnapshot).join();
    final var persistedSnapshot = oldSnapshot.persist().join();

    // then
    assertThat(lastTransientSnapshotPath).doesNotExist();
    assertThat(oldTransientSnapshotPath).exists();
    assertThat(persistedSnapshot.getPath()).exists();
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
                lastTransientSnapshotPath = path;
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
              return false;
            })
        .join();

    // then
    assertThat(lastTransientSnapshotPath).doesNotExist();
  }

  @Test
  public void shouldCleanUpPendingDirOnException() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var oldTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();

    // when
    final var takenFuture =
        oldTransientSnapshot.take(
            path -> {
              try {
                FileUtil.ensureDirectoryExists(path);
                lastTransientSnapshotPath = path;
                throw new RuntimeException("EXPECTED");
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    assertThatThrownBy(takenFuture::join).isNotNull();

    // then
    assertThat(lastTransientSnapshotPath).doesNotExist();
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
    transientSnapshot.take(this::takeSnapshot);

    // when
    final var persistedSnapshot = transientSnapshot.persist().join();

    // then
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
    transientSnapshot.take(this::takeSnapshot);

    // when
    final var persistedSnapshot = transientSnapshot.persist().join();

    // then
    verify(listener, times(0)).onNewSnapshot(persistedSnapshot);
  }

  private boolean takeSnapshot(final Path path) {
    lastTransientSnapshotPath = path;
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
