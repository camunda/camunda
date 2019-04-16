/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.logstreams.util.LogStreamRule;
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

public class AsyncSnapshotingTest {
  private static final long TIMEOUT = 2_000L;
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
    logStream = spy(logStreamRule.getLogStream());
    final ActorScheduler actorScheduler = logStreamRule.getActorScheduler();

    final Supplier<ActorFuture<Long>> positionSupplier = mock(Supplier.class);
    when(positionSupplier.get())
        .thenReturn(CompletableActorFuture.completed(25L))
        .thenReturn(CompletableActorFuture.completed(32L));

    final Supplier<ActorFuture<Long>> writtenSupplier = mock(Supplier.class);
    when(writtenSupplier.get())
        .thenReturn(CompletableActorFuture.completed(99L), CompletableActorFuture.completed(100L));

    mockDeleteCallback = mock(NoopConsumer.class);

    asyncSnapshotDirector =
        new AsyncSnapshotDirector(
            "processor-1",
            Duration.ofSeconds(15),
            positionSupplier,
            writtenSupplier,
            snapshotController,
            actorCondition -> logStream.registerOnCommitPositionUpdatedCondition(actorCondition),
            actorCondition -> logStream.removeOnCommitPositionUpdatedCondition(actorCondition),
            () -> logStream.getCommitPosition(),
            mock(StreamProcessorMetrics.class),
            MAX_SNAPSHOTS,
            mockDeleteCallback::noop);
    actorScheduler.submitActor(asyncSnapshotDirector).join();
  }

  @Test
  public void shouldStartToTakeSnapshot() {
    // given

    // when
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));

    // then
    final InOrder inOrder = Mockito.inOrder(snapshotController, logStream);
    inOrder
        .verify(logStream, timeout(TIMEOUT).times(1))
        .registerOnCommitPositionUpdatedCondition(any());
    inOrder.verify(snapshotController, timeout(TIMEOUT).times(1)).takeTempSnapshot();
    inOrder.verify(logStream, timeout(TIMEOUT).times(2)).getCommitPosition();
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
    inOrder.verify(snapshotController, timeout(TIMEOUT).times(1)).takeTempSnapshot();
    inOrder.verify(snapshotController, timeout(TIMEOUT).times(1)).moveValidSnapshot(25);
    inOrder
        .verify(snapshotController, timeout(TIMEOUT).times(1))
        .ensureMaxSnapshotCount(MAX_SNAPSHOTS);
    inOrder.verify(snapshotController, timeout(TIMEOUT).times(1)).getValidSnapshotsCount();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotTakeMoreThenOneSnapshot() {
    // given
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));
    verify(snapshotController, timeout(500).times(1)).takeTempSnapshot();

    // when
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));

    // then
    final InOrder inOrder = Mockito.inOrder(snapshotController, logStream);
    inOrder
        .verify(logStream, timeout(TIMEOUT).times(1))
        .registerOnCommitPositionUpdatedCondition(any());
    inOrder.verify(snapshotController, timeout(TIMEOUT).times(1)).takeTempSnapshot();
    inOrder.verify(logStream, timeout(TIMEOUT).times(2)).getCommitPosition();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldTakeSnapshotsOneByOne() throws Exception {
    // given
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));
    verify(snapshotController, timeout(500).times(1)).takeTempSnapshot();
    logStreamRule.setCommitPosition(99L);
    verify(snapshotController, timeout(500).times(1)).moveValidSnapshot(25);

    // when
    logStreamRule.getClock().addTime(Duration.ofMinutes(1));
    verify(snapshotController, timeout(500).times(1)).takeTempSnapshot();
    logStreamRule.setCommitPosition(100L);

    // then
    verify(snapshotController, timeout(500).times(1)).moveValidSnapshot(32);
    verify(mockDeleteCallback).noop(eq(32L));
  }

  public class NoopConsumer {
    public void noop(long position) {}
  }
}
