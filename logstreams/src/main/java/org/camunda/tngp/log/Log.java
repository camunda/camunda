package org.camunda.tngp.log;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;
import static org.camunda.tngp.log.fs.LogSegmentDescriptor.*;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.appender.LogAppendHandler;
import org.camunda.tngp.log.appender.LogSegmentAllocationDescriptor;
import org.camunda.tngp.log.conductor.LogConductorCmd;
import org.camunda.tngp.log.fs.LogSegments;
import org.camunda.tngp.log.fs.ReadableLogSegment;

import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class Log implements AutoCloseable
{
    protected final ManyToOneConcurrentArrayQueue<LogConductorCmd> logConductorCmdQueue;

    protected final String name;
    protected final int id;

    protected final LogSegmentAllocationDescriptor allocationDescriptor;
    protected final LogSegments logSegments;
    protected final Dispatcher writeBuffer;
    protected final File logDirectory;

    protected final LogAppendHandler logAppendHandler;

    public Log(final LogContext logContext)
    {
        this.name = logContext.getName();
        this.id = logContext.getId();
        this.logSegments = logContext.getLogSegments();
        this.allocationDescriptor = logContext.getLogAllocationDescriptor();
        this.logAppendHandler = logContext.getLogAppendHandler();

        logDirectory = new File(logContext.getLogAllocationDescriptor().getPath());
        writeBuffer = logContext.getWriteBuffer();
        logConductorCmdQueue = logContext.getLogConductorCmdQueue();

        logAppendHandler.setLog(this);
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

    public void start()
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

    public long getLastPosition()
    {
        return logSegments.getLastPosition();
    }

    public long getFirstPosition(int segmentId)
    {
        return logSegments.getFirstPosition(segmentId);
    }

    public long getLastPosition(int segmentId)
    {
        return logSegments.getLastPosition(segmentId);
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

    public int[] getSegmentIds()
    {
        return logSegments.getSegmentIds();
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

    public File getLogDirectory()
    {
        return logDirectory;
    }

    public LogSegmentAllocationDescriptor getAllocationDescriptor()
    {
        return allocationDescriptor;
    }

    public LogSegments getLogSegments()
    {
        return logSegments;
    }

    public int getId()
    {
        return id;
    }

    public LogAppendHandler getLogAppendHandler()
    {
        return logAppendHandler;
    }
}
