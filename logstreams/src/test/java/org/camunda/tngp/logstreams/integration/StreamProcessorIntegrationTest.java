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

import static org.camunda.tngp.logstreams.integration.LogIntegrationTestUtil.readLogAndAssertEvents;
import static org.camunda.tngp.logstreams.integration.LogIntegrationTestUtil.writeLogEvents;

import java.util.concurrent.ExecutionException;

import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
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

    @Before
    public void setup()
    {
        agentRunnerService = new SharedAgentRunnerService("test-%s", 1, new SimpleAgentRunnerFactory());

        final String logPath = tempFolder.getRoot().getAbsolutePath();

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
            public boolean writeEvent(LogStreamWriter writer)
            {
                final long position = writer
                        .key(event.getLongKey())
                        .value(event.getValueBuffer(), event.getValueOffset(), event.getValueLength())
                        .tryWrite();

                return position >= 0;
            }
        };

        final StreamProcessorController streamProcessorController = LogStreams
            .createStreamProcessor("copy-processor", 1, copyStreamProcessor)
            .sourceStream(sourceLogStream)
            .targetStream(targetLogStream)
            .agentRunnerService(agentRunnerService)
            .build();

        streamProcessorController.openAsync().get();

        writeLogEvents(sourceLogStream, WORK_COUNT, MSG_SIZE, 0);

        final LogStreamReader logReader = new BufferedLogStreamReader(targetLogStream);
        readLogAndAssertEvents(logReader, WORK_COUNT, MSG_SIZE);

        streamProcessorController.closeAsync().get();
    }

}
