package org.camunda.tngp.logstreams.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreamReader;
import org.camunda.tngp.logstreams.LogStreamWriter;
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
    // ~ overall message size 46 MB
    private static final int MSG_SIZE = 911;
    private static final int WORK_COUNT = 50_000;

    private static final int LOG_SEGMENT_SIZE = 1024 * 1024 * 8;
    private static final int INDEX_BLOCK_SIZE = 1024 * 1024 * 2;

    private static final String LOG_NAME = "foo";
    private static final int LOG_ID = 0;

    @Rule
    public TemporaryFolder temFolder = new TemporaryFolder();

    private String logPath;

    private AgentRunnerService agentRunnerService;

    @Before
    public void setup()
    {
        logPath = temFolder.getRoot().getAbsolutePath();

        agentRunnerService = new SharedAgentRunnerService("test-%s", 1, new SimpleAgentRunnerFactory());
    }

    @After
    public void destroy() throws Exception
    {
        agentRunnerService.close();
    }

    @Test
    public void shouldRecoverIndexFromLogStorage() throws InterruptedException, ExecutionException
    {
        final LogStream log = LogStreams.createFsLogStream(LOG_NAME, LOG_ID)
                .logRootPath(logPath)
                .deleteOnClose(false)
                .logSegmentSize(LOG_SEGMENT_SIZE)
                .indexBlockSize(INDEX_BLOCK_SIZE)
                .snapshotPolicy(pos -> false)
                .agentRunnerService(agentRunnerService)
                .build();

        log.open();

        writeLogEvents(log, WORK_COUNT, 0);
        waitUntilFullyWritten(log, WORK_COUNT);

        log.close();

        readLogAndAssertRecoveredIndex(WORK_COUNT);
    }

    @Test
    public void shouldRecoverIndexFromSnapshot() throws InterruptedException, ExecutionException
    {
        final AtomicBoolean isLastLogEntry = new AtomicBoolean(false);

        final LogStream log = LogStreams.createFsLogStream(LOG_NAME, 0)
                .logRootPath(logPath)
                .deleteOnClose(false)
                .logSegmentSize(LOG_SEGMENT_SIZE)
                .indexBlockSize(INDEX_BLOCK_SIZE)
                .snapshotPolicy(pos -> isLastLogEntry.getAndSet(false))
                .agentRunnerService(agentRunnerService)
                .build();

        log.open();

        writeLogEvents(log, WORK_COUNT, 0);
        waitUntilFullyWritten(log, WORK_COUNT);

        isLastLogEntry.set(true);

        // write one more event to create the snapshot
        writeLogEvents(log, 1, WORK_COUNT);
        waitUntilFullyWritten(log, WORK_COUNT + 1);

        log.close();

        readLogAndAssertRecoveredIndex(WORK_COUNT + 1);
    }

    @Test
    public void shouldRecoverIndexFromSnapshotAndLogStorage() throws InterruptedException, ExecutionException
    {
        final AtomicBoolean isSnapshotPoint = new AtomicBoolean(false);

        final LogStream log = LogStreams.createFsLogStream(LOG_NAME, 0)
                .logRootPath(logPath)
                .deleteOnClose(false)
                .logSegmentSize(LOG_SEGMENT_SIZE)
                .indexBlockSize(INDEX_BLOCK_SIZE)
                .snapshotPolicy(pos -> isSnapshotPoint.getAndSet(false))
                .agentRunnerService(agentRunnerService)
                .build();

        log.open();

        writeLogEvents(log, WORK_COUNT / 2, 0);
        waitUntilFullyWritten(log, WORK_COUNT / 2);

        isSnapshotPoint.set(true);

        writeLogEvents(log, WORK_COUNT / 2, WORK_COUNT / 2);
        waitUntilFullyWritten(log, WORK_COUNT);

        log.close();

        readLogAndAssertRecoveredIndex(WORK_COUNT);
    }

    @Test
    public void shouldRecoverIndexFromPeriodicallyCreatedSnapshotAndLogStorage() throws InterruptedException, ExecutionException
    {
        final int snapshotInterval = 10;

        final AtomicInteger snapshotCount = new AtomicInteger(0);

        final LogStream log = LogStreams.createFsLogStream(LOG_NAME, 0)
                .logRootPath(logPath)
                .deleteOnClose(false)
                .logSegmentSize(LOG_SEGMENT_SIZE)
                .indexBlockSize(INDEX_BLOCK_SIZE)
                .snapshotPolicy(pos -> (snapshotCount.incrementAndGet() % snapshotInterval) == 0)
                .agentRunnerService(agentRunnerService)
                .build();

        log.open();

        writeLogEvents(log, WORK_COUNT, 0);
        waitUntilFullyWritten(log, WORK_COUNT);

        log.close();

        readLogAndAssertRecoveredIndex(WORK_COUNT);
    }

    protected void writeLogEvents(final LogStream log, final int workCount, final int offset)
    {
        final LogStreamWriter writer = new LogStreamWriter(log);

        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(MSG_SIZE));

        for (int i = 0; i < workCount; i++)
        {
            msg.putInt(0, offset + i);

            writer
                .key(offset + i)
                .value(msg);

            while (writer.tryWrite() < 0)
            {
                // spin
            }
        }
    }

    protected void waitUntilFullyWritten(final LogStream log, final int workCount)
    {
        final BufferedLogStreamReader logReader = new BufferedLogStreamReader(log);

        logReader.seekToLastEvent();

        long entryKey = 0;
        while (entryKey < workCount - 1)
        {
            if (logReader.hasNext())
            {
                final LoggedEvent nextEntry = logReader.next();
                entryKey = nextEntry.getLongKey();
            }
        }
    }

    protected void readLogAndAssertRecoveredIndex(final int workCount)
    {
        final LogStream newLog = LogStreams.createFsLogStream(LOG_NAME, LOG_ID)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(LOG_SEGMENT_SIZE)
                .agentRunnerService(agentRunnerService)
                .build();

        newLog.open();

        final LogStreamReader logReader = new BufferedLogStreamReader(newLog);

        int count = 0;
        long lastPosition = -1L;

        while (count < workCount)
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

        assertThat(count).isEqualTo(workCount);
        assertThat(logReader.hasNext()).isFalse();

        newLog.close();
    }

}
