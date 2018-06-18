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
import static org.mockito.Mockito.*;

import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class StreamProcessorReprocessingTest {
  private static final String PROCESSOR_NAME = "test";
  private static final int PROCESSOR_ID = 1;
  private static final int OTHER_PROCESSOR_ID = 2;

  private static final DirectBuffer EVENT = wrapString("FOO");

  private TemporaryFolder temporaryFolder = new TemporaryFolder();
  private LogStreamRule logStreamRule = new LogStreamRule(temporaryFolder);
  private LogStreamWriterRule writer = new LogStreamWriterRule(logStreamRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolder).around(logStreamRule).around(writer);

  private RecordingStreamProcessor streamProcessor;
  private EventProcessor eventProcessor;
  private EventFilter eventFilter;

  @Before
  public void init() {
    streamProcessor = RecordingStreamProcessor.createSpy();
    eventProcessor = streamProcessor.getEventProcessorSpy();

    eventFilter = mock(EventFilter.class);
    when(eventFilter.applies(any())).thenReturn(true);
  }

  private StreamProcessorService openStreamProcessorController() {
    return openStreamProcessorController(streamProcessor);
  }

  private ActorFuture<StreamProcessorService> openStreamProcessorControllerAsync() {
    return openStreamProcessorControllerAsync(streamProcessor);
  }

  private StreamProcessorService openStreamProcessorController(StreamProcessor streamProcessor) {
    return openStreamProcessorControllerAsync(streamProcessor).join();
  }

  private ActorFuture<StreamProcessorService> openStreamProcessorControllerAsync(
      StreamProcessor streamProcessor) {
    return LogStreams.createStreamProcessor(PROCESSOR_NAME, PROCESSOR_ID, streamProcessor)
        .logStream(logStreamRule.getLogStream())
        .snapshotStorage(logStreamRule.getSnapshotStorage())
        .actorScheduler(logStreamRule.getActorScheduler())
        .serviceContainer(logStreamRule.getServiceContainer())
        .eventFilter(eventFilter)
        .build();
  }

  /**
   * Format: [1|S:-] --> [2|S:1]
   *
   * <p>=> two events: first event has no source event position, second event has the first event's
   * position as source event position
   */
  @Test
  public void shouldReprocessSourceEvent() {
    // given [1|S:-] --> [2|S:1]
    final long eventPosition1 = writeEvent();
    final long eventPosition2 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition1));

    // when
    openStreamProcessorController();

    waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1, eventPosition2);

    verify(eventProcessor, times(2)).processEvent(any());
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
    verify(eventProcessor, times(2)).updateState();
  }

  @Test
  public void shouldReprocessSourceEventAsync()
      throws InterruptedException, ExecutionException, TimeoutException {
    final ActorFuture<Void> whenProcessEventInvoked = new CompletableActorFuture<>();
    final ActorFuture<Void> whenProcessEventCompleted = new CompletableActorFuture<>();

    doAnswer(
            (invocation) -> {
              final EventLifecycleContext ctx = invocation.getArgument(0);

              if (!whenProcessEventCompleted.isDone()) // only be async on first try
              {
                ctx.async(whenProcessEventCompleted);
                whenProcessEventInvoked.complete(null);
              }

              return null;
            })
        .when(eventProcessor)
        .processEvent(any());

    // given [1|S:-] --> [2|S:1]
    final long eventPosition1 = writeEvent();
    final long eventPosition2 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition1));

    // when
    openStreamProcessorController();
    whenProcessEventInvoked.get(5, TimeUnit.SECONDS);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1);

    verify(eventProcessor, times(1)).processEvent(any());
    verifyNoMoreInteractions(eventProcessor);

    // and when
    whenProcessEventCompleted.complete(null);
    waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1, eventPosition2);

    verify(eventProcessor, times(2)).processEvent(any());
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
    verify(eventProcessor, times(2)).updateState();
  }

  @Test
  public void shouldNotReprocessEventFromOtherProcessor() {
    // given [1|S:-] --> [2|S:1]
    final long eventPosition1 = writeEvent();
    final long eventPosition2 =
        writeEventWith(w -> w.producerId(OTHER_PROCESSOR_ID).sourceRecordPosition(eventPosition1));

    // when
    openStreamProcessorController();

    waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1, eventPosition2);

    verify(eventProcessor, times(2)).processEvent(any());
    verify(eventProcessor, times(2)).executeSideEffects();
    verify(eventProcessor, times(2)).writeEvent(any());
    verify(eventProcessor, times(2)).updateState();
  }

  @Test
  public void shouldReprocessUntilLastSourceEvent() {
    // given [1|S:-] --> [2|S:1] --> [3|S:2]
    final long eventPosition1 = writeEvent();
    final long eventPosition2 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition1));
    final long eventPosition3 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition2));

    // when
    openStreamProcessorController();

    waitUntil(() -> streamProcessor.getProcessedEventCount() == 3);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1, eventPosition2, eventPosition3);

    verify(eventProcessor, times(3)).processEvent(any());
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
    verify(eventProcessor, times(3)).updateState();
  }

  @Test
  public void shouldReprocessAllEventsUntilSourceEvent() {
    // given [1|S:-] --> [2|S:-] --> [3|S:2]
    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();
    final long eventPosition3 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition2));

    // when
    openStreamProcessorController();

    waitUntil(() -> streamProcessor.getProcessedEventCount() == 3);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1, eventPosition2, eventPosition3);

    verify(eventProcessor, times(3)).processEvent(any());
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
    verify(eventProcessor, times(3)).updateState();
  }

  @Test
  public void shouldSkipEventIfNoEventProcessorIsProvided() {
    // given [1|S:-] --> [2|S:-] --> [3|S:2]
    writeEvent();
    final long eventPosition2 = writeEvent();
    final long eventPosition3 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition2));

    // return null as event processor for first event
    doReturn(null).doCallRealMethod().when(streamProcessor).onEvent(any());

    // when
    openStreamProcessorController();

    waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition2, eventPosition3);

    verify(eventProcessor, times(2)).processEvent(any());
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
    verify(eventProcessor, times(2)).updateState();
  }

  @Test
  public void shouldSkipEventIfEventFilterIsNotMet() {
    // given [1|S:-] --> [2|S:-] --> [3|S:2]
    writeEvent();
    final long eventPosition2 = writeEvent();
    final long eventPosition3 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition2));

    when(eventFilter.applies(any())).thenReturn(false, true, true);

    // when
    openStreamProcessorController();

    waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition2, eventPosition3);

    verify(eventProcessor, times(2)).processEvent(any());
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
    verify(eventProcessor, times(2)).updateState();
  }

  @Test
  public void shouldFailOnReprocessing() {
    // given [1|S:-] --> [2|S:1]
    final long eventPosition1 = writeEvent();
    writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition1));

    doThrow(new RuntimeException("expected")).when(eventProcessor).processEvent(any());

    // when
    final ActorFuture<StreamProcessorService> future = openStreamProcessorControllerAsync();

    waitUntil(() -> future.isDone());

    // then
    verify(streamProcessor, times(0)).onRecovered();
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1);
  }

  @Test
  public void shouldFailOnReprocessingAsync() {
    // given [1|S:-] --> [2|S:1]
    final long eventPosition1 = writeEvent();
    writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition1));

    doAnswer(
            (invocation) -> {
              final EventLifecycleContext ctx = invocation.getArgument(0);
              ctx.async(CompletableActorFuture.completedExceptionally(new RuntimeException()));

              return null;
            })
        .when(eventProcessor)
        .processEvent(any());

    // when
    final ActorFuture<StreamProcessorService> future = openStreamProcessorControllerAsync();

    waitUntil(() -> future.isDone());

    // then
    verify(streamProcessor, times(0)).onRecovered();

    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1);
  }

  @Test
  public void shouldNotReprocessEventsIfReadOnly() {
    final StreamProcessorBuilder builder =
        LogStreams.createStreamProcessor("read-only", PROCESSOR_ID, streamProcessor)
            .logStream(logStreamRule.getLogStream())
            .snapshotStorage(logStreamRule.getSnapshotStorage())
            .actorScheduler(logStreamRule.getActorScheduler())
            .serviceContainer(logStreamRule.getServiceContainer())
            .readOnly(true);

    // given [1|S:-] --> [2|S:1]
    final long eventPosition1 = writeEvent();
    final long eventPosition2 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition1));

    // when
    builder.build().join();

    waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1, eventPosition2);

    verify(eventProcessor, times(2)).processEvent(any());
    verify(eventProcessor, times(2)).executeSideEffects();
    verify(eventProcessor, times(2)).writeEvent(any());
    verify(eventProcessor, times(2)).updateState();
  }

  @Test
  public void shouldReprocessRecursively() {
    // given
    final int numberOfRecords = 250;

    for (int i = 0; i < numberOfRecords; i++) {
      while (writer.tryWrite(EVENT) == -1) {}
    }

    // indicating stream processor reached recordPosition1
    final long recordPosition1 = writeEvent();
    final long recordPosition2 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(recordPosition1));

    logStreamRule.getLogStream().setCommitPosition(recordPosition2);

    final AtomicInteger stackDepthAtRecord1 = new AtomicInteger();
    final AtomicInteger processedRecords = new AtomicInteger(0);

    final FunctionProcessor processor =
        new FunctionProcessor(
            e -> {
              processedRecords.incrementAndGet();
              if (e.getPosition() == recordPosition1) {
                // This does not reliably work for greater stack sizes. Javadoc also says
                // the result is JVM-dependent and can be anything.
                // We assume that with the rather low numberOfRecords used, we do not hit an
                // exceptional case.
                final int stackDepth = Thread.currentThread().getStackTrace().length;
                stackDepthAtRecord1.set(stackDepth);
              }
            });

    // when
    openStreamProcessorController(processor);

    // then
    waitUntil(() -> processedRecords.get() == numberOfRecords + 2);
    assertThat(stackDepthAtRecord1.get())
        .isLessThan(numberOfRecords); // ie not linear in number of records
  }

  @Test
  public void shouldNotResumeProcessingDuringReprocessing() throws Exception {
    // given
    final long recordPosition1 = writeEvent();
    writeEvent();
    writeEvent();

    final long recordPosition3 = writeEvent();
    // indicating stream processor reached recordPosition1
    final long recordPosition4 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(recordPosition3));

    final CyclicBarrier barrier = new CyclicBarrier(2);

    final ResumableProcessor processor =
        new ResumableProcessor(
            e -> {
              if (e.getPosition() == recordPosition1) {
                try {
                  barrier.await();
                } catch (InterruptedException | BrokenBarrierException ex) {
                  throw new RuntimeException(ex);
                }
              }
            });
    openStreamProcessorController(processor);

    // when
    waitUntil(() -> barrier.getNumberWaiting() == 1);
    processor.triggerResume();
    barrier.await();

    // then
    waitUntil(() -> processor.processedRecords.contains(recordPosition4));
    assertThat(processor.processedRecords).containsExactly(recordPosition4);
  }

  public class ResumableProcessor extends FunctionProcessor {
    private StreamProcessorContext context;
    private LoggedEvent currentEvent;
    private List<Long> processedRecords = new CopyOnWriteArrayList(); // i.e. not reprocessed

    ResumableProcessor(Consumer<LoggedEvent> function) {
      super(function);
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event) {
      currentEvent = event;
      return super.onEvent(event);
    }

    @Override
    public boolean executeSideEffects() {
      processedRecords.add(currentEvent.getPosition());
      return true;
    }

    @Override
    public void onOpen(StreamProcessorContext context) {
      this.context = context;
    }

    public void triggerResume() {
      context.getActorControl().call(() -> context.resumeController());
    }
  }

  private long writeEvent() {
    return writeEventWith(w -> {});
  }

  private long writeEventWith(final Consumer<LogStreamWriter> wr) {
    return writer.writeEvent(
        w -> {
          w.positionAsKey().value(EVENT);
          wr.accept(w);
        },
        true);
  }
}
