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

import io.atomix.raft.snapshot.impl.FileBasedSnapshotStoreFactory;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReceviedSnapshotTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private PersistedSnapshotStore senderSnapshotStore;
  private PersistedSnapshotStore receiverSnapshotStore;

  @Before
  public void before() throws Exception {
    final FileBasedSnapshotStoreFactory factory = new FileBasedSnapshotStoreFactory();
    final String partitionName = "1";

    senderSnapshotStore =
        factory.createSnapshotStore(temporaryFolder.newFolder("sender").toPath(), partitionName);
    receiverSnapshotStore =
        factory.createSnapshotStore(temporaryFolder.newFolder("received").toPath(), partitionName);
  }

  @Test
  public void shouldThrowExceptionOnInvalidSnapshotId() {
    // given

    // when
    assertThatThrownBy(() -> receiverSnapshotStore.newReceivedSnapshot("invalid"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldReturnIndexOfSnapshot() {
    // given

    // when
    final var receivedSnapshot = receiverSnapshotStore.newReceivedSnapshot("1-0-123");

    // then
    assertThat(receivedSnapshot.index()).isEqualTo(1L);
  }

  @Test
  public void shouldPersistReceivedSnapshotChunks() throws Exception {
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
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(snapshotChunkReader.next());
      }
    }
    final var receivedPersistedSnapshot = receivedSnapshot.persist();

    // then
    // path is different
    assertThat(receivedPersistedSnapshot).isNotEqualTo(persistedSnapshot);

    assertThat(receivedPersistedSnapshot.getPath()).isNotEqualTo(persistedSnapshot.getPath());
    assertThat(receivedPersistedSnapshot.getIndex()).isEqualTo(persistedSnapshot.getIndex());
    assertThat(receivedPersistedSnapshot.getTerm()).isEqualTo(persistedSnapshot.getTerm());
    assertThat(receivedPersistedSnapshot.getCompactionBound())
        .isEqualTo(persistedSnapshot.getCompactionBound());
    assertThat(receivedPersistedSnapshot.getTimestamp())
        .isEqualTo(persistedSnapshot.getTimestamp());
    assertThat(receivedPersistedSnapshot.getId()).isEqualTo(persistedSnapshot.getId());
  }

  @Test
  public void shouldReturnTrueOnConsumingChunk() throws Exception {
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
      final var success = receivedSnapshot.apply(snapshotChunkReader.next());

      // then
      assertThat(success).isTrue();
    }
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkTwice() throws Exception {
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
      receivedSnapshot.apply(snapshotChunkReader.next());
    }

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final var success = receivedSnapshot.apply(snapshotChunkReader.next());

      // then
      assertThat(success).isFalse();
    }
  }

  @Test
  public void shouldReturnTrueWhenSnapshotAlreadyExist() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();

    // when - sender store receives chunk
    final var receivedSnapshot =
        senderSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId().getSnapshotIdAsString());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        final var success = receivedSnapshot.apply(snapshotChunkReader.next());

        // then
        assertThat(success).isTrue();
      }
    }
  }

  @Test
  public void shouldReturnAlreadyExistingSnapshotOnPersist() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();

    // when - sender store receives chunk
    final var receivedSnapshot =
        senderSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId().getSnapshotIdAsString());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        final var success = receivedSnapshot.apply(snapshotChunkReader.next());

        // then
        assertThat(success).isTrue();
      }
    }
    final var persistedReceivedSnapshot = receivedSnapshot.persist();

    // then
    assertThat(persistedReceivedSnapshot).isEqualTo(persistedSnapshot);
    assertThat(persistedReceivedSnapshot == persistedSnapshot).isTrue();
  }

  @Test
  public void shouldCheckForExistingChunk() throws Exception {
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
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(snapshotChunkReader.next());
      }
    }

    // then
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        assertThat(receivedSnapshot.containsChunk(snapshotChunkReader.nextId())).isTrue();
        snapshotChunkReader.next();
      }
    }
  }

  @Test
  public void shouldNotExistChunkWithDifferentReceivingSnapshot() throws Exception {
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
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(snapshotChunkReader.next());
      }
    }

    // then
    final var otherReceivingSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(
            persistedSnapshot.getId().getSnapshotIdAsString());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        assertThat(otherReceivingSnapshot.containsChunk(snapshotChunkReader.nextId())).isFalse();
        snapshotChunkReader.next();
      }
    }
  }

  @Test
  public void shouldCheckForNextChunk() {
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
      receivedSnapshot.setNextExpected(snapshotChunkReader.nextId());
    }

    // then
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      assertThat(receivedSnapshot.isExpectedChunk(snapshotChunkReader.nextId())).isTrue();
    }
  }

  @Test
  public void shouldThrowExceptionOnPersistWhenNoChunkApplied() {
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

    // when
    assertThatThrownBy(receivedSnapshot::persist).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldNotThrowExceptionOnAbortWhenNoChunkApplied() {
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

    // when - then
    receivedSnapshot.abort();
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
