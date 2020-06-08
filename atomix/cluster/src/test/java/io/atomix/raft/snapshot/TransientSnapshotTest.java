/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.snapshot;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.atomix.raft.snapshot.impl.FileBasedSnapshotStoreFactory;
import io.atomix.utils.time.WallClockTimestamp;
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
  private PersistedSnapshotStore persistedSnapshotStore;
  private Path lastTransientSnapshotPath;

  @Before
  public void before() {
    final FileBasedSnapshotStoreFactory factory = new FileBasedSnapshotStoreFactory();
    final String partitionName = "1";
    final File root = temporaryFolder.getRoot();

    persistedSnapshotStore = factory.createSnapshotStore(root.toPath(), partitionName);
  }

  @Test
  public void shouldTransientPathDoesntExist() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
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
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
    final AtomicReference<Path> transientPath = new AtomicReference<>();

    // when
    transientSnapshot.take(
        p -> {
          transientPath.set(p);
          return true;
        });

    // then
    assertThat(transientPath.get()).hasFileName("1-0-123");
  }

  @Test
  public void shouldReturnTrueOnSuccessTake() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);

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
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);

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
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);

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
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
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
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);

    // when - then
    transientSnapshot.abort();
  }

  @Test
  public void shouldPersistTransientSnapshot() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(this::takeSnapshot);

    // when
    final var persistedSnapshot = transientSnapshot.persist();

    // then
    assertThat(persistedSnapshot.getIndex()).isEqualTo(1L);
    assertThat(persistedSnapshot.getTerm()).isEqualTo(0L);
    assertThat(persistedSnapshot.getTimestamp()).isEqualTo(time);

    final var snapshotId = persistedSnapshot.getId();
    assertThat(snapshotId.getSnapshotIdAsString()).isEqualTo("1-0-123");
    assertThat(snapshotId.getIndex()).isEqualTo(1L);
    assertThat(snapshotId.getTerm()).isEqualTo(0L);
    assertThat(snapshotId.getTimestamp()).isEqualTo(time);

    final var snapshotPath = persistedSnapshot.getPath();
    assertThat(snapshotPath).exists();
    assertThat(snapshotPath).isNotEqualTo(lastTransientSnapshotPath);
    assertThat(lastTransientSnapshotPath).doesNotExist();

    final var committedSnapshotDir = snapshotPath.toFile();
    assertThat(committedSnapshotDir.getName()).isEqualTo("1-0-123");
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
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
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
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(this::takeSnapshot);
    final var previousSnapshot = transientSnapshot.persist();

    // when
    final var newTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, time);
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
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(this::takeSnapshot);
    final var previousSnapshot = transientSnapshot.persist();

    // when
    final var newTransientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, time);
    newTransientSnapshot.take(this::takeSnapshot);
    final var persistedSnapshot = newTransientSnapshot.persist();

    // then
    assertThat(previousSnapshot.getPath()).doesNotExist();

    final var snapshotPath = persistedSnapshot.getPath();
    assertThat(snapshotPath).exists();
    assertThat(snapshotPath).isNotEqualTo(lastTransientSnapshotPath);

    final var committedSnapshotDir = snapshotPath.toFile();
    assertThat(committedSnapshotDir.getName()).isEqualTo("2-0-123");
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
    final var oldTransientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
    oldTransientSnapshot.take(this::takeSnapshot);
    final var oldTransientSnapshotPath = this.lastTransientSnapshotPath;

    // when
    final var newSnapshot = persistedSnapshotStore.newTransientSnapshot(index + 1, term, time);
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
        persistedSnapshotStore.newTransientSnapshot(index + 1, term, time);
    newTransientSnapshot.take(this::takeSnapshot);
    final var oldTransientSnapshotPath = this.lastTransientSnapshotPath;

    // when
    final var oldSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
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
    final var oldTransientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);

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
    final var oldTransientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);

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
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
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
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
    persistedSnapshotStore.addSnapshotListener(listener);
    persistedSnapshotStore.removeSnapshotListener(listener);
    transientSnapshot.take(this::takeSnapshot);

    // when
    final var persistedSnapshot = transientSnapshot.persist();

    // then
    verify(listener, times(0)).onNewSnapshot(eq(persistedSnapshot));
  }

  private boolean takeSnapshot(final Path path) {
    this.lastTransientSnapshotPath = path;
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
