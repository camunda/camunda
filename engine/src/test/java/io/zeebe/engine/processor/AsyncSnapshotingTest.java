/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.engine.util.LogStreamRule;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;
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
  private final LogStreamRule logStreamRule = new LogStreamRule(tempFolderRule);

  @Rule
  public final RuleChain chain =
      RuleChain.outerRule(autoCloseableRule).around(tempFolderRule).around(logStreamRule);

  private StateSnapshotController snapshotController;
  private LogStream logStream;
  private AsyncSnapshotDirector asyncSnapshotDirector;
  private Supplier<ActorFuture<Long>> lastProcessedEventPositionSupplier;
  private Supplier<ActorFuture<Long>> lastWrittenEventPositionSupplier;
  private NoopConsumer mockDeleteCallback;

  @Before
  public void setup() throws IOException {
    final File snapshotsDirectory = tempFolderRule.newFolder("snapshots");
    final File runtimeDirectory = tempFolderRule.newFolder("runtime");
    final StateStorage storage = new StateStorage(runtimeDirectory, snapshotsDirectory);

    snapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class), storage);
    snapshotController.openDb();
    autoCloseableRule.manage(snapshotController);
    snapshotController = spy(snapshotController);

    logStreamRule.setCommitPosition(25L);
    logStream = Mockito.spy(logStreamRule.getLogStream());
    final ActorScheduler actorScheduler = logStreamRule.getActorScheduler();

    lastProcessedEventPositionSupplier = mock(Supplier.class);
    when(lastProcessedEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(25L))
        .thenReturn(CompletableActorFuture.completed(32L));

    lastWrittenEventPositionSupplier = mock(Supplier.class);
    when(lastWrittenEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(99L), CompletableActorFuture.completed(100L));

    mockDeleteCallback = mock(NoopConsumer.class);

    asyncSnapshotDirector = createAsyncSnapshotDirector();
    actorScheduler.submitActor(asyncSnapshotDirector).join();
  }

  private AsyncSnapshotDirector createAsyncSnapshotDirector() {
    return new AsyncSnapshotDirector(
        "processor-1",
        Duration.ofSeconds(15),
        lastProcessedEventPositionSupplier,
        lastWrittenEventPositionSupplier,
        snapshotController,
        actorCondition -> logStream.registerOnCommitPositionUpdatedCondition(actorCondition),
        actorCondition -> logStream.removeOnCommitPositionUpdatedCondition(actorCondition),
        () -> logStream.getCommitPosition(),
        mock(SnapshotMetrics.class),
        MAX_SNAPSHOTS,
        mockDeleteCallback::noop);
  }

  @Test
  public void shouldStartToTakeSnapshot() {
    // given

    // when
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));

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
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));

    // when
    logStreamRule.setCommitPosition(100L);

    // then
    final InOrder inOrder = Mockito.inOrder(snapshotController);
    inOrder.verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    inOrder.verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(25);

    inOrder.verify(snapshotController, TIMEOUT.times(1)).ensureMaxSnapshotCount(MAX_SNAPSHOTS);
    inOrder.verify(snapshotController, TIMEOUT.times(1)).getValidSnapshotsCount();
    inOrder.verify(snapshotController, TIMEOUT.times(1)).replicateLatestSnapshot(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotStopTakingSnapshotsAfterFailingReplication() throws Exception {
    // given
    final RuntimeException expectedException = new RuntimeException("expected");
    doThrow(expectedException).when(snapshotController).replicateLatestSnapshot(any());

    logStreamRule.getClock().addTime(Duration.ofMinutes(1));

    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    logStreamRule.setCommitPosition(99L);
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(25);
    verify(snapshotController, TIMEOUT.times(1)).replicateLatestSnapshot(any());

    // when
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    logStreamRule.setCommitPosition(100L);

    // then
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(32);
    verify(snapshotController, TIMEOUT.times(2)).replicateLatestSnapshot(any());
  }

  @Test
  public void shouldNotTakeMoreThenOneSnapshot() {
    // given
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();

    // when
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));

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
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    logStreamRule.setCommitPosition(99L);
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(25);

    // when
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    logStreamRule.setCommitPosition(100L);

    // then
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(32);
  }

  @Test
  public void shouldInvokeDataDeleteCallbackOnMaxSnapshots() throws IOException {
    // when
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    logStreamRule.setCommitPosition(99L);
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(25);

    logStreamRule.getClock().addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT.times(1)).takeTempSnapshot();
    logStreamRule.setCommitPosition(100L);
    verify(snapshotController, TIMEOUT.times(1)).moveValidSnapshot(32);

    // then
    verify(mockDeleteCallback, TIMEOUT).noop(eq(25L));
  }

  @Test
  public void shouldNotTakeSameSnapshotTwice() throws Exception {
    // given
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = 26L;
    final long commitPosition = 100L;

    when(lastProcessedEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    when(lastWrittenEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    logStreamRule.setCommitPosition(commitPosition);

    logStreamRule.getClock().addTime(Duration.ofMinutes(1));

    final InOrder inOrder = Mockito.inOrder(snapshotController, lastProcessedEventPositionSupplier);
    inOrder.verify(lastProcessedEventPositionSupplier, TIMEOUT).get();
    inOrder.verify(snapshotController, TIMEOUT).takeTempSnapshot();
    inOrder.verify(snapshotController, TIMEOUT).moveValidSnapshot(lastProcessedPosition);
    inOrder.verify(snapshotController, TIMEOUT).ensureMaxSnapshotCount(MAX_SNAPSHOTS);
    inOrder.verify(snapshotController, TIMEOUT).replicateLatestSnapshot(any());

    // when
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));

    // then
    inOrder.verify(lastProcessedEventPositionSupplier, TIMEOUT).get();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldEnforceSnapshotCreation() throws Exception {
    // given
    long lastProcessedPosition = 25L;
    long lastWrittenPosition = 26L;
    final long commitPosition = 100L;

    when(lastProcessedEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    when(lastWrittenEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    logStreamRule.setCommitPosition(commitPosition);

    logStreamRule.getClock().addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT).moveValidSnapshot(lastProcessedPosition);

    // when
    lastProcessedPosition = 26L;
    lastWrittenPosition = 27L;

    asyncSnapshotDirector
        .enforceSnapshotCreation(lastWrittenPosition, lastProcessedPosition)
        .join();

    // then
    verify(snapshotController, TIMEOUT.times(1)).takeSnapshot(lastProcessedPosition);
  }

  @Test
  public void shouldNotEnforceSnapshotCreationIfExists() throws Exception {
    // given
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = 26L;
    final long commitPosition = 100L;

    when(lastProcessedEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    when(lastWrittenEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    logStreamRule.setCommitPosition(commitPosition);

    logStreamRule.getClock().addTime(Duration.ofMinutes(1));
    verify(snapshotController, TIMEOUT).moveValidSnapshot(lastProcessedPosition);

    // when
    asyncSnapshotDirector
        .enforceSnapshotCreation(lastWrittenPosition, lastProcessedPosition)
        .join();

    // then
    verify(snapshotController, never()).takeSnapshot(lastProcessedPosition);
  }

  @Test
  public void shouldNotEnforceSnapshotCreationIfNotCommitted() throws Exception {
    // given
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = 101L;
    final long commitPosition = 100L;

    when(lastProcessedEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    when(lastWrittenEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    logStreamRule.setCommitPosition(commitPosition);

    // when
    asyncSnapshotDirector
        .enforceSnapshotCreation(lastWrittenPosition, lastProcessedPosition)
        .join();

    // then
    verify(snapshotController, never()).takeSnapshot(lastProcessedPosition);
  }

  @Test
  public void shouldNotTakeSnapshotIfExistsAfterRestart() throws IOException {
    // given
    final long lastProcessedPosition = 25L;
    final long lastWrittenPosition = lastProcessedPosition;
    final long commitPosition = 100L;

    when(lastProcessedEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(lastProcessedPosition));
    when(lastWrittenEventPositionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(lastWrittenPosition));
    logStreamRule.setCommitPosition(commitPosition);

    logStreamRule.getClock().addTime(Duration.ofMinutes(1));

    // when
    final InOrder inOrder = Mockito.inOrder(snapshotController, lastProcessedEventPositionSupplier);
    inOrder.verify(snapshotController, TIMEOUT).getLastValidSnapshotPosition();
    inOrder.verify(lastProcessedEventPositionSupplier, TIMEOUT).get();
    inOrder.verify(snapshotController, TIMEOUT).takeTempSnapshot();
    inOrder.verify(snapshotController, TIMEOUT).moveValidSnapshot(lastProcessedPosition);
    inOrder.verify(snapshotController, TIMEOUT).ensureMaxSnapshotCount(MAX_SNAPSHOTS);
    inOrder.verify(snapshotController, TIMEOUT).replicateLatestSnapshot(any());

    asyncSnapshotDirector.close();

    asyncSnapshotDirector = createAsyncSnapshotDirector();
    logStreamRule.getActorScheduler().submitActor(asyncSnapshotDirector).join();

    logStreamRule.getClock().addTime(Duration.ofMinutes(1));

    // then
    inOrder.verify(snapshotController, TIMEOUT).getLastValidSnapshotPosition();
    inOrder.verify(lastProcessedEventPositionSupplier, TIMEOUT).get();
    inOrder.verifyNoMoreInteractions();
  }

  public class NoopConsumer {
    public void noop(long position) {}
  }
}
