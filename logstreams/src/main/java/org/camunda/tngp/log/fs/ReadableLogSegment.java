package org.camunda.tngp.log.fs;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

import org.camunda.tngp.log.LogBlockHandler;
import org.camunda.tngp.log.LogFragmentHandler;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

/**
 * not threadsafe, supports single reader
 *
 */
public class ReadableLogSegment extends LogSegment
{
    protected final ByteBuffer headerBuffer;
    protected final UnsafeBuffer header;

    protected boolean isFilled = false;

    public ReadableLogSegment(String fileName)
    {
        super(fileName);
        headerBuffer = ByteBuffer.allocateDirect(HEADER_LENGTH);
        header = new UnsafeBuffer(headerBuffer);
    }

    public int pollFragment(
            final int offset,
            final LogFragmentHandler fragmentHandler)
    {

        final int limit = getReaderLimit();

        int nextOffset = -1;

        if(offset < limit)
        {
            final int read = readHeader(offset, fileChannel, headerBuffer);

            if(read == HEADER_LENGTH)
            {
                final int fragmentLength = header.getInt(lengthOffset(0));
                final short type = header.getShort(typeOffset(0));

                if(type == TYPE_MESSAGE)
                {
                    nextOffset = notifyFragmentHandler(
                            offset,
                            fragmentHandler,
                            fragmentLength);
                }
                if(type == TYPE_PADDING)
                {
                    nextOffset = -2;
                }
            }
        }

        return nextOffset;
    }


    public int pollBlock(
            int offset,
            final LogBlockHandler blockHandler,
            final int maxBlockSize)
    {
        final int limit = getReaderLimit();

        int blockLength = 0;

        while(offset + blockLength < limit && blockLength < maxBlockSize)
        {
            int read = readHeader(offset, fileChannel, headerBuffer);
            if(read == HEADER_LENGTH)
            {
                final int fragLength = header.getInt(lengthOffset(0));
                final short fragType = header.getShort(typeOffset(0));

                if(fragType == TYPE_MESSAGE)
                {
                    blockLength += alignedLength(fragLength);
                }
                else
                {
                    if(blockLength == 0)
                    {
                        blockLength = -2;
                    }
                    break;
                }

            }
            else
            {
                blockLength = -1;
                break;
            }
        }


        if(blockLength > 0)
        {
            try
            {
                final int segmentId = getSegmentId();
                final long blockPosition = position(segmentId, offset);

                blockHandler.onBlock(
                        blockPosition,
                        fileChannel,
                        offset,
                        blockLength);
            }
            catch(Exception e)
            {
                blockLength = -1;
                e.printStackTrace();
            }
        }

        return blockLength;
    }

    protected int notifyFragmentHandler(
            final int offset,
            final LogFragmentHandler fragmentHandler,
            final int fragmentLength)
    {
        final int segmentId = getSegmentId();
        final int fragmentOffset = messageOffset(offset);
        final long fragmentPosition = position(segmentId, offset);

        int nextOffset = -1;

        try
        {
            fragmentHandler.onFragment(
                    fragmentPosition,
                    fileChannel,
                    fragmentOffset,
                    fragmentLength);

            nextOffset = alignedLength(fragmentLength);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return nextOffset;
    }

    protected static int readHeader(
            final int offset,
            final FileChannel fileChannel,
            final ByteBuffer buffer)
    {
        int bytesRead = -1;

        try
        {
            buffer.clear();
            bytesRead = fileChannel.read(buffer, offset);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return bytesRead;
    }

    private int getReaderLimit()
    {
        if(isFilled)
        {
            return getSegmentSize();
        }
        else
        {
            int tail = getTailVolatile();
            if(tail == getSegmentSize())
            {
                isFilled = true;
            }
            return tail;
        }
    }
}
