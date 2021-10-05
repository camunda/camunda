/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class AsyncSnapshotingTest {

  private final TemporaryFolder tempFolderRule = new TemporaryFolder();
  private final AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  @Rule
  public final RuleChain chain =
      RuleChain.outerRule(autoCloseableRule).around(tempFolderRule).around(actorSchedulerRule);

  private StateControllerImpl snapshotController;
  private AsyncSnapshotDirector asyncSnapshotDirector;
  private StreamProcessor mockStreamProcessor;
  private ConstructableSnapshotStore persistedSnapshotStore;

  @Before
  public void setup() throws IOException {
    final var rootDirectory = tempFolderRule.getRoot().toPath();
    final var factory = new FileBasedSnapshotStoreFactory(actorSchedulerRule.get(), 1);
    final int partitionId = 1;
    factory.createReceivableSnapshotStore(rootDirectory, partitionId);
    persistedSnapshotStore = factory.getConstructableSnapshotStore(partitionId);

    snapshotController =
        new StateControllerImpl(
            ZeebeRocksDbFactory.newFactory(),
            persistedSnapshotStore,
            rootDirectory.resolve("runtime"),
            l ->
                Optional.of(
                    new TestIndexedRaftLogEntry(
                        l + 100, 1, new ApplicationEntry(1, 10, new UnsafeBuffer()))),
            db -> Long.MAX_VALUE,
            new TestConcurrencyControl());

    snapshotController.recover().join();
    autoCloseableRule.manage(snapshotController);
    snapshotController = spy(snapshotController);

    createStreamProcessorControllerMock();
  }

  private void setCommitPosition(final long commitPosition) {
    asyncSnapshotDirector.newPositionCommitted(commitPosition);
  }

  private void createStreamProcessorControllerMock() {
    mockStreamProcessor = mock(StreamProcessor.class);

    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(0L))
        .thenReturn(CompletableActorFuture.completed(25L))
        .thenReturn(CompletableActorFuture.completed(32L));

    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(99L), CompletableActorFuture.completed(100L));
  }

  private void createAsyncSnapshotDirectorOfProcessingMode() {
    asyncSnapshotDirector =
        AsyncSnapshotDirector.ofProcessingMode(
            0, 1, mockStreamProcessor, snapshotController, Duration.ofMinutes(1));
    actorSchedulerRule.submitActor(asyncSnapshotDirector).join();
  }

  private void createAsyncSnapshotDirectorOfReplayMode() {
    asyncSnapshotDirector =
        AsyncSnapshotDirector.ofReplayMode(
            0, 1, mockStreamProcessor, snapshotController, Duration.ofMinutes(1));
    actorSchedulerRule.submitActor(asyncSnapshotDirector).join();
  }

  @Test
  public void shouldValidSnapshotWhenCommitPositionGreaterEquals() {
    // given
    createAsyncSnapshotDirectorOfProcessingMode();
    asyncSnapshotDirector.forceSnapshot();

    // when
    setCommitPosition(100L);

    // then
    Awaitility.await()
        .untilAsserted(() -> assertThat(persistedSnapshotStore.getLatestSnapshot()).isPresent());
  }

  @Test
  public void shouldTakeSnapshotsOneByOne() {
    // given
    createAsyncSnapshotDirectorOfProcessingMode();
    asyncSnapshotDirector.forceSnapshot();
    setCommitPosition(99L);
    Awaitility.await()
        .untilAsserted(() -> assertThat(persistedSnapshotStore.getLatestSnapshot()).isPresent());
    final PersistedSnapshot oldSnapshot = persistedSnapshotStore.getLatestSnapshot().orElseThrow();

    // when
    asyncSnapshotDirector.forceSnapshot();
    setCommitPosition(100L);

    // then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(persistedSnapshotStore.getCurrentSnapshotIndex())
                    .describedAs("New snapshot is taken")
                    .isGreaterThan(oldSnapshot.getIndex()));
  }

  @Test
  public void shouldSucceedToTakeSnapshotOnNextIntervalWhenLastWritePosRetrievingFailed() {
    // given
    createAsyncSnapshotDirectorOfProcessingMode();
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = 26L;
    final long commitPosition = 100L;

    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(
            CompletableActorFuture.completedExceptionally(
                new RuntimeException("getLastWrittenPositionAsync fails")));
    setCommitPosition(commitPosition);
    asyncSnapshotDirector.forceSnapshot();
    verify(mockStreamProcessor, timeout(5000).times(1)).getLastWrittenPositionAsync();

    // when
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    asyncSnapshotDirector.forceSnapshot();

    // then
    Awaitility.await()
        .untilAsserted(() -> assertThat(persistedSnapshotStore.getLatestSnapshot()).isPresent());
  }

  @Test
  public void shouldSucceedToTakeSnapshotOnNextIntervalWhenLastProcessedPosRetrievingFailed() {
    // given
    createAsyncSnapshotDirectorOfProcessingMode();
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = 26L;
    final long commitPosition = 100L;

    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(
            CompletableActorFuture.completedExceptionally(
                new RuntimeException("getLastProcessedPositionAsync fails")));
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    asyncSnapshotDirector.forceSnapshot();
    verify(mockStreamProcessor, timeout(5000).times(1)).getLastProcessedPositionAsync();

    // when
    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    asyncSnapshotDirector.forceSnapshot();
    setCommitPosition(commitPosition);

    // then
    Awaitility.await()
        .untilAsserted(() -> assertThat(persistedSnapshotStore.getLatestSnapshot()).isPresent());
  }

  @Test
  public void shouldPersistSnapshotWithoutWaitingForCommitWhenInReplayMode() {
    // when
    createAsyncSnapshotDirectorOfReplayMode();
    asyncSnapshotDirector.forceSnapshot();

    // then
    Awaitility.await()
        .untilAsserted(() -> assertThat(persistedSnapshotStore.getLatestSnapshot()).isPresent());
  }
}
