/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.snapshots.SnapshotCopyUtil;
import io.camunda.zeebe.snapshots.SnapshotTransferUtil;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotMetadata;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.impl.SnapshotMetrics;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferService.TakeSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

public class SnapshotTransferTest {

  private static final int DEFAULT_MAXIMUM_CHUNK_SIZE = 8 * 1024 * 1024;

  @RegisterExtension
  public final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension();

  @TempDir Path temporaryFolder;
  FileBasedSnapshotStore senderSnapshotStore;
  FileBasedSnapshotStore receiverSnapshotStore;
  private SnapshotTransferImpl snapshotTransfer;
  private TakeSnapshot takeSnapshotMock;
  @AutoClose private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final SnapshotMetrics snapshotMetrics = new SnapshotMetrics(meterRegistry);

  @BeforeEach
  public void beforeEach() throws Exception {
    final int partitionId = 1;
    final var senderDirectory = temporaryFolder.resolve("sender");
    final var receiverDirectory = temporaryFolder.resolve("receiver");

    senderSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, senderDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());
    actorScheduler.submitActor(senderSnapshotStore);
    actorScheduler.workUntilDone();

    receiverSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, receiverDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());

    actorScheduler.submitActor(receiverSnapshotStore);
    actorScheduler.workUntilDone();
    takeSnapshotMock = mock(TakeSnapshot.class);
    snapshotTransfer = newSnapshotTransfer(DEFAULT_MAXIMUM_CHUNK_SIZE);
    actorScheduler.workUntilDone();
  }

  private SnapshotTransferImpl newSnapshotTransfer(final int maximumChunkSize) {
    final var transfer =
        new SnapshotTransferImpl(
            control ->
                spy(
                    new SnapshotTransferServiceImpl(
                        senderSnapshotStore,
                        takeSnapshotMock,
                        1,
                        SnapshotCopyUtil::copyAllFiles,
                        control,
                        maximumChunkSize)),
            snapshotMetrics,
            receiverSnapshotStore);
    actorScheduler.submitActor(transfer);
    actorScheduler.workUntilDone();
    return transfer;
  }

  @Test
  public void shouldTransferSnapshot() {
    // given
    final int partitionId = 1;
    final var takeSnapshotFuture =
        SnapshotTransferUtil.takePersistedSnapshot(
            senderSnapshotStore,
            SnapshotTransferUtil.SNAPSHOT_FILE_CONTENTS,
            receiverSnapshotStore);
    actorScheduler.workUntilDone();
    assertThat(takeSnapshotFuture).succeedsWithin(Duration.ofSeconds(3));

    // when
    final var persistedSnapshotFuture = snapshotTransfer.getLatestSnapshot(partitionId);

    actorScheduler.workUntilDone();

    // then
    assertThat(persistedSnapshotFuture)
        .succeedsWithin(Duration.ofSeconds(30))
        .satisfies(
            snapshot -> {
              assertThat(snapshot.getId()).startsWith("1-1-0-0-0-");
              assertThat(snapshot.getMetadata())
                  .isEqualTo(FileBasedSnapshotMetadata.forBootstrap(1));
              assertThat(snapshot.isBootstrap()).isTrue();
              assertThat(snapshot.files()).isNotEmpty();
            });
    assertThat(snapshotMetrics.getTransferDuration(true).mean(TimeUnit.MILLISECONDS))
        .isGreaterThan(0.1D);
    verify((SnapshotTransferServiceImpl) snapshotTransfer.snapshotTransferService())
        .withReservation(any(), any());
  }

  @Test
  public void shouldTakeSnapshotIfNoneIsPresent() {
    // given
    final int partitionId = 1;
    when(takeSnapshotMock.takeSnapshot(anyLong()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var persistedSnapshotFuture = snapshotTransfer.getLatestSnapshot(partitionId);

    // then
    verify(takeSnapshotMock, timeout(5000)).takeSnapshot(eq(-1L));
  }

  @Test
  public void shouldSplitFilesIntoChunksOfMaximumChunkSize() throws IOException {
    // given
    final int partitionId = 1;
    final int maximumChunkSize = 16;
    final var fileContents =
        Map.of(
            "file1", "a".repeat(100),
            "file2", "b".repeat(maximumChunkSize),
            "file3", "c".repeat(maximumChunkSize + 1));
    final var chunkedTransfer = newSnapshotTransfer(maximumChunkSize);
    final var takeSnapshotFuture =
        SnapshotTransferUtil.takePersistedSnapshot(
            senderSnapshotStore, fileContents, receiverSnapshotStore);
    actorScheduler.workUntilDone();
    final var sentSnapshot = takeSnapshotFuture.join();

    // when
    final var persistedSnapshotFuture = chunkedTransfer.getLatestSnapshot(partitionId);
    actorScheduler.workUntilDone();

    // then
    assertThat(persistedSnapshotFuture)
        .succeedsWithin(Duration.ofSeconds(30))
        .satisfies(
            received -> {
              // the bootstrap snapshot carries its own metadata, so only compare the data files
              final var receivedChecksums = received.getChecksums().getChecksums();
              final var sentChecksums = sentSnapshot.getChecksums().getChecksums();
              for (final var fileName : fileContents.keySet()) {
                assertThat(receivedChecksums).containsEntry(fileName, sentChecksums.get(fileName));
              }
              for (final var fileName : fileContents.keySet()) {
                assertThat(received.getPath().resolve(fileName))
                    .hasSameBinaryContentAs(sentSnapshot.getPath().resolve(fileName));
              }
            });

    // one chunk per started maximumChunkSize bytes of each transferred file, plus one call that
    // returns no further chunk
    final var receivedSnapshot = persistedSnapshotFuture.join();
    final long expectedChunks;
    final long fileCount;
    try (final var files = Files.list(receivedSnapshot.getPath())) {
      final var sizes = files.map(file -> file.toFile().length()).toList();
      fileCount = sizes.size();
      expectedChunks =
          sizes.stream().mapToLong(size -> Math.max(1, Math.ceilDiv(size, maximumChunkSize))).sum();
    }
    assertThat(expectedChunks).isGreaterThan(fileCount);
    verify(
            (SnapshotTransferServiceImpl) chunkedTransfer.snapshotTransferService(),
            times(Math.toIntExact(expectedChunks)))
        .getNextChunk(eq(partitionId), any(), any(), any());
  }

  @Test
  public void shouldRejectNonPositiveMaximumChunkSize() {
    // when / then
    assertThatThrownBy(
            () ->
                new SnapshotTransferServiceImpl(
                    senderSnapshotStore,
                    takeSnapshotMock,
                    1,
                    SnapshotCopyUtil::copyAllFiles,
                    mock(ConcurrencyControl.class),
                    0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
