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
package io.atomix.raft.snapshot.impl;

import static io.atomix.raft.snapshot.impl.SnapshotChunkWrapper.withDifferentChecksum;
import static io.atomix.raft.snapshot.impl.SnapshotChunkWrapper.withDifferentSnapshotChecksum;
import static io.atomix.raft.snapshot.impl.SnapshotChunkWrapper.withDifferentSnapshotId;
import static io.atomix.raft.snapshot.impl.SnapshotChunkWrapper.withDifferentTotalCount;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.atomix.raft.snapshot.PersistedSnapshot;
import io.atomix.raft.snapshot.PersistedSnapshotListener;
import io.atomix.raft.snapshot.PersistedSnapshotStore;
import io.atomix.raft.snapshot.ReceivedSnapshot;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileBasedReceivedSnapshotTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private PersistedSnapshotStore senderSnapshotStore;
  private PersistedSnapshotStore receiverSnapshotStore;
  private Path receiverSnapshotsDir;
  private Path receiverPendingSnapshotsDir;
  private FileBasedSnapshotStoreFactory factory;

  @Before
  public void before() throws Exception {
    factory = new FileBasedSnapshotStoreFactory();
    final String partitionName = "1";
    final File senderRoot = temporaryFolder.newFolder("sender");

    senderSnapshotStore = factory.createSnapshotStore(senderRoot.toPath(), partitionName);

    final var receiverRoot = temporaryFolder.newFolder("received");
    receiverSnapshotStore = factory.createSnapshotStore(receiverRoot.toPath(), partitionName);

    receiverSnapshotsDir =
        receiverRoot.toPath().resolve(FileBasedSnapshotStoreFactory.SNAPSHOTS_DIRECTORY);
    receiverPendingSnapshotsDir =
        receiverRoot.toPath().resolve(FileBasedSnapshotStoreFactory.PENDING_DIRECTORY);
  }

  @Test
  public void shouldNotCreateDirDirectlyOnNewReceivedSnapshot() {
    // given

    // when
    senderSnapshotStore.newReceivedSnapshot("1-0-123");

    // then
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();
    assertThat(receiverSnapshotsDir.toFile().listFiles()).isEmpty();
  }

  @Test
  public void shouldWriteChunkInPendingDirOnApplyChunk() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    takeAndReceiveSnapshot(index, term, time);

    // then
    assertThat(receiverSnapshotsDir.toFile().listFiles()).isEmpty();

    assertThat(receiverPendingSnapshotsDir).exists();
    final var files = receiverPendingSnapshotsDir.toFile().listFiles();
    assertThat(files).isNotNull().hasSize(1);

    final var dir = files[0];
    assertThat(dir).hasName("1-0-123-1");

    final var snapshotFileList = dir.listFiles();
    assertThat(snapshotFileList).isNotNull().extracting(File::getName).containsExactly("file1.txt");
  }

  @Test
  public void shouldDeletePendingSnapshotDirOnAbort() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final PersistedSnapshot persistedSnapshot = takeSnapshot(index, term, time);

    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(
            persistedSnapshot.getId().getSnapshotIdAsString());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(snapshotChunkReader.next());
      }
    }

    // when
    receivedSnapshot.abort();

    // then
    assertThat(receiverSnapshotsDir.toFile().listFiles()).isEmpty();
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();
  }

  @Test
  public void shouldPurgePendingOnStore() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    takeAndReceiveSnapshot(index, term, time);

    // when
    receiverSnapshotStore.purgePendingSnapshots();

    // then
    assertThat(receiverSnapshotsDir.toFile().listFiles()).isEmpty();
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();
  }

  @Test
  public void shouldPersistSnapshot() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var receivedSnapshot = takeAndReceiveSnapshot(index, term, time);

    // when
    final var snapshot = receivedSnapshot.persist();

    // then
    assertThat(snapshot).isNotNull();
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();

    assertThat(receiverSnapshotsDir).exists();
    final var files = receiverSnapshotsDir.toFile().listFiles();
    assertThat(files).isNotNull().hasSize(1);

    final var dir = files[0];
    assertThat(dir).hasName("1-0-123");

    final var snapshotFileList = dir.listFiles();
    assertThat(snapshotFileList).isNotNull().extracting(File::getName).containsExactly("file1.txt");
  }

  @Test
  public void shouldNotDeletePersistedSnapshotOnPurgePendingOnStore() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    takeAndReceiveSnapshot(index, term, time).persist();

    // when
    receiverSnapshotStore.purgePendingSnapshots();

    // then
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();

    assertThat(receiverSnapshotsDir).exists();
    final var files = receiverSnapshotsDir.toFile().listFiles();
    assertThat(files).isNotNull().hasSize(1);

    final var dir = files[0];
    assertThat(dir).hasName("1-0-123");

    final var snapshotFileList = dir.listFiles();
    assertThat(snapshotFileList).isNotNull().extracting(File::getName).containsExactly("file1.txt");
  }

  @Test
  public void shouldReplaceSnapshotOnNextSnapshot() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    takeAndReceiveSnapshot(index, term, time).persist();

    // when
    takeAndReceiveSnapshot(index + 1, term, time).persist();

    // then
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();

    final var snapshotDirs = receiverSnapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).isNotNull().hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(committedSnapshotDir.getName()).isEqualTo("2-0-123");
    assertThat(committedSnapshotDir.listFiles())
        .isNotNull()
        .extracting(File::getName)
        .containsExactly("file1.txt");
  }

  @Test
  public void shouldRemovePendingSnapshotOnCommittingSnapshot() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    takeAndReceiveSnapshot(index, term, time);

    // when
    takeAndReceiveSnapshot(index + 1, term, time).persist();

    // then
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();

    final var snapshotDirs = receiverSnapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).isNotNull().hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(committedSnapshotDir.getName()).isEqualTo("2-0-123");
    assertThat(committedSnapshotDir.listFiles())
        .isNotNull()
        .extracting(File::getName)
        .containsExactly("file1.txt");
  }

  @Test
  public void shouldNotRemovePendingSnapshotOnCommittingSnapshotWhenHigher() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);

    final var otherStore =
        factory.createSnapshotStore(temporaryFolder.newFolder("other").toPath(), "1");
    final var olderTransient = otherStore.newTransientSnapshot(index, term, time);
    olderTransient.take(this::takeSnapshot);
    final var olderPersistedSnapshot = olderTransient.persist();

    final PersistedSnapshot newPersistedSnapshot = takeSnapshot(index + 1, term, time);
    receiveSnapshot(newPersistedSnapshot);

    // when
    receiveSnapshot(olderPersistedSnapshot).persist();

    // then
    final var pendingSnapshotDirs = receiverPendingSnapshotsDir.toFile().listFiles();
    assertThat(pendingSnapshotDirs).isNotNull().hasSize(1);

    final var pendingSnapshotDir = pendingSnapshotDirs[0];
    assertThat(pendingSnapshotDir.getName()).isEqualTo("2-0-123-1");
    assertThat(pendingSnapshotDir.listFiles())
        .isNotNull()
        .extracting(File::getName)
        .containsExactly("file1.txt");

    final var snapshotDirs = receiverSnapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).isNotNull().hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(committedSnapshotDir.getName()).isEqualTo("1-0-123");
    assertThat(committedSnapshotDir.listFiles())
        .isNotNull()
        .extracting(File::getName)
        .containsExactly("file1.txt");
  }

  @Test
  public void shouldNotifyListenersOnNewSnapshot() throws Exception {
    // given
    final var listener = mock(PersistedSnapshotListener.class);
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    receiverSnapshotStore.addSnapshotListener(listener);

    // when
    final var persistedSnapshot = takeAndReceiveSnapshot(index, term, time).persist();

    // then
    verify(listener, times(1)).onNewSnapshot(eq(persistedSnapshot));
  }

  @Test
  public void shouldNotNotifyListenersOnNewSnapshotWhenDeregistered() throws Exception {
    // given
    final var listener = mock(PersistedSnapshotListener.class);
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    senderSnapshotStore.addSnapshotListener(listener);
    senderSnapshotStore.removeSnapshotListener(listener);

    // when
    final var persistedSnapshot = takeAndReceiveSnapshot(index, term, time).persist();

    // then
    verify(listener, times(0)).onNewSnapshot(eq(persistedSnapshot));
  }

  @Test
  public void shouldReceiveConcurrentlyButWriteInDifferentPendingDirs() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var persistedSnapshot = takeSnapshot(index, term, time);

    // when
    receiveSnapshot(persistedSnapshot);
    receiveSnapshot(persistedSnapshot);

    // then
    assertThat(receiverSnapshotsDir.toFile().listFiles()).isEmpty();

    assertThat(receiverPendingSnapshotsDir).exists();
    final var fileArray = receiverPendingSnapshotsDir.toFile().listFiles();
    assertThat(fileArray).isNotNull();
    final var files = Arrays.stream(fileArray).sorted().collect(Collectors.toList());
    assertThat(files).isNotNull().hasSize(2);

    final var dir = files.get(0);
    assertThat(dir).hasName("1-0-123-1");

    final var snapshotFileList = dir.listFiles();
    assertThat(snapshotFileList).isNotNull().extracting(File::getName).containsExactly("file1.txt");

    final var otherDir = files.get(1);
    assertThat(otherDir).hasName("1-0-123-2");

    final var otherSnapshotFileList = dir.listFiles();
    assertThat(otherSnapshotFileList)
        .isNotNull()
        .extracting(File::getName)
        .containsExactly("file1.txt");
  }

  @Test
  public void shouldReceiveConcurrentlyAndPersist() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var persistedSnapshot = takeSnapshot(index, term, time);
    final var receivedSnapshot = receiveSnapshot(persistedSnapshot);
    final var otherReceivedSnapshot = receiveSnapshot(persistedSnapshot);

    // when
    final var receivedPersisted = receivedSnapshot.persist();
    final var otherReceivedPersisted = otherReceivedSnapshot.persist();

    // then
    assertThat(receivedPersisted).isEqualTo(otherReceivedPersisted);

    final var snapshotDirs = receiverSnapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).isNotNull().hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(committedSnapshotDir.getName()).isEqualTo("1-0-123");
    assertThat(committedSnapshotDir.listFiles())
        .isNotNull()
        .extracting(File::getName)
        .containsExactly("file1.txt");

    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();
  }

  @Test
  public void shouldReceiveConcurrentlyAndPersistDoesnotDependOnTheOrder() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var persistedSnapshot = takeSnapshot(index, term, time);
    final var receivedSnapshot = receiveSnapshot(persistedSnapshot);
    final var otherReceivedSnapshot = receiveSnapshot(persistedSnapshot);

    // when
    final var otherReceivedPersisted = otherReceivedSnapshot.persist();
    final var receivedPersisted = receivedSnapshot.persist();

    // then
    assertThat(receivedPersisted).isEqualTo(otherReceivedPersisted);

    final var snapshotDirs = receiverSnapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).isNotNull().hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(committedSnapshotDir.getName()).isEqualTo("1-0-123");
    assertThat(committedSnapshotDir.listFiles())
        .isNotNull()
        .extracting(File::getName)
        .containsExactly("file1.txt");

    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();
  }

  @Test
  public void shouldBeAbleToAbortAfterPersistingDoesntWork() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(
            persistedSnapshot.getId().getSnapshotIdAsString());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(withDifferentSnapshotChecksum(snapshotChunkReader.next(), 0xCAFEL));
      }
    }
    assertThatThrownBy(receivedSnapshot::persist).isInstanceOf(IllegalStateException.class);

    // when
    receivedSnapshot.abort();

    // then
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();
    assertThat(receiverSnapshotsDir.toFile().listFiles()).isEmpty();
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithInvalidSnapshotChecksum() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(
            persistedSnapshot.getId().getSnapshotIdAsString());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      var success = receivedSnapshot.apply(snapshotChunkReader.next());
      assertThat(success).isTrue();
      success =
          receivedSnapshot.apply(
              withDifferentSnapshotChecksum(snapshotChunkReader.next(), 0xCAFEL));

      // then
      assertThat(success).isFalse();
    }
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithInvalidChunkChecksum() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(
            persistedSnapshot.getId().getSnapshotIdAsString());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final var success =
          receivedSnapshot.apply(withDifferentChecksum(snapshotChunkReader.next(), 0xCAFEL));

      // then
      assertThat(success).isFalse();
    }
  }

  @Test
  public void shouldNotPersistWhenSnapshotChecksumIsWrong() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(
            persistedSnapshot.getId().getSnapshotIdAsString());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(withDifferentSnapshotChecksum(snapshotChunkReader.next(), 0xCAFEL));
      }
    }

    // when - then
    assertThatThrownBy(receivedSnapshot::persist).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithNotEqualTotalCount() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(
            persistedSnapshot.getId().getSnapshotIdAsString());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      var success = receivedSnapshot.apply(snapshotChunkReader.next());
      assertThat(success).isTrue();
      success = receivedSnapshot.apply(withDifferentTotalCount(snapshotChunkReader.next(), 55));

      // then
      assertThat(success).isFalse();
    }
  }

  @Test
  public void shouldNotPersistWhenTotalCountIsWrong() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(
            persistedSnapshot.getId().getSnapshotIdAsString());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(withDifferentTotalCount(snapshotChunkReader.next(), 2));
      }
    }

    // when - then
    assertThatThrownBy(receivedSnapshot::persist).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithNotEqualSnapshotId() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(
            persistedSnapshot.getId().getSnapshotIdAsString());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      var success = receivedSnapshot.apply(snapshotChunkReader.next());
      assertThat(success).isTrue();
      success = receivedSnapshot.apply(withDifferentSnapshotId(snapshotChunkReader.next(), "id"));

      // then
      assertThat(success).isFalse();
    }
  }

  private ReceivedSnapshot takeAndReceiveSnapshot(
      final long index, final long term, final WallClockTimestamp time) throws IOException {
    final PersistedSnapshot persistedSnapshot = takeSnapshot(index, term, time);

    return receiveSnapshot(persistedSnapshot);
  }

  private PersistedSnapshot takeSnapshot(
      final long index, final long term, final WallClockTimestamp time) {
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(this::takeSnapshot);
    return transientSnapshot.persist();
  }

  private ReceivedSnapshot receiveSnapshot(final PersistedSnapshot persistedSnapshot)
      throws IOException {
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(
            persistedSnapshot.getId().getSnapshotIdAsString());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(snapshotChunkReader.next());
      }
    }

    return receivedSnapshot;
  }

  private boolean takeSnapshot(final Path path) {
    return takeSnapshot(path, List.of("file1.txt"), List.of("This is the content"));
  }

  private boolean takeSnapshot(
      final Path path, final List<String> fileNames, final List<String> fileContents) {
    assertThat(fileNames).hasSize(fileContents.size());

    try {
      FileUtil.ensureDirectoryExists(path);

      for (int i = 0; i < fileNames.size(); i++) {
        final var fileName = fileNames.get(i);
        final var fileContent = fileContents.get(i);
        Files.write(
            path.resolve(fileName), fileContent.getBytes(), CREATE_NEW, StandardOpenOption.WRITE);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
  }
}
