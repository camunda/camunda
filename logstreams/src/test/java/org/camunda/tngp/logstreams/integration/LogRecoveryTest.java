package org.camunda.tngp.logstreams.integration;

import static org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil.waitUntilWrittenKey;
import static org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil.writeLogEvents;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.integration.util.LogIntegrationTestUtil;
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

        agentRunnerService = new SharedAgentRunnerService(new SimpleAgentRunnerFactory(), "test");
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
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        log.open();

        writeLogEvents(log, WORK_COUNT, MSG_SIZE, 0);
        waitUntilWrittenKey(log, WORK_COUNT);

        log.close();

        readLogAndAssertEvents(WORK_COUNT);
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
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        log.open();

        writeLogEvents(log, WORK_COUNT, MSG_SIZE, 0);
        waitUntilWrittenKey(log, WORK_COUNT);

        isLastLogEntry.set(true);

        // write one more event to create the snapshot
        writeLogEvents(log, 1, MSG_SIZE, WORK_COUNT);
        waitUntilWrittenKey(log, WORK_COUNT + 1);

        log.close();

        readLogAndAssertEvents(WORK_COUNT + 1);
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
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        log.open();

        writeLogEvents(log, WORK_COUNT / 2, MSG_SIZE, 0);
        waitUntilWrittenKey(log, WORK_COUNT / 2);

        isSnapshotPoint.set(true);

        writeLogEvents(log, WORK_COUNT / 2, MSG_SIZE, WORK_COUNT / 2);
        waitUntilWrittenKey(log, WORK_COUNT);

        log.close();

        readLogAndAssertEvents(WORK_COUNT);
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
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        log.open();

        writeLogEvents(log, WORK_COUNT, MSG_SIZE, 0);
        waitUntilWrittenKey(log, WORK_COUNT);

        log.close();

        readLogAndAssertEvents(WORK_COUNT);
    }

    @Test
    public void shouldRecoverEventPosition() throws InterruptedException, ExecutionException
    {
        final LogStream log = LogStreams.createFsLogStream(LOG_NAME, LOG_ID)
                .logRootPath(logPath)
                .deleteOnClose(false)
                .snapshotPolicy(pos -> false)
                .agentRunnerService(agentRunnerService)
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        log.open();

        // write events
        writeLogEvents(log, 10, MSG_SIZE, 0);
        waitUntilWrittenKey(log, 10);

        log.close();

        // re-open the log
        final LogStream newLog = LogStreams.createFsLogStream(LOG_NAME, LOG_ID)
                .logRootPath(logPath)
                .deleteOnClose(false)
                .snapshotPolicy(pos -> false)
                .agentRunnerService(agentRunnerService)
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        newLog.open();

        // write more events
        writeLogEvents(newLog, 15, MSG_SIZE, 10);
        waitUntilWrittenKey(newLog, 25);

        newLog.close();

        // assert that the event position is recovered after re-open and continues after the last event
        readLogAndAssertEvents(25);
    }

    @Test
    public void shouldResumeLogStream() throws InterruptedException, ExecutionException
    {
        final LogStream log = LogStreams.createFsLogStream(LOG_NAME, LOG_ID)
                .logRootPath(logPath)
                .deleteOnClose(false)
                .snapshotPolicy(pos -> false)
                .agentRunnerService(agentRunnerService)
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        log.open();

        // write events
        writeLogEvents(log, 10, MSG_SIZE, 0);
        waitUntilWrittenKey(log, 10);

        log.close();

        // resume the log
        log.open();

        // write more events
        writeLogEvents(log, 15, MSG_SIZE, 10);
        waitUntilWrittenKey(log, 25);

        log.close();

        readLogAndAssertEvents(25);
    }

    protected void readLogAndAssertEvents(int workCount)
    {
        final LogStream newLog = LogStreams.createFsLogStream(LOG_NAME, LOG_ID)
                .logRootPath(logPath)
                .deleteOnClose(true)
                .logSegmentSize(LOG_SEGMENT_SIZE)
                .agentRunnerService(agentRunnerService)
                .writeBufferAgentRunnerService(agentRunnerService)
                .build();

        newLog.open();

        final LogStreamReader logReader = new BufferedLogStreamReader(newLog);

        LogIntegrationTestUtil.readLogAndAssertEvents(logReader, workCount, MSG_SIZE);

        newLog.close();
    }

}
