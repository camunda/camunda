package org.camunda.tngp.logstreams.integration;

import static org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil.readLogAndAssertEvents;
import static org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil.waitUntilWrittenEvents;
import static org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil.writeLogEvents;
import static org.camunda.tngp.util.buffer.BufferUtil.wrapString;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamBatchWriter;
import org.camunda.tngp.logstreams.log.LogStreamBatchWriterImpl;
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
    private static final DirectBuffer TOPIC_NAME = wrapString("test-topic");
    private static final int MSG_SIZE = 911;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    protected AgentRunnerService agentRunnerService;
    private LogStream logStream;

    @Before
    public void setup()
    {
        agentRunnerService = new SharedAgentRunnerService(new SimpleAgentRunnerFactory(), "test");

        final String logPath = tempFolder.getRoot().getAbsolutePath();

        logStream = LogStreams.createFsLogStream(TOPIC_NAME, 0)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(1024 * 1024 * 16)
                .agentRunnerService(agentRunnerService)
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        logStream.open();
    }

    @After
    public void destroy() throws Exception
    {
        logStream.close();

        agentRunnerService.close();
    }

    @Test
    public void shouldWriteEvents()
    {
        final int workPerIteration = 10_000;

        writeLogEvents(logStream, workPerIteration, MSG_SIZE, 0);

        final LogStreamReader logReader = new BufferedLogStreamReader(logStream, true);
        readLogAndAssertEvents(logReader, workPerIteration, MSG_SIZE);
    }

    @Test
    public void shouldWriteEventsAsBatch()
    {
        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(MSG_SIZE));

        final int batchSize = 1_000;
        final int eventSizePerBatch = 100;

        final LogStreamBatchWriter logStreamWriter = new LogStreamBatchWriterImpl(logStream);

        for (int i = 0; i < batchSize; i++)
        {
            for (int j = 0; j < eventSizePerBatch; j++)
            {
                final int key = i * eventSizePerBatch + j;

                msg.putInt(0, key);

                logStreamWriter.event()
                    .key(key)
                    .value(msg)
                    .done();
            }

            while (logStreamWriter.tryWrite() < 0)
            {
                // spin
            }
        }

        final LogStreamReader logStreamReader = new BufferedLogStreamReader(logStream, true);
        readLogAndAssertEvents(logStreamReader, batchSize * eventSizePerBatch, MSG_SIZE);
    }

    @Test
    public void shouldWriteEventBatchesWithDifferentLengths()
    {
        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocate(MSG_SIZE));

        final int batchSize = 1_000;
        final int eventSizePerBatch = 100;

        final LogStreamBatchWriter logStreamWriter = new LogStreamBatchWriterImpl(logStream);

        int eventCount = 0;
        for (int i = 0; i < batchSize; i++)
        {
            for (int j = 0; j < 1 + (i % eventSizePerBatch); j++)
            {
                final int key = i * eventSizePerBatch + j;

                msg.putInt(0, key);

                logStreamWriter.event()
                    .key(key)
                    .value(msg, 0, MSG_SIZE - (j % 8))
                    .done();

                eventCount += 1;
            }

            while (logStreamWriter.tryWrite() < 0)
            {
                // spin
            }
        }

        waitUntilWrittenEvents(logStream, eventCount);
    }

}
