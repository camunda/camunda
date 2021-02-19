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

import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.snapshots.broker.ConstructableSnapshotStore;
import io.zeebe.snapshots.raft.ReceivableSnapshotStore;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.ActorScheduler;
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

public class ReceivedSnapshotTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private ConstructableSnapshotStore senderSnapshotStore;
  private ReceivableSnapshotStore receiverSnapshotStore;

  @Before
  public void before() throws Exception {
    final int partitionId = 1;

    final var senderFactory = new FileBasedSnapshotStoreFactory(createActorScheduler(), 1);
    senderFactory.createReceivableSnapshotStore(
        temporaryFolder.newFolder("sender").toPath(), partitionId);
    senderSnapshotStore = senderFactory.getConstructableSnapshotStore(partitionId);
    receiverSnapshotStore =
        new FileBasedSnapshotStoreFactory(createActorScheduler(), 2)
            .createReceivableSnapshotStore(
                temporaryFolder.newFolder("received").toPath(), partitionId);
  }

  private ActorScheduler createActorScheduler() {
    final var actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
    return actorScheduler;
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
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(snapshotChunkReader.next());
      }
    }
    final var receivedPersistedSnapshot = receivedSnapshot.persist().join();

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
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final var success = receivedSnapshot.apply(snapshotChunkReader.next()).join();

      // then
      assertThat(success).isTrue();
    }
  }

  @Test
  public void shouldReturnTrueOnConsumingChunkTwice() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      receivedSnapshot.apply(snapshotChunkReader.next()).join();
    }

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final var success = receivedSnapshot.apply(snapshotChunkReader.next()).join();
      assertThat(success).isTrue();
    }
  }

  @Test
  public void shouldReturnTrueWhenSnapshotAlreadyExist() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();
    final var firstReceivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        final var success = firstReceivedSnapshot.apply(snapshotChunkReader.next()).join();
        assertThat(success).isTrue();
      }
    }

    // when - same snapshot received again
    final var secondReceivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        final var success = secondReceivedSnapshot.apply(snapshotChunkReader.next()).join();

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
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();
    final var firstReceivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        firstReceivedSnapshot.apply(snapshotChunkReader.next());
      }
    }
    final var alreadyPeristedSnapshot = firstReceivedSnapshot.persist().join();

    // when - receives same snapshot again
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        final var success = receivedSnapshot.apply(snapshotChunkReader.next()).join();
        assertThat(success).isTrue();
      }
    }
    final var persistedReceivedSnapshot = receivedSnapshot.persist().join();

    // then
    assertThat(persistedReceivedSnapshot).isEqualTo(alreadyPeristedSnapshot);
    assertThat(persistedReceivedSnapshot == alreadyPeristedSnapshot).isTrue();
  }

  @Test
  public void shouldThrowExceptionOnPersistWhenNoChunkApplied() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    // when
    assertThatThrownBy(() -> receivedSnapshot.persist().join())
        .hasCauseInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldNotThrowExceptionOnAbortWhenNoChunkApplied() {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

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
