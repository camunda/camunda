package org.camunda.tngp.log.conductor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.camunda.tngp.dispatcher.AsyncCompletionCallback;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogContext;
import org.camunda.tngp.log.appender.LogAppenderCmd;
import org.camunda.tngp.log.appender.LogSegmentAllocationDescriptor;
import org.camunda.tngp.log.fs.AppendableLogSegment;
import org.camunda.tngp.log.fs.AvailableSegments;
import org.camunda.tngp.log.fs.ReadableLogSegment;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.AgentRunner;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

public class LogConductor implements Agent, Consumer<LogConductorCmd>
{

    protected final OneToOneConcurrentArrayQueue<LogConductorCmd> cmdQueue;
    protected final OneToOneConcurrentArrayQueue<LogAppenderCmd> appenderCmdQueue;

    protected final LogSegmentAllocationDescriptor logAllocationDescriptor;
    protected final AvailableSegments availableSegments;

    protected Dispatcher writeBuffer;
    protected AgentRunner[] agentRunners;
    protected LogContext logContext;

    public LogConductor(LogContext logContext)
    {
        this.logContext = logContext;
        cmdQueue = logContext.getLogConductorCmdQueue();
        appenderCmdQueue = logContext.getAppenderCmdQueue();
        availableSegments = logContext.getAvailableSegments();
        logAllocationDescriptor = logContext.getLogAllocationDescriptor();
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

    public void allocate(int segmentId)
    {
        final String fileName = logAllocationDescriptor.fileName(segmentId);
        final AppendableLogSegment appendableSegment = new AppendableLogSegment(fileName);
        final ReadableLogSegment readableSegment = new ReadableLogSegment(fileName);

        if(appendableSegment.allocate(segmentId, logAllocationDescriptor.getSegmentSize())
                && readableSegment.openSegment(false))
        {
            appenderCmdQueue.add((a) ->
            {
                a.onNextSegmentAllocated(appendableSegment);
            });

            availableSegments.addSegment(readableSegment);
        }
        else
        {
            appenderCmdQueue.add((a) ->
            {
                a.onNextSegmentAllocationFailed();
            });
        }
    }

    public void openLog(CompletableFuture<Log> future, Log log)
    {
        writeBuffer = logContext.getWriteBuffer();
        agentRunners = logContext.getAgentRunners();

        final String path = logAllocationDescriptor.getPath();
        final List<ReadableLogSegment> readableLogSegments = new ArrayList<>();
        final File logDir = new File(path);
        final List<File> logFiles = Arrays.asList(logDir.listFiles((f) -> f.getName().endsWith(".data")));

        logFiles.forEach((file) ->
        {
           final ReadableLogSegment readableLogSegment = new ReadableLogSegment(file.getAbsolutePath());
           if(readableLogSegment.openSegment(false))
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
        if(existingSegments > 0)
        {
            final ReadableLogSegment firstSegment = readableLogSegments.get(0);
            final ReadableLogSegment secondLastSegment = readableLogSegments.get(Math.max(0, existingSegments -2));

            ReadableLogSegment lastSegment = readableLogSegments.get(existingSegments - 1);
            if(!secondLastSegment.isFilled())
            {
                // remove last pre-allocated segment
                lastSegment = secondLastSegment;
                readableLogSegments.remove(existingSegments - 1);
                --existingSegments;
            }

            initialSegmentId = firstSegment.getSegmentId();
            appendableLogSegment = new AppendableLogSegment(lastSegment.getFileName());

            if(!appendableLogSegment.openSegment(false))
            {
                future.completeExceptionally(new RuntimeException("Cannot open log segemtn " + appendableLogSegment.getFileName()));
                return;
            }
        }
        else
        {
            final String initialSegmentName = logAllocationDescriptor.fileName(initialSegmentId);
            appendableLogSegment = new AppendableLogSegment(initialSegmentName);

            if(!appendableLogSegment.allocate(initialSegmentId, logAllocationDescriptor.getSegmentSize()))
            {
                future.completeExceptionally(new RuntimeException("Cannot allocate initial segment"));
                return;
            }

            final ReadableLogSegment readableLogSegment = new ReadableLogSegment(initialSegmentName);
            if(!readableLogSegment.openSegment(false))
            {
                future.completeExceptionally(new RuntimeException("Cannot open initial segment"));
                return;
            }

            readableLogSegments.add(readableLogSegment);
        }

        final ReadableLogSegment[] segmentsArray = readableLogSegments.toArray(new ReadableLogSegment[0]);
        availableSegments.init(initialSegmentId, segmentsArray);

        appenderCmdQueue.add(new InitAppenderCmd(appendableLogSegment));

        future.complete(log);
    }

    public void closeLog(final CompletableFuture<Boolean> future)
    {
        this.writeBuffer.closeAsync(new AsyncCompletionCallback<Dispatcher>()
        {
            public void onComplete(Throwable t, Dispatcher result)
            {
                cmdQueue.add((c) -> onWriteBufferClosed(future, t == null));
            }
        });
    }

    protected void onWriteBufferClosed(final CompletableFuture<Boolean> future, final boolean closedClean)
    {
        final Thread closeThread = new Thread("log-closer-thread")
        {
            @Override
            public void run() {

                try
                {
                    availableSegments.closeAll();

                    for (AgentRunner agentRunner : agentRunners)
                    {
                        agentRunner.close();
                    }
                }
                finally
                {
                    future.complete(closedClean);
                }
            }
        };

        closeThread.start();
    }

    public void closeSegment(AppendableLogSegment segment)
    {
        segment.closeSegment();
    }
}
