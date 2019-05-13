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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
import io.zeebe.engine.util.LogStreamRule;
import io.zeebe.engine.util.LogStreamWriterRule;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private DbContext dbContext;
  private final LogStreamRule logStreamRule =
      new LogStreamRule(
          temporaryFolder,
          logStreamBuilder -> {
            final String logDirectory = logStreamBuilder.getLogDirectory();
            final StateStorage stateStorage = new StateStorage(logDirectory);
            stateSnapshotController =
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
                    stateStorage);
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

  private void openStreamProcessorController() {
    openStreamProcessorControllerAsync(() -> {}).join();
  }

  private void openStreamProcessorController(Runnable runnable) {
    openStreamProcessorControllerAsync(runnable).join();
  }

  private ActorFuture<Void> openStreamProcessorControllerAsync(Runnable runnable) {
    final ActorFuture<Void> openedFuture = new CompletableActorFuture<>();
    openStreamProcessorControllerAsync(
        (actor, zeebeDb, dbContext) -> {
          createStreamProcessor(zeebeDb, openedFuture);
          runnable.run();
          return streamProcessor;
        });
    return openedFuture;
  }

  private StreamProcessor createStreamProcessor(ZeebeDb zeebeDb, ActorFuture<Void> openFuture) {
    final RecordingStreamProcessor recordingProcessor =
        RecordingStreamProcessor.createSpy(zeebeDb, openFuture);
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

    return StreamProcessors.createStreamProcessor(PROCESSOR_NAME, PROCESSOR_ID)
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
  public void shouldRetryProcessingOnReprocessingError() {
    // given
    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();
    final long eventPosition3 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition2));

    // when
    openStreamProcessorController(
        () -> {
          final EventProcessor eventProcessorSpy = streamProcessor.getEventProcessorSpy();
          final AtomicLong count = new AtomicLong(0);
          doAnswer(
                  (invocationOnMock) -> {
                    if (count.getAndIncrement() == 0) {
                      throw new RuntimeException("expected");
                    } else {
                      return invocationOnMock.callRealMethod();
                    }
                  })
              .when(eventProcessorSpy)
              .processEvent();
        });
    waitUntilProcessedAndFailedCountReached(3, 0);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1, eventPosition2, eventPosition3);

    verify(eventProcessor, times(4)).processEvent();
    verify(eventProcessor, times(0)).onError(any());
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
  }

  @Test
  public void shouldCallOnErrorForFailedEvent() {
    // given
    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();
    final long eventPosition3 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition2));

    // when
    openStreamProcessorController(
        () -> {
          streamProcessor.setFailedEventPosition(eventPosition2);
        });
    waitUntilProcessedAndFailedCountReached(2, 1);

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsExactly(eventPosition1, eventPosition2, eventPosition3);

    verify(eventProcessor, times(2)).processEvent();
    verify(eventProcessor, times(1)).onError(any());
    verify(eventProcessor, times(1)).executeSideEffects();
    verify(eventProcessor, times(1)).writeEvent(any());
  }

  @Test
  public void shouldRetryReprocessingOnException() throws Exception {
    // given
    final CountDownLatch latch = new CountDownLatch(2);

    final long eventPosition1 = writeEvent();
    final long eventPosition2 =
        writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition1));

    // when
    openStreamProcessorController(
        () -> {
          doThrow(new RuntimeException("expected"))
              .doAnswer(
                  (invocationOnMock -> {
                    latch.countDown();
                    return invocationOnMock.callRealMethod();
                  }))
              .when(eventProcessor)
              .processEvent();
        });
    latch.await();

    // then
    assertThat(streamProcessor.getEvents())
        .extracting(LoggedEvent::getPosition)
        .containsOnly(eventPosition1, eventPosition2);

    verify(streamProcessor, timeout(500).times(2)).onEvent(any());
    verify(dbContext, timeout(500).atLeast(3)).getCurrentTransaction();
    verify(eventProcessor, timeout(500).times(3)).processEvent();
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
    writeEventWith(w -> w.producerId(PROCESSOR_ID).sourceRecordPosition(eventPosition1));

    final ActorFuture<Void> future =
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
    openStreamProcessorController((actor, zeebeDb, dbContext) -> processor);

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
    openStreamProcessorController((actor, zeebeDb, dbContext) -> processor);

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
          w.key(-1).value(EVENT);
          wr.accept(w);
        });
  }

  private void waitUntilProcessedAndFailedCountReached(int processCount, int failedCount) {
    waitUntil(
        () ->
            streamProcessor.getProcessedEventCount() == processCount
                && streamProcessor.getProcessingFailedCount() == failedCount);
  }
}
