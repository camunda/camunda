package org.camunda.tngp.log.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.impl.agent.LogConductorCmd;

public class LogImpl implements Log
{
    protected final int logId;
    protected final Dispatcher writeBuffer;
    protected final LogContext logContext;

    protected final ClaimedFragment writeFragment = new ClaimedFragment();
    protected final ManyToOneConcurrentArrayQueue<LogConductorCmd> toConductorCmdQueue;
    protected final LogBlockIndex blockIndex;

    public LogImpl(LogContext logContext)
    {
        this.logContext = logContext;
        logId = logContext.getLogId();
        writeBuffer = logContext.getWriteBuffer();
        toConductorCmdQueue = logContext.getToConductorCmdQueue();
        blockIndex = logContext.getBlockIndex();
    }

    public LogContext getLogContext()
    {
        return logContext;
    }

    @Override
    public int getId()
    {
        return logContext.getLogId();
    }

    @Override
    public long getInitialPosition()
    {
        if (blockIndex.size() >= 1)
        {
            return blockIndex.getLogPosition(0);
        }
        else
        {
            return -1;
        }
    }

    @Override
    public void close()
    {
        try
        {
            closeAsync().get();
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Future<Void> closeAsync()
    {
        final CompletableFuture<Void> f = new CompletableFuture<>();

        toConductorCmdQueue.add((c) ->
        {
            c.closeLog(this, f);
        });

        return f;
    }
}
