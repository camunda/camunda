package org.camunda.tngp.logstreams.impl;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.logstreams.spi.ReadResultProcessor;

import java.nio.ByteBuffer;

import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.*;
import static org.camunda.tngp.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;

/**
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class CompleteEventsInBlockProcessor implements ReadResultProcessor
{
    protected final MutableDirectBuffer directBuffer = new UnsafeBuffer(0, 0);
    protected long lastReadEventPosition = -1;

    public long getLastReadEventPosition()
    {
        return lastReadEventPosition;
    }

    @Override
    public int process(ByteBuffer byteBuffer, int readResult)
    {
        if (byteBuffer.capacity() < HEADER_BLOCK_LENGTH)
        {
            readResult = (int) OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
        }

        directBuffer.wrap(byteBuffer);

        readResult = calculateCorrectReadResult(readResult, byteBuffer.position() - readResult);

        if (readResult >= HEADER_BLOCK_LENGTH)
        {
            byteBuffer.position(readResult);
            byteBuffer.limit(readResult);
        }
        else
        {
            byteBuffer.clear();
        }
        return readResult;
    }

    /**
     * Iterates over the given logged events and calculates
     * the correct bytes which are read. This means if an event is not read completely it must be excluded.
     * For that the readResult will be decreased by the fragment length of the logged event.
     *
     * @param readResult  the given read result, count of bytes which was read
     * @param position the current position in the buffer
     * @return the calculated read result
     */
    private int calculateCorrectReadResult(int readResult, int position)
    {
        int remainingBytes = readResult;
        while (remainingBytes >= HEADER_BLOCK_LENGTH)
        {
            final int fragmentLength = getFragmentLength(directBuffer, position);

            if (fragmentLength <= remainingBytes)
            {
                lastReadEventPosition = getPosition(directBuffer, position);
                remainingBytes -= fragmentLength;
                position += fragmentLength;
            }
            else
            {
                if (fragmentLength > directBuffer.capacity())
                {
                    readResult = (int) OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
                }
                else
                {
                    readResult -= remainingBytes;
                }
                remainingBytes = 0;
            }
        }

        if (remainingBytes != 0)
        {
            readResult -= remainingBytes;
        }

        return readResult;
    }
}
