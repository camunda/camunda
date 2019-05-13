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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.engine.util.LogStreamReaderRule;
import io.zeebe.engine.util.LogStreamRule;
import io.zeebe.engine.util.LogStreamWriterRule;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.util.exception.RecoverableException;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

public class StreamProcessorControllerTest {

  private static final String PROCESSOR_NAME = "testProcessor";
  private static final int PROCESSOR_ID = 1;
  private static final Duration SNAPSHOT_INTERVAL = Duration.ofMinutes(1);
  private static final int MAX_SNAPSHOTS = 3;

  private static final DirectBuffer EVENT_1 = wrapString("FOO");
  private static final DirectBuffer EVENT_2 = wrapString("BAR");

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final LogStreamRule logStreamRule = new LogStreamRule(temporaryFolder);
  private final LogStreamWriterRule writer = new LogStreamWriterRule(logStreamRule);
  private final LogStreamReaderRule reader = new LogStreamReaderRule(logStreamRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolder).around(logStreamRule).around(reader).around(writer);

  private StreamProcessorService streamProcessorService;
  private StreamProcessorController streamProcessorController;
  private RecordingStreamProcessor streamProcessor;
  private Consumer<RecordingStreamProcessor> changeRecordingStreamProcessor = (processor) -> {};
  private SnapshotController snapshotController;
  private EventProcessor eventProcessor;
  private EventFilter eventFilter;

  private ZeebeDb zeebeDb;
  private DbContext dbContext;
  private ActorFuture<Void> openedFuture;
  private CountDownLatch processorCreated;
  private StateStorage stateStorage;

  @Before
  public void setup() throws Exception {
    eventFilter = mock(EventFilter.class);
    when(eventFilter.applies(any())).thenReturn(true);

    installStreamProcessorService();
  }

  @Test
  public void testStreamProcessorLifecycle() throws Exception {
    // when
    writeEventAndWaitUntilProcessed(EVENT_1);

    streamProcessorController.closeAsync().join();

    // then
    final InOrder inOrder = inOrder(streamProcessor, eventProcessor, snapshotController);
    inOrder.verify(streamProcessor, times(1)).onOpen(any());
    inOrder.verify(streamProcessor, times(1)).onEvent(any());

    inOrder.verify(eventProcessor, times(1)).processEvent();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();

    inOrder.verify(streamProcessor, times(1)).onClose();
    inOrder.verify(snapshotController, times(1)).close();

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testStreamProcessorLifecycleOnRecoverableException() throws Exception {
    // given
    final CountDownLatch latch = new CountDownLatch(2);
    changeMockInActorContext(
        () -> {
          doAnswer(
                  (invocationOnMock -> {
                    latch.countDown();
                    return invocationOnMock.callRealMethod();
                  }))
              .when(streamProcessor)
              .onEvent(any());

          doThrow(new RecoverableException("expected", new RuntimeException("expected")))
              .when(dbContext)
              .getCurrentTransaction();
        });

    // when
    writer.writeEvent(EVENT_1);
    latch.await();
    streamProcessorController.closeAsync().join();

    // then
    final InOrder inOrder = inOrder(streamProcessor, eventProcessor, snapshotController);
    inOrder.verify(streamProcessor, times(1)).onOpen(any());
    inOrder.verify(streamProcessor, atLeast(2)).onEvent(any());

    inOrder.verify(streamProcessor, times(1)).onClose();
    inOrder.verify(snapshotController, times(1)).close();

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testStreamProcessorLifecycleOnError() throws Exception {
    // given
    final EventProcessor eventProcessorSpy = streamProcessor.getEventProcessorSpy();
    changeMockInActorContext(
        () -> doThrow(new RuntimeException("expected")).when(eventProcessorSpy).processEvent());

    // when
    writeEventAndWaitUntilProcessedOrFailed(EVENT_1);
    streamProcessorController.closeAsync().join();

    // then
    final InOrder inOrder = inOrder(streamProcessor, eventProcessor, snapshotController);
    inOrder.verify(streamProcessor, times(1)).onOpen(any());
    inOrder.verify(streamProcessor, times(1)).onEvent(any());

    inOrder.verify(eventProcessor, times(1)).processEvent();
    inOrder.verify(eventProcessor, times(1)).onError(any());
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();

    inOrder.verify(streamProcessor, times(1)).onClose();
    inOrder.verify(snapshotController, times(1)).close();

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldReadEvents() {
    // when
    final long firstEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);
    final long secondEventPosition = writeEventAndWaitUntilProcessed(EVENT_2);

    // then
    assertThat(streamProcessor.getEvents())
        .hasSize(2)
        .extracting(LoggedEvent::getPosition)
        .containsExactly(firstEventPosition, secondEventPosition);
  }

  @Test
  public void shouldExecuteSideEffectsUntilDone() {
    // when
    changeMockInActorContext(
        () -> when(eventProcessor.executeSideEffects()).thenReturn(false, false, true));
    writeEventAndWaitUntilProcessed(EVENT_1);

    // then
    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, timeout(500L).times(1)).processEvent();
    inOrder.verify(eventProcessor, timeout(500L).times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, timeout(500L).times(3)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldWriteEventUntilDone() {
    // when
    changeMockInActorContext(() -> when(eventProcessor.writeEvent(any())).thenReturn(-1L, -1L, 1L));
    writeEventAndWaitUntilProcessed(EVENT_1);

    // then
    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent();
    inOrder.verify(eventProcessor, times(3)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSkipEventIfNoEventProcessorIsProvided() {
    // when
    // return null as event processor for first event
    changeMockInActorContext(
        () -> doReturn(null).doCallRealMethod().when(streamProcessor).onEvent(any()));
    writer.writeEvent(EVENT_1);
    final long secondEventPosition = writer.writeEvent(EVENT_2);

    // then
    waitUntil(() -> streamProcessor.getEvents().size() == 1);

    final LoggedEvent processedEvent = streamProcessor.getEvents().get(0);
    assertThat(processedEvent.getPosition()).isEqualTo(secondEventPosition);

    final InOrder inOrder = inOrder(streamProcessor, eventProcessor);
    inOrder.verify(streamProcessor, times(2)).onEvent(any());
    inOrder.verify(eventProcessor, times(1)).processEvent();
  }

  @Test
  public void shouldSkipEventIfEventFilterIsNotMet() {
    // when
    changeMockInActorContext(() -> when(eventFilter.applies(any())).thenReturn(false, true));
    writer.writeEvent(EVENT_1);
    final long secondEventPosition = writer.writeEvent(EVENT_2);

    // then
    waitUntil(() -> streamProcessor.getEvents().size() == 1);

    final LoggedEvent processedEvent = streamProcessor.getEvents().get(0);
    assertThat(processedEvent.getPosition()).isEqualTo(secondEventPosition);

    final InOrder inOrder = inOrder(streamProcessor, eventProcessor);
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(eventProcessor, times(1)).processEvent();
  }

  @Test
  public void shouldSuspendAndResume() {
    // when
    streamProcessor.suspend();

    // then
    assertThat(streamProcessorController.isSuspended()).isTrue();

    // when
    writer.writeEvent(EVENT_1);
    streamProcessor.resume();

    // then
    waitUntil(() -> !streamProcessorController.isSuspended());
    waitUntil(() -> streamProcessor.getProcessedEventCount() == 1);
  }

  @Test
  public void shouldWriteEvent() {
    // given
    final AtomicLong writtenEventPosition = new AtomicLong();

    // when
    changeMockInActorContext(
        () ->
            when(eventProcessor.writeEvent(any()))
                .thenAnswer(
                    inv -> {
                      final LogStreamRecordWriter writer = inv.getArgument(0);

                      final long position =
                          writer.key(2L).metadata(wrapString("META")).value(EVENT_2).tryWrite();

                      writtenEventPosition.set(position);

                      return position;
                    })
                .thenReturn(1L));

    final long firstEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);
    waitUntil(() -> writtenEventPosition.get() > 0);
    final long position = writtenEventPosition.get();
    writer.waitForPositionToBeAppended(position);

    // then
    final LoggedEvent writtenEvent = reader.readEventAtPosition(position);

    assertThat(writtenEvent.getKey()).isEqualTo(2L);

    assertThat(writtenEvent.getProducerId()).isEqualTo(PROCESSOR_ID);

    assertThat(writtenEvent.getSourceEventPosition()).isEqualTo(firstEventPosition);

    final UnsafeBuffer eventMetadata =
        new UnsafeBuffer(
            writtenEvent.getMetadata(),
            writtenEvent.getMetadataOffset(),
            writtenEvent.getMetadataLength());
    assertThat(eventMetadata).isEqualTo(wrapString("META"));

    final UnsafeBuffer eventValue =
        new UnsafeBuffer(
            writtenEvent.getValueBuffer(),
            writtenEvent.getValueOffset(),
            writtenEvent.getValueLength());
    assertThat(eventValue).isEqualTo(EVENT_2);
  }

  @Test
  public void shouldCloseOnExecuteSideEffects() {
    // given
    final AtomicInteger invocations = new AtomicInteger();

    // when
    changeMockInActorContext(
        () ->
            doAnswer(
                    inv -> {
                      invocations.incrementAndGet();
                      return false;
                    })
                .when(eventProcessor)
                .executeSideEffects());
    writer.writeEvent(EVENT_1);

    waitUntil(() -> invocations.get() >= 1);

    final ActorFuture<Void> future = streamProcessorController.closeAsync();

    // then
    waitUntil(future::isDone);

    assertThat(streamProcessorController.isOpened()).isFalse();
  }

  @Test
  public void shouldCloseOnWriteEvent() {
    // given
    final AtomicInteger invocations = new AtomicInteger();

    // when
    changeMockInActorContext(
        () ->
            doAnswer(
                    inv -> {
                      invocations.incrementAndGet();
                      return -1L;
                    })
                .when(eventProcessor)
                .writeEvent(any()));
    writer.writeEvent(EVENT_1);

    waitUntil(() -> invocations.get() >= 1);

    final ActorFuture<Void> future = streamProcessorController.closeAsync();

    // then
    waitUntil(future::isDone);

    assertThat(streamProcessorController.isOpened()).isFalse();
  }

  @Test
  public void shouldWriteSnapshot() throws Exception {
    // given
    final ArgumentCaptor<Long> metadata = ArgumentCaptor.forClass(Long.class);

    // when
    final long lastEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);
    logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);

    // then
    verify(snapshotController, timeout(5000).times(1)).moveValidSnapshot(metadata.capture());
    assertThat(metadata.getValue()).isEqualTo(lastEventPosition);
  }

  @Test
  public void shouldEnsureMaxSnapshotCount() throws Exception {
    // given
    final long eventCount = 5L;

    // when
    for (int i = 0; i < eventCount; i++) {
      writeEventAndWaitUntilProcessed(EVENT_1);
      logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);
      writeEventAndWaitUntilProcessed(EVENT_1);
    }

    // then
    verify(snapshotController, timeout(5000).atLeast(4)).moveValidSnapshot(anyLong());
    verify(snapshotController, timeout(5000).atLeast(4)).ensureMaxSnapshotCount(3);

    waitUntil(() -> stateStorage.list().size() == 3);
    assertThat(stateStorage.listByPositionAsc()).hasSize(3);
  }

  @Test
  public void shouldWriteSnapshotOnClosing() throws Exception {
    // given
    final ArgumentCaptor<Long> args = ArgumentCaptor.forClass(Long.class);
    final CountDownLatch latch = new CountDownLatch(1);
    changeMockInActorContext(
        () ->
            doAnswer(
                    i -> {
                      latch.countDown();
                      return i.callRealMethod();
                    })
                .when(eventProcessor)
                .executeSideEffects());

    // when
    final long lastEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);

    latch.await();
    streamProcessorController.closeAsync().join();

    // then
    verify(snapshotController, timeout(5000).times(1)).takeSnapshot(args.capture());
    assertThat(args.getValue()).isEqualTo(lastEventPosition);
  }

  @Test
  public void shouldRecoverLastPositionFromSnapshot() throws Exception {
    // given
    final long firstEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);
    logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);
    verify(snapshotController, timeout(500).times(1)).takeTempSnapshot();
    verify(snapshotController, timeout(500).times(1)).moveValidSnapshot(anyLong());

    streamProcessorController.closeAsync().join();
    final List<LoggedEvent> seenEventsBefore = streamProcessor.getEvents();

    // when
    processorCreated = new CountDownLatch(1);
    streamProcessorController.openAsync().join();
    processorCreated.await();
    openedFuture.join();
    final long secondEventPosition = writeEventAndWaitUntilProcessed(EVENT_2);

    // then
    assertThat(seenEventsBefore)
        .hasSize(1)
        .extracting(LoggedEvent::getPosition)
        .containsExactly(firstEventPosition);

    assertThat(streamProcessor.getEvents())
        .hasSize(1)
        .extracting(LoggedEvent::getPosition)
        .containsExactly(secondEventPosition);
  }

  @Test
  public void shouldRecoverFromSnapshotButReturnNoValidPosition() throws Exception {
    // given
    final long firstEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);
    logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);
    verify(snapshotController, timeout(500).times(1)).takeTempSnapshot();
    verify(snapshotController, timeout(500).times(1)).moveValidSnapshot(anyLong());

    streamProcessorController.closeAsync().join();
    final List<LoggedEvent> seenEventsBefore = streamProcessor.getEvents();

    // when
    changeRecordingStreamProcessor =
        (processor) -> doReturn(-1L).when(processor).getPositionToRecoverFrom();
    streamProcessorController.openAsync().join();
    final long secondEventPosition = writeEventAndWaitUntilProcessed(EVENT_2);

    // then
    assertThat(seenEventsBefore)
        .hasSize(1)
        .extracting(LoggedEvent::getPosition)
        .containsExactly(firstEventPosition);

    assertThat(streamProcessor.getEvents())
        .hasSize(2)
        .extracting(LoggedEvent::getPosition)
        .containsExactly(firstEventPosition, secondEventPosition);
  }

  @Test
  public void shouldSkipEventOnEventError() {
    // given
    final AtomicLong count = new AtomicLong(0);
    changeMockInActorContext(
        () ->
            doAnswer(
                    (invocationOnMock) -> {
                      if (count.getAndIncrement() == 1) {
                        throw new RuntimeException("expected");
                      }
                      return invocationOnMock.callRealMethod();
                    })
                .when(streamProcessor)
                .onEvent(any()));

    // when
    writer.writeEvents(3, EVENT_1);

    // then
    waitUntil(() -> count.get() == 3);

    final InOrder inOrderStreamProcessor = inOrder(streamProcessor);
    inOrderStreamProcessor.verify(streamProcessor, times(3)).onEvent(any());
    inOrderStreamProcessor.verifyNoMoreInteractions();

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor).processEvent();
    inOrder.verify(eventProcessor).writeEvent(any());
    inOrder.verify(eventProcessor).executeSideEffects();

    inOrder.verify(eventProcessor).processEvent();
    inOrder.verify(eventProcessor).writeEvent(any());
    inOrder.verify(eventProcessor).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSkipEventOnProcessEventError() {
    // given
    final AtomicLong count = new AtomicLong(0);
    changeMockInActorContext(
        () ->
            doAnswer(
                    (invocationOnMock) -> {
                      if (count.getAndIncrement() == 1) {
                        throw new RuntimeException("expected");
                      } else {
                        invocationOnMock.callRealMethod();
                      }
                      return null;
                    })
                .when(eventProcessor)
                .processEvent());

    // when
    writer.writeEvents(3, EVENT_1);

    // then
    waitUntil(() -> count.get() == 3);

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor).processEvent();
    inOrder.verify(eventProcessor).writeEvent(any());
    inOrder.verify(eventProcessor).executeSideEffects();
    // includes skip
    inOrder.verify(eventProcessor, times(2)).processEvent();

    inOrder.verify(eventProcessor).writeEvent(any());
    inOrder.verify(eventProcessor).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotRetryExecuteSideEffectsOnException() throws Exception {
    // when
    final CountDownLatch latch = new CountDownLatch(2);
    changeMockInActorContext(
        () ->
            doAnswer(
                    (invocationOnMock) -> {
                      latch.countDown();
                      throw new RecoverableException("expected");
                    })
                .when(eventProcessor)
                .executeSideEffects());
    writer.writeEvents(2, EVENT_1);

    // then
    latch.await();

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).processEvent();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotRetryOnNonRecoverableException() throws Exception {
    // when
    final CountDownLatch latch = new CountDownLatch(2);
    changeMockInActorContext(
        () ->
            doAnswer(
                    (invocationOnMock) -> {
                      latch.countDown();
                      return invocationOnMock.callRealMethod();
                    })
                .when(eventProcessor)
                .writeEvent(any()));
    changeMockInActorContext(
        () -> doThrow(new RuntimeException("expected")).when(eventProcessor).executeSideEffects());
    writer.writeEvents(2, EVENT_1);

    // then
    latch.await();

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).processEvent();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldNotRetryWhenWriteEventFailed() throws Exception {
    // when
    final CountDownLatch latch = new CountDownLatch(2);
    changeMockInActorContext(
        () ->
            doAnswer(
                    (invocationOnMock) -> {
                      latch.countDown();
                      if (latch.getCount() == 1) {
                        throw new RuntimeException("expected");
                      }
                      return invocationOnMock.callRealMethod();
                    })
                .when(eventProcessor)
                .writeEvent(any()));
    writer.writeEvents(2, EVENT_1);

    // then
    latch.await();

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, timeout(500L).times(1)).processEvent();
    inOrder.verify(eventProcessor, timeout(500L).times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, timeout(500L).times(1)).onError(any(RuntimeException.class));
    inOrder.verify(eventProcessor, timeout(500L).times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, timeout(500L).times(1)).executeSideEffects();

    inOrder.verify(eventProcessor, timeout(500L).times(1)).processEvent();
    inOrder.verify(eventProcessor, timeout(500L).times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, timeout(500L).times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  private void installStreamProcessorService() throws Exception {
    stateStorage = createStateStorage();
    snapshotController =
        spy(
            new StateSnapshotController(
                (path) -> {
                  final ZeebeDb<DefaultColumnFamily> db =
                      ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class).createDb(path);
                  zeebeDb = spy(db);
                  doAnswer(
                          invocationOnMock -> {
                            dbContext = (DbContext) spy(invocationOnMock.callRealMethod());
                            return dbContext;
                          })
                      .when(zeebeDb)
                      .createContext();
                  return zeebeDb;
                },
                stateStorage));

    processorCreated = new CountDownLatch(1);
    streamProcessorService =
        StreamProcessors.createStreamProcessor(PROCESSOR_NAME, PROCESSOR_ID)
            .logStream(logStreamRule.getLogStream())
            .actorScheduler(logStreamRule.getActorScheduler())
            .eventFilter(eventFilter)
            .serviceContainer(logStreamRule.getServiceContainer())
            .snapshotController(snapshotController)
            .maxSnapshots(MAX_SNAPSHOTS)
            .streamProcessorFactory(
                (actor, db, ctx) -> {
                  openedFuture = new CompletableActorFuture<>();
                  processorCreated.countDown();
                  return createStreamProcessor(db, openedFuture);
                })
            .snapshotPeriod(SNAPSHOT_INTERVAL)
            .build()
            .join();

    processorCreated.await();
    openedFuture.join();
    openedFuture = null;

    streamProcessorController = streamProcessorService.getController();
  }

  private StreamProcessor createStreamProcessor(ZeebeDb zeebeDb, ActorFuture<Void> openFuture) {
    streamProcessor = RecordingStreamProcessor.createSpy(zeebeDb, openFuture);
    changeRecordingStreamProcessor.accept(streamProcessor);
    eventProcessor = streamProcessor.getEventProcessorSpy();
    return streamProcessor;
  }

  private void changeMockInActorContext(Runnable runnable) {
    streamProcessor.getContext().getActorControl().call(runnable).join();
  }

  private StateStorage createStateStorage() throws IOException {
    final File runtimeDirectory = temporaryFolder.newFolder("state-runtime");
    final File snapshotsDirectory = temporaryFolder.newFolder("state-snapshots");

    return new StateStorage(runtimeDirectory, snapshotsDirectory);
  }

  private long writeEventAndWaitUntilProcessedOrFailed(DirectBuffer event) {
    final int beforeProcessed = streamProcessor.getProcessedEventCount();
    final int beforeFailed = streamProcessor.getProcessingFailedCount();

    final long eventPosition = writer.writeEvent(event);

    waitUntilProcessedOrFailed(beforeProcessed, beforeFailed);
    return eventPosition;
  }

  private void waitUntilProcessedOrFailed(int beforeProcessed, int beforeFailed) {
    waitUntil(
        () ->
            streamProcessor.getProcessedEventCount() == beforeProcessed + 1
                || streamProcessor.getProcessingFailedCount() == beforeFailed + 1);
  }

  private long writeEventAndWaitUntilProcessed(DirectBuffer event) {
    final int before = streamProcessor.getProcessedEventCount();

    final long eventPosition = writer.writeEvent(event);

    waitUntil(() -> streamProcessor.getProcessedEventCount() >= before + 1);
    return eventPosition;
  }
}
