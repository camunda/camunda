/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.logstreams.impl.delete.NoopDeletionService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.verification.VerificationWithTimeout;

public class AsyncSnapshotingTest {

  private static final VerificationWithTimeout TIMEOUT = timeout(2000L);
  private static final int MAX_SNAPSHOTS = 2;

  private final TemporaryFolder tempFolderRule = new TemporaryFolder();
  private final AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private final ControlledActorClock clock = new ControlledActorClock();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(clock);

  @Rule
  public final RuleChain chain =
      RuleChain.outerRule(autoCloseableRule).around(tempFolderRule).around(actorSchedulerRule);

  private StateSnapshotController snapshotController;
  private LogStream logStream;
  private AsyncSnapshotDirector asyncSnapshotDirector;
  private StreamProcessor mockStreamProcessor;
  private NoopDeletionService noopDeletionService;
  private List<ActorCondition> conditionList;

  @Before
  public void setup() throws IOException {
    final File snapshotsDirectory = tempFolderRule.newFolder("snapshots");
    final File runtimeDirectory = tempFolderRule.newFolder("runtime");
    final StateStorage storage = new StateStorage(runtimeDirectory, snapshotsDirectory);

    snapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class), storage, MAX_SNAPSHOTS);
    snapshotController.openDb();
    autoCloseableRule.manage(snapshotController);
    snapshotController = spy(snapshotController);

    logStream = mock(LogStream.class);
    when(logStream.getCommitPosition()).thenReturn(25L);
    conditionList = new ArrayList<>();
    doAnswer(
            invocationOnMock -> {
              final Object[] arguments = invocationOnMock.getArguments();
              conditionList.add((ActorCondition) arguments[0]);
              return null;
            })
        .when(logStream)
        .registerOnCommitPositionUpdatedCondition(any());

    final ActorScheduler actorScheduler = actorSchedulerRule.get();
    createStreamProcessorControllerMock();
    createAsyncSnapshotDirector(actorScheduler);
  }

  private void setCommitPosition(long commitPosition) {
    when(logStream.getCommitPosition()).thenReturn(commitPosition);
    conditionList.forEach(c -> c.signal());
  }

  private void createStreamProcessorControllerMock() {
    mockStreamProcessor = mock(StreamProcessor.class);

    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(25L))
        .thenReturn(CompletableActorFuture.completed(32L));

    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(99L), CompletableActorFuture.completed(100L));
  }

  private void createAsyncSnapshotDirector(ActorScheduler actorScheduler) {
    noopDeletionService = spy(new NoopDeletionService());
    snapshotController.setDeletionService(noopDeletionService);
    asyncSnapshotDirector =
        new AsyncSnapshotDirector(
            mockStreamProcessor, snapshotController, logStream, Duration.ofMinutes(1));
    actorScheduler.submitActor(this.asyncSnapshotDirector).join();
  }

  @Test
  public void shouldStartToTakeSnapshot() {
    // given

    // when
    clock.addTime(Duration.ofMinutes(1));

    // then
    final InOrder inOrder = Mockito.inOrder(snapshotController, logStream);
    inOrder.verify(logStream, TIMEOUT.times(1)).registerOnCommitPositionUpdatedCondition(any());
    inOrder.verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    inOrder.verify(logStream, TIMEOUT.times(2)).getCommitPosition();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldValidSnapshotWhenCommitPositionGreaterEquals() throws Exception {
    // given
    clock.addTime(Duration.ofMinutes(1));

    // when
    setCommitPosition(100L);

    // then
    final InOrder inOrder = Mockito.inOrder(snapshotController);
    inOrder.verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    inOrder.verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(25);

    inOrder.verify(snapshotController, TIMEOUT.times(1)).ensureMaxSnapshotCount();
    inOrder.verify(snapshotController, TIMEOUT.times(1)).getValidSnapshotsCount();
    inOrder.verify(snapshotController, TIMEOUT.times(1)).replicateLatestSnapshot(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotStopTakingSnapshotsAfterFailingReplication() throws Exception {
    // given
    final RuntimeException expectedException = new RuntimeException("expected");
    doThrow(expectedException).when(snapshotController).replicateLatestSnapshot(any());

    clock.addTime(Duration.ofMinutes(1));

    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    setCommitPosition(99L);
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(25);
    verify(snapshotController, TIMEOUT.times(1)).replicateLatestSnapshot(any());

    // when
    clock.addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    setCommitPosition(100L);

    // then
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(32);
    verify(snapshotController, TIMEOUT.times(2)).replicateLatestSnapshot(any());
  }

  @Test
  public void shouldNotTakeMoreThenOneSnapshot() {
    // given
    clock.addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();

    // when
    clock.addTime(Duration.ofMinutes(1));

    // then
    final InOrder inOrder = Mockito.inOrder(snapshotController, logStream);
    inOrder.verify(logStream, TIMEOUT.times(1)).registerOnCommitPositionUpdatedCondition(any());
    inOrder.verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    inOrder.verify(logStream, TIMEOUT.times(2)).getCommitPosition();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldTakeSnapshotsOneByOne() throws Exception {
    // given
    clock.addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    setCommitPosition(99L);
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(25);

    // when
    clock.addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    setCommitPosition(100L);

    // then
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(32);
  }

  @Test
  public void shouldDeleteDataOnMaxSnapshots() throws IOException {
    // when
    clock.addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    setCommitPosition(99L);
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(25);

    clock.addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    setCommitPosition(100L);
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(32);

    // then
    verify(noopDeletionService, TIMEOUT).delete(eq(25L));
  }

  @Test
  public void shouldNotTakeSameSnapshotTwice() throws Exception {
    // given
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = 26L;
    final long commitPosition = 100L;

    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    setCommitPosition(commitPosition);

    clock.addTime(Duration.ofMinutes(1));

    final InOrder inOrder = Mockito.inOrder(snapshotController, mockStreamProcessor);
    inOrder.verify(mockStreamProcessor, TIMEOUT).getLastProcessedPositionAsync();
    inOrder.verify(snapshotController, TIMEOUT).takeTempSnapshot();
    inOrder.verify(snapshotController, TIMEOUT).moveValidSnapshot(lastProcessedPosition);
    inOrder.verify(snapshotController, TIMEOUT).ensureMaxSnapshotCount();
    inOrder.verify(snapshotController, TIMEOUT).replicateLatestSnapshot(any());

    // when
    clock.addTime(Duration.ofMinutes(1));

    // then
    inOrder.verify(mockStreamProcessor, TIMEOUT).getLastProcessedPositionAsync();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldEnforceSnapshotCreation() {
    // given
    long lastProcessedPosition = 25L;
    long lastWrittenPosition = 26L;
    final long commitPosition = 100L;

    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    setCommitPosition(commitPosition);
    verify(snapshotController, TIMEOUT).getLastValidSnapshotPosition();

    // when
    lastProcessedPosition = 26L;
    lastWrittenPosition = 27L;
    asyncSnapshotDirector.enforceSnapshotCreation(lastWrittenPosition, lastProcessedPosition);

    // then
    verify(snapshotController, TIMEOUT).takeSnapshot(lastProcessedPosition);
  }

  @Test
  public void shouldNotEnforceSnapshotCreationIfExists() throws Exception {
    // given
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = 26L;
    final long commitPosition = 100L;

    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    setCommitPosition(commitPosition);

    clock.addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT).moveValidSnapshot(lastProcessedPosition);

    // when
    asyncSnapshotDirector.enforceSnapshotCreation(lastWrittenPosition, lastProcessedPosition);

    // then
    verify(snapshotController, never()).takeSnapshot(lastProcessedPosition);
  }

  @Test
  public void shouldNotEnforceSnapshotCreationIfNotCommitted() {
    // given
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = 101L;
    final long commitPosition = 100L;

    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    setCommitPosition(commitPosition);

    // when
    asyncSnapshotDirector.enforceSnapshotCreation(lastWrittenPosition, lastProcessedPosition);

    // then
    verify(snapshotController, never()).takeSnapshot(lastProcessedPosition);
  }

  @Test
  public void shouldNotTakeSnapshotIfExistsAfterRestart() throws Exception {
    // given
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = lastProcessedPosition;
    final long commitPosition = 100L;

    when(mockStreamProcessor.getLastProcessedPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    when(mockStreamProcessor.getLastWrittenPositionAsync())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    setCommitPosition(commitPosition);

    clock.addTime(Duration.ofMinutes(1));

    // when
    final InOrder inOrder = Mockito.inOrder(snapshotController, mockStreamProcessor);
    inOrder.verify(snapshotController, TIMEOUT).getLastValidSnapshotPosition();
    inOrder.verify(mockStreamProcessor, TIMEOUT).getLastProcessedPositionAsync();
    inOrder.verify(snapshotController, TIMEOUT).takeTempSnapshot();
    inOrder.verify(snapshotController, TIMEOUT).moveValidSnapshot(lastProcessedPosition);
    inOrder.verify(snapshotController, TIMEOUT).ensureMaxSnapshotCount();
    inOrder.verify(snapshotController, TIMEOUT).replicateLatestSnapshot(any());

    createAsyncSnapshotDirector(actorSchedulerRule.get());

    clock.addTime(Duration.ofMinutes(1));

    // then
    inOrder.verify(snapshotController, TIMEOUT).getLastValidSnapshotPosition();
    inOrder.verify(mockStreamProcessor, TIMEOUT.atLeastOnce()).getLastProcessedPositionAsync();
    inOrder.verify(snapshotController, never()).takeTempSnapshot();
  }
}
