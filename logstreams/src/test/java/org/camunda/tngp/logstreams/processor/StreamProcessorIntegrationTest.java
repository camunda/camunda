package org.camunda.tngp.logstreams.processor;

import java.io.File;
import java.util.concurrent.ExecutionException;

import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreams;
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
    @Rule
    public TemporaryFolder temFolder = new TemporaryFolder();

    LogStream sourceStream;
    LogStream targetStream;

    AgentRunnerService agentRunnerService;

    @Before
    public void openStreams() throws InterruptedException, ExecutionException
    {
        final String logPath = temFolder.getRoot().getAbsolutePath();

        agentRunnerService = new SharedAgentRunnerService("test-%s", 1, new SimpleAgentRunnerFactory());

        sourceStream = LogStreams.createFsLogStream("foo", 0)
                .logRootPath(logPath + File.separator + "src")
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 8)
                .agentRunnerService(agentRunnerService)
                .build();

        targetStream = LogStreams.createFsLogStream("foo", 0)
                .logRootPath(logPath + File.separator + "target")
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 8)
                .agentRunnerService(agentRunnerService)
                .build();

        sourceStream.open();
        targetStream.open();
    }

    @After
    public void closeSourceStream() throws Exception
    {
        if (sourceStream != null)
        {
            sourceStream.close();
        }
    }

    @After
    public void closeTargetStream()
    {
        if (targetStream != null)
        {
            targetStream.close();
        }
    }

    @After
    public void closeAgentRunnerServices() throws Exception
    {
        agentRunnerService.close();
    }

    @Test
    public void testCopyStream()
    {
        final StreamProcessor processor = (event, logger) ->
        {
            logger
                .key(event.getLongKey())
                .value(event.getValueBuffer(), event.getValueOffset(), event.getValueLength())
                .requestWrite();
        };

        final StreamProcessorController ctr = LogStreams.createStreamProcessor("logger", processor)
            .sourceStream(sourceStream)
            .targetStream(targetStream)
            .build();

        ctr.close();
    }

}
