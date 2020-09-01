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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.snapshots.broker.ConstructableSnapshotStore;
import io.zeebe.snapshots.raft.PersistedSnapshotListener;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TransientSnapshotTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private ConstructableSnapshotStore persistedSnapshotStore;
  private Path lastTransientSnapshotPath;

  @Before
  public void before() {
    final FileBasedSnapshotStoreFactory factory = new FileBasedSnapshotStoreFactory();
    final String partitionName = "1";
    final File root = temporaryFolder.getRoot();

    factory.createReceivableSnapshotStore(root.toPath(), partitionName);
    persistedSnapshotStore = factory.getConstructableSnapshotStore(partitionName);
  }

  @Test
  public void shouldTransientPathDoesntExist() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    final AtomicReference<Path> transientPath = new AtomicReference<>();

    // when
    transientSnapshot.take(
        p -> {
          transientPath.set(p);
          return true;
        });

    // then
    assertThat(transientPath.get()).doesNotExist();
  }

  @Test
  public void shouldTransientPathContainsMetadata() {
    // given
    final var index = 1L;
    final var term = 2L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 3, 4).get();
    final AtomicReference<Path> transientPath = new AtomicReference<>();

    // when
    transientSnapshot.take(
        p -> {
          transientPath.set(p);
          return true;
        });

    // then
    final var metadata =
        FileBasedSnapshotMetadata.ofFileName(transientPath.get().getFileName().toString()).get();
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
    final var success = transientSnapshot.take(p -> true);

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
    final var success = transientSnapshot.take(p -> false);

    // then
    assertThat(success).isFalse();
  }

  @Test
  public void shouldReturnFalseOnExceptionInTake() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();

    // when
    final var success =
        transientSnapshot.take(
            p -> {
              throw new RuntimeException("EXPECTED");
            });

    // then
    assertThat(success).isFalse();
  }

  @Test
  public void shouldFailToPersistWhenTakeDoesntWrote() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(p -> true);

    // when - then
    assertThatThrownBy(transientSnapshot::persist).hasCauseInstanceOf(NoSuchFileException.class);
  }

  @Test
  public void shouldBeAbleToAbortNotStartedSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();

    // when - then
    transientSnapshot.abort();
  }

  @Test
  public void shouldPersistTransientSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(this::takeSnapshot);

    // when
    final var persistedSnapshot = transientSnapshot.persist();

    // then
    assertThat(persistedSnapshot.getIndex()).isEqualTo(1L);
    assertThat(persistedSnapshot.getTerm()).isEqualTo(0L);

    final var snapshotPath = persistedSnapshot.getPath();
    assertThat(snapshotPath).exists();
    assertThat(snapshotPath).isNotEqualTo(lastTransientSnapshotPath);
    assertThat(lastTransientSnapshotPath).doesNotExist();

    final var committedSnapshotDir = snapshotPath.toFile();
    assertThat(committedSnapshotDir.getName()).isEqualTo(persistedSnapshot.getId());
    assertThat(committedSnapshotDir.listFiles())
        .isNotNull()
        .extracting(File::getName)
        .containsExactly("file1.txt");
  }

  @Test
  public void shouldDeleteSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(this::takeSnapshot);
    final var persistedSnapshot = transientSnapshot.persist();

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
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(this::takeSnapshot);
    final var previousSnapshot = transientSnapshot.persist();

    // when
    final var newTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, 1, 0).get();
    newTransientSnapshot.take(this::takeSnapshot);
    final var persistedSnapshot = newTransientSnapshot.persist();

    // then
    assertThat(previousSnapshot.getId()).isLessThan(persistedSnapshot.getId());
  }

  @Test
  public void shouldReplacePersistedTransientSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(this::takeSnapshot);
    final var previousSnapshot = transientSnapshot.persist();

    // when
    final var newTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, 1, 0).get();
    newTransientSnapshot.take(this::takeSnapshot);
    final var persistedSnapshot = newTransientSnapshot.persist();

    // then
    assertThat(previousSnapshot.getPath()).doesNotExist();

    final var snapshotPath = persistedSnapshot.getPath();
    assertThat(snapshotPath).exists();
    assertThat(snapshotPath).isNotEqualTo(lastTransientSnapshotPath);

    final var committedSnapshotDir = snapshotPath.toFile();
    assertThat(committedSnapshotDir.getName()).isEqualTo(persistedSnapshot.getId());
    assertThat(committedSnapshotDir.listFiles())
        .isNotNull()
        .extracting(File::getName)
        .containsExactly("file1.txt");
  }

  @Test
  public void shouldRemovePendingSnapshotOnCommittingSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var oldTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    oldTransientSnapshot.take(this::takeSnapshot);
    final var oldTransientSnapshotPath = lastTransientSnapshotPath;

    // when
    final var newSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, 1, 0).get();
    newSnapshot.take(this::takeSnapshot);
    final var persistedSnapshot = newSnapshot.persist();

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
    final var time = WallClockTimestamp.from(123);
    final var newTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, 1, 0).get();
    newTransientSnapshot.take(this::takeSnapshot);
    final var oldTransientSnapshotPath = lastTransientSnapshotPath;

    // when
    final var oldSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    oldSnapshot.take(this::takeSnapshot);
    final var persistedSnapshot = oldSnapshot.persist();

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
    final var time = WallClockTimestamp.from(123);
    final var oldTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();

    // when
    oldTransientSnapshot.take(
        path -> {
          try {
            FileUtil.ensureDirectoryExists(path);
            lastTransientSnapshotPath = path;
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
          return false;
        });

    // then
    assertThat(lastTransientSnapshotPath).doesNotExist();
  }

  @Test
  public void shouldCleanUpPendingDirOnException() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var oldTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();

    // when
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

    // then
    assertThat(lastTransientSnapshotPath).doesNotExist();
  }

  @Test
  public void shouldNotifyListenersOnNewSnapshot() {
    // given
    final var listener = mock(PersistedSnapshotListener.class);
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    persistedSnapshotStore.addSnapshotListener(listener);
    transientSnapshot.take(this::takeSnapshot);

    // when
    final var persistedSnapshot = transientSnapshot.persist();

    // then
    verify(listener, times(1)).onNewSnapshot(eq(persistedSnapshot));
  }

  @Test
  public void shouldNotNotifyListenersOnNewSnapshotWhenDeregistered() {
    // given
    final var listener = mock(PersistedSnapshotListener.class);
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    persistedSnapshotStore.addSnapshotListener(listener);
    persistedSnapshotStore.removeSnapshotListener(listener);
    transientSnapshot.take(this::takeSnapshot);

    // when
    final var persistedSnapshot = transientSnapshot.persist();

    // then
    verify(listener, times(0)).onNewSnapshot(eq(persistedSnapshot));
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
