package net.long_running.dispatcher.impl.log;

import static net.long_running.dispatcher.impl.log.DataFrameDescriptor.*;
import static uk.co.real_logic.agrona.BitUtil.*;
import static uk.co.real_logic.agrona.UnsafeAccess.UNSAFE;

import net.long_running.dispatcher.ClaimedFragment;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class LogAppender
{

    public int appendFrame(
            final LogBufferPartition partition,
            final int activePartitionId,
            final DirectBuffer msg,
            final int start,
            final int length,
            final int streamId)
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
            buffer.putIntOrdered(lengthOffset(frameOffset), -length);
            UNSAFE.storeFence();
            buffer.putShort(typeOffset(frameOffset), TYPE_MESSAGE);
            buffer.putInt(streamIdOffset(frameOffset), streamId);
            buffer.putBytes(messageOffset(frameOffset), msg, start, length);

            // commit the message
            buffer.putIntOrdered(lengthOffset(frameOffset), length);
        }
        else
        {
            newTail = onEndOfPartition(partition, frameOffset);
        }

        return newTail;
    }

    public int claim(
            final LogBufferPartition partition,
            final int activePartitionId,
            final ClaimedFragment claim,
            final int length,
            final int streamId)
    {
        final int partitionSize = partition.getPartitionSize();
        final int framedMessageLength = length + HEADER_LENGTH;
        final int alignedFrameLength = align(framedMessageLength, FRAME_ALIGNMENT);

        // move the tail of the partition
        final int frameOffset = partition.getAndAddTail(alignedFrameLength);

        int newTail = frameOffset + alignedFrameLength;

        if(newTail <= (partitionSize - HEADER_LENGTH))
        {
            final UnsafeBuffer buffer = partition.getDataBuffer();

            // write negative length field
            buffer.putIntOrdered(lengthOffset(frameOffset), -length);
            UNSAFE.storeFence();
            buffer.putShort(typeOffset(frameOffset), TYPE_MESSAGE);
            buffer.putInt(streamIdOffset(frameOffset), streamId);

            claim.wrap(buffer, frameOffset, framedMessageLength);

            // Do not commit the message
        }
        else
        {
            newTail = onEndOfPartition(partition, frameOffset);
        }

        return newTail;
    }

    protected int onEndOfPartition (final LogBufferPartition partition, final int partitionOffset)
    {
        int newTail = -1;

        final int padLength = partition.getPartitionSize() - partitionOffset - HEADER_LENGTH;

        if(padLength >= 0)
        {
            // this message tripped the end of the partition, fill buffer with padding
            final UnsafeBuffer buffer = partition.getDataBuffer();
            buffer.putIntOrdered(lengthOffset(partitionOffset), -padLength);
            UNSAFE.storeFence();
            buffer.putShort(typeOffset(partitionOffset), TYPE_PADDING);
            buffer.putIntOrdered(lengthOffset(partitionOffset), padLength);

            newTail = -2;
        }

        return newTail;
    }


}
