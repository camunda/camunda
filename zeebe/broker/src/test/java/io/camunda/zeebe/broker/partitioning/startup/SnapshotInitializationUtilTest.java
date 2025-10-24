/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.test.TestActorSchedulerFactory;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotCopyUtil;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransfer;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotInitializationUtilTest {

  @TempDir private Path tempDir;

  @Mock private SnapshotTransfer snapshotTransfer;

  private ActorScheduler scheduler;
  private Actor concurrencyControl;
  private FileBasedSnapshotStore receiverSnapshotStore;
  private FileBasedSnapshotStore senderSnapshotStore;
  private PersistedSnapshot senderBootstrapSnapshot;

  @BeforeEach
  void setUp() throws IOException {
    final var brokerConfig = new BrokerCfg();
    scheduler = TestActorSchedulerFactory.ofBrokerConfig(brokerConfig);
    concurrencyControl = new Actor() {};
    scheduler.submitActor(concurrencyControl);

    // Create receiver snapshot store (the one being tested)
    final var receiverSnapshotDir = Files.createDirectory(tempDir.resolve("receiver-snapshots"));
    receiverSnapshotStore =
        new FileBasedSnapshotStore(
            1, // brokerId
            2, // partitionId
            receiverSnapshotDir,
            snapshotPath -> Map.of(),
            new SimpleMeterRegistry());

    // Create sender snapshot store (simulates the leader/sender)
    final var senderSnapshotDir = Files.createDirectory(tempDir.resolve("sender-snapshots"));
    senderSnapshotStore =
        new FileBasedSnapshotStore(
            2, // different brokerId
            1, // same partitionId
            senderSnapshotDir,
            snapshotPath -> Map.of(),
            new SimpleMeterRegistry());

    scheduler.submitActor(receiverSnapshotStore).join();
    scheduler.submitActor(senderSnapshotStore).join();

    senderBootstrapSnapshot = createBootstrapSnapshotOnSender();
  }

  @AfterEach
  void tearDown() {
    if (scheduler != null) {
      scheduler.stop();
    }
  }

  @Test
  void shouldCompleteSuccessfullyWithNoExistingSnapshot() {
    // given - create a bootstrap snapshot on the sender side for transfer
    mockTransferSnapshot();

    // when
    final ActorFuture<Void> result =
        SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl);

    // then
    assertThat(result.join()).isNull();
    verify(snapshotTransfer).getLatestSnapshot(Protocol.DEPLOYMENT_PARTITION);

    // Verify bootstrap snapshot was restored on receiver side
    assertThat(receiverSnapshotStore.getLatestSnapshot())
        .isPresent()
        .get()
        .satisfies(
            snapshot -> {
              assertThat(snapshot.getId()).isEqualTo(senderBootstrapSnapshot.getId());
              assertThat(snapshot.getMetadata().isBootstrap()).isTrue();
            });
  }

  @Test
  void shouldFailWithNonBootstrapSnapshot() {
    // given - create a non-bootstrap snapshot on receiver side
    createNonBootstrapSnapshotOnReceiver();

    // when
    final ActorFuture<Void> result =
        SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl);

    // then
    assertThatThrownBy(result::join)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not for bootstrap");
  }

  @Test
  void shouldHandleNoSnapshotFromLeader() {
    // given - no snapshot available from sender
    when(snapshotTransfer.getLatestSnapshot(Protocol.DEPLOYMENT_PARTITION))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final ActorFuture<Void> result =
        SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl);

    // then
    assertThat(result.join()).isNull();
    assertThat(receiverSnapshotStore.getLatestSnapshot()).isEmpty();
  }

  @Test
  void shouldPropagateRestoreFailure() {
    // given - create a snapshot on sender and corrupt it
    mockTransferSnapshot();
    final var spiedSnapshotStore = org.mockito.Mockito.spy(receiverSnapshotStore);
    doReturn(CompletableActorFuture.completedExceptionally(new RuntimeException("Forced failure")))
        .when(spiedSnapshotStore)
        .restore(any(PersistedSnapshot.class));

    // when
    final ActorFuture<Void> result =
        SnapshotInitializationUtil.initializeFromSnapshot(
            spiedSnapshotStore, snapshotTransfer, concurrencyControl);

    // then
    assertThatThrownBy(result::join)
        .isInstanceOf(ExecutionException.class)
        .hasMessageContaining("Forced failure");
  }

  @Test
  void shouldPropagateSnapshotTransferFailure() {
    // given
    final RuntimeException transferError = new RuntimeException("Transfer failed");
    when(snapshotTransfer.getLatestSnapshot(Protocol.DEPLOYMENT_PARTITION))
        .thenReturn(CompletableActorFuture.completedExceptionally(transferError));

    // when
    final ActorFuture<Void> result =
        SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl);

    // then
    assertThatThrownBy(result::join).isInstanceOf(ExecutionException.class).hasCause(transferError);
  }

  @Test
  void shouldBeIdempotent() {
    // given - transfer bootstrap snapshot to receiver side
    mockTransferSnapshot();
    SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl)
        .join();

    // when
    // Retry is always with a new snapshot store instance
    restartReceiverSnapshotStore();

    final ActorFuture<Void> retryResult =
        SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl);

    // then
    assertThat(retryResult).succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldBeIdempotentAfterValidationFailure() {
    // given - create a non-bootstrap snapshot on receiver side
    createNonBootstrapSnapshotOnReceiver();

    // when - first attempt fails
    final ActorFuture<Void> firstResult =
        SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl);

    assertThatThrownBy(firstResult::join)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class);

    // Retry is always with a new snapshot store instance
    restartReceiverSnapshotStore();

    // when - retry the validation
    final ActorFuture<Void> retryResult =
        SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl);

    // then - should fail the same way (idempotent)
    assertThatThrownBy(retryResult::join)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not for bootstrap");
  }

  @Test
  void shouldBeIdempotentOnFetchFailure() {
    // given
    final RuntimeException transferError = new RuntimeException("Transfer failed");
    doReturn(CompletableActorFuture.completedExceptionally(transferError))
        // second attempt succeeds
        .doAnswer(ignore -> transferSnapshot(senderBootstrapSnapshot))
        .when(snapshotTransfer)
        .getLatestSnapshot(Protocol.DEPLOYMENT_PARTITION);

    final ActorFuture<Void> result =
        SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl);

    assertThatThrownBy(result::join).isInstanceOf(ExecutionException.class).hasCause(transferError);

    // when - retry

    // Retry is always with a new snapshot store instance
    restartReceiverSnapshotStore();

    final ActorFuture<Void> retryResult =
        SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl);

    // then
    assertThat(retryResult).succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldBeIdempotentIfRestoreFails() {
    // given - bootstrap snapshot exists on receiver side
    mockTransferSnapshot();
    final var spiedSnapshotStore = org.mockito.Mockito.spy(receiverSnapshotStore);
    doAnswer(
            ignore ->
                CompletableActorFuture.completedExceptionally(
                    new RuntimeException("Force fail restore")))
        .when(spiedSnapshotStore)
        .restore(any(PersistedSnapshot.class));

    final ActorFuture<Void> firstResult =
        SnapshotInitializationUtil.initializeFromSnapshot(
            spiedSnapshotStore, snapshotTransfer, concurrencyControl);

    assertThat(firstResult)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Force fail");

    // when - retry

    // Retry is always with a new snapshot store instance
    restartReceiverSnapshotStore();

    final ActorFuture<Void> retryResult =
        SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl);

    // then - second call also succeeds without trying to delete again
    assertThat(retryResult).succeedsWithin(Duration.ofSeconds(1));
    assertThat(receiverSnapshotStore.getBootstrapSnapshot()).isEmpty();
  }

  @Test
  void shouldBeIdempotentOnDeleteFailure() {
    // given - create a snapshot on sender and corrupt it
    mockTransferSnapshot();
    final var spiedSnapshotStore = org.mockito.Mockito.spy(receiverSnapshotStore);
    // Fail first attempt
    doReturn(
            CompletableActorFuture.completedExceptionally(
                new RuntimeException("Force fail first time")))
        .when(spiedSnapshotStore)
        .deleteBootstrapSnapshots();

    // when
    final ActorFuture<Void> firstTry =
        SnapshotInitializationUtil.initializeFromSnapshot(
            spiedSnapshotStore, snapshotTransfer, concurrencyControl);

    // then
    assertThatThrownBy(firstTry::join)
        .isInstanceOf(ExecutionException.class)
        .hasMessageContaining("Force fail first time");
    assertThat(receiverSnapshotStore.getLatestSnapshot()).isNotEmpty();

    // when
    // Retry is always with a new snapshot store instance
    restartReceiverSnapshotStore();

    final ActorFuture<Void> secondTry =
        SnapshotInitializationUtil.initializeFromSnapshot(
            receiverSnapshotStore, snapshotTransfer, concurrencyControl);

    // then
    assertThat(secondTry).succeedsWithin(Duration.ofSeconds(1));
  }

  private ActorFuture<PersistedSnapshot> transferSnapshot(
      final PersistedSnapshot senderBootstrapSnapshot) {
    final var tempReceiveSnapshot =
        receiverSnapshotStore.newReceivedSnapshot(senderBootstrapSnapshot.getId());

    return tempReceiveSnapshot.andThen(
        receivedSnapshot -> {
          try (final var reader = senderBootstrapSnapshot.newChunkReader()) {
            while (reader.hasNext()) {
              receivedSnapshot.apply(reader.next());
            }
            return receivedSnapshot.persist();
          }
        },
        concurrencyControl);
  }

  private void restartReceiverSnapshotStore() {
    receiverSnapshotStore.close();
    final var receiverSnapshotDir = tempDir.resolve("receiver-snapshots");
    receiverSnapshotStore =
        new FileBasedSnapshotStore(
            1, // brokerId
            2, // partitionId
            receiverSnapshotDir,
            snapshotPath -> Map.of(),
            new SimpleMeterRegistry());
    scheduler.submitActor(receiverSnapshotStore).join();
  }

  private void mockTransferSnapshot() {
    doAnswer(ignore -> transferSnapshot(senderBootstrapSnapshot))
        .when(snapshotTransfer)
        .getLatestSnapshot(Protocol.DEPLOYMENT_PARTITION);
  }

  private PersistedSnapshot createBootstrapSnapshotOnSender() {
    return createBootstrapSnapshot(senderSnapshotStore);
  }

  private PersistedSnapshot createBootstrapSnapshot(final FileBasedSnapshotStore snapshotStore) {
    // Create a regular snapshot first on sender
    final var regularSnapshot = createPersistedSnapshot(snapshotStore, 2L, "base");

    // Copy it for bootstrap
    return snapshotStore.copyForBootstrap(regularSnapshot, SnapshotCopyUtil::copyAllFiles).join();
  }

  private void createNonBootstrapSnapshotOnReceiver() {
    createPersistedSnapshotOnReceiver(1L, "non-bootstrap");
  }

  private PersistedSnapshot createPersistedSnapshotOnReceiver(
      final long index, final String suffix) {
    return createPersistedSnapshot(receiverSnapshotStore, index, suffix);
  }

  private PersistedSnapshot createPersistedSnapshot(
      final FileBasedSnapshotStore snapshotStore, final long index, final String suffix) {
    return takeTransientSnapshotWithFiles(snapshotStore, index, 200L + index, suffix)
        .persist()
        .join();
  }

  private TransientSnapshot takeTransientSnapshotWithFiles(
      final FileBasedSnapshotStore store,
      final long index,
      final long processedPosition,
      final String suffix) {
    final var transientSnapshot =
        store.newTransientSnapshot(index, 1L, processedPosition, 0L, false).get();

    final var snapshotFileNames =
        List.of("zeebe.metadata", "table-0.sst", "table-1.sst", "table-2.sst");
    transientSnapshot
        .take(
            path -> {
              try {
                FileUtil.ensureDirectoryExists(path);
                for (final var filename : snapshotFileNames) {
                  final var file = new File(path.toString(), filename);
                  // Create file if it doesn't exist, ignore if it does
                  file.createNewFile();
                  try (final var fos = new FileOutputStream(file)) {
                    fos.write((suffix + "-content-" + processedPosition).getBytes());
                    fos.flush();
                  }
                }
                FileUtil.flushDirectory(path);
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            })
        .join();
    return transientSnapshot;
  }
}
