package org.camunda.tngp.logstreams.integration;

import static org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil.readLogAndAssertEvents;
import static org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil.writeLogEvents;

import java.util.concurrent.ExecutionException;

import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.agent.SharedAgentRunnerService;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LogIntegrationTest
{
    private static final int MSG_SIZE = 911;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    protected AgentRunnerService agentRunnerService;

    @Before
    public void setup()
    {
        agentRunnerService = new SharedAgentRunnerService(new SimpleAgentRunnerFactory(), "test");
    }

    @After
    public void destroy() throws Exception
    {
        agentRunnerService.close();
    }

    @Test
    public void shouldAppend() throws InterruptedException, ExecutionException
    {
        final String logPath = tempFolder.getRoot().getAbsolutePath();

        final LogStream logStream = LogStreams.createFsLogStream("foo", 0)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 16)
                .agentRunnerService(agentRunnerService)
                .build();

        logStream.open();

        final LogStreamReader logReader = new BufferedLogStreamReader(logStream);

        for (int j = 0; j < 10; j++)
        {
            final int workPerIteration = 10_000;

            writeLogEvents(logStream, workPerIteration, MSG_SIZE, 0);

            readLogAndAssertEvents(logReader, workPerIteration, MSG_SIZE);
        }

        logStream.close();
    }

}
