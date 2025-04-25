/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class AsyncSnapshottingTest {

  private final TemporaryFolder tempFolderRule = new TemporaryFolder();
  private final AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  @Rule
  public final RuleChain chain =
      RuleChain.outerRule(autoCloseableRule).around(tempFolderRule).around(actorSchedulerRule);

  private StateControllerImpl snapshotController;
  private AsyncSnapshotDirector asyncSnapshotDirector;
  private StreamProcessor mockStreamProcessor;
  private FileBasedSnapshotStore persistedSnapshotStore;

  @Before
  public void setup() throws IOException {
    final var rootDirectory = tempFolderRule.getRoot().toPath();
    final int partitionId = 1;
    final var meterRegistry = new SimpleMeterRegistry();
    persistedSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, rootDirectory, snapshotPath -> Map.of(), meterRegistry);
    actorSchedulerRule.submitActor(persistedSnapshotStore).join();

    snapshotController =
        new StateControllerImpl(
            DefaultZeebeDbFactory.defaultFactory(),
            persistedSnapshotStore,
            rootDirectory.resolve("runtime"),
            l ->
                Optional.of(
                    new TestIndexedRaftLogEntry(
                        l + 100, 1, new SerializedApplicationEntry(1, 10, new UnsafeBuffer()))),
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
        .thenReturn(CompletableActorFuture.completed(99L))
        .thenReturn(CompletableActorFuture.completed(100L));
  }

  private void createAsyncSnapshotDirectorOfProcessingMode() {
    asyncSnapshotDirector =
        AsyncSnapshotDirector.ofProcessingMode(
            1,
            mockStreamProcessor,
            snapshotController,
            Duration.ofMinutes(1),
            () -> CompletableFuture.completedFuture(null));
    actorSchedulerRule.submitActor(asyncSnapshotDirector).join();
  }

  private void createAsyncSnapshotDirectorOfReplayMode() {
    asyncSnapshotDirector =
        AsyncSnapshotDirector.ofReplayMode(
            1,
            mockStreamProcessor,
            snapshotController,
            Duration.ofMinutes(1),
            () -> CompletableFuture.completedFuture(null));
    actorSchedulerRule.submitActor(asyncSnapshotDirector).join();
  }

  @Test
  public void shouldValidSnapshotWhenCommitPositionGreaterEquals() {
    // given
    createAsyncSnapshotDirectorOfProcessingMode();
    final var snapshot = asyncSnapshotDirector.forceSnapshot();

    // when
    setCommitPosition(100L);

    // then
    assertThat(snapshot.join()).isNotNull();
    assertThat(persistedSnapshotStore.getLatestSnapshot()).hasValue(snapshot.join());
  }

  @Test
  public void shouldTakeSnapshotsOneByOne() {
    // given
    createAsyncSnapshotDirectorOfProcessingMode();
    final var firstSnapshot = asyncSnapshotDirector.forceSnapshot();
    setCommitPosition(99L);
    assertThat(firstSnapshot.join()).isNotNull();
    final var firstSnapshotIndex = firstSnapshot.join().getIndex();

    // when
    final var secondSnapshot = asyncSnapshotDirector.forceSnapshot();
    setCommitPosition(100L);

    // then
    assertThat(secondSnapshot.join())
        .describedAs("Second snapshot is taken")
        .isNotNull()
        .describedAs("Second snapshot has a higher index")
        .extracting(PersistedSnapshot::getIndex, as(InstanceOfAssertFactories.LONG))
        .isGreaterThan(firstSnapshotIndex);
    assertThat(persistedSnapshotStore.getLatestSnapshot()).hasValue(secondSnapshot.join());
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
    final var initialFailure = new RuntimeException("getLastWrittenPositionAsync fails");
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completedExceptionally(initialFailure));
    setCommitPosition(commitPosition);
    assertThatThrownBy(() -> asyncSnapshotDirector.forceSnapshot().join()).hasCause(initialFailure);
    verify(mockStreamProcessor, timeout(10000).times(1)).getLastWrittenPositionAsync();

    // when
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));

    // then
    assertThat(asyncSnapshotDirector.forceSnapshot().join()).isNotNull();
    assertThat(persistedSnapshotStore.getLatestSnapshot()).isPresent();
    verify(mockStreamProcessor, timeout(10000).times(2)).getLastWrittenPositionAsync();
  }

  @Test
  public void shouldSucceedToTakeSnapshotOnNextIntervalWhenLastProcessedPosRetrievingFailed() {
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

    assertThatThrownBy(() -> asyncSnapshotDirector.forceSnapshot().join()).hasCause(initialFailure);
    verify(mockStreamProcessor, timeout(5000).times(1)).getLastProcessedPositionAsync();

    // when
    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    final var secondSnapshot = asyncSnapshotDirector.forceSnapshot();
    setCommitPosition(commitPosition);

    // then
    assertThat(secondSnapshot.join()).isNotNull();
    assertThat(persistedSnapshotStore.getLatestSnapshot()).hasValue(secondSnapshot.join());
  }

  @Test
  public void shouldPersistSnapshotWithoutWaitingForCommitWhenInReplayMode() {
    // when
    createAsyncSnapshotDirectorOfReplayMode();
    final var snapshot = asyncSnapshotDirector.forceSnapshot();

    // then
    assertThat(snapshot.join()).isNotNull();
    assertThat(persistedSnapshotStore.getLatestSnapshot()).hasValue(snapshot.join());
  }

  @Test
  public void shouldNotCommitSnapshotIfFlushFailedInProcessingMode() {
    // given
    final CompletableFuture<Void> flushFuture = new CompletableFuture<>();

    asyncSnapshotDirector =
        AsyncSnapshotDirector.ofProcessingMode(
            1, mockStreamProcessor, snapshotController, Duration.ofMinutes(1), () -> flushFuture);
    actorSchedulerRule.submitActor(asyncSnapshotDirector).join();
    setCommitPosition(100L);

    // when
    final var result = asyncSnapshotDirector.forceSnapshot();
    flushFuture.completeExceptionally(new RuntimeException("Flush failed"));

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(1000))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Flush failed");
  }

  @Test
  public void shouldNotCommitSnapshotIfFlushFailedInReplayMode() {
    // given
    final CompletableFuture<Void> flushFuture = new CompletableFuture<>();

    asyncSnapshotDirector =
        AsyncSnapshotDirector.ofReplayMode(
            1, mockStreamProcessor, snapshotController, Duration.ofMinutes(1), () -> flushFuture);
    actorSchedulerRule.submitActor(asyncSnapshotDirector).join();

    // when
    final var result = asyncSnapshotDirector.forceSnapshot();
    flushFuture.completeExceptionally(new RuntimeException("Flush failed"));

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(1000))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Flush failed");
  }
}
