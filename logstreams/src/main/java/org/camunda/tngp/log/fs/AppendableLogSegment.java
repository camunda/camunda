package org.camunda.tngp.log.fs;

import java.io.File;

import static org.camunda.tngp.log.fs.LogSegmentDescriptor.*;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.camunda.tngp.log.util.FileChannelUtil;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

/**
 * The appender's view of the log segment
 */
public class AppendableLogSegment extends LogSegment
{
    final static ByteBuffer PADDING_BUFFER = ByteBuffer.allocateDirect(HEADER_LENGTH);
    final static UnsafeBuffer PADDING = new UnsafeBuffer(PADDING_BUFFER);

    static
    {
        PADDING.putShort(typeOffset(0), TYPE_PADDING);
    }

    public AppendableLogSegment(String fileName)
    {
        super(fileName);
    }

    public FileChannel getFileChannel()
    {
        return fileChannel;
    }

    public boolean allocate(int segmentId, int segmentSize)
    {
        boolean allocated = false;

        try
        {
            final File file = new File(fileName);
            final long availableSpace = FileChannelUtil.getAvailableSpace(file.getParentFile());

            if(availableSpace > segmentSize)
            {
                openSegment(true);

                setSegmentId(segmentId);
                setSegmentSize(segmentSize);
                setTailVolatile(METADATA_LENGTH);

                allocated = true;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return allocated;
    }

    @Override
    public void closeSegment()
    {
        super.closeSegment();
    }

    public int append(final ByteBuffer block)
    {
        final int segmentSize = getSegmentSize();

        final int blockLength = block.remaining();
        final int currentTail = getTail();

        int newTail = -1;

        if(currentTail + blockLength + HEADER_LENGTH  <= segmentSize)
        {
            if(writeToChannel(currentTail, block) == blockLength)
            {
                newTail = currentTail + blockLength;
                setTailOrdered(newTail);
            }
        }
        else
        {
            newTail = onSegmentFilled(currentTail);
        }

        return newTail;
    }

    protected int onSegmentFilled(int currentTail)
    {
        final int padSize = getSegmentSize() - currentTail - HEADER_LENGTH;

        PADDING.putInt(lengthOffset(0), padSize);
        PADDING_BUFFER.clear();

        int newTail = -1;

        if(writeToChannel(currentTail, PADDING_BUFFER) == HEADER_LENGTH)
        {
            setTailVolatile(getSegmentSize());
            newTail = -2;
        }

        return newTail;
    }

    protected int writeToChannel(final int position, ByteBuffer buff)
    {
        int bytesWritten = -1;

        try
        {
            int written = fileChannel.write(buff, position);

            // TODO: fsync?
//            fileChannel.force(false);

            bytesWritten = written;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return bytesWritten;
    }
}
