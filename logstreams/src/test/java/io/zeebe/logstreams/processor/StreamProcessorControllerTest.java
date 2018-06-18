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
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.ReadableSnapshot;
import io.zeebe.logstreams.util.LogStreamReaderRule;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.InOrder;

public class StreamProcessorControllerTest {
  private static final String PROCESSOR_NAME = "test";
  private static final int PROCESSOR_ID = 1;
  private static final Duration SNAPSHOT_INTERVAL = Duration.ofMinutes(1);

  private static final DirectBuffer EVENT_1 = wrapString("FOO");
  private static final DirectBuffer EVENT_2 = wrapString("BAR");

  private TemporaryFolder temporaryFolder = new TemporaryFolder();
  private LogStreamRule logStreamRule = new LogStreamRule(temporaryFolder);
  private LogStreamWriterRule writer = new LogStreamWriterRule(logStreamRule);
  private LogStreamReaderRule reader = new LogStreamReaderRule(logStreamRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolder).around(logStreamRule).around(reader).around(writer);

  private StreamProcessorController controller;
  private RecordingStreamProcessor streamProcessor;
  private EventProcessor eventProcessor;
  private EventFilter eventFilter;

  @Before
  public void init() {
    streamProcessor = RecordingStreamProcessor.createSpy();
    eventProcessor = streamProcessor.getEventProcessorSpy();

    eventFilter = mock(EventFilter.class);
    when(eventFilter.applies(any())).thenReturn(true);

    final StreamProcessorService streamProcessorService =
        LogStreams.createStreamProcessor(PROCESSOR_NAME, PROCESSOR_ID, streamProcessor)
            .logStream(logStreamRule.getLogStream())
            .snapshotStorage(logStreamRule.getSnapshotStorage())
            .actorScheduler(logStreamRule.getActorScheduler())
            .eventFilter(eventFilter)
            .snapshotPeriod(SNAPSHOT_INTERVAL)
            .serviceContainer(logStreamRule.getServiceContainer())
            .build()
            .join();

    controller = streamProcessorService.getController();
  }

  @Test
  public void testStreamProcessorLifecycle() {
    // when
    writeEventAndWaitUntilProcessed(EVENT_1);

    controller.closeAsync().join();

    // then
    final InOrder inOrder = inOrder(streamProcessor, eventProcessor);
    inOrder.verify(streamProcessor, times(1)).onOpen(any());
    inOrder.verify(streamProcessor, times(1)).onEvent(any());

    inOrder.verify(eventProcessor, times(1)).processEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).updateState();

    inOrder.verify(streamProcessor, times(1)).onClose();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testAsyncProcessEvent()
      throws InterruptedException, ExecutionException, TimeoutException {
    final ActorFuture<Void> whenProcessingInvoked = new CompletableActorFuture<>();
    final ActorFuture<Void> whenProcessingDone = new CompletableActorFuture<>();

    doAnswer(
            invocation -> {
              final EventLifecycleContext ctx = invocation.getArgument(0);
              ctx.async(whenProcessingDone);

              whenProcessingInvoked.complete(null);

              return null;
            })
        .when(eventProcessor)
        .processEvent(any());

    // when
    writer.writeEvent(EVENT_1, true);
    whenProcessingInvoked.get(5, TimeUnit.SECONDS);

    // then
    final InOrder inOrder = inOrder(eventProcessor);

    inOrder.verify(eventProcessor, times(1)).processEvent(any());
    inOrder.verifyNoMoreInteractions();

    // and when
    whenProcessingDone.complete(null);
    waitUntil(() -> streamProcessor.getProcessedEventCount() == 1);

    // then
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).updateState();

    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testAsyncProcessEventWaitEvenIfNewEventIsWritten()
      throws InterruptedException, ExecutionException, TimeoutException {
    // tests that the stream processor that is currently blocked on
    // future passed during processEvent does not continue when a new
    // event is written (the stream processor is triggered on commit update)

    final ActorFuture<Void> whenProcessingInvoked = new CompletableActorFuture<>();
    final ActorFuture<Void> whenProcessingDone = new CompletableActorFuture<>();

    doAnswer(
            invocation -> {
              final EventLifecycleContext ctx = invocation.getArgument(0);

              if (!whenProcessingDone.isDone()) // only block on first invocation
              {
                ctx.async(whenProcessingDone);
                whenProcessingInvoked.complete(null);
              }

              return null;
            })
        .when(eventProcessor)
        .processEvent(any());

    // given
    writer.writeEvent(EVENT_1, true);
    whenProcessingInvoked.get(5, TimeUnit.SECONDS);

    // when
    writer.writeEvent(EVENT_2, true);
    Thread.sleep(500); // how to do it better?

    // then
    verify(eventProcessor, times(1)).processEvent(any());

    // and when
    whenProcessingDone.complete(null);
    waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

    // then
    verify(eventProcessor, times(2)).processEvent(any());
  }

  @Test
  public void testAsyncProcessEventCompleteExceptionally()
      throws InterruptedException, ExecutionException, TimeoutException {
    final ActorFuture<Void> whenProcessingInvoked = new CompletableActorFuture<>();

    doAnswer(
            invocation -> {
              final EventLifecycleContext ctx = invocation.getArgument(0);
              ctx.async(CompletableActorFuture.completedExceptionally(new RuntimeException()));
              whenProcessingInvoked.complete(null);
              return null;
            })
        .when(eventProcessor)
        .processEvent(any());

    // when
    writer.writeEvent(EVENT_1, true);
    whenProcessingInvoked.get(5, TimeUnit.SECONDS);

    // then
    final InOrder inOrder = inOrder(eventProcessor);

    inOrder.verify(eventProcessor, times(1)).processEvent(any());
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
    // given
    when(eventProcessor.executeSideEffects()).thenReturn(false, false, true);

    // when
    writeEventAndWaitUntilProcessed(EVENT_1);

    // then
    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent(any());
    inOrder.verify(eventProcessor, times(3)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).updateState();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldWriteEventUntilDone() {
    // given
    when(eventProcessor.writeEvent(any())).thenReturn(-1L, -1L, 1L);

    // when
    writeEventAndWaitUntilProcessed(EVENT_1);

    // then
    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(3)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).updateState();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldSkipEventIfNoEventProcessorIsProvided() {
    // given
    // return null as event processor for first event
    doReturn(null).doCallRealMethod().when(streamProcessor).onEvent(any());

    // when
    writer.writeEvent(EVENT_1, true);
    final long secondEventPosition = writer.writeEvent(EVENT_2, true);

    // then
    waitUntil(() -> streamProcessor.getEvents().size() == 1);

    final LoggedEvent processedEvent = streamProcessor.getEvents().get(0);
    assertThat(processedEvent.getPosition()).isEqualTo(secondEventPosition);

    final InOrder inOrder = inOrder(streamProcessor, eventProcessor);
    inOrder.verify(streamProcessor, times(2)).onEvent(any());
    inOrder.verify(eventProcessor, times(1)).processEvent(any());
  }

  @Test
  public void shouldSkipEventIfEventFilterIsNotMet() {
    // given
    when(eventFilter.applies(any())).thenReturn(false, true);

    // when
    writer.writeEvent(EVENT_1, true);
    final long secondEventPosition = writer.writeEvent(EVENT_2, true);

    // then
    waitUntil(() -> streamProcessor.getEvents().size() == 1);

    final LoggedEvent processedEvent = streamProcessor.getEvents().get(0);
    assertThat(processedEvent.getPosition()).isEqualTo(secondEventPosition);

    final InOrder inOrder = inOrder(streamProcessor, eventProcessor);
    inOrder.verify(streamProcessor, times(1)).onEvent(any());
    inOrder.verify(eventProcessor, times(1)).processEvent(any());
  }

  @Test
  public void shouldSuspendAndResume() {
    // given
    streamProcessor.suspend();

    assertThat(controller.isSuspended()).isTrue();

    // when
    writer.writeEvent(EVENT_1, true);

    streamProcessor.resume();

    // then
    waitUntil(() -> !controller.isSuspended());
    waitUntil(() -> streamProcessor.getProcessedEventCount() == 1);
  }

  @Test
  public void shouldWriteEvent() {
    // given
    final AtomicLong writtenEventPosition = new AtomicLong();

    when(eventProcessor.writeEvent(any()))
        .thenAnswer(
            inv -> {
              final LogStreamWriter writer = inv.getArgument(0);

              final long position =
                  writer.key(2L).metadata(wrapString("META")).value(EVENT_2).tryWrite();

              writtenEventPosition.set(position);

              return position;
            });

    // when
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
    doAnswer(
            inv -> {
              invocations.incrementAndGet();
              return false;
            })
        .when(eventProcessor)
        .executeSideEffects();

    // when
    writer.writeEvent(EVENT_1, true);

    waitUntil(() -> invocations.get() >= 1);

    final ActorFuture<Void> future = controller.closeAsync();

    // then
    waitUntil(() -> future.isDone());

    assertThat(controller.isOpened()).isFalse();
  }

  @Test
  public void shouldCloseOnWriteEvent() {
    // given
    final AtomicInteger invocations = new AtomicInteger();
    doAnswer(
            inv -> {
              invocations.incrementAndGet();
              return -1L;
            })
        .when(eventProcessor)
        .writeEvent(any());

    // when
    writer.writeEvent(EVENT_1, true);

    waitUntil(() -> invocations.get() >= 1);

    final ActorFuture<Void> future = controller.closeAsync();

    // then
    waitUntil(() -> future.isDone());

    assertThat(controller.isOpened()).isFalse();
  }

  @Test
  public void shouldWriteSnapshot() {
    // given
    final long lastEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);

    // when
    logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);

    // then
    waitUntil(() -> getLatestSnapshot() != null);

    final long snapshotPosition = getLatestSnapshot().getPosition();
    assertThat(snapshotPosition).isEqualTo(lastEventPosition);
  }

  @Test
  public void shouldWriteSnapshotOnClosing() {
    // given
    final long lastEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);

    // when
    controller.closeAsync().join();

    // then
    assertThat(getLatestSnapshot()).isNotNull();

    final long snapshotPosition = getLatestSnapshot().getPosition();
    assertThat(snapshotPosition).isEqualTo(lastEventPosition);
  }

  @Test
  public void shouldRecoverStateFromSnapshot() {
    // given
    writeEventAndWaitUntilProcessed(EVENT_1);

    streamProcessor.getSnapshot().setValue("foo");

    controller.closeAsync().join();

    streamProcessor.getSnapshot().setValue(null);

    // when
    controller.openAsync().join();

    // then
    assertThat(streamProcessor.getSnapshot().getValue()).isEqualTo("foo");
  }

  @Test
  public void shouldRecoverLastPositionFromSnapshot() {
    // given
    final long firstEventPosition = writeEventAndWaitUntilProcessed(EVENT_1);

    controller.closeAsync().join();

    // when
    controller.openAsync().join();

    final long secondEventPosition = writeEventAndWaitUntilProcessed(EVENT_2);

    // then
    assertThat(streamProcessor.getEvents())
        .hasSize(2)
        .extracting(LoggedEvent::getPosition)
        .containsExactly(firstEventPosition, secondEventPosition);
  }

  @Test
  public void shouldFailOnProcessEvent() {
    // given
    doThrow(new RuntimeException("expected")).when(eventProcessor).processEvent(any());

    // when
    writer.writeEvents(2, EVENT_1, true);

    // then
    waitUntil(() -> controller.isFailed());

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldFailOnExecuteSideEffects() {
    // given
    doThrow(new RuntimeException("expected")).when(eventProcessor).executeSideEffects();

    // when
    writer.writeEvents(2, EVENT_1, true);

    // then
    waitUntil(() -> controller.isFailed());

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldFailOnWriteEvent() {
    // given
    doThrow(new RuntimeException("expected")).when(eventProcessor).writeEvent(any());

    // when
    writer.writeEvents(2, EVENT_1, true);

    // then
    waitUntil(() -> controller.isFailed());

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldFailOnUpdateState() {
    // given
    doThrow(new RuntimeException("expected")).when(eventProcessor).updateState();

    // when
    writer.writeEvents(2, EVENT_1, true);

    // then
    waitUntil(() -> controller.isFailed());

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verify(eventProcessor, times(1)).updateState();
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldFailToWriteEventIfReadOnly() {
    controller.closeAsync().join();

    controller =
        LogStreams.createStreamProcessor("read-only", PROCESSOR_ID, streamProcessor)
            .logStream(logStreamRule.getLogStream())
            .snapshotStorage(logStreamRule.getSnapshotStorage())
            .actorScheduler(logStreamRule.getActorScheduler())
            .serviceContainer(logStreamRule.getServiceContainer())
            .readOnly(true)
            .build()
            .join()
            .getController();

    // given
    when(eventProcessor.writeEvent(any()))
        .thenAnswer(
            inv -> {
              final LogStreamWriter writer = inv.getArgument(0);

              return writer.key(2L).metadata(wrapString("META")).value(EVENT_2).tryWrite();
            });

    // when
    writer.writeEvent(EVENT_1, true);

    // then
    waitUntil(() -> controller.isFailed());

    final InOrder inOrder = inOrder(eventProcessor);
    inOrder.verify(eventProcessor, times(1)).processEvent(any());
    inOrder.verify(eventProcessor, times(1)).executeSideEffects();
    inOrder.verify(eventProcessor, times(1)).writeEvent(any());
    inOrder.verifyNoMoreInteractions();
  }

  private long writeEventAndWaitUntilProcessed(DirectBuffer event) {
    final int before = streamProcessor.getProcessedEventCount();

    final long eventPosition = writer.writeEvent(event, true);

    waitUntil(() -> streamProcessor.getProcessedEventCount() == before + 1);
    return eventPosition;
  }

  private ReadableSnapshot getLatestSnapshot() {
    try {
      return logStreamRule.getSnapshotStorage().getLastSnapshot(PROCESSOR_NAME);
    } catch (Exception e) {
      fail("Fail to read snapshot", e);
      return null;
    }
  }
}
