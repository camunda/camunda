/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.logstreams.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil.readLogAndAssertEvents;
import static org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil.waitUntilWrittenEvents;
import static org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil.waitUntilWrittenKey;
import static org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil.writeLogEvents;

import java.io.FileNotFoundException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.impl.LogStreamController;
import org.camunda.tngp.logstreams.integration.util.ControllableFsLogStorage;
import org.camunda.tngp.logstreams.integration.util.ControllableFsLogStreamBuilder;
import org.camunda.tngp.logstreams.integration.util.Counter;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.logstreams.snapshot.SerializableWrapper;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.agent.SharedAgentRunnerService;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StreamProcessorIntegrationTest
{
    private static final int MSG_SIZE = 911;
    private static final int WORK_COUNT = 50_000;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private AgentRunnerService agentRunnerService;

    private LogStream sourceLogStream;
    private LogStream targetLogStream;

    private SnapshotStorage snapshotStorage;
    private SerializableWrapper<Counter> resourceCounter;

    private String logPath;

    @Before
    public void setup()
    {
        agentRunnerService = new SharedAgentRunnerService(new SimpleAgentRunnerFactory(), "test");

        resourceCounter = new SerializableWrapper<>(new Counter());

        logPath = tempFolder.getRoot().getAbsolutePath();

        snapshotStorage = LogStreams.createFsSnapshotStore(logPath).build();

        sourceLogStream = LogStreams.createFsLogStream("source", 0)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 16)
                .agentRunnerService(agentRunnerService)
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        targetLogStream = LogStreams.createFsLogStream("target", 1)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 16)
                .agentRunnerService(agentRunnerService)
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        sourceLogStream.open();
        targetLogStream.open();
    }

    @After
    public void destroy() throws Exception
    {
        sourceLogStream.close();
        targetLogStream.close();

        agentRunnerService.close();
    }

    @Test
    public void shouldCopyEventsToTargetStream() throws InterruptedException, ExecutionException
    {
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", 1, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .agentRunnerService(agentRunnerService)
            .snapshotPolicy(position -> false)
            .snapshotStorage(snapshotStorage)
            .build();

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT, MSG_SIZE, 0);

        final LogStreamReader logReader = new BufferedLogStreamReader(targetLogStream);
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
                        final long nextKey = event.getLongKey() + 1;
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
            .createStreamProcessor("increment-processor", 1, streamProcessor)
            .sourceStream(sourceLogStream)
            .targetStream(sourceLogStream)
            .agentRunnerService(agentRunnerService)
            .snapshotPolicy(position -> isSnapshotPoint.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

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
    public void shouldRecoverFromSnapshot() throws FileNotFoundException, Exception
    {
        final AtomicBoolean isLastLogEntry = new AtomicBoolean(false);

        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", 1, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .agentRunnerService(agentRunnerService)
            .snapshotPolicy(pos -> isLastLogEntry.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT, MSG_SIZE, 0);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT);

        isLastLogEntry.set(true);

        // write one more event to create the snapshot
        writeLogEvents(sourceLogStream, 1, MSG_SIZE, WORK_COUNT);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT + 1);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT + 1);

        streamProcessorController.closeAsync().get();

        // reset the resource manually to ensure that recovery happens
        resourceCounter.getObject().reset();
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(0);

        streamProcessorController.openAsync().get();

        // write one more event to verify that the processor resume on snapshot position
        writeLogEvents(sourceLogStream, 1, MSG_SIZE, WORK_COUNT + 1);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT + 2);

        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT + 2);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldRecoverFromSnapshotAndReprocessEvents() throws FileNotFoundException, Exception
    {
        final AtomicBoolean isSnapshotPoint = new AtomicBoolean(false);

        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", 1, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .agentRunnerService(agentRunnerService)
            .snapshotPolicy(pos -> isSnapshotPoint.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT / 2, MSG_SIZE, 0);
        waitUntilWrittenKey(targetLogStream, WORK_COUNT / 2);

        isSnapshotPoint.set(true);

        writeLogEvents(sourceLogStream, WORK_COUNT / 2, MSG_SIZE, WORK_COUNT / 2);
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
    public void shouldRecoverWithoutSnapshot() throws FileNotFoundException, Exception
    {
        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", 1, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .agentRunnerService(agentRunnerService)
            .snapshotPolicy(pos -> false)
            .snapshotStorage(snapshotStorage)
            .build();

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
    public void shouldRecoverWithTwoProcessors() throws FileNotFoundException, Exception
    {
        final SerializableWrapper<Counter> resourceCounter2 = new SerializableWrapper<>(new Counter());

        final AtomicBoolean isSnapshotPoint1 = new AtomicBoolean(false);
        final AtomicBoolean isSnapshotPoint2 = new AtomicBoolean(false);

        final StreamProcessorController streamProcessorController1 = LogStreams
            .createStreamProcessor("processor-1", 1, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .agentRunnerService(agentRunnerService)
            .snapshotPolicy(pos -> isSnapshotPoint1.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

        final StreamProcessorController streamProcessorController2 = LogStreams
            .createStreamProcessor("processor-2", 2, new CopyStreamProcessor(resourceCounter2))
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .agentRunnerService(agentRunnerService)
            .snapshotPolicy(pos -> isSnapshotPoint2.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

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
    public void shouldRecoverAfterLogStreamFailure() throws InterruptedException, ExecutionException
    {
        final LogStream controllableTargetLogStream = new ControllableFsLogStreamBuilder("target-controllable", 3)
                .logRootPath(tempFolder.getRoot().getAbsolutePath())
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 16)
                .agentRunnerService(agentRunnerService)
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        final ControllableFsLogStorage controllableTargetLogStorage = (ControllableFsLogStorage) controllableTargetLogStream.getContext().getLogStorage();
        final LogStreamController targetLogStreamController = controllableTargetLogStream.getContext().getLogStreamController();

        final AtomicBoolean isSnapshotPoint = new AtomicBoolean(false);

        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", 1, new CopyStreamProcessor(resourceCounter))
            .sourceStream(sourceLogStream)
            .targetStream(controllableTargetLogStream)
            .agentRunnerService(agentRunnerService)
            .snapshotPolicy(position -> isSnapshotPoint.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

        controllableTargetLogStream.open();
        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT / 2, MSG_SIZE, 0);
        waitUntilWrittenKey(controllableTargetLogStream, WORK_COUNT / 2);

        isSnapshotPoint.set(true);

        writeLogEvents(sourceLogStream, WORK_COUNT / 2, MSG_SIZE, WORK_COUNT / 2);
        waitUntilWrittenKey(controllableTargetLogStream, (int) (WORK_COUNT * 0.75));

        controllableTargetLogStorage.setFailure(true);

        while (!streamProcessorController.isFailed())
        {
            // wait until the stream controller is in fail state
        }

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
                            .key(event.getLongKey())
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

}
