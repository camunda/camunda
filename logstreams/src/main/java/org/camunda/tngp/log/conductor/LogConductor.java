package org.camunda.tngp.log.conductor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogAgentContext;
import org.camunda.tngp.log.appender.LogAppenderCmd;
import org.camunda.tngp.log.appender.LogSegmentAllocationDescriptor;
import org.camunda.tngp.log.fs.AppendableLogSegment;
import org.camunda.tngp.log.fs.LogSegments;
import org.camunda.tngp.log.fs.ReadableLogSegment;
import org.camunda.tngp.util.FileUtil;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class LogConductor implements Agent, Consumer<LogConductorCmd>
{
    protected final ManyToOneConcurrentArrayQueue<LogConductorCmd> cmdQueue;
    protected final ManyToOneConcurrentArrayQueue<LogAppenderCmd> appenderCmdQueue;
    protected final LogAgentContext agentContext;

    public LogConductor(LogAgentContext agentContext)
    {
        this.agentContext = agentContext;
        this.cmdQueue = agentContext.getLogConductorCmdQueue();
        this.appenderCmdQueue = agentContext.getAppenderCmdQueue();
    }

    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += cmdQueue.drain(this);

        return workCount;
    }

    public String roleName()
    {
        return "log conductor";
    }

    @Override
    public void accept(LogConductorCmd c)
    {
        c.execute(this);
    }

    public void allocateSegment(Log log, int segmentId)
    {
        final LogSegmentAllocationDescriptor allocationDescriptor = log.getAllocationDescriptor();
        final LogSegments logSegements = log.getLogSegments();

        final String fileName = allocationDescriptor.fileName(segmentId);
        final AppendableLogSegment appendableSegment = new AppendableLogSegment(fileName);
        final ReadableLogSegment readableSegment = new ReadableLogSegment(fileName);

        if (appendableSegment.allocate(segmentId, allocationDescriptor.getSegmentSize())
                && readableSegment.openSegment(false))
        {
            appenderCmdQueue.add((a) -> a.onNextSegmentAllocated(log, appendableSegment));
            logSegements.addSegment(readableSegment);
        }
        else
        {
            appenderCmdQueue.add((a) -> a.onNextSegmentAllocationFailed(log));
        }
    }

    public void openLog(CompletableFuture<Log> future, Log log)
    {
        final LogSegmentAllocationDescriptor logAllocationDescriptor = log.getAllocationDescriptor();
        final LogSegments availableSegments = log.getLogSegments();

        final String path = logAllocationDescriptor.getPath();
        final List<ReadableLogSegment> readableLogSegments = new ArrayList<>();
        final File logDir = new File(path);
        final List<File> logFiles = Arrays.asList(logDir.listFiles((f) -> f.getName().endsWith(".data")));

        logFiles.forEach((file) ->
        {
            final ReadableLogSegment readableLogSegment = new ReadableLogSegment(file.getAbsolutePath());
            if (readableLogSegment.openSegment(false))
            {
                readableLogSegments.add(readableLogSegment);
            }
            else
            {
                future.completeExceptionally(new RuntimeException("Cannot open log segment " + file));
                return;
            }

        });

        // sort segments by id
        readableLogSegments.sort((s1, s2) -> Integer.compare(s1.getSegmentId(), s2.getSegmentId()));

        AppendableLogSegment appendableLogSegment = null;
        int initialSegmentId = 0;

        int existingSegments = readableLogSegments.size();
        if (existingSegments > 0)
        {
            final ReadableLogSegment firstSegment = readableLogSegments.get(0);
            final ReadableLogSegment secondLastSegment = readableLogSegments.get(Math.max(0, existingSegments - 2));

            ReadableLogSegment lastSegment = readableLogSegments.get(existingSegments - 1);
            if (!secondLastSegment.isFilled() && lastSegment != secondLastSegment)
            {
                // remove last pre-allocated segment
                lastSegment = secondLastSegment;
                readableLogSegments.remove(existingSegments - 1);
                --existingSegments;
            }

            initialSegmentId = firstSegment.getSegmentId();
            appendableLogSegment = new AppendableLogSegment(lastSegment.getFileName());

            if (!appendableLogSegment.openSegment(false))
            {
                future.completeExceptionally(new RuntimeException("Cannot open log segemtn " + appendableLogSegment.getFileName()));
                return;
            }
        }
        else
        {
            final String initialSegmentName = logAllocationDescriptor.fileName(initialSegmentId);
            appendableLogSegment = new AppendableLogSegment(initialSegmentName);

            if (!appendableLogSegment.allocate(initialSegmentId, logAllocationDescriptor.getSegmentSize()))
            {
                future.completeExceptionally(new RuntimeException("Cannot allocate initial segment"));
                return;
            }

            final ReadableLogSegment readableLogSegment = new ReadableLogSegment(initialSegmentName);
            if (!readableLogSegment.openSegment(false))
            {
                future.completeExceptionally(new RuntimeException("Cannot open initial segment"));
                return;
            }

            readableLogSegments.add(readableLogSegment);
        }

        final ReadableLogSegment[] segmentsArray = readableLogSegments.toArray(new ReadableLogSegment[0]);
        availableSegments.init(initialSegmentId, segmentsArray);

        log.getLogAppendHandler().init(appendableLogSegment);
        appenderCmdQueue.add((a) -> a.onLogOpened(log));

        future.complete(log);
    }

    public void closeLog(final Log log, final CompletableFuture<Log> closeFuture)
    {

        try
        {
            log.getLogSegments().closeAll();
        }
        catch (Exception e)
        {
            System.out.println("Exception while closing log:");
            e.printStackTrace();
        }

        final CompletableFuture<Log> appenderCloseFuture = new CompletableFuture<>();

        appenderCloseFuture.whenComplete((l, t) ->
        {
            if (log.isDeleteOnClose())
            {
                deleteLogFolder(log);
            }

            closeFuture.complete(log);
        });

        appenderCmdQueue.add((a) -> a.onLogClosed(log, appenderCloseFuture));
    }

    private static void deleteLogFolder(Log log)
    {
        try
        {
            final LogSegmentAllocationDescriptor allocationDescriptor = log.getAllocationDescriptor();
            final String logPath = allocationDescriptor.getPath();
            FileUtil.deleteFolder(logPath);
        }
        catch (Exception e)
        {
            System.out.println("Exception while closing log:");
            e.printStackTrace();
        }
    }

    public CompletableFuture<Void> close()
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        cmdQueue.add((c) -> c.close(future));

        return future;
    }

    public void close(final CompletableFuture<Void> future)
    {
        final Thread closeThread = new Thread("log-closer-thread")
        {
            @Override
            public void run()
            {

                if (!agentContext.isWriteBufferExternallyManaged())
                {
                    agentContext.getWriteBuffer().close();
                }

                try
                {
                    final AgentRunner[] agentRunners = agentContext.getAgentRunners();
                    if (agentRunners != null)
                    {
                        for (AgentRunner agentRunner : agentRunners)
                        {
                            agentRunner.close();
                        }
                    }
                }
                finally
                {
                    future.complete(null);
                }
            }
        };

        closeThread.start();
    }
}
