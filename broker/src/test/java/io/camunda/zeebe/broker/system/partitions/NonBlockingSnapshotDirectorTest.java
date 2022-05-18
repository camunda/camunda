/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.camunda.zeebe.broker.system.partitions.impl.CommitAwaiter;
import io.camunda.zeebe.broker.system.partitions.impl.NonBlockingSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorMode;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.util.sched.ActorCompatability;
import io.camunda.zeebe.util.sched.TestConcurrencyControl;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.testcontainers.shaded.org.awaitility.Awaitility;

public final class NonBlockingSnapshotDirectorTest {

  private final TemporaryFolder tempFolderRule = new TemporaryFolder();
  private final AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  @Rule
  public final RuleChain chain =
      RuleChain.outerRule(autoCloseableRule).around(tempFolderRule).around(actorSchedulerRule);

  private StateControllerImpl snapshotController;
  private NonBlockingSnapshotDirector asyncSnapshotDirector;
  private StreamProcessor mockStreamProcessor;
  private ConstructableSnapshotStore persistedSnapshotStore;

  private final ActorCompatability actorCompatability = new ActorCompatability();

  @Before
  public void setup() throws IOException {
    final var rootDirectory = tempFolderRule.getRoot().toPath();
    final var factory = new FileBasedSnapshotStoreFactory(actorSchedulerRule.get(), 1);
    final int partitionId = 1;
    factory.createReceivableSnapshotStore(rootDirectory, partitionId);
    persistedSnapshotStore = factory.getConstructableSnapshotStore(partitionId);

    snapshotController =
        new StateControllerImpl(
            DefaultZeebeDbFactory.defaultFactory(),
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

    actorSchedulerRule.submitActor(actorCompatability);

    createStreamProcessorControllerMock();
  }

  private void setCommitPosition(final long commitPosition) {
    final IndexedRaftLogEntry raftLogEntry =
        new TestIndexedRaftLogEntry(
            1,
            1,
            new ApplicationEntry(commitPosition, commitPosition, ByteBuffer.wrap(new byte[1])));
    asyncSnapshotDirector.onCommit(raftLogEntry);
  }

  private void createStreamProcessorControllerMock() {
    mockStreamProcessor = mock(StreamProcessor.class);

    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(0L))
        .thenReturn(CompletableActorFuture.completed(25L))
        .thenReturn(CompletableActorFuture.completed(32L));

    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(99L))
        .thenReturn(CompletableActorFuture.completed(100L));
  }

  private void createAsyncSnapshotDirectorOfProcessingMode() {
    asyncSnapshotDirector =
        new NonBlockingSnapshotDirector(
            actorCompatability,
            snapshotController,
            mockStreamProcessor,
            StreamProcessorMode.PROCESSING,
            Duration.ofMinutes(1),
            new CommitAwaiter());
  }

  private void createAsyncSnapshotDirectorOfReplayMode() {
    asyncSnapshotDirector =
        new NonBlockingSnapshotDirector(
            actorCompatability,
            snapshotController,
            mockStreamProcessor,
            StreamProcessorMode.REPLAY,
            Duration.ofMinutes(1),
            new CommitAwaiter());
  }

  @Test
  public void shouldValidSnapshotWhenCommitPositionGreaterEquals()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given
    createAsyncSnapshotDirectorOfProcessingMode();
    final var snapshot = asyncSnapshotDirector.forceSnapshot();

    // when
    setCommitPosition(100L);

    // then
    assertThat(snapshot.get(10, TimeUnit.SECONDS)).isNotNull();
    assertThat(persistedSnapshotStore.getLatestSnapshot()).hasValue(snapshot.get());
  }

  @Test
  public void shouldNotCommitSnapshotAfterClose() throws Exception {
    // given
    createAsyncSnapshotDirectorOfProcessingMode();
    final var snapshot = asyncSnapshotDirector.forceSnapshot();

    // when
    asyncSnapshotDirector.close();
    setCommitPosition(100L);

    // then
    Awaitility.await()
        .during(Duration.ofSeconds(10))
        .atMost(Duration.ofSeconds(11))
        .until(() -> persistedSnapshotStore.getLatestSnapshot().isEmpty());
  }

  @Test
  public void shouldTakeSnapshotsOneByOne()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given
    createAsyncSnapshotDirectorOfProcessingMode();
    final var firstSnapshot = asyncSnapshotDirector.forceSnapshot();
    setCommitPosition(99L);
    assertThat(firstSnapshot.get(10, TimeUnit.SECONDS)).isNotNull();
    final var firstSnapshotIndex = firstSnapshot.get().getIndex();

    // when
    final var secondSnapshot = asyncSnapshotDirector.forceSnapshot();
    setCommitPosition(100L);

    // then
    assertThat(secondSnapshot.get(10, TimeUnit.SECONDS))
        .describedAs("Second snapshot is taken")
        .isNotNull()
        .describedAs("Second snapshot has a higher index")
        .extracting(PersistedSnapshot::getIndex, as(InstanceOfAssertFactories.LONG))
        .isGreaterThan(firstSnapshotIndex);
    assertThat(persistedSnapshotStore.getLatestSnapshot())
        .hasValue(secondSnapshot.get(10, TimeUnit.SECONDS));
  }

  @Test
  public void shouldSucceedToTakeSnapshotOnNextIntervalWhenLastWritePosRetrievingFailed()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given
    createAsyncSnapshotDirectorOfProcessingMode();
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = 26L;
    final long commitPosition = 100L;

    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    final var initialFailure = new RuntimeException("getLastWrittenPositionAsync fails");
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completedExceptionally(initialFailure));
    setCommitPosition(commitPosition);
    assertThatThrownBy(() -> asyncSnapshotDirector.forceSnapshot().get(10, TimeUnit.SECONDS))
        .hasCause(initialFailure);
    verify(mockStreamProcessor, timeout(10000).times(1)).getLastWrittenPositionAsync();

    // when
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));

    // then
    assertThat(asyncSnapshotDirector.forceSnapshot().get(10, TimeUnit.SECONDS)).isNotNull();
    assertThat(persistedSnapshotStore.getLatestSnapshot()).isPresent();
    verify(mockStreamProcessor, timeout(10000).times(2)).getLastWrittenPositionAsync();
  }

  @Test
  public void shouldSucceedToTakeSnapshotOnNextIntervalWhenLastProcessedPosRetrievingFailed()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given
    createAsyncSnapshotDirectorOfProcessingMode();
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = 26L;
    final long commitPosition = 100L;

    final var initialFailure = new RuntimeException("getLastProcessedPositionAsync fails");
    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completedExceptionally(initialFailure));
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));

    assertThatThrownBy(() -> asyncSnapshotDirector.forceSnapshot().get(10, TimeUnit.SECONDS))
        .hasCause(initialFailure);
    verify(mockStreamProcessor, timeout(5000).times(1)).getLastProcessedPositionAsync();

    // when
    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    final var secondSnapshot = asyncSnapshotDirector.forceSnapshot();
    setCommitPosition(commitPosition);

    // then
    assertThat(secondSnapshot.get(10, TimeUnit.SECONDS)).isNotNull();
    assertThat(persistedSnapshotStore.getLatestSnapshot())
        .hasValue(secondSnapshot.get(10, TimeUnit.SECONDS));
  }

  @Test
  public void shouldPersistSnapshotWithoutWaitingForCommitWhenInReplayMode()
      throws ExecutionException, InterruptedException, TimeoutException {
    // when
    createAsyncSnapshotDirectorOfReplayMode();
    final var snapshot = asyncSnapshotDirector.forceSnapshot();

    // then
    assertThat(snapshot.get(10, TimeUnit.SECONDS)).isNotNull();
    assertThat(persistedSnapshotStore.getLatestSnapshot())
        .hasValue(snapshot.get(10, TimeUnit.SECONDS));
  }
}
