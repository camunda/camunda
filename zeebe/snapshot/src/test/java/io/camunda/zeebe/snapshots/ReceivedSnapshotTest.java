/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.snapshots.SnapshotException.SnapshotAlreadyExistsException;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.impl.SnapshotWriteException;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
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
    final var senderDirectory = temporaryFolder.newFolder("sender").toPath();
    final var receiverDirectory = temporaryFolder.newFolder("receiver").toPath();

    senderSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, senderDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());
    scheduler.get().submitActor((Actor) senderSnapshotStore).join();

    receiverSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, receiverDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());

    scheduler.get().submitActor((Actor) receiverSnapshotStore).join();
  }

  @Test
  public void shouldThrowExceptionOnInvalidSnapshotId() {
    // given

    // when
    assertThatThrownBy(() -> receiverSnapshotStore.newReceivedSnapshot("invalid").join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldReturnIndexOfSnapshot() {
    // given

    // when
    final var receivedSnapshot = receiverSnapshotStore.newReceivedSnapshot("1-0-123-121").join();

    // then
    assertThat(receivedSnapshot.index()).isEqualTo(1L);
  }

  @Test
  public void shouldNotCommitUntilPersisted() {
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
  public void shouldReceiveSnapshot() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot = receiveSnapshot(persistedSnapshot).persist().join();

    // then
    assertThat(receiverSnapshotStore.getLatestSnapshot())
        .as("the received snapshot was committed and is the latest snapshot")
        .hasValue(receivedSnapshot);
    assertThat(receivedSnapshot.getChecksums().sameChecksums(persistedSnapshot.getChecksums()))
        .as("the received snapshot has the same checksum as the sent snapshot")
        .isTrue();
    assertThat(receivedSnapshot.getId())
        .as("the received snapshot has the same ID as the sent snapshot")
        .isEqualTo(persistedSnapshot.getId());
  }

  @Test
  public void shouldDeletePendingSnapshotDirOnAbort() {
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
  public void shouldNotDeletePersistedSnapshotOnPurgePendingOnStore() {
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
    assertThat(receivedSnapshot.getChecksums().sameChecksums(persistedSnapshot.getChecksums()))
        .as("the received snapshot still has the same checksum")
        .isTrue();
  }

  @Test
  public void shouldPersistEvenIfSameChunkIsConsumedMultipleTimes() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId()).join();

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final SnapshotChunk chunk = snapshotChunkReader.next();
      receivedSnapshot.apply(chunk).join();
      receivedSnapshot.apply(chunk).join();

      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(snapshotChunkReader.next()).join();
      }
    }

    final var receivedPersistedSnapshot = receivedSnapshot.persist().join();

    // then
    assertThat(receivedPersistedSnapshot)
        .as("the snapshot was persisted even if one chunk was applied more than once")
        .isEqualTo(receiverSnapshotStore.getLatestSnapshot().orElseThrow());
  }

  @Test
  public void shouldThrowWhenAlreadyExistingSnapshotWasReceivedAgain() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();
    final var firstReceivedSnapshot = receiveSnapshot(persistedSnapshot);
    final var firstPersistedSnapshot = firstReceivedSnapshot.persist().join();

    // when then throw - receives same snapshot again
    assertThat(receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId()))
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(SnapshotAlreadyExistsException.class)
        .withMessageContaining(
            "Expected to receive snapshot with id 1-0-1-0-0, but was already persisted");
  }

  @Test
  public void shouldThrowExceptionOnPersistWhenNoChunkApplied() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId()).join();

    // when
    assertThatThrownBy(() -> receivedSnapshot.persist().join())
        .hasCauseInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldBeAbleToAbortAfterPersistFails() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId()).join();
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
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId()).join();

    // when - then
    assertThatCode(receivedSnapshot::abort).doesNotThrowAnyException();
  }

  @Test
  public void shouldReceiveConcurrentlyAndPersist() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final ReceivedSnapshot firstReceivedSnapshot = receiveSnapshot(persistedSnapshot);
    final ReceivedSnapshot secondReceivedSnapshot = receiveSnapshot(persistedSnapshot);
    final PersistedSnapshot receivedPersistedSnapshot = firstReceivedSnapshot.persist().join();

    // then
    // both snapshots will exist since they point to the same directory
    assertThat(firstReceivedSnapshot.getPath())
        .as("the first received snapshot was removed but the new was persist on the same directory")
        .exists();
    assertThat(secondReceivedSnapshot.getPath())
        .as("the second received snapshot was not removed as it's not considered older")
        .exists();
    assertThat(
            receivedPersistedSnapshot
                .getChecksums()
                .sameChecksums(persistedSnapshot.getChecksums()))
        .as("the received, persisted snapshot have the same checksum as the persisted one")
        .isTrue();
  }

  @Test
  public void shouldDoNothingOnPersistOfAlreadyCommittedSnapshot() {
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
    assertThat(receivedPersisted.getChecksums().sameChecksums(persistedSnapshot.getChecksums()))
        .as("the received, persisted snapshot have the same checksum as the persisted one")
        .isTrue();
  }

  @Test
  public void shouldCompleteExceptionallyOnConsumingChunkWithInvalidChunkChecksum() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId()).join();
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final SnapshotChunk originalChunk = snapshotChunkReader.next();
      final SnapshotChunk corruptedChunk =
          SnapshotChunkWrapper.withChecksum(originalChunk, 0xCAFEL);

      // then
      assertThatThrownBy(() -> receivedSnapshot.apply(corruptedChunk).join())
          .as("the chunk should not be applied as its content checksum is not 0xCAFEL")
          .hasCauseInstanceOf(SnapshotWriteException.class);
    }
  }

  @Test
  public void shouldNotPersistWhenSnapshotIsPartial() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId()).join();
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
  public void shouldCompleteExceptionallyOnConsumingChunkWithNotEqualTotalCount() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId()).join();
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final var firstChunk = snapshotChunkReader.next();
      receivedSnapshot.apply(firstChunk).join();

      final var corruptedChunk =
          SnapshotChunkWrapper.withTotalCount(snapshotChunkReader.next(), 55);

      // then
      assertThatThrownBy(() -> receivedSnapshot.apply(corruptedChunk).join())
          .as("the second chunk should not be applied as it reports a different chunk count")
          .hasCauseInstanceOf(SnapshotWriteException.class);
    }
  }

  @Test
  public void shouldNotPersistWhenTotalCountIsWrong() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId()).join();
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
  public void shouldCompleteExceptionallyOnConsumingChunkWithNotEqualSnapshotId() {
    // given
    final var persistedSnapshot = takePersistedSnapshot();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId()).join();
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final var originalChunk = snapshotChunkReader.next();
      final SnapshotChunk corruptedChunk = SnapshotChunkWrapper.withSnapshotId(originalChunk, "id");

      // then
      assertThatThrownBy(() -> receivedSnapshot.apply(corruptedChunk).join())
          .as("the chunk should not be applied since it has a different snapshot ID than expected")
          .hasCauseInstanceOf(SnapshotWriteException.class);
    }
  }

  private ReceivedSnapshot receiveSnapshot(final PersistedSnapshot persistedSnapshot) {
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId()).join();

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(snapshotChunkReader.next()).join();
      }
    }

    return receivedSnapshot;
  }

  private PersistedSnapshot takePersistedSnapshot() {
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(1L, 0L, 1, 0).get();
    transientSnapshot.take(this::writeSnapshot).join();
    return transientSnapshot.persist().join();
  }

  private void writeSnapshot(final Path path) {
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
  }
}
