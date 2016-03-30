package org.camunda.tngp.log;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;
import static org.camunda.tngp.log.fs.LogSegmentDescriptor.*;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.conductor.LogConductorCmd;
import org.camunda.tngp.log.fs.LogSegments;
import org.camunda.tngp.log.fs.ReadableLogSegment;

import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

public class Log
{
    protected final OneToOneConcurrentArrayQueue<LogConductorCmd> logConductorCmdQueue;

    protected final LogSegments logSegments;

    protected final Dispatcher writeBuffer;

    public Log(final LogContext logContext)
    {
        this.logSegments = logContext.getLogSegments();
        writeBuffer = logContext.getWriteBuffer();
        logConductorCmdQueue = logContext.getLogConductorCmdQueue();
    }

    public CompletableFuture<Log> startAsync()
    {
        final CompletableFuture<Log> future = new CompletableFuture<>();

        logConductorCmdQueue.add((c) ->
        {
            c.openLog(future, this);
        });

        return future;
    }

    public void start() throws InterruptedException
    {
        startAsync().join();
    }

    public CompletableFuture<Log> closeAsync()
    {
        final CompletableFuture<Log> future = new CompletableFuture<>();

        logConductorCmdQueue.add((c) ->
        {
            c.closeLog(this, future);
        });

        return future;
    }

    public void close()
    {
        closeAsync().join();
    }

    public Dispatcher getWriteBuffer()
    {
        return writeBuffer;
    }

    public long getInitialPosition()
    {
        return logSegments.getInitialPosition();
    }

    public long pollFragment(long position, LogFragmentHandler fragmentHandler)
    {
        final int segmentId = partitionId(position);
        final int segmentOffset = partitionOffset(position);
        final ReadableLogSegment segment = logSegments.getSegment(segmentId);

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
        final ReadableLogSegment segment = logSegments.getSegment(segmentId);

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
