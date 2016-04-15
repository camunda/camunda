package org.camunda.tngp.log.appender;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.impl.Subscription;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogAgentContext;
import org.camunda.tngp.log.conductor.LogConductorCmd;
import org.camunda.tngp.log.fs.AppendableLogSegment;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;
import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class LogAppender implements Agent, Consumer<LogAppenderCmd>
{
    private static final String NAME = "log-appender";

    protected final ManyToOneConcurrentArrayQueue<LogConductorCmd> toLogConductorCmdQueue;

    protected final ManyToOneConcurrentArrayQueue<LogAppenderCmd> cmdQueue;

    protected final Int2ObjectHashMap<LogAppendHandler> appendHandlers = new Int2ObjectHashMap<>();

    protected Subscription appenderSubsciption;

    protected final BlockPeek blockPeek;

    protected int maxAppendSize = 1024 * 1024;

    public LogAppender(LogAgentContext context)
    {
        toLogConductorCmdQueue = context.getLogConductorCmdQueue();
        cmdQueue = context.getAppenderCmdQueue();
        appenderSubsciption = context.getWriteBuffer().openSubscription();
        blockPeek = new BlockPeek();
    }

    @Override
    public String roleName()
    {
        return NAME;
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += cmdQueue.drain(this);

        workCount += poll();

        return workCount;
    }

    protected int poll()
    {
        int bytesWritten = 0;

        final int bytesAvailable = appenderSubsciption.peekBlock(blockPeek, maxAppendSize, true);

        if(bytesAvailable > 0)
        {
            final LogAppendHandler logAppendHandler = appendHandlers.get(blockPeek.getStreamId());

            if(logAppendHandler != null)
            {
                bytesWritten = logAppendHandler.append(blockPeek, this);
            }
            else
            {
                System.err.println("Could not find append handler for log with id "+blockPeek.getStreamId());
                blockPeek.markFailed();
            }
        }

        return bytesWritten;
    }

    protected void requestAllocateNextSegment(Log log, int nextSegmentId)
    {
        toLogConductorCmdQueue.add((c) ->
        {
           c.allocateSegment(log, nextSegmentId);
        });
    }

    public void onNextSegmentAllocated(Log log, AppendableLogSegment segment)
    {
        log.getLogAppendHandler()
            .onNextSegmentAllocated(segment);
    }

    public void onNextSegmentAllocationFailed(Log log)
    {
        log.getLogAppendHandler()
            .onNextSegmentAllocationFailed();
    }

    @Override
    public void accept(LogAppenderCmd t)
    {
        t.execute(this);
    }

    public void onLogOpened(Log log)
    {
        final LogAppendHandler logAppendHandler = log.getLogAppendHandler();
        this.appendHandlers.put(log.getId(), logAppendHandler);

        logAppendHandler.allocateNextSegment(this);
    }

    public void onLogClosed(Log log, CompletableFuture<Log> future)
    {
        try
        {
            final LogAppendHandler appendHandler = this.appendHandlers.remove(log.getId());
            appendHandler.close();
        }
        finally
        {
            future.complete(log);
        }
    }

}
