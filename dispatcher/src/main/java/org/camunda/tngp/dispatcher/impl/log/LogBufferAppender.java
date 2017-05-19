package org.camunda.tngp.dispatcher.impl.log;

import static org.agrona.BitUtil.align;
import static org.agrona.UnsafeAccess.UNSAFE;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.FRAME_ALIGNMENT;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.TYPE_MESSAGE;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.TYPE_PADDING;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.typeOffset;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.ClaimedFragmentBatch;


public class LogBufferAppender
{
    public static final int RESULT_PADDING_AT_END_OF_PARTITION = -2;
    public static final int RESULT_END_OF_PARTITION = -1;

    @SuppressWarnings("restriction")
    public int appendFrame(
            final LogBufferPartition partition,
            final int activePartitionId,
            final DirectBuffer msg,
            final int start,
            final int length,
            final int streamId)
    {
        final int partitionSize = partition.getPartitionSize();
        final int alignedFrameLength = alignedLength(length);

        // move the tail of the partition
        final int frameOffset = partition.getAndAddTail(alignedFrameLength);

        int newTail = frameOffset + alignedFrameLength;

        if (newTail <= (partitionSize - HEADER_LENGTH))
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

    @SuppressWarnings("restriction")
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

        if (newTail <= (partitionSize - HEADER_LENGTH))
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

    public int claim(
            final LogBufferPartition partition,
            final int activePartitionId,
            final ClaimedFragmentBatch batch,
            final int fragmentCount,
            final int batchLength)
    {
        final int partitionSize = partition.getPartitionSize();
        // reserve enough space for frame alignment because each batch fragment must start on an aligned position
        final int framedMessageLength = batchLength + fragmentCount * (HEADER_LENGTH + FRAME_ALIGNMENT) + FRAME_ALIGNMENT;
        final int alignedFrameLength = align(framedMessageLength, FRAME_ALIGNMENT);

        // move the tail of the partition
        final int frameOffset = partition.getAndAddTail(alignedFrameLength);

        int newTail = frameOffset + alignedFrameLength;

        if (newTail <= (partitionSize - HEADER_LENGTH))
        {
            final UnsafeBuffer buffer = partition.getDataBuffer();
            // all fragment data are written using the claimed batch
            batch.wrap(buffer, activePartitionId, frameOffset, alignedFrameLength);
        }
        else
        {
            newTail = onEndOfPartition(partition, frameOffset);
        }

        return newTail;
    }

    @SuppressWarnings("restriction")
    protected int onEndOfPartition(final LogBufferPartition partition, final int partitionOffset)
    {
        int newTail = RESULT_END_OF_PARTITION;

        final int padLength = partition.getPartitionSize() - partitionOffset - HEADER_LENGTH;

        if (padLength >= 0)
        {
            // this message tripped the end of the partition, fill buffer with padding
            final UnsafeBuffer buffer = partition.getDataBuffer();
            buffer.putIntOrdered(lengthOffset(partitionOffset), -padLength);
            UNSAFE.storeFence();
            buffer.putShort(typeOffset(partitionOffset), TYPE_PADDING);
            buffer.putIntOrdered(lengthOffset(partitionOffset), padLength);

            newTail = RESULT_PADDING_AT_END_OF_PARTITION;
        }

        return newTail;
    }


}
