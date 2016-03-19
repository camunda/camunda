package org.camunda.tngp.log;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;
import static org.camunda.tngp.log.fs.LogSegmentDescriptor.*;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.fs.ReadableLogSegment;

public class Log
{
    protected LogContext context;

    protected int initialSegmentId;

    public Log(final LogContext logContext)
    {
        this.context = logContext;
    }

    public Dispatcher getWriteBuffer()
    {
        return context.getWriteBuffer();
    }

    public long getInitialPosition()
    {
        final ReadableLogSegment[] segments = getSegments();

        if(segments.length > 0)
        {
            return position(segments[0].getSegmentId(), METADATA_LENGTH);
        }
        else
        {
            return -1;
        }
    }

    protected ReadableLogSegment[] getSegments()
    {
        return context.getReadableSegments();
    }

    public long pollFragment(long position, LogFragmentHandler fragmentHandler)
    {
        final ReadableLogSegment[] segments = getSegments();
        final int segmentId = partitionId(position) - initialSegmentId;
        final int segmentOffset = partitionOffset(position);

        long nextPosition = -1;

        if(0 <= segmentId && segmentId < segments.length)
        {
            int nextOffset = segments[segmentId].pollFragment(segmentOffset, fragmentHandler);

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
        final ReadableLogSegment[] segments = getSegments();
        final int segmentId = partitionId(position) - initialSegmentId;
        final int segmentOffset = partitionOffset(position);

        long nextPosition = -1;

        if(0 <= segmentId && segmentId < segments.length)
        {
            final int blockLength = segments[segmentId].pollBlock(segmentOffset, blockHandler, maxBlockSize);

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
