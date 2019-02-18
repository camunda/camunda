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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class StreamProcessorReprocessingTest {
  private static final String PROCESSOR_NAME = "test";
  private static final int PROCESSOR_ID = 1;
  private static final int OTHER_PROCESSOR_ID = 2;

  private static final DirectBuffer EVENT = wrapString("FOO");

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final LogStreamRule logStreamRule =
      new LogStreamRule(
          temporaryFolder,
          logStreamBuilder -> {
            final String logDirectory = logStreamBuilder.getLogDirectory();
            final StateStorage stateStorage = new StateStorage(logDirectory);
            stateSnapshotController =
                new StateSnapshotController(
                    ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class), stateStorage);
          });
  private final LogStreamWriterRule writer = new LogStreamWriterRule(logStreamRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolder).around(logStreamRule).around(writer);

  private RecordingStreamProcessor streamProcessor;
  private EventProcessor eventProcessor;
  private EventFilter eventFilter;
  private StateSnapshotController stateSnapshotController;

  @Before
  public void init() {

    eventFilter = mock(EventFilter.class);
    when(eventFilter.applies(any())).thenReturn(true);
  }

  private StreamProcessorService openStreamProcessorController() {
    return openStreamProcessorControllerAsync(() -> {}).join();
  }

  private StreamProcessorService openStreamProcessorController(Runnable runnable) {
    return openStreamProcessorControllerAsync(runnable).join();
  }

  private ActorFuture<StreamProcessorService> openStreamProcessorControllerAsync(
      Runnable runnable) {
    return openStreamProcessorControllerAsync(
        zeebeDb -> {
          createStreamProcessor(zeebeDb);
          runnable.run();
          return streamProcessor;
        });
  }

  private StreamProcessor createStreamProcessor(ZeebeDb zeebeDb) {
    final RecordingStreamProcessor recordingProcessor = RecordingStreamProcessor.createSpy(zeebeDb);
    this.streamProcessor = recordingProcessor;
    eventProcessor = recordingProcessor.getEventProcessorSpy();
    return streamProcessor;
  }

  private StreamProcessorService openStreamProcessorController(
      StreamProcessorFactory streamProcessorFactory) {
    return openStreamProcessorControllerAsync(streamProcessorFactory).join();
  }

  private ActorFuture<StreamProcessorService> openStreamProcessorControllerAsync(
      StreamProcessorFactory streamProcessorFactory) {

    return LogStreams.createStreamProcessor(PROCESSOR_NAME, PROCESSOR_ID)
        .logStream(logStreamRule.getLogStream())
        .actorScheduler(logStreamRule.getActorScheduler())
        .snapshotController(stateSnapshotController)
        .serviceContainer(logStreamRule.getServiceContainer())
        .streamProcessorFactory(streamProcessorFactory)
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

    verify(eventProcessor, times(2)).processEvent();
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
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

    verify(eventProcessor, times(2)).processEvent();
    verify(eventProcessor, times(2)).executeSideEffects();
    verify(eventProcessor, times(2)).writeEvent(any());
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

    verify(eventProcessor, times(3)).processEvent();
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
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

    verify(eventProcessor, times(3)).processEvent();
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
  }

  @Test
  public void shouldSkipEventIfNoEventProcessorIsProvided() {
    // given [1|S:-] --> [2|S:-] --> [3|S:2]
    writeEvent();
    final long eventPosition2 = writeEvent();
    final long eventPosition3 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition2));

    // when
    openStreamProcessorController(
        () -> doReturn(null).doCallRealMethod().when(streamProcessor).onEvent(any()));

    waitUntil(() -> streamProcessor.getProcessedEventCount() == 2);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition2, eventPosition3);

    verify(eventProcessor, times(2)).processEvent();
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
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

    verify(eventProcessor, times(2)).processEvent();
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
  }

  @Test
  public void shouldSkipEventOnEventError() {
    // given [1|S:-] --> [2|S:1]
    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();
    final long eventPosition3 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition2));

    final ActorFuture<StreamProcessorService> future =
        openStreamProcessorControllerAsync(
            () -> {
              doThrow(new RuntimeException("expected")).when(streamProcessor).onEvent(any());
            });

    // when
    waitUntil(() -> future.isDone());

    // then
    verify(streamProcessor, times(1)).onRecovered();
  }

  @Test
  public void shouldSkipEventOnReprocessingError() {
    // given [1|S:-] --> [2|S:1]
    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();
    final long eventPosition3 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition2));

    final ActorFuture<StreamProcessorService> future =
        openStreamProcessorControllerAsync(
            () -> {
              doThrow(new RuntimeException("expected")).when(eventProcessor).processEvent();
            });

    // when
    waitUntil(() -> future.isDone());

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1, eventPosition2, eventPosition3);

    verify(streamProcessor, times(1)).onRecovered();
    verify(eventProcessor, times(3)).processEvent();
  }

  @Test
  public void shouldNotReprocessEventsIfReadOnly() {
    final StreamProcessorBuilder builder =
        LogStreams.createStreamProcessor("read-only", PROCESSOR_ID)
            .logStream(logStreamRule.getLogStream())
            .snapshotController(stateSnapshotController)
            .streamProcessorFactory(this::createStreamProcessor)
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

    verify(eventProcessor, times(2)).processEvent();
    verify(eventProcessor, times(2)).executeSideEffects();
    verify(eventProcessor, times(2)).writeEvent(any());
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
    openStreamProcessorController(zeebeDb -> processor);

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
    openStreamProcessorController(zeebeDb -> processor);

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
    private final List<Long> processedRecords = new CopyOnWriteArrayList(); // i.e. not reprocessed

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

  private long writeEventWith(final Consumer<LogStreamRecordWriter> wr) {
    return writer.writeEvent(
        w -> {
          w.positionAsKey().value(EVENT);
          wr.accept(w);
        },
        true);
  }
}
