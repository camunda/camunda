package org.camunda.tngp.logstreams.impl.fs;

import static org.camunda.tngp.logstreams.impl.fs.FsLogSegmentDescriptor.METADATA_LENGTH;
import static org.camunda.tngp.logstreams.impl.fs.FsLogSegmentDescriptor.SEGMENT_CAPACITY_OFFSET;
import static org.camunda.tngp.logstreams.impl.fs.FsLogSegmentDescriptor.SEGMENT_ID_OFFSET;
import static org.camunda.tngp.logstreams.impl.fs.FsLogSegmentDescriptor.SEGMENT_SIZE_OFFSET;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import org.agrona.IoUtil;
import org.agrona.LangUtil;
import org.agrona.concurrent.UnsafeBuffer;

public class FsLogSegment
{
    public static final short INVALID_ADDR = -1;

    public static final short NO_DATA = -2;

    public static final short END_OF_SEGMENT = -3;

    public static final short INSUFFICIENT_CAPACITY = -4;

    public static final short STATE_ACTIVE = 1;

    public static final short STATE_FILLED = 2;

    protected volatile short state;

    protected final String fileName;

    protected FileChannel fileChannel;

    protected UnsafeBuffer metadataSection;

    protected MappedByteBuffer mappedBuffer;

    public FsLogSegment(String fileName)
    {
        this.fileName = fileName;
    }

    public boolean openSegment(boolean create)
    {
        fileChannel = FileChannelUtil.openChannel(fileName, create);

        if (fileChannel != null)
        {
            try
            {
                mappedBuffer = fileChannel.map(MapMode.READ_WRITE, 0, METADATA_LENGTH);
                metadataSection = new UnsafeBuffer(mappedBuffer, 0, METADATA_LENGTH);
            }
            catch (IOException e)
            {
                fileChannel = null;
                metadataSection = null;
                LangUtil.rethrowUnchecked(e);
            }
        }

        return fileChannel != null;
    }

    public void closeSegment()
    {
        if (fileChannel.isOpen())
        {
            try
            {
                this.metadataSection = null;
                IoUtil.unmap(mappedBuffer);
                fileChannel.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void delete()
    {
        final File file = new File(fileName);
        if (file.exists())
        {
            file.delete();
        }
    }

    public String getFileName()
    {
        return fileName;
    }

    public int getSegmentId()
    {
        return metadataSection.getInt(SEGMENT_ID_OFFSET);
    }

    protected void setSegmentId(int segmentId)
    {
        metadataSection.putInt(SEGMENT_ID_OFFSET, segmentId);
    }

    public int getSize()
    {
        return metadataSection.getInt(SEGMENT_SIZE_OFFSET);
    }

    public int getSizeVolatile()
    {
        return metadataSection.getIntVolatile(SEGMENT_SIZE_OFFSET);
    }

    protected void setSizeOrdered(int tail)
    {
        metadataSection.putIntOrdered(SEGMENT_SIZE_OFFSET, tail);
    }

    protected void setSizeVolatile(int tail)
    {
        metadataSection.putIntVolatile(SEGMENT_SIZE_OFFSET, tail);
    }

    public int getCapacity()
    {
        return metadataSection.getInt(SEGMENT_CAPACITY_OFFSET);
    }

    protected void setCapacity(int capacity)
    {
        metadataSection.putInt(SEGMENT_CAPACITY_OFFSET, capacity);
    }

    public boolean isFilled()
    {
        return state == STATE_FILLED;
    }

    public boolean isActive()
    {
        return state == STATE_ACTIVE;
    }

    public boolean allocate(int segmentId, int segmentSize)
    {
        boolean allocated = false;

        try
        {
            final File file = new File(fileName);
            final long availableSpace = FileChannelUtil.getAvailableSpace(file.getParentFile());

            if (availableSpace > segmentSize)
            {
                openSegment(true);

                setSegmentId(segmentId);
                setCapacity(segmentSize);
                setSizeVolatile(METADATA_LENGTH);

                allocated = true;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return allocated;
    }

    /**
     * @param block
     * @return the offset at which the block was appended
     */
    public int append(final ByteBuffer block)
    {
        final int blockLength = block.remaining();
        final int currentSize = getSize();
        final int remainingCapacity = getCapacity() - currentSize;

        if (remainingCapacity < blockLength)
        {
            return INSUFFICIENT_CAPACITY;
        }

        int newSize = currentSize;

        while (newSize - currentSize < blockLength)
        {
            try
            {
                newSize += fileChannel.write(block, newSize);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                return -1;
            }
        }

        // TODO: make configurable
//        try
//        {
//            fileChannel.force(false);
//        }
//        catch (IOException e)
//        {
//            e.printStackTrace();
//            return -1;
//        }

        setSizeOrdered(newSize);

        return currentSize;
    }

    /**
     * Reads a sequence of bytes into the provided read buffer. Returns the result of the read operation
     * which is either
     * <ul>
     * <li>Number of bytes read in case of a successful read operation</li>
     * <li>{@link #NO_DATA} in case no data is available</li>
     * <li>{@link #END_OF_SEGMENT} in case the end of the segment is reached</li>
     * </ul>
     *
     * @param readBuffer the buffer to read data into
     * @param offset the offset in the buffer into which data is to be read
     * @param length the max number of bytes to read
     * @param fileOffset the offset in the file to read from
     * @return operation result
     */
    public int readBytes(ByteBuffer readBuffer, int fileOffset)
    {
        final int limit = getSizeVolatile();
        final int bufferOffset = readBuffer.position();
        final int bufferRemaining = readBuffer.remaining();

        int opResult = INVALID_ADDR;

        if (fileOffset >= METADATA_LENGTH && fileOffset <= limit)
        {
            final int available = limit - fileOffset;
            final int bytesToRead = Math.min(bufferRemaining, available);

            if (bytesToRead > 0)
            {
                readBuffer.limit(bufferOffset + bytesToRead);

                try
                {
                    opResult = fileChannel.read(readBuffer, fileOffset);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }

            }
            else if (available == 0)
            {
                opResult = isFilled() ? END_OF_SEGMENT : NO_DATA;
            }
        }

        return opResult;
    }

    public void setFilled()
    {
        // invoked by appender when segment is filled
        state = STATE_FILLED;
    }
}
