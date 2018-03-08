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
package io.zeebe.logstreams.integration;

import static io.zeebe.logstreams.integration.util.LogIntegrationTestUtil.*;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.LongFunction;
import java.util.function.Predicate;

import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.integration.util.Counter;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.processor.*;
import io.zeebe.logstreams.snapshot.SerializableWrapper;
import io.zeebe.logstreams.spi.*;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.sched.*;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class StreamProcessorIntegrationTest
{
    private static final int MSG_SIZE = 911;
    private static final int WORK_COUNT = 50_000;

    private static final int STREAM_PROCESSOR_ID = 1;

    private static final Duration NO_SNAPSHOT = Duration.ofMinutes(10);
    private static final Duration SNAPSHOT_PERIOD = Duration.ofMillis(250);

    public TemporaryFolder tempFolder = new TemporaryFolder();

    public AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

    public ActorSchedulerRule actorScheduler = new ActorSchedulerRule();

    @Rule
    public RuleChain chain = RuleChain.outerRule(tempFolder)
                                      .around(actorScheduler)
                                      .around(autoCloseableRule);


    private LogStream logStream;

    private ControllableSnapshotStorage snapshotStorage;
    private SerializableWrapper<Counter> resourceCounter;
    private StreamProcessorController streamProcessorController;

    private String logPath;

    protected List<StreamProcessorController> controllers;

    @Before
    public void setup()
    {
        resourceCounter = new SerializableWrapper<>(new Counter());

        logPath = tempFolder.getRoot().getAbsolutePath();

        snapshotStorage = new ControllableSnapshotStorage(LogStreams.createFsSnapshotStore(logPath).build());

        logStream = LogStreams.createFsLogStream(wrapString("source"), 0)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 16)
                .actorScheduler(actorScheduler.get())
                .build();

        logStream.open();
        logStream.openLogStreamController().join();
        autoCloseableRule.manage(logStream);

        controllers = new ArrayList<>();
    }

    private void scheduleCommitPositionUpdated(LogStream logStream)
    {
        // update commit position when new event is appended
        // this triggers the stream processor controller to process available events
        actorScheduler.get().submitActor(new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                final ActorCondition condition = actor.onCondition("on-append", () ->
                {
                    logStream.setCommitPosition(Long.MAX_VALUE);
                });
                logStream.registerOnAppendCondition(condition);
            }
        });
    }

    @After
    public void destroy() throws Exception
    {
        resourceCounter.reset();
        for (StreamProcessorController controller : controllers)
        {
            controller.closeAsync().get();
        }
        if (streamProcessorController != null)
        {
            streamProcessorController.closeAsync().get();
        }
    }

    protected void manage(StreamProcessorController controller)
    {
        this.controllers.add(controller);
    }

    @Test
    public void shouldProcessAndWriteEventsToStream() throws InterruptedException, ExecutionException
    {
        // given
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("increment-processor", STREAM_PROCESSOR_ID, new SimpleProducerProcessor())
            .logStream(logStream)
            .actorScheduler(actorScheduler.get())
            .snapshotPeriod(NO_SNAPSHOT)
            .snapshotStorage(snapshotStorage)
            .build();

        scheduleCommitPositionUpdated(logStream);

        streamProcessorController.openAsync().get();

        // when
        writeLogEvents(logStream, 1, MSG_SIZE, 0);

        // then
        waitUntilWrittenKey(logStream, WORK_COUNT);
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldCreateSnapshotOnProcessingAndWritingEventsToStream() throws InterruptedException, ExecutionException
    {
        // given
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("increment-processor", STREAM_PROCESSOR_ID, new SimpleProducerProcessor())
            .logStream(logStream)
            .actorScheduler(actorScheduler.get())
            .snapshotPeriod(SNAPSHOT_PERIOD)
            .snapshotStorage(snapshotStorage)
            .build();

        scheduleCommitPositionUpdated(logStream);

        streamProcessorController.openAsync().get();

        // when
        writeLogEvents(logStream, 1, MSG_SIZE, 0);
        waitUntilWrittenKey(logStream, WORK_COUNT);

        // then
        assertThat(snapshotStorage.isSnapshotCreated()).isTrue();
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldReprocessEvents() throws InterruptedException, ExecutionException
    {
        // given
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("increment-processor", STREAM_PROCESSOR_ID, new SimpleProducerProcessor())
            .logStream(logStream)
            .actorScheduler(actorScheduler.get())
            .snapshotPeriod(SNAPSHOT_PERIOD)
            .snapshotStorage(snapshotStorage)
            .build();

        scheduleCommitPositionUpdated(logStream);

        streamProcessorController.openAsync().get();

        writeLogEvents(logStream, 1, MSG_SIZE, 0);
        waitUntilWrittenKey(logStream, WORK_COUNT);

        // when
        snapshotStorage.readOnly();
        streamProcessorController.closeAsync().get();
        resourceCounter.getObject().reset();
        streamProcessorController.openAsync().get();

        // then
        // state is recovered
        assertThat(resourceCounter.getObject().getCount()).isGreaterThan(WORK_COUNT / 2);
        waitUntilCounterReached(WORK_COUNT);
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldReprocessEventsAndProcessNewEvents() throws InterruptedException, ExecutionException
    {
        // given
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("increment-processor", STREAM_PROCESSOR_ID, new SimpleProducerProcessor())
            .logStream(logStream)
            .actorScheduler(actorScheduler.get())
            .snapshotPeriod(SNAPSHOT_PERIOD)
            .snapshotStorage(snapshotStorage)
            .build();

        scheduleCommitPositionUpdated(logStream);

        streamProcessorController.openAsync().get();

        writeLogEvents(logStream, 1, MSG_SIZE, 0);
        waitUntilWrittenKey(logStream, WORK_COUNT);

        // when
        snapshotStorage.readOnly();
        streamProcessorController.closeAsync().get();
        resourceCounter.getObject().reset();
        streamProcessorController.openAsync().get();

        // then
        waitUntilCounterReached(WORK_COUNT);

        // when
        writeLogEvents(logStream, 1, MSG_SIZE, WORK_COUNT);
        waitUntilWrittenKey(logStream, WORK_COUNT + 1);

        // then
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT + 1);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldRecoverWithoutSnapshot() throws Exception
    {
        // given
        streamProcessorController = LogStreams
            .createStreamProcessor("increment-processor", STREAM_PROCESSOR_ID, new SimpleProducerProcessor())
            .logStream(logStream)
            .actorScheduler(actorScheduler.get())
            .snapshotPeriod(NO_SNAPSHOT)
            .snapshotStorage(snapshotStorage)
            .build();

        scheduleCommitPositionUpdated(logStream);

        streamProcessorController.openAsync().get();

        writeLogEvents(logStream, 1, MSG_SIZE, 0);
        waitUntilWrittenKey(logStream, WORK_COUNT);
        snapshotStorage.readOnly();
        streamProcessorController.closeAsync().get();

        // when
        resourceCounter.getObject().reset();
        streamProcessorController.openAsync().get();
        writeLogEvents(logStream, 1, MSG_SIZE, WORK_COUNT);
        waitUntilWrittenKey(logStream, WORK_COUNT + 1);
        waitUntilCounterReached(WORK_COUNT + 1);

        // then
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT + 1);
    }

    @Test
    public void shouldProcessWithTwoProcessors() throws Exception
    {
        // given
        final SerializableWrapper<Counter> resourceCounter2 = new SerializableWrapper<>(new Counter());

        final StreamProcessorController streamProcessorController1 = LogStreams
            .createStreamProcessor("processor-1", 1,
                new ProducerProcessor(resourceCounter, aLong -> aLong < 2 * WORK_COUNT && (aLong % 2) == 0, value -> value + 1))
            .logStream(logStream)
            .actorScheduler(actorScheduler.get())
            .snapshotPeriod(NO_SNAPSHOT)
            .snapshotStorage(snapshotStorage)
            .build();

        final StreamProcessorController streamProcessorController2 = LogStreams
            .createStreamProcessor("processor-2", 2,
                new ProducerProcessor(resourceCounter2, aLong -> aLong < 2 * WORK_COUNT && (aLong % 2) != 0, value -> value + 1))
            .logStream(logStream)
            .actorScheduler(actorScheduler.get())
            .snapshotPeriod(NO_SNAPSHOT)
            .snapshotStorage(snapshotStorage)
            .build();

        scheduleCommitPositionUpdated(logStream);

        // when
        FutureUtil.join(streamProcessorController1.openAsync());
        FutureUtil.join(streamProcessorController2.openAsync());

        writeLogEvents(logStream, 1, MSG_SIZE, 0);
        waitUntilWrittenEvents(logStream, 2 * WORK_COUNT);
        waitUntil(() -> resourceCounter.getObject().getCount() == 2 * WORK_COUNT);
        waitUntil(() -> resourceCounter2.getObject().getCount() == 2 * WORK_COUNT);

        // then
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(2 * WORK_COUNT);
        assertThat(resourceCounter2.getObject().getCount()).isEqualTo(2 * WORK_COUNT);

        FutureUtil.join(streamProcessorController1.closeAsync());
        FutureUtil.join(streamProcessorController2.closeAsync());
    }

    @Test
    public void shouldReprocessWithTwoProcessors() throws Exception
    {
        // given
        final SerializableWrapper<Counter> resourceCounter2 = new SerializableWrapper<>(new Counter());

        final StreamProcessorController streamProcessorController1 = LogStreams
            .createStreamProcessor("processor-1", 1,
                new ProducerProcessor(resourceCounter, aLong -> aLong < 2 * WORK_COUNT && (aLong % 2) == 0, value -> value + 1))
            .logStream(logStream)
            .actorScheduler(actorScheduler.get())
            .snapshotPeriod(SNAPSHOT_PERIOD)
            .snapshotStorage(snapshotStorage)
            .build();

        final StreamProcessorController streamProcessorController2 = LogStreams
            .createStreamProcessor("processor-2", 2,
                new ProducerProcessor(resourceCounter2, aLong -> aLong < 2 * WORK_COUNT && (aLong % 2) != 0, value -> value + 1))
            .logStream(logStream)
            .actorScheduler(actorScheduler.get())
            .snapshotPeriod(SNAPSHOT_PERIOD)
            .snapshotStorage(snapshotStorage)
            .build();

        scheduleCommitPositionUpdated(logStream);

        FutureUtil.join(streamProcessorController1.openAsync());
        FutureUtil.join(streamProcessorController2.openAsync());

        writeLogEvents(logStream, 1, MSG_SIZE, 0);
        waitUntilWrittenEvents(logStream, WORK_COUNT * 2);
        writeLogEvents(logStream, 1, MSG_SIZE, 2 * WORK_COUNT);
        waitUntilWrittenEvents(logStream, 2 * WORK_COUNT + 1);

        snapshotStorage.readOnly();
        FutureUtil.join(streamProcessorController1.closeAsync());
        FutureUtil.join(streamProcessorController2.closeAsync());

        // when
        resourceCounter.getObject().reset();
        resourceCounter2.getObject().reset();

        FutureUtil.join(streamProcessorController1.openAsync());
        FutureUtil.join(streamProcessorController2.openAsync());

        // then
        assertThat(resourceCounter.getObject().getCount()).isGreaterThan(WORK_COUNT / 2);
        assertThat(resourceCounter2.getObject().getCount()).isGreaterThan(WORK_COUNT);

        // when
        writeLogEvents(logStream, 1, MSG_SIZE, 2 * WORK_COUNT + 1);
        waitUntilWrittenEvents(logStream, 2 * WORK_COUNT + 2);

        while (resourceCounter.getObject().getCount() < 2 * WORK_COUNT + 2
               || resourceCounter2.getObject().getCount() < 2 * WORK_COUNT + 2)
        {
            Thread.sleep(20);
            // wait until last event is re-processed again
        }

        // then
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(2 * WORK_COUNT + 2);
        assertThat(resourceCounter2.getObject().getCount()).isEqualTo(2 * WORK_COUNT + 2);

        FutureUtil.join(streamProcessorController1.closeAsync());
        FutureUtil.join(streamProcessorController2.closeAsync());
    }

    @Test
    public void shouldProcessWithEventBatches() throws Exception
    {
        // given
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-event-batch-processor", STREAM_PROCESSOR_ID, new EventBatchStreamProcessor(resourceCounter))
            .logStream(logStream)
            .actorScheduler(actorScheduler.get())
            .snapshotPeriod(NO_SNAPSHOT)
            .snapshotStorage(snapshotStorage)
            .build();

        scheduleCommitPositionUpdated(logStream);

        streamProcessorController.openAsync().get();

        // when
        writeLogEvents(logStream, 1, MSG_SIZE, 1);
        waitUntilWrittenKey(logStream, WORK_COUNT);

        // then
        assertThat(resourceCounter.getObject().getCount()).isGreaterThan(WORK_COUNT / 2).isLessThan(WORK_COUNT);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldRecoverWithEventBatches() throws Exception
    {
        // given
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-event-batch-processor", STREAM_PROCESSOR_ID, new EventBatchStreamProcessor(resourceCounter))
            .logStream(logStream)
            .actorScheduler(actorScheduler.get())
            .snapshotPeriod(NO_SNAPSHOT)
            .snapshotStorage(snapshotStorage)
            .build();

        scheduleCommitPositionUpdated(logStream);

        streamProcessorController.openAsync().get();

        writeLogEvents(logStream, 1, MSG_SIZE, 1);
        waitUntilWrittenKey(logStream, WORK_COUNT);
        snapshotStorage.readOnly();
        streamProcessorController.closeAsync().get();

        // when
        resourceCounter.getObject().reset();
        streamProcessorController.openAsync().get();
        writeLogEvents(logStream, 1, MSG_SIZE, WORK_COUNT);
        waitUntilWrittenKey(logStream, WORK_COUNT + 1);
        waitUntilCounterReached(WORK_COUNT + 1);

        // then
        assertThat(resourceCounter.getObject().getCount()).isGreaterThan(WORK_COUNT + 1);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldRecoverWithIntermediateIndexingState() throws InterruptedException, ExecutionException
    {
        // given
        final ControllableStreamProcessor streamProcessor = new ControllableStreamProcessor();
        streamProcessor.blockOnKey(2);

        final StreamProcessorController streamProcessorController = LogStreams
                .createStreamProcessor("test", STREAM_PROCESSOR_ID, streamProcessor)
                .logStream(logStream)
                .actorScheduler(actorScheduler.get())
                .snapshotPeriod(SNAPSHOT_PERIOD)
                .snapshotStorage(snapshotStorage)
                .build();
        manage(streamProcessorController);

        final LogStreamReader logReader = newLogReader(logStream);

        scheduleCommitPositionUpdated(logStream);

        streamProcessorController.openAsync().get();

        writeLogEvents(logStream, 2, MSG_SIZE, 1);
        logReader.seekToFirstEvent();
        waitUntil(() -> logReader.hasNext());
        final long firstEventPosition = logReader.next().getPosition();
        waitUntil(() -> logReader.hasNext());
        final long secondEventPosition = logReader.next().getPosition();

        waitUntil(() -> streamProcessor.successfullyHandledEvents.contains(firstEventPosition));

        // when
        streamProcessorController.closeAsync().get();
        streamProcessor.successfullyHandledEvents.clear();

        final long newEventKey = Integer.MAX_VALUE;
        streamProcessor.blockOnKey(-1);

        final LogStreamWriter writer = new LogStreamWriterImpl(logStream);

        // write one more event to verify that the processor resume on snapshot position
        final long newEventPosition = TestUtil.doRepeatedly(() ->
        {
            return writer
                    .key(newEventKey)
                    .value(new UnsafeBuffer(new byte[4]))
                    .tryWrite();
        })
                .until(p -> p >= 0);

        // then
        streamProcessorController.openAsync().get();

        waitUntil(() -> streamProcessor.successfullyHandledEvents.contains(newEventPosition));

        assertThat(streamProcessor.successfullyHandledEvents).contains(secondEventPosition);
    }


    private LogStreamReader newLogReader(LogStream stream)
    {
        final BufferedLogStreamReader reader = new BufferedLogStreamReader(stream, true);
        autoCloseableRule.manage(reader);
        return reader;
    }

    @Test
    public void shouldUseDisabledWriterForReadOnlyProcessor() throws InterruptedException, ExecutionException
    {
        // given
        final ErrorRecodingStreamProcessor streamProcessor = new ErrorRecodingStreamProcessor();

        final StreamProcessorController streamProcessorController = LogStreams
                .createStreamProcessor("copy-processor", STREAM_PROCESSOR_ID, streamProcessor)
                .readOnly(true)
                .logStream(logStream)
                .actorScheduler(actorScheduler.get())
                .snapshotPeriod(NO_SNAPSHOT)
                .snapshotStorage(snapshotStorage)
                .build();

        scheduleCommitPositionUpdated(logStream);

        streamProcessorController.openAsync().get();

        writeLogEvents(logStream, WORK_COUNT, MSG_SIZE, 0);

        // when
        TestUtil.waitUntil(() -> streamProcessorController.isFailed());

        // then
        final Exception exception = streamProcessor.getRecordedException();
        assertThat(exception).isNotNull();
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception).hasMessageContaining("Cannot write event; Writing is disabled");

        streamProcessorController.closeAsync().get();
    }


    @Ignore
    @Test
    public void shouldReadCommittedEntries() throws InterruptedException, ExecutionException
    {
        // given
        streamProcessorController = LogStreams
                .createStreamProcessor("processor", STREAM_PROCESSOR_ID, new SimpleProducerProcessor())
                .logStream(logStream)
                .actorScheduler(actorScheduler.get())
                .snapshotPeriod(NO_SNAPSHOT)
                .snapshotStorage(snapshotStorage)
                .build();
        logStream.setCommitPosition(-1);
        streamProcessorController.openAsync().get();
        final long[] positions = writeLogEventsAndReturnPosition(logStream, 1, MSG_SIZE, 0);

        // when
        final BufferedLogStreamReader sourceReader = new BufferedLogStreamReader(logStream, true);
        autoCloseableRule.manage(sourceReader);

        // then
        waitUntil(() -> logStream.getCurrentAppenderPosition() > positions[0]);
        int eventCount = countEvents(sourceReader);
        assertThat(eventCount).isEqualTo(1);

        // when
        logStream.setCommitPosition(positions[0]);
        waitUntilCounterReached(1);

        // then controller can read event and create new one
        eventCount = countEvents(sourceReader);
        assertThat(eventCount).isEqualTo(2);

        // when
        logStream.setCommitPosition(Long.MAX_VALUE);

        // then
        waitUntilCounterReached(WORK_COUNT);
    }

    private class EventBatchStreamProcessor implements StreamProcessor
    {
        private final SerializableWrapper<Counter> resourceCounter;

        private LogStreamBatchWriter batchWriter = new LogStreamBatchWriterImpl(logStream);

        EventBatchStreamProcessor(SerializableWrapper<Counter> resourceCounter)
        {
            this.resourceCounter = resourceCounter;
        }

        @Override
        public EventProcessor onEvent(LoggedEvent event)
        {
            return new EventProcessor()
            {
                @Override
                public void processEvent()
                {
                    final Counter counter = resourceCounter.getObject();
                    counter.increment();
                }

                @Override
                public long writeEvent(LogStreamWriter writer)
                {
                    batchWriter.reset();

                    return batchWriter
                        .producerId(STREAM_PROCESSOR_ID)
                        .sourceEvent(logStream.getPartitionId(), event.getPosition())
                        .event()
                            .key(event.getKey() * 2)
                            .value(wrapString("event-1"))
                            .done()
                        .event()
                            .key(event.getKey() * 2 + 1)
                            .value(wrapString("event-2"))
                            .done()
                         .tryWrite();
                }

            };
        }

        @Override
        public SnapshotSupport getStateResource()
        {
            return resourceCounter;
        }
    }

    protected class ErrorRecodingStreamProcessor implements StreamProcessor
    {

        protected Exception recordedException;

        public Exception getRecordedException()
        {
            return recordedException;
        }

        @Override
        public SnapshotSupport getStateResource()
        {
            return new SerializableWrapper<>(new Integer(0));
        }

        @Override
        public EventProcessor onEvent(LoggedEvent event)
        {
            return new EventProcessor()
            {

                @Override
                public void processEvent()
                {
                }

                @Override
                public long writeEvent(LogStreamWriter writer)
                {
                    if (recordedException != null)
                    {
                        throw new RuntimeException("Can at most record one exception");
                    }
                    else
                    {
                        try
                        {
                            return writer
                                .key(event.getKey())
                                .value(event.getValueBuffer(), event.getValueOffset(), event.getValueLength())
                                .tryWrite();
                        }
                        catch (Exception e)
                        {
                            recordedException = e;
                            throw e;
                        }
                    }
                }
            };
        }
    }

    protected class ControllableStreamProcessor implements StreamProcessor, EventProcessor
    {

        protected long eventKeyToBlock = -1;
        protected CopyOnWriteArrayList<Long> successfullyHandledEvents = new CopyOnWriteArrayList<>();
        protected final SerializableWrapper<CopyOnWriteArrayList<Long>> stateResource = new SerializableWrapper<>(successfullyHandledEvents);


        protected boolean blockOnCurrentEvent = false;
        protected LoggedEvent currentEvent;

        public void blockOnKey(long key)
        {
            this.eventKeyToBlock = key;
        }

        @Override
        public void onOpen(StreamProcessorContext context)
        {
            this.successfullyHandledEvents = stateResource.getObject();
        }

        @Override
        public SnapshotSupport getStateResource()
        {
            return stateResource;
        }

        @Override
        public EventProcessor onEvent(LoggedEvent event)
        {
            currentEvent = event;
            return this;
        }

        @Override
        public void processEvent()
        {
            blockOnCurrentEvent = eventKeyToBlock == currentEvent.getKey();
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            if (blockOnCurrentEvent)
            {
                return -1;
            }
            else
            {
                return writer
                    .key(currentEvent.getKey())
                    .value(currentEvent.getValueBuffer(), currentEvent.getValueOffset(), currentEvent.getValueLength())
                    .tryWrite();
            }
        }

        @Override
        public void updateState()
        {
            successfullyHandledEvents.add(currentEvent.getPosition());
        }

    }

    protected class ControllableSnapshotStorage implements SnapshotStorage
    {
        private final SnapshotStorage snapshotStorage;
        private boolean readOnly = false;
        private boolean snapshotCreated = false;

        public ControllableSnapshotStorage(SnapshotStorage snapshotStorage)
        {
            this.snapshotStorage = snapshotStorage;
        }

        public void readOnly()
        {
            readOnly = true;
        }

        @Override
        public ReadableSnapshot getLastSnapshot(String name) throws Exception
        {
            return snapshotStorage.getLastSnapshot(name);
        }

        @Override
        public SnapshotWriter createSnapshot(String name, long logPosition) throws Exception
        {
            snapshotCreated = true;
            if (readOnly)
            {
                return Mockito.mock(SnapshotWriter.class);
            }
            else
            {
                return snapshotStorage.createSnapshot(name, logPosition);
            }
        }

        @Override
        public boolean purgeSnapshot(String name)
        {
            if (readOnly)
            {
                return true;
            }
            else
            {
                return snapshotStorage.purgeSnapshot(name);
            }
        }

        public boolean isSnapshotCreated()
        {
            return snapshotCreated;
        }
    }

    private class SimpleProducerProcessor implements StreamProcessor
    {

        @Override
        public EventProcessor onEvent(LoggedEvent event)
        {
            return new EventProcessor()
            {
                @Override
                public void processEvent()
                {
                    final Counter c = resourceCounter.getObject();
                    c.increment();
                }

                @Override
                public long writeEvent(LogStreamWriter writer)
                {
                    final long nextKey = event.getKey() + 1;
                    if (nextKey < WORK_COUNT)
                    {
                        return writer.key(nextKey)
                            .value(event.getValueBuffer(), event.getValueOffset(), event.getValueLength())
                            .tryWrite();
                    }
                    else
                    {
                        return 0;
                    }
                }
            };
        }

        @Override
        public SnapshotSupport getStateResource()
        {
            return resourceCounter;
        }
    };

    private class ProducerProcessor implements StreamProcessor
    {
        private final Predicate<Long> shouldWrite;
        private final LongFunction<Long> newKeyProducer;
        private SerializableWrapper<Counter> counter;

        ProducerProcessor(SerializableWrapper<Counter> counter, Predicate<Long> shouldWrite, LongFunction<Long> newKeyProducer)
        {
            this.counter = counter;
            this.shouldWrite = shouldWrite;
            this.newKeyProducer = newKeyProducer;
        }

        @Override
        public EventProcessor onEvent(LoggedEvent event)
        {
            return new EventProcessor()
            {
                @Override
                public void processEvent()
                {
                    final Counter c = counter.getObject();
                    c.increment();
                }

                @Override
                public long writeEvent(LogStreamWriter writer)
                {
                    final long nextKey = newKeyProducer.apply(event.getKey());
                    if (shouldWrite.test(nextKey))
                    {
                        return writer.key(nextKey)
                            .value(event.getValueBuffer(), event.getValueOffset(), event.getValueLength())
                            .tryWrite();
                    }
                    else
                    {
                        return 0;
                    }
                }
            };
        }

        @Override
        public SnapshotSupport getStateResource()
        {
            return counter;
        }
    };

    public void waitUntilCounterReached(int workCount) throws InterruptedException
    {
        while (resourceCounter.getObject().getCount() < workCount)
        {
            Thread.sleep(100);
            // wait until last event is re-processed again
        }
    }

    private int countEvents(BufferedLogStreamReader sourceReader)
    {
        sourceReader.seekToFirstEvent();
        int eventCount = 0;
        long lastPos = -1;
        long lastKey = -1;
        while (sourceReader.hasNext())
        {
            final LoggedEvent next = sourceReader.next();
            assertThat(next.getPosition()).isGreaterThan(lastPos);
            lastPos = next.getPosition();
            assertThat(next.getKey()).isGreaterThan(lastKey);
            lastKey = next.getKey();
            eventCount++;
        }
        return eventCount;
    }
}
