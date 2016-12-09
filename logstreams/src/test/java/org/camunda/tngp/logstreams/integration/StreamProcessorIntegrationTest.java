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
import static org.camunda.tngp.logstreams.integration.LogIntegrationTestUtil.readLogAndAssertEvents;
import static org.camunda.tngp.logstreams.integration.LogIntegrationTestUtil.waitUntilFullyWritten;
import static org.camunda.tngp.logstreams.integration.LogIntegrationTestUtil.writeLogEvents;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.camunda.tngp.logstreams.LogStreams;
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

    protected AgentRunnerService agentRunnerService;

    private LogStream sourceLogStream;

    private LogStream targetLogStream;
    private SnapshotStorage snapshotStorage;

    @Before
    public void setup()
    {
        agentRunnerService = new SharedAgentRunnerService("test-%s", 1, new SimpleAgentRunnerFactory());

        final String logPath = tempFolder.getRoot().getAbsolutePath();

        snapshotStorage = LogStreams.createFsSnapshotStore(logPath).build();

        sourceLogStream = LogStreams.createFsLogStream("source", 0)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 16)
                .agentRunnerService(agentRunnerService)
                .build();

        targetLogStream = LogStreams.createFsLogStream("target", 1)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 16)
                .agentRunnerService(agentRunnerService)
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
        final StreamProcessor copyStreamProcessor = (LoggedEvent event) -> new EventProcessor()
        {
            @Override
            public void processEvent()
            {
                // do nothing
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

        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", 1, copyStreamProcessor)
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .agentRunnerService(agentRunnerService)
            .snapshotPolicy(position -> false)
            .build();

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT, MSG_SIZE, 0);

        final LogStreamReader logReader = new BufferedLogStreamReader(targetLogStream);
        readLogAndAssertEvents(logReader, WORK_COUNT, MSG_SIZE);

        streamProcessorController.closeAsync().get();
    }

    @Test
    public void shouldCreateSnapshotOfResource() throws FileNotFoundException, Exception
    {
        final SerializableWrapper<Counter> resourceCounter = new SerializableWrapper<>(new Counter());

        final StreamProcessor streamProcessor = (LoggedEvent event) -> new EventProcessor()
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

        final AtomicBoolean isLastLogEntry = new AtomicBoolean(false);

        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("processor", 1, streamProcessor)
            .resource(resourceCounter)
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .agentRunnerService(agentRunnerService)
            .snapshotPolicy(pos -> isLastLogEntry.getAndSet(false))
            .snapshotStorage(snapshotStorage)
            .build();

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT, MSG_SIZE, 0);
        waitUntilFullyWritten(targetLogStream, WORK_COUNT);

        isLastLogEntry.set(true);

        // write one more event to create the snapshot
        writeLogEvents(sourceLogStream, 1, MSG_SIZE, WORK_COUNT);
        waitUntilFullyWritten(targetLogStream, WORK_COUNT + 1);

        streamProcessorController.closeAsync().get();

        // search for the snapshot file
        final File[] files = tempFolder.getRoot().listFiles((dir, name) -> name.endsWith("snapshot"));
        assertThat(files.length).isEqualTo(1);

        // restore the resource from snapshot and verify it
        resourceCounter.recoverFromSnapshot(new FileInputStream(files[0]));
        assertThat(resourceCounter.getObject().getCount()).isEqualTo(WORK_COUNT + 1);
    }

}
