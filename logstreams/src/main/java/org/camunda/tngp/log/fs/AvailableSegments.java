package org.camunda.tngp.log.fs;

import static org.camunda.tngp.log.fs.LogSegmentDescriptor.*;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;

public class AvailableSegments
{
    protected int initalSegmentId = -1;

    protected ReadableLogSegment[] segments = new ReadableLogSegment[0];

    protected volatile boolean isInitialized = false;

    public void init(int initalSegmentId, ReadableLogSegment[] initialSegments)
    {
        this.segments = initialSegments;
        this.initalSegmentId = initalSegmentId;

        // volatile store flushes both stores above
        isInitialized = true;
    }

    /**
     * invoked by the conductor after a new segment has been allocated
     */
    public void addSegment(ReadableLogSegment segment)
    {
        final ReadableLogSegment[] newSegments = new ReadableLogSegment[segments.length + 1];

        System.arraycopy(segments, 0, newSegments, 0, segments.length);
        newSegments[segments.length] = segment;

        // lazy set the new segment array
        segments = newSegments;
    }

    public ReadableLogSegment getSegment(int segmentId)
    {
        final ReadableLogSegment[] segments = this.segments;

        final int segmentIdx = segmentId - initalSegmentId;

        if(0 <= segmentIdx && segmentIdx < segments.length)
        {
            return segments[segmentIdx];
        }
        else
        {
            return null;
        }
    }

    public long getInitialPosition()
    {
        long initialPosition = -1;

        if(isInitialized)
        {
            final ReadableLogSegment[] segments = this.segments;

            if(segments.length > 0)
            {
                initialPosition = position(segments[0].getSegmentId(), METADATA_LENGTH);
            }

        }
        return initialPosition;
    }

    public boolean isInitialized()
    {
        return isInitialized;
    }

    public void closeAll()
    {
        final ReadableLogSegment[] segments = this.segments;
        this.segments = new ReadableLogSegment[0];
        this.isInitialized = false;

        for (ReadableLogSegment readableLogSegment : segments)
        {
            readableLogSegment.closeSegment();
        }
    }
}
