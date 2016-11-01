package org.camunda.tngp.logstreams.impl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreamFailureListener;

public class StreamImpl implements LogStream
{
    protected final int streamId;
    protected final Dispatcher writeBuffer;
    protected final StreamContext context;

    protected final LogBlockIndex blockIndex;

    protected long firstFailedPosition;
    protected final LogStreamController logStreamController;

    public StreamImpl(StreamContext logContext)
    {
        this.context = logContext;
        streamId = logContext.getLogId();
        writeBuffer = logContext.getWriteBuffer();
        blockIndex = logContext.getBlockIndex();
        logStreamController = logContext.getLogStreamController();
    }

    public StreamContext getContext()
    {
        return context;
    }

    @Override
    public int getId()
    {
        return context.getLogId();
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
    public void open()
    {
        try
        {
            openAsync().get();
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
    public CompletableFuture<Void> openAsync()
    {
        return logStreamController.openAsync();
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
    public CompletableFuture<Void> closeAsync()
    {
        return logStreamController.closeAsync();
    }

    @Override
    public void registerFailureListener(LogStreamFailureListener listener)
    {
        logStreamController.registerFailureListener(listener);
    }

    @Override
    public void removeFailureListener(LogStreamFailureListener listener)
    {
        logStreamController.removeFailureListener(listener);
    }
}
