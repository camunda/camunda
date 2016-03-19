package org.camunda.tngp.log.appender;

import java.io.File;
import java.nio.channels.FileChannel;

import org.camunda.tngp.log.util.FileChannelUtil;

/**
 * The appender's view of a log fragment
 */
public class AppendableSegment
{
    static final int STATE_NEW = 0;
    static final int STATE_ALLOCATED = 1;
    static final int STATE_ALLOCATION_FAILED = 2;
    static final int STATE_ACTIVE = 3;
    static final int STATE_FILLED = 4;

    protected final SegmentAllocationDescriptor allocationDescriptor;

    protected final int segmentId;

    protected final int segmentSize;

    protected volatile int state = STATE_NEW;

    protected FileChannel fileChannel;

    protected int tailPosition;

    public AppendableSegment(int fragmentId, SegmentAllocationDescriptor allocationDescriptor)
    {
        this.segmentId = fragmentId;
        this.allocationDescriptor = allocationDescriptor;
        this.segmentSize = allocationDescriptor.fragmentSize;
        this.tailPosition = 0;
    }

    public FileChannel getFileChannel()
    {
        return fileChannel;
    }

    public int getSegmentId()
    {
        return segmentId;
    }

    public int getTailPosition()
    {
        return tailPosition;
    }

    public int getSegmentSize()
    {
        return segmentSize;
    }

    public void setTailPosition(int tailPosition)
    {
        this.tailPosition = tailPosition;
    }

    public void allocate()
    {
        int newState = STATE_ALLOCATION_FAILED;
        try
        {
            final long availableSpace = FileChannelUtil.getAvailableSpace(new File(allocationDescriptor.path));
            if(availableSpace > segmentSize)
            {
                openChannel();
                newState = STATE_ALLOCATED;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            state = newState;
        }
    }

    protected void openChannel()
    {
        final String path = allocationDescriptor.getPath();
        final String nameTemplate = allocationDescriptor.getFragmentFileNameTemplate();
        fileChannel = FileChannelUtil.openChannel(path, nameTemplate, segmentId);
    }

    public void setStateVolatile(int state)
    {
        this.state = state;
    }

    public int getStateVolatile()
    {
        return state;
    }

}
