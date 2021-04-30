/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.zeebe.snapshots.impl.InvalidSnapshotChecksum;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReceivedSnapshotTest {
  private static final Map<String, String> SNAPSHOT_FILE_CONTENTS =
      Map.of(
          "file1", "file1 contents",
          "file2", "file2 contents");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public ActorSchedulerRule scheduler = new ActorSchedulerRule();

  private ConstructableSnapshotStore senderSnapshotStore;
  private ReceivableSnapshotStore receiverSnapshotStore;

  @Before
  public void beforeEach() throws Exception {
    final int partitionId = 1;

    final var senderFactory = new FileBasedSnapshotStoreFactory(scheduler.get(), 1);
    senderFactory.createReceivableSnapshotStore(
        temporaryFolder.newFolder("sender").toPath(), partitionId);
    senderSnapshotStore = senderFactory.getConstructableSnapshotStore(partitionId);
    receiverSnapshotStore =
        new FileBasedSnapshotStoreFactory(scheduler.get(), 2)
            .createReceivableSnapshotStore(
                temporaryFolder.newFolder("received").toPath(), partitionId);
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
    final var receivedSnapshot = receiverSnapshotStore.newReceivedSnapshot("1-0-123-121");

    // then
    assertThat(receivedSnapshot.index()).isEqualTo(1L);
  }

  @Test
  public void shouldNotCommitUntilPersisted() throws IOException {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot = receiveSnapshot(persistedSnapshot);

    // then
    assertThat(receivedSnapshot.getPath()).as("there exists a pending snapshot").isDirectory();
    assertThat(receiverSnapshotStore.getLatestSnapshot())
        .as("the pending snapshot was not committed")
        .isEmpty();
  }

  @Test
  public void shouldPurgePendingOnPersist() throws IOException {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot = receiveSnapshot(persistedSnapshot);
    final var receivedPersistedSnapshot = receivedSnapshot.persist().join();

    // then
    assertThat(receivedSnapshot.getPath())
        .as("the pending snapshot was removed after persist")
        .doesNotExist();
    assertThat(receivedPersistedSnapshot.getPath())
        .as("there exists a persisted received snapshot")
        .exists();
  }

  @Test
  public void shouldReceiveSnapshot() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot = receiveSnapshot(persistedSnapshot).persist().join();

    // then
    assertThat(receiverSnapshotStore.getLatestSnapshot())
        .as("the received snapshot was committed and is the latest snapshot")
        .hasValue(receivedSnapshot);
    assertThat(receivedSnapshot.getChecksum())
        .as("the received snapshot has the same checksum as the sent snapshot")
        .isEqualTo(persistedSnapshot.getChecksum());
    assertThat(receivedSnapshot.getId())
        .as("the received snapshot has the same ID as the sent snapshot")
        .isEqualTo(persistedSnapshot.getId());
  }

  @Test
  public void shouldDeletePendingSnapshotDirOnAbort() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();
    final var receivedSnapshot = receiveSnapshot(persistedSnapshot);

    // when
    receivedSnapshot.abort().join();

    // then
    assertThat(receivedSnapshot.getPath())
        .as("the pending snapshot does not exist anymore after purging")
        .doesNotExist();
  }

  @Test
  public void shouldNotDeletePersistedSnapshotOnPurgePendingOnStore() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();
    final var receivedSnapshot = receiveSnapshot(persistedSnapshot).persist().join();

    // when
    receiverSnapshotStore.purgePendingSnapshots().join();

    // then
    assertThat(receivedSnapshot.getPath()).as("the received snapshot still exists").exists();
    assertThat(receivedSnapshot)
        .as("the previous snapshot should still be the latest snapshot")
        .isEqualTo(receiverSnapshotStore.getLatestSnapshot().orElseThrow());
    assertThat(receivedSnapshot.getChecksum())
        .as("the received snapshot still has the same checksum")
        .isEqualTo(persistedSnapshot.getChecksum());
  }

  @Test
  public void shouldReturnTrueOnConsumingChunk() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final boolean chunkApplied;
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      chunkApplied = receivedSnapshot.apply(snapshotChunkReader.next()).join();
    }

    // then
    assertThat(chunkApplied).as("the chunk should be successfully applied").isTrue();
  }

  @Test
  public void shouldPersistEvenIfSameChunkIsConsumedMultipleTimes() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final boolean chunkApplied;
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final SnapshotChunk chunk = snapshotChunkReader.next();
      receivedSnapshot.apply(chunk).join();
      chunkApplied = receivedSnapshot.apply(chunk).join();

      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(snapshotChunkReader.next()).join();
      }
    }

    final var receivedPersistedSnapshot = receivedSnapshot.persist().join();

    // then
    assertThat(chunkApplied).as("the chunk was successfully applied twice in a row").isTrue();
    assertThat(receivedPersistedSnapshot)
        .as("the snapshot was persisted even if one chunk was applied more than once")
        .isEqualTo(receiverSnapshotStore.getLatestSnapshot().orElseThrow());
  }

  @Test
  public void shouldReturnAlreadyExistingSnapshotOnPersist() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();
    final var firstReceivedSnapshot = receiveSnapshot(persistedSnapshot);
    final var firstPersistedSnapshot = firstReceivedSnapshot.persist().join();

    // when - receives same snapshot again
    final var secondReceivedSnapshot = receiveSnapshot(persistedSnapshot);
    final var secondPersistedSnapshot = secondReceivedSnapshot.persist().join();

    // then
    assertThat(secondPersistedSnapshot)
        .isEqualTo(firstPersistedSnapshot)
        .isSameAs(firstPersistedSnapshot);
  }

  @Test
  public void shouldThrowExceptionOnPersistWhenNoChunkApplied() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    // when
    assertThatThrownBy(() -> receivedSnapshot.persist().join())
        .hasCauseInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldBeAbleToAbortAfterPersistFails() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      receivedSnapshot.apply(snapshotChunkReader.next()).join();
    }

    // when
    assertThatThrownBy(() -> receivedSnapshot.persist().join())
        .as("the received snapshot is partial and should not be persisted")
        .hasCauseInstanceOf(IllegalStateException.class);
    receivedSnapshot.abort().join();

    // then
    assertThat(receivedSnapshot.getPath())
        .as("the corrupted pending snapshot was deleted on abort")
        .doesNotExist();
  }

  @Test
  public void shouldNotThrowExceptionOnAbortWhenNoChunkApplied() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    // when - then
    assertThatCode(receivedSnapshot::abort).doesNotThrowAnyException();
  }

  @Test
  public void shouldReceiveConcurrentlyWithoutOverwritingEachOther() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final ReceivedSnapshot firstReceivedSnapshot = receiveSnapshot(persistedSnapshot);
    final ReceivedSnapshot secondReceivedSnapshot = receiveSnapshot(persistedSnapshot);

    // then
    assertThat(firstReceivedSnapshot.getPath())
        .as("the first received snapshot is stored somewhere else than the second")
        .exists()
        .isNotEqualTo(secondReceivedSnapshot.getPath());
    assertThat(secondReceivedSnapshot.getPath()).as("the second received snapshot exists").exists();
  }

  @Test
  public void shouldReceiveConcurrentlyAndPersist() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final ReceivedSnapshot firstReceivedSnapshot = receiveSnapshot(persistedSnapshot);
    final ReceivedSnapshot secondReceivedSnapshot = receiveSnapshot(persistedSnapshot);
    final PersistedSnapshot receivedPersistedSnapshot = firstReceivedSnapshot.persist().join();

    // then
    assertThat(firstReceivedSnapshot.getPath())
        .as("the first received snapshot was removed on persist of concurrent receive")
        .doesNotExist();
    assertThat(secondReceivedSnapshot.getPath())
        .as("the second received snapshot was not removed as it's not considered older")
        .exists();
    assertThat(receivedPersistedSnapshot.getChecksum())
        .as("the received, persisted snapshot have the same checksum as the persisted one")
        .isEqualTo(persistedSnapshot.getChecksum());
  }

  @Test
  public void shouldDoNothingOnPersistOfAlreadyCommittedSnapshot() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();
    final var receivedSnapshot = receiveSnapshot(persistedSnapshot);
    final var otherReceivedSnapshot = receiveSnapshot(persistedSnapshot);

    // when
    final var otherReceivedPersisted = otherReceivedSnapshot.persist().join();
    final var receivedPersisted = receivedSnapshot.persist().join();

    // then
    assertThat(receivedPersisted)
        .as("the last persisted snapshot is the same as the first one as they have the same ID")
        .isEqualTo(otherReceivedPersisted);
    assertThat(receivedSnapshot.getPath())
        .as("the received snapshot was removed on persist of the other snapshot")
        .doesNotExist();
    assertThat(receivedPersisted.getChecksum())
        .as("the received, persisted snapshot have the same checksum as the persisted one")
        .isEqualTo(persistedSnapshot.getChecksum());
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithInvalidSnapshotChecksum() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final boolean chunkApplied;
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      receivedSnapshot.apply(snapshotChunkReader.next()).join();

      final var originalChunk = snapshotChunkReader.next();
      final SnapshotChunk corruptedChunk =
          SnapshotChunkWrapper.withSnapshotChecksum(originalChunk, 0xCAFEL);
      chunkApplied = receivedSnapshot.apply(corruptedChunk).join();
    }

    // then
    assertThat(chunkApplied)
        .as("the snapshot chunk should not be applied as it had a different snapshot checksum")
        .isFalse();
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithInvalidChunkChecksum() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final boolean chunkApplied;
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final SnapshotChunk originalChunk = snapshotChunkReader.next();
      final SnapshotChunk corruptedChunk =
          SnapshotChunkWrapper.withChecksum(originalChunk, 0xCAFEL);
      chunkApplied = receivedSnapshot.apply(corruptedChunk).join();
    }

    // then
    assertThat(chunkApplied)
        .as("the chunk should not be applied as its content checksum is not 0xCAFEL")
        .isFalse();
  }

  @Test
  public void shouldNotPersistWhenSnapshotChecksumIsWrong() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        final var originalChunk = snapshotChunkReader.next();
        final var corruptedChunk =
            SnapshotChunkWrapper.withSnapshotChecksum(originalChunk, 0xDEADBEEFL);
        receivedSnapshot.apply(corruptedChunk).join();
      }
    }

    // then
    assertThatThrownBy(() -> receivedSnapshot.persist().join())
        .as(
            "the snapshot should not be persisted since the computed checksum is not the reported one")
        .hasCauseInstanceOf(InvalidSnapshotChecksum.class);
  }

  @Test
  public void shouldNotPersistWhenSnapshotIsPartial() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final var firstChunk = snapshotChunkReader.next();
      receivedSnapshot.apply(firstChunk).join();
    }

    // then
    assertThatThrownBy(() -> receivedSnapshot.persist().join())
        .as("the snapshot should be persisted as it's corrupted due to a missing chunk")
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithNotEqualTotalCount() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final boolean chunkApplied;
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final var firstChunk = snapshotChunkReader.next();
      receivedSnapshot.apply(firstChunk).join();

      final var corruptedChunk =
          SnapshotChunkWrapper.withTotalCount(snapshotChunkReader.next(), 55);
      chunkApplied = receivedSnapshot.apply(corruptedChunk).join();
    }

    // then
    assertThat(chunkApplied)
        .as("the second chunk should not be applied as it reports a different chunk count")
        .isFalse();
  }

  @Test
  public void shouldNotPersistWhenTotalCountIsWrong() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        final var corruptedChunk =
            SnapshotChunkWrapper.withTotalCount(snapshotChunkReader.next(), 1);
        receivedSnapshot.apply(corruptedChunk).join();
      }
    }
    final var persisted = receivedSnapshot.persist();

    // then
    assertThatThrownBy(persisted::join)
        .as(
            "the snapshot should not be persisted as it's corrupted due to missing chunks,"
                + " even if the total count was present")
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithNotEqualSnapshotId() throws Exception {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final boolean chunkApplied;
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final var originalChunk = snapshotChunkReader.next();
      final SnapshotChunk corruptedChunk = SnapshotChunkWrapper.withSnapshotId(originalChunk, "id");
      chunkApplied = receivedSnapshot.apply(corruptedChunk).join();
    }

    // then
    assertThat(chunkApplied)
        .as("the chunk should not be applied since it has a different snapshot ID than expected")
        .isFalse();
  }

  private ReceivedSnapshot receiveSnapshot(final PersistedSnapshot persistedSnapshot)
      throws IOException {
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(snapshotChunkReader.next()).join();
      }
    }

    return receivedSnapshot;
  }

  private PersistedSnapshot takePersistedSnapshot() {
    final var transientSnapshot =
        senderSnapshotStore.newTransientSnapshot(1L, 0L, 1, 0).orElseThrow();
    transientSnapshot.take(this::writeSnapshot).join();
    return transientSnapshot.persist().join();
  }

  private boolean writeSnapshot(final Path path) {
    try {
      FileUtil.ensureDirectoryExists(path);

      for (final var entry : SNAPSHOT_FILE_CONTENTS.entrySet()) {
        final var fileName = path.resolve(entry.getKey());
        final var fileContent = entry.getValue().getBytes(StandardCharsets.UTF_8);
        Files.write(fileName, fileContent, CREATE_NEW, StandardOpenOption.WRITE);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
  }
}
