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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbString;
import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateSnapshotMetadata;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.logstreams.util.LogStreamReaderRule;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.logstreams.util.MutableStateSnapshotMetadata;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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

  private static final String STATE_KEY = "stateValue";
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
  private SnapshotController snapshotController;
  private EventProcessor eventProcessor;
  private EventFilter eventFilter;

  private ZeebeDb db;
  private DbString value;
  private DbString key;
  private ColumnFamily<DbString, DbString> columnFamily;

  @Before
  public void setup() throws Exception {
    eventFilter = mock(EventFilter.class);
    when(eventFilter.applies(any())).thenReturn(true);

    key = new DbString();
    key.wrapString(STATE_KEY);
    value = new DbString();

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
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());

    inOrder.verify(streamProcessor, times(1)).onClose();
    inOrder.verify(snapshotController, times(1)).takeSnapshot(any());
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
    inOrder.verify(eventProcessor, times(1)).processEvent();
    inOrder.verify(eventProcessor, times(3)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
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
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(3)).writeEvent(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSkipEventIfNoEventProcessorIsProvided() {
    // when
    // return null as event processor for first event
    changeMockInActorContext(
        () -> doReturn(null).doCallRealMethod().when(streamProcessor).onEvent(any()));
    writer.writeEvent(EVENT_1, true);
    final long secondEventPosition = writer.writeEvent(EVENT_2, true);

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
    writer.writeEvent(EVENT_1, true);
    final long secondEventPosition = writer.writeEvent(EVENT_2, true);

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
    writer.writeEvent(EVENT_1, true);
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
                    }));

    final long firstEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);
    waitUntil(() -> writtenEventPosition.get() > 0);
    final long position = writtenEventPosition.get();
    writer.waitForPositionToBeAppended(position);
    logStreamRule.setCommitPosition(position);

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
    writer.writeEvent(EVENT_1, true);

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
    writer.writeEvent(EVENT_1, true);

    waitUntil(() -> invocations.get() >= 1);

    final ActorFuture<Void> future = streamProcessorController.closeAsync();

    // then
    waitUntil(future::isDone);

    assertThat(streamProcessorController.isOpened()).isFalse();
  }

  @Test
  public void shouldWriteSnapshot() throws Exception {
    // given
    final ArgumentCaptor<StateSnapshotMetadata> args =
        ArgumentCaptor.forClass(StateSnapshotMetadata.class);

    // when
    final long lastEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);
    logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);

    // then
    verify(snapshotController, timeout(5000).times(1)).takeSnapshot(args.capture());
    assertThat(args.getValue().getLastSuccessfulProcessedEventPosition())
        .isEqualTo(lastEventPosition);
    assertThat(args.getValue().getLastWrittenEventPosition()).isEqualTo(lastEventPosition);
    assertThat(args.getValue().getLastWrittenEventTerm())
        .isEqualTo(logStreamRule.getLogStream().getTerm());
  }

  @Test
  public void shouldWriteSnapshotOnClosing() throws Exception {
    // given
    final ArgumentCaptor<StateSnapshotMetadata> args =
        ArgumentCaptor.forClass(StateSnapshotMetadata.class);

    // when
    final long lastEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);
    streamProcessorController.closeAsync().join();

    // then
    verify(snapshotController, timeout(5000).times(1)).takeSnapshot(args.capture());
    assertThat(args.getValue().getLastSuccessfulProcessedEventPosition())
        .isEqualTo(lastEventPosition);
    assertThat(args.getValue().getLastWrittenEventPosition()).isEqualTo(lastEventPosition);
    assertThat(args.getValue().getLastWrittenEventTerm())
        .isEqualTo(logStreamRule.getLogStream().getTerm());
  }

  @Test
  public void shouldNotRecoverFromSnapshotWithInvalidLastWrittenTerm() throws Exception {
    // given
    final long lastProcessedEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);
    final StateSnapshotMetadata valid =
        new StateSnapshotMetadata(
            lastProcessedEventPosition,
            lastProcessedEventPosition,
            logStreamRule.getLogStream().getTerm(),
            false);
    final StateSnapshotMetadata invalid =
        new StateSnapshotMetadata(
            lastProcessedEventPosition,
            lastProcessedEventPosition,
            logStreamRule.getLogStream().getTerm() + 1,
            false);

    // when
    setState("valid");
    streamProcessorController.closeAsync().join();
    streamProcessorController.openAsync().join();

    setState("invalid");
    snapshotController.takeSnapshot(invalid);

    streamProcessorController.closeAsync().join();
    streamProcessorController.openAsync().join();

    // then
    assertThat(getState()).isEqualTo("valid");
  }

  @Test
  public void shouldNotRecoverFromSnapshotWithUncommittedLastWrittenEvent() throws Exception {
    // given
    final long lastProcessedEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);
    final StateSnapshotMetadata valid =
        new StateSnapshotMetadata(
            lastProcessedEventPosition,
            lastProcessedEventPosition,
            logStreamRule.getLogStream().getTerm(),
            false);
    final StateSnapshotMetadata invalid =
        new StateSnapshotMetadata(
            lastProcessedEventPosition,
            logStreamRule.getCommitPosition() + 1,
            logStreamRule.getLogStream().getTerm(),
            false);

    // when
    setState("valid");
    streamProcessorController.closeAsync().join();
    streamProcessorController.openAsync().join();

    setState("invalid");
    snapshotController.takeSnapshot(invalid);

    streamProcessorController.closeAsync().join();
    streamProcessorController.openAsync().join();

    // then
    assertThat(getState()).isEqualTo("valid");
  }

  @Test
  public void shouldRecoverLastPositionFromSnapshot() {
    // given
    final long firstEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);
    streamProcessorController.closeAsync().join();
    final List<LoggedEvent> seenEventsBefore = streamProcessor.getEvents();

    // when
    streamProcessorController.openAsync().join();
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
    writer.writeEvents(3, EVENT_1, true);

    // then
    waitUntil(() -> count.get() == 3);

    final InOrder inOrderStreamProcessor = inOrder(streamProcessor);
    inOrderStreamProcessor.verify(streamProcessor, times(3)).onEvent(any());
    inOrderStreamProcessor.verifyNoMoreInteractions();

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor).processEvent();
    inOrder.verify(eventProcessor).executeSideEffects();
    inOrder.verify(eventProcessor).writeEvent(any());

    inOrder.verify(eventProcessor).processEvent();
    inOrder.verify(eventProcessor).executeSideEffects();
    inOrder.verify(eventProcessor).writeEvent(any());
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
    writer.writeEvents(3, EVENT_1, true);

    // then
    waitUntil(() -> count.get() == 3);

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor).processEvent();
    inOrder.verify(eventProcessor).executeSideEffects();
    inOrder.verify(eventProcessor).writeEvent(any());
    // includes skip
    inOrder.verify(eventProcessor, times(2)).processEvent();

    inOrder.verify(eventProcessor).executeSideEffects();
    inOrder.verify(eventProcessor).writeEvent(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldFailOnExecuteSideEffects() {
    // when
    changeMockInActorContext(
        () -> doThrow(new RuntimeException("expected")).when(eventProcessor).executeSideEffects());
    writer.writeEvents(2, EVENT_1, true);

    // then
    waitUntil(() -> streamProcessorController.isFailed());

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent();
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldFailOnWriteEvent() {
    // when
    changeMockInActorContext(
        () -> doThrow(new RuntimeException("expected")).when(eventProcessor).writeEvent(any()));
    writer.writeEvents(2, EVENT_1, true);

    // then
    waitUntil(() -> streamProcessorController.isFailed());

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent();
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldFailToWriteEventIfReadOnly() {
    // when
    streamProcessorController.closeAsync().join();

    streamProcessorController =
        LogStreams.createStreamProcessor("read-only", PROCESSOR_ID)
            .logStream(logStreamRule.getLogStream())
            .actorScheduler(logStreamRule.getActorScheduler())
            .serviceContainer(logStreamRule.getServiceContainer())
            .snapshotController(snapshotController)
            .streamProcessorFactory((zeebeDb -> streamProcessor))
            .readOnly(true)
            .build()
            .join()
            .getController();

    // given

    changeMockInActorContext(
        () ->
            when(eventProcessor.writeEvent(any()))
                .thenAnswer(
                    inv -> {
                      final LogStreamRecordWriter writer = inv.getArgument(0);

                      return writer.key(2L).metadata(wrapString("META")).value(EVENT_2).tryWrite();
                    }));

    // when
    writer.writeEvent(EVENT_1, true);

    // then
    waitUntil(() -> streamProcessorController.isFailed());

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent();
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldTakeStateSnapshotEvenIfLastWrittenEventIsUncommitted() throws Exception {
    // given
    final ArgumentCaptor<StateSnapshotMetadata> args =
        ArgumentCaptor.forClass(StateSnapshotMetadata.class);
    final MutableStateSnapshotMetadata expectedState =
        new MutableStateSnapshotMetadata(-1, -1, -1, true);
    // when
    changeMockInActorContext(
        () ->
            doAnswer(
                    i -> {
                      expectedState.setLastWrittenEventPosition(writer.writeEvent(EVENT_2, false));
                      expectedState.setLastWrittenEventTerm(logStreamRule.getLogStream().getTerm());
                      return expectedState.getLastWrittenEventPosition();
                    })
                .when(eventProcessor)
                .writeEvent(any()));
    expectedState.setLastSuccessfulProcessedEventPosition(writeEventAndWaitUntilProcessed(EVENT_1));
    // when
    logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);
    streamProcessorController.closeAsync().join();
    // then
    verify(snapshotController, timeout(5000).times(1)).takeSnapshot(args.capture());
    assertThat(args.getValue()).isEqualTo(expectedState);
  }

  private void installStreamProcessorService() throws IOException {
    snapshotController =
        spy(
            new StateSnapshotController(
                ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class), createStateStorage()));

    streamProcessorService =
        LogStreams.createStreamProcessor(PROCESSOR_NAME, PROCESSOR_ID)
            .logStream(logStreamRule.getLogStream())
            .actorScheduler(logStreamRule.getActorScheduler())
            .eventFilter(eventFilter)
            .serviceContainer(logStreamRule.getServiceContainer())
            .snapshotController(snapshotController)
            .streamProcessorFactory(
                (db) -> {
                  streamProcessor = RecordingStreamProcessor.createSpy(db);
                  eventProcessor = streamProcessor.getEventProcessorSpy();
                  this.db = db;
                  columnFamily = db.createColumnFamily(DefaultColumnFamily.DEFAULT, key, value);
                  return streamProcessor;
                })
            .snapshotPeriod(SNAPSHOT_INTERVAL)
            .build()
            .join();

    streamProcessorController = streamProcessorService.getController();
  }

  private void changeMockInActorContext(Runnable runnable) {
    streamProcessor.getContext().getActorControl().call(runnable).join();
  }

  private StateStorage createStateStorage() throws IOException {
    final File runtimeDirectory = temporaryFolder.newFolder("state-runtime");
    final File snapshotsDirectory = temporaryFolder.newFolder("state-snapshots");

    return new StateStorage(runtimeDirectory, snapshotsDirectory);
  }

  private long writeEventAndWaitUntilProcessed(DirectBuffer event) {
    final int before = streamProcessor.getProcessedEventCount();

    final long eventPosition = writer.writeEvent(event, true);

    waitUntil(() -> streamProcessor.getProcessedEventCount() == before + 1);
    return eventPosition;
  }

  private void setState(final String value) {
    this.value.wrapString(value);
    columnFamily.put(key, this.value);
  }

  private String getState() {
    final DbString zbString = columnFamily.get(key);
    return zbString != null ? zbString.toString() : null;
  }
}
