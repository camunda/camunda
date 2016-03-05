package net.long_running.dispatcher.impl.log;

import static net.long_running.dispatcher.impl.log.DataFrameDescriptor.*;
import static uk.co.real_logic.agrona.BitUtil.*;
import static uk.co.real_logic.agrona.UnsafeAccess.UNSAFE;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class LogAppender
{

    public int appendUnfragmented(
            final LogBufferPartition partition,
            final int activePartitionId,
            final DirectBuffer msg,
            final int start,
            final int length)
    {

        final int partitionSize = partition.getPartitionSize();
        final int alignedFrameLength = align(length + HEADER_LENGTH, FRAME_ALIGNMENT);

        // move the tail of the partition
        final int frameOffset = partition.getAndAddTail(alignedFrameLength);

        int newTail = frameOffset + alignedFrameLength;

        if(newTail <= (partitionSize - HEADER_LENGTH))
        {
            final UnsafeBuffer buffer = partition.getDataBuffer();

            // write negative length field
            buffer.putIntOrdered(frameLengthOffset(frameOffset), -length);
            UNSAFE.storeFence();
            buffer.putShort(typeOffset(frameOffset), TYPE_MESSAGE);
            buffer.putBytes(messageOffset(frameOffset), msg, start, length);

            // commit the message
            buffer.putIntOrdered(frameLengthOffset(frameOffset), length);
        }
        else
        {
            newTail = onEndOfPartition(partition, frameOffset);
        }

        return newTail;
    }

    public int appendFramented(
            final LogBufferPartition partition,
            final int activePartitionId,
            final DirectBuffer msg,
            final int start,
            final int length)
    {
        throw new RuntimeException("not implemented");
    }

    protected int onEndOfPartition (final LogBufferPartition partition, final int partitionOffset)
    {
        int newTail = -1;

        final int padLength = partition.getPartitionSize() - partitionOffset - HEADER_LENGTH;

        if(padLength >= 0)
        {
            // this message tripped the end of the partition, fill buffer with padding
            final UnsafeBuffer buffer = partition.getDataBuffer();
            buffer.putIntOrdered(frameLengthOffset(partitionOffset), -padLength);
            UNSAFE.storeFence();
            buffer.putShort(typeOffset(partitionOffset), TYPE_PADDING);
            buffer.putIntOrdered(frameLengthOffset(partitionOffset), padLength);

            newTail = -2;
        }

        return newTail;
    }


}
