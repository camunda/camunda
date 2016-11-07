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

public class LogRecoveryTest
{
    private static final int MSG_SIZE = 911;

    @Rule
    public TemporaryFolder temFolder = new TemporaryFolder();

    private AgentRunnerService agentRunnerService;

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
    public void shouldRecover() throws InterruptedException, ExecutionException
    {
        final String logPath = temFolder.getRoot().getAbsolutePath();

        // TODO make sure to recover from a snapshot
        final LogStream log1 = LogStreams.createFsLogStream("foo", 0)
                .logRootPath(logPath)
                .deleteOnClose(false)
                .logSegmentSize(1024 * 1024 * 8)
                .agentRunnerService(agentRunnerService)
                .build();

        log1.open();

        final EventLogger writer = new EventLogger(log1);

        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(MSG_SIZE));

        final int workCount = 200_000;

        for (int i = 0; i < workCount; i++)
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

        // wait until fully written
        final BufferedLogStreamReader log1Reader = new BufferedLogStreamReader(log1);

        long entryKey = 0;
        while (entryKey < workCount - 1)
        {
            log1Reader.seekToLastEvent();

            if (log1Reader.hasNext())
            {
                final LoggedEvent nextEntry = log1Reader.next();
                entryKey = nextEntry.getLongKey();
            }
        }

        log1.close();

        final LogStream log2 = LogStreams.createFsLogStream("foo", 0)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 8)
                .agentRunnerService(agentRunnerService)
                .build();

        log2.open();

        final LogStreamReader logReader = new BufferedLogStreamReader(log2);
        logReader.seekToFirstEvent();

        int count = 0;
        long lastPosition = -1L;

        while (count < workCount)
        {
            if (count % 10 == 0)
            {
                // TODO make sure index is used
                // logReader.seek(lastPosition + 1);
            }

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

        assertThat(count).isEqualTo(workCount);
        assertThat(logReader.hasNext()).isFalse();

        log2.close();
    }

}
