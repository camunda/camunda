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
import io.zeebe.snapshots.raft.PersistedSnapshot;
import io.zeebe.snapshots.raft.PersistedSnapshotListener;
import io.zeebe.snapshots.raft.ReceivableSnapshotStore;
import io.zeebe.snapshots.raft.ReceivedSnapshot;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.ActorScheduler;
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
  private ConstructableSnapshotStore senderSnapshotStore;
  private ReceivableSnapshotStore receiverSnapshotStore;
  private Path receiverSnapshotsDir;
  private Path receiverPendingSnapshotsDir;

  @Before
  public void before() throws Exception {
    final String partitionName = "1";
    final File senderRoot = temporaryFolder.newFolder("sender");

    final var senderSnapshotStoreFactory =
        new FileBasedSnapshotStoreFactory(createActorScheduler());
    senderSnapshotStoreFactory.createReceivableSnapshotStore(senderRoot.toPath(), partitionName);
    senderSnapshotStore = senderSnapshotStoreFactory.getConstructableSnapshotStore(partitionName);

    final var receiverRoot = temporaryFolder.newFolder("received");
    receiverSnapshotStore =
        new FileBasedSnapshotStoreFactory(createActorScheduler())
            .createReceivableSnapshotStore(receiverRoot.toPath(), partitionName);

    receiverSnapshotsDir =
        receiverRoot.toPath().resolve(FileBasedSnapshotStoreFactory.SNAPSHOTS_DIRECTORY);
    receiverPendingSnapshotsDir =
        receiverRoot.toPath().resolve(FileBasedSnapshotStoreFactory.PENDING_DIRECTORY);
  }

  private ActorScheduler createActorScheduler() {
    final var actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
    return actorScheduler;
  }

  @Test
  public void shouldNotCreateDirDirectlyOnNewReceivedSnapshot() {
    // given

    // when
    receiverSnapshotStore.newReceivedSnapshot("1-0-123");

    // then
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();
    assertThat(receiverSnapshotsDir.toFile().listFiles()).isEmpty();
  }

  @Test
  public void shouldWriteChunkInPendingDirOnApplyChunk() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    takeAndReceiveSnapshot(index, term);

    // then
    assertThat(receiverSnapshotsDir.toFile().listFiles()).isEmpty();

    assertThat(receiverPendingSnapshotsDir).exists();
    final var files = receiverPendingSnapshotsDir.toFile().listFiles();
    assertThat(files).isNotNull().hasSize(1);

    final var dir = files[0];
    final var snapshotFileList = dir.listFiles();
    assertThat(snapshotFileList).isNotNull().extracting(File::getName).containsExactly("file1.txt");
  }

  @Test
  public void shouldDeletePendingSnapshotDirOnAbort() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final PersistedSnapshot persistedSnapshot = takeSnapshot(index, term);

    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
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
    takeAndReceiveSnapshot(index, term);

    // when
    receiverSnapshotStore.purgePendingSnapshots().join();

    // then
    assertThat(receiverSnapshotsDir.toFile().listFiles()).isEmpty();
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();
  }

  @Test
  public void shouldPersistSnapshot() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var receivedSnapshot = takeAndReceiveSnapshot(index, term);

    // when
    final var snapshot = receivedSnapshot.persist().join();

    // then
    assertThat(snapshot).isNotNull();
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();

    assertThat(receiverSnapshotsDir).exists();
    final var files = receiverSnapshotsDir.toFile().listFiles();
    assertThat(files).isNotNull().hasSize(1);

    final var dir = files[0];
    assertThat(dir).hasName(snapshot.getId());

    final var snapshotFileList = dir.listFiles();
    assertThat(snapshotFileList).isNotNull().extracting(File::getName).containsExactly("file1.txt");
  }

  @Test
  public void shouldNotDeletePersistedSnapshotOnPurgePendingOnStore() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var snapshot = takeAndReceiveSnapshot(index, term).persist().join();

    // when
    receiverSnapshotStore.purgePendingSnapshots().join();

    // then
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();

    assertThat(receiverSnapshotsDir).exists();
    final var files = receiverSnapshotsDir.toFile().listFiles();
    assertThat(files).isNotNull().hasSize(1);

    final var dir = files[0];
    assertThat(dir).hasName(snapshot.getId());

    final var snapshotFileList = dir.listFiles();
    assertThat(snapshotFileList).isNotNull().extracting(File::getName).containsExactly("file1.txt");
  }

  @Test
  public void shouldReplaceSnapshotOnNextSnapshot() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    takeAndReceiveSnapshot(index, term).persist().join();

    // when
    takeAndReceiveSnapshot(index + 1, term).persist().join();

    // then
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();

    final var snapshotDirs = receiverSnapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).isNotNull().hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(
            FileBasedSnapshotMetadata.ofFileName(committedSnapshotDir.getName()).get().getIndex())
        .isEqualTo(2);
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
    takeAndReceiveSnapshot(index, term);

    // when
    takeAndReceiveSnapshot(index + 1, term).persist().join();

    // then
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();

    final var snapshotDirs = receiverSnapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).isNotNull().hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(
            FileBasedSnapshotMetadata.ofFileName(committedSnapshotDir.getName()).get().getIndex())
        .isEqualTo(2);
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

    final FileBasedSnapshotStoreFactory fileBasedSnapshotStoreFactory =
        new FileBasedSnapshotStoreFactory(createActorScheduler());
    fileBasedSnapshotStoreFactory.createReceivableSnapshotStore(
        temporaryFolder.newFolder("other").toPath(), "1");
    final var otherStore = fileBasedSnapshotStoreFactory.getConstructableSnapshotStore("1");
    final var olderTransient = otherStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    olderTransient.take(this::takeSnapshot);
    final var olderPersistedSnapshot = olderTransient.persist().join();

    final PersistedSnapshot newPersistedSnapshot = takeSnapshot(index + 1, term);
    receiveSnapshot(newPersistedSnapshot);

    // when
    receiveSnapshot(olderPersistedSnapshot).persist().join();

    // then
    final var pendingSnapshotDirs = receiverPendingSnapshotsDir.toFile().listFiles();
    assertThat(pendingSnapshotDirs).isNotNull().hasSize(1);

    final var pendingSnapshotDir = pendingSnapshotDirs[0];
    assertThat(FileBasedSnapshotMetadata.ofFileName(pendingSnapshotDir.getName()).get().getIndex())
        .isEqualTo(2);
    assertThat(pendingSnapshotDir.listFiles())
        .isNotNull()
        .extracting(File::getName)
        .containsExactly("file1.txt");

    final var snapshotDirs = receiverSnapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).isNotNull().hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(committedSnapshotDir.getName()).isEqualTo(olderPersistedSnapshot.getId());
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
    receiverSnapshotStore.addSnapshotListener(listener);

    // when
    final var persistedSnapshot = takeAndReceiveSnapshot(index, term).persist().join();

    // then
    verify(listener, times(1)).onNewSnapshot(eq(persistedSnapshot));
  }

  @Test
  public void shouldNotNotifyListenersOnNewSnapshotWhenDeregistered() throws Exception {
    // given
    final var listener = mock(PersistedSnapshotListener.class);
    final var index = 1L;
    final var term = 0L;
    senderSnapshotStore.addSnapshotListener(listener);
    senderSnapshotStore.removeSnapshotListener(listener);

    // when
    final var persistedSnapshot = takeAndReceiveSnapshot(index, term).persist().join();

    // then
    verify(listener, times(0)).onNewSnapshot(eq(persistedSnapshot));
  }

  @Test
  public void shouldReceiveConcurrentlyButWriteInDifferentPendingDirs() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var persistedSnapshot = takeSnapshot(index, term);

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
    assertThat(dir).hasName(persistedSnapshot.getId() + "-1");

    final var snapshotFileList = dir.listFiles();
    assertThat(snapshotFileList).isNotNull().extracting(File::getName).containsExactly("file1.txt");

    final var otherDir = files.get(1);
    assertThat(otherDir).hasName(persistedSnapshot.getId() + "-2");

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
    final var persistedSnapshot = takeSnapshot(index, term);
    final var receivedSnapshot = receiveSnapshot(persistedSnapshot);
    final var otherReceivedSnapshot = receiveSnapshot(persistedSnapshot);

    // when
    final var receivedPersisted = receivedSnapshot.persist().join();
    final var otherReceivedPersisted = otherReceivedSnapshot.persist().join();

    // then
    assertThat(receivedPersisted).isEqualTo(otherReceivedPersisted);

    final var snapshotDirs = receiverSnapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).isNotNull().hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(committedSnapshotDir.getName()).isEqualTo(persistedSnapshot.getId());
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
    final var persistedSnapshot = takeSnapshot(index, term);
    final var receivedSnapshot = receiveSnapshot(persistedSnapshot);
    final var otherReceivedSnapshot = receiveSnapshot(persistedSnapshot);

    // when
    final var otherReceivedPersisted = otherReceivedSnapshot.persist().join();
    final var receivedPersisted = receivedSnapshot.persist().join();

    // then
    assertThat(receivedPersisted).isEqualTo(otherReceivedPersisted);

    final var snapshotDirs = receiverSnapshotsDir.toFile().listFiles();
    assertThat(snapshotDirs).isNotNull().hasSize(1);

    final var committedSnapshotDir = snapshotDirs[0];
    assertThat(committedSnapshotDir.getName()).isEqualTo(persistedSnapshot.getId());
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
    final var transientSnapshot =
        senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(
            SnapshotChunkWrapper.withDifferentSnapshotChecksum(
                snapshotChunkReader.next(), 0xCAFEL));
      }
    }
    assertThatThrownBy(() -> receivedSnapshot.persist().join())
        .hasCauseInstanceOf(IllegalStateException.class);

    // when
    receivedSnapshot.abort().join();

    // then
    assertThat(receiverPendingSnapshotsDir.toFile().listFiles()).isEmpty();
    assertThat(receiverSnapshotsDir.toFile().listFiles()).isEmpty();
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithInvalidSnapshotChecksum() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      var success = receivedSnapshot.apply(snapshotChunkReader.next()).join();
      assertThat(success).isTrue();
      success =
          receivedSnapshot
              .apply(
                  SnapshotChunkWrapper.withDifferentSnapshotChecksum(
                      snapshotChunkReader.next(), 0xCAFEL))
              .join();

      // then
      assertThat(success).isFalse();
    }
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithInvalidChunkChecksum() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      final var success =
          receivedSnapshot
              .apply(
                  SnapshotChunkWrapper.withDifferentChecksum(snapshotChunkReader.next(), 0xCAFEL))
              .join();

      // then
      assertThat(success).isFalse();
    }
  }

  @Test
  public void shouldNotPersistWhenSnapshotChecksumIsWrong() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(
            SnapshotChunkWrapper.withDifferentSnapshotChecksum(
                snapshotChunkReader.next(), 0xCAFEL));
      }
    }

    // when - then
    assertThatThrownBy(() -> receivedSnapshot.persist().join())
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithNotEqualTotalCount() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      var success = receivedSnapshot.apply(snapshotChunkReader.next()).join();
      assertThat(success).isTrue();
      success =
          receivedSnapshot
              .apply(SnapshotChunkWrapper.withDifferentTotalCount(snapshotChunkReader.next(), 55))
              .join();

      // then
      assertThat(success).isFalse();
    }
  }

  @Test
  public void shouldNotPersistWhenTotalCountIsWrong() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        receivedSnapshot.apply(
            SnapshotChunkWrapper.withDifferentTotalCount(snapshotChunkReader.next(), 2));
      }
    }

    // when - then
    assertThatThrownBy(() -> receivedSnapshot.persist().join())
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldReturnFalseOnConsumingChunkWithNotEqualSnapshotId() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var receivedSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(persistedSnapshot.getId());

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      var success = receivedSnapshot.apply(snapshotChunkReader.next()).join();
      assertThat(success).isTrue();
      success =
          receivedSnapshot
              .apply(SnapshotChunkWrapper.withDifferentSnapshotId(snapshotChunkReader.next(), "id"))
              .join();

      // then
      assertThat(success).isFalse();
    }
  }

  private ReceivedSnapshot takeAndReceiveSnapshot(final long index, final long term)
      throws IOException {
    final PersistedSnapshot persistedSnapshot = takeSnapshot(index, term);

    return receiveSnapshot(persistedSnapshot);
  }

  private PersistedSnapshot takeSnapshot(final long index, final long term) {
    final var transientSnapshot =
        senderSnapshotStore.newTransientSnapshot(index, term, 1, 0).orElseThrow();
    transientSnapshot.take(this::takeSnapshot).join();
    return transientSnapshot.persist().join();
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
