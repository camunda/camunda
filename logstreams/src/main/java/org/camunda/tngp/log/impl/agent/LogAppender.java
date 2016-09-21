package org.camunda.tngp.log.impl.agent;


import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.impl.LogContext;
import org.camunda.tngp.log.impl.LogImpl;

public class LogAppender implements Agent, Consumer<LogAppenderCmd>
{
    private static final String NAME = "log-appender";

    protected final ManyToOneConcurrentArrayQueue<LogAppenderCmd> cmdQueue;

    protected final Int2ObjectHashMap<LogContext> logCtxById = new Int2ObjectHashMap<>();

    protected Subscription appenderSubsciption;

    protected final BlockPeek blockPeek;

    protected int maxAppendSize = 1024 * 1024 * 16;

    public LogAppender(LogAgentContext context)
    {
        cmdQueue = context.getAppenderCmdQueue();
        appenderSubsciption = context.getWriteBuffer().getSubscriptionByName("log-appender");
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

        if (bytesAvailable > 0)
        {
            final LogContext logContext = logCtxById.get(blockPeek.getStreamId());

            if (logContext != null)
            {
                final LogAppendHandler logAppendHandler = logContext.getLogAppendHandler();
                bytesWritten = logAppendHandler.append(blockPeek);
            }
        }

        return bytesWritten;
    }

    @Override
    public void accept(LogAppenderCmd t)
    {
        t.execute(this);
    }

    public void onLogOpened(LogImpl log, CompletableFuture<Log> future)
    {
        try
        {
            final LogContext logContext = log.getLogContext();
            this.logCtxById.put(logContext.getLogId(), logContext);
            future.complete(log);
        }
        catch (Exception e)
        {
            future.completeExceptionally(e);
        }
    }

    public void onLogClosed(LogImpl log, CompletableFuture<Void> future)
    {
        try
        {
            final LogContext logContext = log.getLogContext();
            this.logCtxById.remove(logContext.getLogId());
            future.complete(null);
        }
        catch (Exception e)
        {
            future.completeExceptionally(e);
        }
    }

}
