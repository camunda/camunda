package org.camunda.tngp.logstreams.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.EventLogger;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreamReader;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.LoggedEvent;
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
        agentRunnerService = new SharedAgentRunnerService("test-%s", 1, new SimpleAgentRunnerFactory());
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

        final EventLogger writer = new EventLogger(logStream);
        final LogStreamReader logReader = new BufferedLogStreamReader(logStream);

        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(MSG_SIZE));

        for (int j = 0; j < 50; j++)
        {
            final int workPerIteration = 20000;

            for (int i = 0; i < workPerIteration; i++)
            {
                msg.putInt(0, i);

                writer
                    .key(i)
                    .value(msg);

                while (writer.tryWrite() < 0)
                {
                    // spin
                }
            }


            int count = 0;
            long lastPosition = -1L;

            while (count < workPerIteration)
            {
                if (logReader.hasNext())
                {
                    final LoggedEvent entry = logReader.next();
                    final long currentPosition = entry.getPosition();

                    assertThat(currentPosition > lastPosition);

                    final DirectBuffer valueBuffer = entry.getValueBuffer();
                    final long value = valueBuffer.getInt(entry.getValueOffset());
                    assertThat(value).isEqualTo(entry.getLongKey());
                    assertThat(entry.getValueLength()).isEqualTo(MSG_SIZE);

                    lastPosition = currentPosition;

                    count++;
                }
            }

            assertThat(count).isEqualTo(workPerIteration);
            assertThat(logReader.hasNext()).isFalse();
        }

        logStream.close();
    }

}
