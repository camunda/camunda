package org.camunda.tngp.log.fs;

import static org.camunda.tngp.log.fs.LogSegmentDescriptor.*;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;

public class LogSegments
{
    protected int initalSegmentId = -1;

    protected ReadableLogSegment[] segments = new ReadableLogSegment[0];

    protected volatile int segmentCount = 0;

    public void init(int initalSegmentId, ReadableLogSegment[] initialSegments)
    {
        this.segments = initialSegments;
        this.initalSegmentId = initalSegmentId;
        this.segmentCount = initialSegments.length; // volatile store
    }

    /**
     * invoked by the conductor after a new segment has been allocated
     */
    public void addSegment(ReadableLogSegment segment)
    {
        final ReadableLogSegment[] newSegments = new ReadableLogSegment[segments.length + 1];

        System.arraycopy(segments, 0, newSegments, 0, segments.length);
        newSegments[segments.length] = segment;
        this.segments = newSegments;

        this.segmentCount = newSegments.length; // volatile store
    }

    public ReadableLogSegment getSegment(int segmentId)
    {
        final ReadableLogSegment[] segments = this.segments;

        final int segmentIdx = segmentId - initalSegmentId;

        if (0 <= segmentIdx && segmentIdx < segmentCount)
        {
            return segments[segmentIdx];
        }
        else
        {
            return null;
        }
    }

    public int getSegmentCount()
    {
        return segmentCount;
    }

    public long getInitialPosition()
    {
        long initialPosition = -1;

        if (segmentCount > 0)
        {
            final ReadableLogSegment[] segments = this.segments;
            initialPosition = position(segments[0].getSegmentId(), METADATA_LENGTH);

        }
        return initialPosition;
    }

    public long getLastPosition()
    {
        final int segmentCount = this.segmentCount; // volatile load

        long lastPosition = -1;

        if (segmentCount > 0)
        {
            // We typically have an additional segment on top of the segment
            // that is currently written to. When the current segment is full,
            // we can quickly exchange the last segment with that spare, while allocating
            // a new file in parallel => we use segmentCount - 2 to determine the current segment.
            // During startup, the second fragment may not be allocated yet, which is why we have the max expression.
            final int segmentIndex = Math.max(0, segmentCount - 2);

            final ReadableLogSegment lastSegment = this.segments[segmentIndex];
            lastPosition = position(lastSegment.getSegmentId(), lastSegment.getTailVolatile());
        }
        return lastPosition;
    }

    public boolean isInitialized()
    {
        return segmentCount > 0;
    }

    public void closeAll()
    {
        final ReadableLogSegment[] segments = this.segments;
        this.segments = new ReadableLogSegment[0];
        this.segmentCount = 0;

        for (ReadableLogSegment readableLogSegment : segments)
        {
            readableLogSegment.closeSegment();
        }
    }

    public int[] getSegmentIds()
    {
        final int[] segmentIds = new int[segmentCount];

        for (int i = 0; i < segmentIds.length; i++)
        {
            segmentIds[i] = segments[i].getSegmentId();
        }

        return segmentIds;
    }

    public long getFirstPosition(int segmentId)
    {
        final ReadableLogSegment segment = getSegment(segmentId);

        if (segment != null)
        {
            return position(segment.getSegmentId(), METADATA_LENGTH);
        }

        return  -1;
    }

    public long getLastPosition(int segmentId)
    {
        final ReadableLogSegment segment = getSegment(segmentId);

        if (segment != null)
        {
            return position(segment.getSegmentId(), segment.getTailVolatile());
        }

        return  -1;
    }
}
