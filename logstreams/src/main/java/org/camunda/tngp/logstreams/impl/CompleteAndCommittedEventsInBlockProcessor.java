package org.camunda.tngp.logstreams.impl;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicLongPosition;
import org.agrona.concurrent.status.Position;
import org.camunda.tngp.logstreams.spi.ReadResultProcessor;

import java.nio.ByteBuffer;

import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.logstreams.impl.LogEntryDescriptor.*;
import static org.camunda.tngp.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;

/**
 * @author Christopher Zell <christopher.zell@camunda.com>
 */
public class CompleteAndCommittedEventsInBlockProcessor implements ReadResultProcessor
{
    public static final int POSITION_LENGTH = positionOffset(messageOffset(0)) + SIZE_OF_LONG;

    protected final MutableDirectBuffer directBuffer = new UnsafeBuffer(0, 0);
    protected final Position commitPosition = new AtomicLongPosition();

    public long getCommitPosition()
    {
        return commitPosition.get();
    }

    public void setCommitPosition(long commitPosition)
    {
        this.commitPosition.setOrdered(commitPosition);
    }

    @Override
    public int process(ByteBuffer byteBuffer, int readResult)
    {
        if (byteBuffer.capacity() < POSITION_LENGTH)
        {
            readResult = (int) OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
        }

        directBuffer.wrap(byteBuffer);

        readResult = calculateCorrectReadResult(readResult, byteBuffer.position() - readResult);

        if (readResult >= POSITION_LENGTH)
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
     * the correct bytes which are read. This means if an event is not read completely or
     * the event has not a committed position is must be excluded. For that the readResult will be
     * decreased by the fragment length of the logged event.
     *
     * @param readResult  the given read result, count of bytes which was read
     * @param position the current position in the buffer
     * @return the calculated read result
     */
    private int calculateCorrectReadResult(int readResult, int position)
    {
        int remainingBytes = readResult;
        while (remainingBytes >= POSITION_LENGTH)
        {
            final int fragmentLength = getFragmentLength(directBuffer, position);
            final long loggedEventPosition = getPosition(directBuffer, position);

            if (fragmentLength <= remainingBytes && loggedEventPosition <= commitPosition.get())
            {
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
