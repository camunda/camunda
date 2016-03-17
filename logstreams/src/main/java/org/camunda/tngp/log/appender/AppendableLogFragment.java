package org.camunda.tngp.log.appender;

import static java.lang.Math.max;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.camunda.tngp.log.util.FileChannelUtil;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

/**
 * The appender's view on a log fragment
 */
public class AppendableLogFragment
{

    static ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(1024 * 1024);

    static
    {
        // fill buffer with 0s
        new UnsafeBuffer(EMPTY_BUFFER)
            .setMemory(0, EMPTY_BUFFER.capacity(), (byte)0);
    }

    static final int STATE_NEW = 0;
    static final int STATE_ALLOCATED = 1;
    static final int STATE_ALLOCATION_FAILED = 2;
    static final int STATE_ACTIVE = 3;
    static final int STATE_FILLED = 4;

    protected final LogAllocationDescriptor allocationDescriptor;

    protected final int fragmentId;

    protected final int fragmentSize;

    protected volatile int state = STATE_NEW;

    protected FileChannel fileChannel;

    protected int tailPosition;

    public AppendableLogFragment(int fragmentId, LogAllocationDescriptor allocationDescriptor)
    {
        this.fragmentId = fragmentId;
        this.allocationDescriptor = allocationDescriptor;
        this.fragmentSize = allocationDescriptor.fragmentSize;
        this.tailPosition = 0;
    }

    public FileChannel getFileChannel()
    {
        return fileChannel;
    }

    public int getFragmentId()
    {
        return fragmentId;
    }

    public int getTailPosition()
    {
        return tailPosition;
    }

    public int getFragmentSize()
    {
        return fragmentSize;
    }

    public void setTailPosition(int tailPosition)
    {
        this.tailPosition = tailPosition;
    }

    public void allocate()
    {
        int newState = STATE_ALLOCATION_FAILED;
        try {

            final long availableSpace = FileChannelUtil.getAvailableSpace(new File(allocationDescriptor.path));

            if(availableSpace > fragmentSize)
            {
                openChannel();

                // fill file with 0s
//                for (int pos = 0; pos < fragmentSize; pos += EMPTY_BUFFER.capacity())
//                {
//                    EMPTY_BUFFER.clear();
//                    EMPTY_BUFFER.limit(Math.min(EMPTY_BUFFER.capacity(), fragmentSize - pos));
//                    fileChannel.write(EMPTY_BUFFER, pos);
//                }

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

        fileChannel = FileChannelUtil.openChannel(path, nameTemplate, fragmentId);
    }

    public void proposeMaxTail(int newTail)
    {
        tailPosition = max(tailPosition, newTail);
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
