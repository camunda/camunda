package org.camunda.tngp.logstreams.integration;

import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.util.agent.AgentRunnerService;
import org.camunda.tngp.util.agent.SharedAgentRunnerService;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class DeleteOnCloseTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private AgentRunnerService agentRunnerService;

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
    public void shouldNotDeleteOnCloseByDefault() throws InterruptedException, ExecutionException
    {
        final File logFolder = tempFolder.getRoot();

        final LogStream log = LogStreams.createFsLogStream("foo", 0)
            .logRootPath(logFolder.getAbsolutePath())
            .agentRunnerService(agentRunnerService)
            .writeBufferAgentRunnerService(agentRunnerService)
            .build();

        log.open();

        // if
        log.close();

        // then
        assertThat(logFolder.listFiles().length).isGreaterThan(0);
    }

    @Test
    @Ignore
    public void shouldDeleteOnCloseIfSet() throws InterruptedException, ExecutionException
    {
        final File logFolder = tempFolder.getRoot();

        final LogStream log = LogStreams.createFsLogStream("foo", 0)
            .logRootPath(logFolder.getAbsolutePath())
            .deleteOnClose(true)
            .agentRunnerService(agentRunnerService)
            .writeBufferAgentRunnerService(agentRunnerService)
            .build();

        log.open();

        // if
        log.close();

        // then
        assertThat(logFolder.listFiles().length).isEqualTo(0);
    }
}
