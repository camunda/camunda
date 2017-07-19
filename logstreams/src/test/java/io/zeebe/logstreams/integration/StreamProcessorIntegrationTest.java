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
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileNotFoundException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.LogStreamController;
import io.zeebe.logstreams.integration.util.*;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.processor.*;
import io.zeebe.logstreams.snapshot.SerializableWrapper;
import io.zeebe.logstreams.spi.*;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class StreamProcessorIntegrationTest
{
    private static final int MSG_SIZE = 911;
    private static final int WORK_COUNT = 50_000;

    private static final int STREAM_PROCESSOR_ID = 1;
    private static final SnapshotPolicy NO_SNAPSHOT_POLICY = pos -> false;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

    private ActorScheduler actorScheduler;

    private LogStream sourceLogStream;
    private LogStream targetLogStream;

    private ControllableSnapshotStorage snapshotStorage;
    private SerializableWrapper<Counter> resourceCounter;

    private String logPath;

    @Before
    public void setup()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");

        resourceCounter = new SerializableWrapper<>(new Counter());

        logPath = tempFolder.getRoot().getAbsolutePath();

        snapshotStorage = new ControllableSnapshotStorage(LogStreams.createFsSnapshotStore(logPath).build());

        sourceLogStream = LogStreams.createFsLogStream(wrapString("source"), 0)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 16)
                .actorScheduler(actorScheduler)
                .build();

        targetLogStream = LogStreams.createFsLogStream(wrapString("target"), 1)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 16)
                .actorScheduler(actorScheduler)
                .build();

        sourceLogStream.open();
        targetLogStream.open();
    }

    @After
    public void destroy() throws Exception
    {
        sourceLogStream.close();
        targetLogStream.close();

        actorScheduler.close();
    }

    @Test
    public void shouldCopyEventsToTargetStream() throws InterruptedException, ExecutionException
    {
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", STREAM_PROCESSOR_ID, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .actorScheduler(actorScheduler)
            .snapshotPolicy(NO_SNAPSHOT_POLICY)
            .snapshotStorage(snapshotStorage)
            .build();

        sourceLogStream.setCommitPosition(Long.MAX_VALUE);
        targetLogStream.setCommitPosition(Long.MAX_VALUE);

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT, MSG_SIZE, 0);

        final LogStreamReader logReader = new BufferedLogStreamReader(targetLogStream, true);
        readLogAndAssertEvents(logReader, WORK_COUNT, MSG_SIZE);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldWriteEventsToSourceStream() throws InterruptedException, ExecutionException
    {
        final StreamProcessor streamProcessor = new StreamProcessor()
        {
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
                        final long nextKey = event.getKey() + 1;
                        if (nextKey < WORK_COUNT)
                        {
                            return writer.key(nextKey).value(event.getValueBuffer(), event.getValueOffset(), event.getValueLength()).tryWrite();
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

        final AtomicBoolean isSnapshotPoint = new AtomicBoolean(false);

        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("increment-processor", STREAM_PROCESSOR_ID, streamProcessor)
            .sourceStream(sourceLogStream)
            .targetStream(sourceLogStream)
            .actorScheduler(actorScheduler)
            .snapshotPolicy(position -> isSnapshotPoint.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

        sourceLogStream.setCommitPosition(Long.MAX_VALUE);

        streamProcessorController.openAsync().get();

        // just write one initial event
        writeLogEvents(sourceLogStream, 1, MSG_SIZE, 0);
        waitUntilWrittenKey(sourceLogStream, WORK_COUNT / 2);

        isSnapshotPoint.set(true);

        waitUntilWrittenKey(sourceLogStream, WORK_COUNT);

        while (resourceCounter.getObject().getCount() < WORK_COUNT)
        {
            // wait until last event is processed
        }
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT);

        snapshotStorage.readOnly();
        streamProcessorController.closeAsync().get();

        // reset the resource manually to ensure that recovery happens
        resourceCounter.getObject().reset();
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(0);

        streamProcessorController.openAsync().get();

        while (resourceCounter.getObject().getCount() < WORK_COUNT)
        {
            // wait until last event is processed again
        }
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldRecoverFromSnapshot() throws Exception
    {
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", STREAM_PROCESSOR_ID, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .actorScheduler(actorScheduler)
            .snapshotPolicy(NO_SNAPSHOT_POLICY)
            .snapshotStorage(snapshotStorage)
            .build();

        sourceLogStream.setCommitPosition(Long.MAX_VALUE);
        targetLogStream.setCommitPosition(Long.MAX_VALUE);

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT, MSG_SIZE, 0);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT);

        streamProcessorController.closeAsync().get();

        // reset the resource manually to ensure that recovery happens
        resourceCounter.getObject().reset();
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(0);

        streamProcessorController.openAsync().get();

        // write one more event to verify that the processor resume on snapshot position
        writeLogEvents(sourceLogStream, 1, MSG_SIZE, WORK_COUNT);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT + 1);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT + 1);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldRecoverFromSnapshotAndReprocessEvents() throws FileNotFoundException, Exception
    {
        final AtomicBoolean isSnapshotPoint = new AtomicBoolean(false);

        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", STREAM_PROCESSOR_ID, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .actorScheduler(actorScheduler)
            .snapshotPolicy(pos -> isSnapshotPoint.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

        sourceLogStream.setCommitPosition(Long.MAX_VALUE);
        targetLogStream.setCommitPosition(Long.MAX_VALUE);

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT / 2, MSG_SIZE, 0);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT / 2);

        isSnapshotPoint.set(true);

        writeLogEvents(sourceLogStream, WORK_COUNT / 2, MSG_SIZE, WORK_COUNT / 2);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT);

        snapshotStorage.readOnly();
        streamProcessorController.closeAsync().get();

        // reset the resource manually to ensure that recovery happens
        resourceCounter.getObject().reset();
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(0);

        streamProcessorController.openAsync().get();

        // write one more event to verify that the processor resume on snapshot position
        writeLogEvents(sourceLogStream, 1, MSG_SIZE, WORK_COUNT);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT + 1);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT + 1);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldRecoverWithoutSnapshot() throws FileNotFoundException, Exception
    {
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", STREAM_PROCESSOR_ID, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .actorScheduler(actorScheduler)
            .snapshotPolicy(NO_SNAPSHOT_POLICY)
            .snapshotStorage(snapshotStorage)
            .build();

        sourceLogStream.setCommitPosition(Long.MAX_VALUE);
        targetLogStream.setCommitPosition(Long.MAX_VALUE);

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT, MSG_SIZE, 0);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT);

        snapshotStorage.readOnly();
        streamProcessorController.closeAsync().get();

        // reset the resource manually to ensure that recovery happens
        resourceCounter.getObject().reset();
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(0);

        streamProcessorController.openAsync().get();

        // write one more event to verify that the processor resume on snapshot position
        writeLogEvents(sourceLogStream, 1, MSG_SIZE, WORK_COUNT);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT + 1);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT + 1);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldRecoverWithTwoProcessors() throws FileNotFoundException, Exception
    {
        final SerializableWrapper<Counter> resourceCounter2 = new SerializableWrapper<>(new Counter());

        final AtomicBoolean isSnapshotPoint1 = new AtomicBoolean(false);
        final AtomicBoolean isSnapshotPoint2 = new AtomicBoolean(false);

        final StreamProcessorController streamProcessorController1 = LogStreams
            .createStreamProcessor("processor-1", 1, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .actorScheduler(actorScheduler)
            .snapshotPolicy(pos -> isSnapshotPoint1.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

        final StreamProcessorController streamProcessorController2 = LogStreams
            .createStreamProcessor("processor-2", 2, new CopyStreamProcessor(resourceCounter2))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .actorScheduler(actorScheduler)
            .snapshotPolicy(pos -> isSnapshotPoint2.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

        sourceLogStream.setCommitPosition(Long.MAX_VALUE);
        targetLogStream.setCommitPosition(Long.MAX_VALUE);

        CompletableFuture.allOf(streamProcessorController1.openAsync(), streamProcessorController2.openAsync()).get();

        writeLogEvents(sourceLogStream, WORK_COUNT / 2, MSG_SIZE, 0);
        waitUntilWrittenEvents(targetLogStream, WORK_COUNT);

        isSnapshotPoint1.set(true);

        writeLogEvents(sourceLogStream, WORK_COUNT / 2, MSG_SIZE, WORK_COUNT / 2);
        waitUntilWrittenEvents(targetLogStream, 2 * WORK_COUNT);

        isSnapshotPoint2.set(true);

        // write one more event to create the snapshot
        writeLogEvents(sourceLogStream, 1, MSG_SIZE, WORK_COUNT);
        waitUntilWrittenEvents(targetLogStream, 2 * WORK_COUNT + 2);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT + 1);
        assertThat(resourceCounter2.getObject().getCount()).isEqualTo(WORK_COUNT + 1);

        snapshotStorage.readOnly();
        CompletableFuture.allOf(streamProcessorController1.closeAsync(), streamProcessorController2.closeAsync()).get();

        // reset the resource manually to ensure that recovery happens
        resourceCounter.getObject().reset();
        resourceCounter2.getObject().reset();
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(0);
        assertThat(resourceCounter2.getObject().getCount()).isEqualTo(0);

        CompletableFuture.allOf(streamProcessorController1.openAsync(), streamProcessorController2.openAsync()).get();

        // write one more event to verify that the processors resume on snapshot position
        writeLogEvents(sourceLogStream, 1, MSG_SIZE, WORK_COUNT + 1);
        waitUntilWrittenEvents(targetLogStream, 2 * WORK_COUNT + 4);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT + 2);
        assertThat(resourceCounter2.getObject().getCount()).isEqualTo(WORK_COUNT + 2);

        CompletableFuture.allOf(streamProcessorController1.closeAsync(), streamProcessorController2.closeAsync()).get();
    }

    @Test
    public void shouldRecoverWithEventBatches() throws FileNotFoundException, Exception
    {
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-event-batch-processor", STREAM_PROCESSOR_ID, new EventBatchStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .actorScheduler(actorScheduler)
            .snapshotPolicy(NO_SNAPSHOT_POLICY)
            .snapshotStorage(snapshotStorage)
            .build();

        sourceLogStream.setCommitPosition(Long.MAX_VALUE);
        targetLogStream.setCommitPosition(Long.MAX_VALUE);

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT, MSG_SIZE, 0);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT);

        snapshotStorage.readOnly();
        streamProcessorController.closeAsync().get();

        // reset the resource manually to ensure that recovery happens
        resourceCounter.getObject().reset();
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(0);

        streamProcessorController.openAsync().get();

        // write one more event to verify that the processor resume on snapshot position
        writeLogEvents(sourceLogStream, 1, MSG_SIZE, WORK_COUNT);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT + 1);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT + 1);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldRecoverAfterLogStreamFailure() throws InterruptedException, ExecutionException
    {
        final LogStream controllableTargetLogStream = new ControllableFsLogStreamBuilder(wrapString("target-controllable"), 3)
                .logRootPath(tempFolder.getRoot().getAbsolutePath())
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 16)
                .actorScheduler(actorScheduler)
                .build();

        final ControllableFsLogStorage controllableTargetLogStorage = (ControllableFsLogStorage) controllableTargetLogStream.getLogStorage();
        final LogStreamController targetLogStreamController = controllableTargetLogStream.getLogStreamController();

        final AtomicBoolean isSnapshotPoint = new AtomicBoolean(false);

        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", STREAM_PROCESSOR_ID, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(controllableTargetLogStream)
            .actorScheduler(actorScheduler)
            .snapshotPolicy(position -> isSnapshotPoint.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

        sourceLogStream.setCommitPosition(Long.MAX_VALUE);
        controllableTargetLogStream.setCommitPosition(Long.MAX_VALUE);

        controllableTargetLogStream.open();
        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT / 2, MSG_SIZE, 0);
        waitUntilWrittenKey(controllableTargetLogStream, WORK_COUNT / 2);

        isSnapshotPoint.set(true);

        writeLogEvents(sourceLogStream, WORK_COUNT / 2, MSG_SIZE, WORK_COUNT / 2);
        waitUntilWrittenKey(controllableTargetLogStream, (int) (WORK_COUNT * 0.75));

        controllableTargetLogStorage.setFailure(true);

        TestUtil.waitUntil(() -> streamProcessorController.isFailed());

        controllableTargetLogStorage.setFailure(false);

        // reset the resource manually to ensure that recovery happens
        resourceCounter.getObject().reset();
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(0);

        // notify the controller that the log storage is recovered
        targetLogStreamController.recover();

        // verify that the stream processor recover and resume processing
        waitUntilWrittenKey(controllableTargetLogStream, WORK_COUNT);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT);

        streamProcessorController.closeAsync().get();
        controllableTargetLogStream.close();
    }

    @Test
    public void shouldUseDisabledWriterForReadOnlyProcessor() throws InterruptedException, ExecutionException
    {
        // given
        final ErrorRecodingStreamProcessor streamProcessor = new ErrorRecodingStreamProcessor();

        final StreamProcessorController streamProcessorController = LogStreams
                .createStreamProcessor("copy-processor", STREAM_PROCESSOR_ID, streamProcessor)
                .readOnly(true)
                .sourceStream(sourceLogStream)
                .targetStream(targetLogStream)
                .actorScheduler(actorScheduler)
                .snapshotPolicy(NO_SNAPSHOT_POLICY)
                .snapshotStorage(snapshotStorage)
                .build();

        sourceLogStream.setCommitPosition(Long.MAX_VALUE);
        targetLogStream.setCommitPosition(Long.MAX_VALUE);

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT, MSG_SIZE, 0);

        // when
        TestUtil.waitUntil(() -> streamProcessorController.isFailed());

        // then
        final Exception exception = streamProcessor.getRecordedException();
        assertThat(exception).isNotNull();
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception).hasMessageContaining("Cannot write event; Writing is disabled");
    }

    @Test
    public void shouldReadCommittedEntries() throws InterruptedException, ExecutionException
    {
        final StreamProcessorController streamProcessorController = LogStreams
                .createStreamProcessor("copy-processor", STREAM_PROCESSOR_ID, new CopyStreamProcessor(resourceCounter))
                .sourceStream(sourceLogStream)
                .targetStream(targetLogStream)
                .actorScheduler(actorScheduler)
                .snapshotPolicy(NO_SNAPSHOT_POLICY)
                .snapshotStorage(snapshotStorage)
                .build();

        streamProcessorController.openAsync().get();

        final int keyOffset = 0;
        writeLogEvents(sourceLogStream, WORK_COUNT, MSG_SIZE, keyOffset);
        waitUntilWrittenKey(sourceLogStream, WORK_COUNT + keyOffset);

        final BufferedLogStreamReader sourceReader = new BufferedLogStreamReader(sourceLogStream, true);
        autoCloseableRule.manage(sourceReader);

        sourceReader.seekToFirstEvent();

        int events = 0;
        while (sourceReader.hasNext() && events <= (WORK_COUNT / 2))
        {
            sourceReader.next();
            events += 1;
        }

        sourceLogStream.setCommitPosition(sourceReader.getPosition());
        waitUntilWrittenKey(targetLogStream, WORK_COUNT / 2);

        sourceReader.seekToLastEvent();

        sourceLogStream.setCommitPosition(sourceReader.getPosition());
        waitUntilWrittenKey(targetLogStream, WORK_COUNT);

        targetLogStream.setCommitPosition(Long.MAX_VALUE);

        streamProcessorController.closeAsync().get();
    }

    private class CopyStreamProcessor implements StreamProcessor
    {
        private final SerializableWrapper<Counter> resourceCounter;

        CopyStreamProcessor(SerializableWrapper<Counter> resourceCounter)
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
                    return writer
                            .key(event.getKey())
                            .value(event.getValueBuffer(), event.getValueOffset(), event.getValueLength())
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

    private class EventBatchStreamProcessor implements StreamProcessor
    {
        private final SerializableWrapper<Counter> resourceCounter;

        private LogStreamBatchWriter batchWriter = new LogStreamBatchWriterImpl(targetLogStream);

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
                    return batchWriter
                        .producerId(STREAM_PROCESSOR_ID)
                        .sourceEvent(sourceLogStream.getTopicName(), sourceLogStream.getPartitionId(), event.getPosition())
                        .event()
                            .key(1L)
                            .value(wrapString("event-1"))
                            .done()
                        .event()
                            .key(event.getKey())
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

    protected class ControllableSnapshotStorage implements SnapshotStorage
    {
        private final SnapshotStorage snapshotStorage;
        private boolean readOnly = false;

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
    }

}
