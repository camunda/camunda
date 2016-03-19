package org.camunda.tngp.log;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;
import static org.camunda.tngp.log.fs.LogSegmentDescriptor.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.conductor.LogConductorCmd;
import org.camunda.tngp.log.fs.AvailableSegments;
import org.camunda.tngp.log.fs.ReadableLogSegment;

import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

public class Log
{
    protected final OneToOneConcurrentArrayQueue<LogConductorCmd> logConductorCmdQueue;

    protected final AvailableSegments availableSegments;

    protected final Dispatcher writeBuffer;

    public Log(final LogContext logContext)
    {
        this.availableSegments = logContext.getAvailableSegments();
        writeBuffer = logContext.getWriteBuffer();
        logConductorCmdQueue = logContext.getLogConductorCmdQueue();
    }

    public CompletableFuture<Log> start()
    {
        final CompletableFuture<Log> future = new CompletableFuture<>();

        logConductorCmdQueue.add((c) ->
        {
            c.openLog(future, this);
        });

        return future;
    }

    public void startSync() throws InterruptedException
    {
        try
        {
            start().get();
        }
        catch (ExecutionException e)
        {
            LangUtil.rethrowUnchecked((Exception) e.getCause());
        }
    }

    public CompletableFuture<Boolean> close()
    {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();

        logConductorCmdQueue.add((c) ->
        {
            c.closeLog(future);
        });

        return future;
    }

    public boolean closeSync() throws InterruptedException
    {
        boolean success = false;

        try
        {
            success = close().get();
        }
        catch (ExecutionException e)
        {
            LangUtil.rethrowUnchecked((Exception) e.getCause());
        }

        return success;
    }

    public Dispatcher getWriteBuffer()
    {
        return writeBuffer;
    }

    public long getInitialPosition()
    {
        return availableSegments.getInitialPosition();
    }

    public long pollFragment(long position, LogFragmentHandler fragmentHandler)
    {
        final int segmentId = partitionId(position);
        final int segmentOffset = partitionOffset(position);
        final ReadableLogSegment segment = availableSegments.getSegment(segmentId);

        long nextPosition = -1;

        if(null != segment)
        {
            final int nextOffset = segment.pollFragment(segmentOffset, fragmentHandler);

            if(nextOffset > segmentOffset)
            {
                nextPosition = position(segmentId, nextOffset);
            }
            else if(nextOffset == -2)
            {
                nextPosition = position(1 + segmentId, METADATA_LENGTH);
            }
        }

        return nextPosition;

    }

    public long pollBlock(long position, LogBlockHandler blockHandler, int maxBlockSize)
    {
        final int segmentId = partitionId(position);
        final int segmentOffset = partitionOffset(position);
        final ReadableLogSegment segment = availableSegments.getSegment(segmentId);

        long nextPosition = -1;

        if(null != segment)
        {
            final int blockLength = segment.pollBlock(segmentOffset, blockHandler, maxBlockSize);

            if(blockLength > 0)
            {
                nextPosition = position(segmentId, segmentOffset + blockLength);
            }
            else if(blockLength == -2)
            {
                nextPosition = position(1 + segmentId, METADATA_LENGTH);
            }
        }

        return nextPosition;
    }

}
