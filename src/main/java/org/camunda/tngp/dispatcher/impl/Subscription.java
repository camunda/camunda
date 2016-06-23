package org.camunda.tngp.dispatcher.impl;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;
import static uk.co.real_logic.agrona.BitUtil.*;

import java.nio.ByteBuffer;

import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.BlockHandler;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.impl.log.LogBuffer;
import org.camunda.tngp.dispatcher.impl.log.LogBufferPartition;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.concurrent.status.Position;

public class Subscription
{
    protected final Position position;
    protected final LogBuffer logBuffer;
    protected final Dispatcher dispatcher;
    protected final int subscriberId;

    public Subscription(Position position, int subscriberId, Dispatcher dispatcher)
    {
        this.position = position;
        this.subscriberId = subscriberId;
        this.dispatcher = dispatcher;
        this.logBuffer = dispatcher.getLogBuffer();
    }

    public long getPosition()
    {
        return position.get();
    }

    public int poll(FragmentHandler frgHandler, int maxNumOfFragments)
    {
        int fragmentsRead = 0;

        final long currentPosition = position.get();

        final long limit = dispatcher.subscriberLimit(this);

        if (limit > currentPosition)
        {
            final int partitionId = partitionId(currentPosition);
            final int partitionOffset = partitionOffset(currentPosition);

            final LogBufferPartition partition = logBuffer.getPartition(partitionId % logBuffer.getPartitionCount());

            fragmentsRead = pollFragments(partition,
                    frgHandler,
                    maxNumOfFragments,
                    partitionId,
                    partitionOffset);
        }

        return fragmentsRead;
    }

    protected int pollFragments(
            final LogBufferPartition partition,
            final FragmentHandler frgHandler,
            final int maxNumOfFragments,
            int partitionId,
            int fragmentOffset)
    {
        final UnsafeBuffer buffer = partition.getDataBuffer();

        int fragmentsRead = 0;

        do
        {
            final int length = buffer.getIntVolatile(lengthOffset(fragmentOffset));
            if (length <= 0)
            {
                break;
            }

            final short type = buffer.getShort(typeOffset(fragmentOffset));
            if (type == TYPE_PADDING)
            {
                fragmentOffset += align(length + HEADER_LENGTH, FRAME_ALIGNMENT);

                if (fragmentOffset >= partition.getPartitionSize())
                {
                    ++partitionId;
                    fragmentOffset = 0;
                    break;
                }
            }
            else
            {
                final int streamId = buffer.getInt(streamIdOffset(fragmentOffset));
                try
                {
                    frgHandler.onFragment(buffer, messageOffset(fragmentOffset), length, streamId);
                }
                catch (RuntimeException e)
                {
                    // TODO!
                    e.printStackTrace();
                }

                fragmentOffset += align(length + HEADER_LENGTH, FRAME_ALIGNMENT);
                ++fragmentsRead;
            }
        }
        while (fragmentsRead < maxNumOfFragments);

        position.setOrdered(position(partitionId, fragmentOffset));

        return fragmentsRead;
    }

    public int pollBlock(BlockHandler blockHandler, int maxNumOfFragments, boolean isStreamAware)
    {
        return pollBlock(0, blockHandler, maxNumOfFragments, isStreamAware);
    }

    public int pollBlock(int subscriberId, BlockHandler blockHandler, int maxNumOfFragments, boolean isStreamAware)
    {
        int fragmentsRead = 0;

        final long currentPosition = position.get();

        final long limit = dispatcher.subscriberLimit(this);

        if (limit > currentPosition)
        {
            final int partitionId = partitionId(currentPosition);
            final int partitionOffset = partitionOffset(currentPosition);

            final LogBufferPartition partition = logBuffer.getPartition(partitionId % logBuffer.getPartitionCount());

            fragmentsRead = pollBlock(partition,
                    blockHandler,
                    maxNumOfFragments,
                    partitionId,
                    partitionOffset,
                    isStreamAware);
        }

        return fragmentsRead;
    }

    protected int pollBlock(
            final LogBufferPartition partition,
            final BlockHandler blockHandler,
            final int maxNumOfFragments,
            int partitionId,
            int partitionOffset,
            final boolean isStreamAware)
    {

        final UnsafeBuffer buffer = partition.getDataBuffer();
        final ByteBuffer rawBuffer = partition.getUnderlyingBuffer().getRawBuffer();
        final int bufferOffset = partition.getUnderlyingBufferOffset();
        final long blockPosition = position(partitionId, partitionOffset);

        int fragmentsRead = 0;

        final int firstFragmentOffset = partitionOffset;
        int blockLength = 0;
        int initialStreamId = -1;

        // scan buffer for block
        do
        {
            final int length = buffer.getIntVolatile(lengthOffset(partitionOffset));
            if (length <= 0)
            {
                break;
            }

            final short type = buffer.getShort(typeOffset(partitionOffset));
            if (type == TYPE_PADDING)
            {
                partitionOffset += align(length + HEADER_LENGTH, FRAME_ALIGNMENT);

                if (partitionOffset >= partition.getPartitionSize())
                {
                    ++partitionId;
                    partitionOffset = 0;
                }

                break;
            }
            else
            {
                if (isStreamAware)
                {
                    final int streamId = buffer.getInt(streamIdOffset(partitionOffset));
                    if (fragmentsRead == 0)
                    {
                        initialStreamId = streamId;
                    }
                    else
                    {
                        if (streamId != initialStreamId)
                        {
                            break;
                        }
                    }
                }

                final int alignedFrameLength = align(length + HEADER_LENGTH, FRAME_ALIGNMENT);
                partitionOffset += alignedFrameLength;
                blockLength += alignedFrameLength;
                ++fragmentsRead;
            }
        }
        while (fragmentsRead < maxNumOfFragments);

        if (fragmentsRead > 0)
        {
            final int absoluteOffset = bufferOffset + firstFragmentOffset;
            try
            {
                blockHandler.onBlockAvailable(
                        rawBuffer,
                        absoluteOffset,
                        blockLength,
                        initialStreamId,
                        blockPosition);
            }
            catch (Exception e)
            {
                // TODO!
                e.printStackTrace();
            }
        }

        position.setOrdered(position(partitionId, partitionOffset));

        return fragmentsRead;
    }

    public void close()
    {
        dispatcher.closeSubscription(this);
        position.close();
    }

    public int peekBlock(
            BlockPeek availableBlock,
            int maxBlockSize,
            boolean isStreamAware)
    {
        int bytesAvailable = 0;

        final long currentPosition = position.get();

        final long limit = dispatcher.subscriberLimit(this);

        if (limit > currentPosition)
        {
            final int partitionId = partitionId(currentPosition);
            final int partitionOffset = partitionOffset(currentPosition);

            final LogBufferPartition partition = logBuffer.getPartition(partitionId % logBuffer.getPartitionCount());

            bytesAvailable = peekBlock(partition,
                    availableBlock,
                    maxBlockSize,
                    partitionId,
                    partitionOffset,
                    isStreamAware);
        }

        return bytesAvailable;
    }

    protected int peekBlock(
            final LogBufferPartition partition,
            final BlockPeek availableBlock,
            final int maxBlockSize,
            int partitionId,
            int partitionOffset,
            final boolean isStreamAware)
    {

        final UnsafeBuffer buffer = partition.getDataBuffer();
        final int bufferOffset = partition.getUnderlyingBufferOffset();
        final ByteBuffer rawBuffer = partition.getUnderlyingBuffer().getRawBuffer();

        final int firstFragmentOffset = partitionOffset;
        int blockLength = 0;
        int initialStreamId = -1;

        // scan buffer for block
        do
        {
            final int length = buffer.getIntVolatile(lengthOffset(partitionOffset));
            if (length <= 0)
            {
                break;
            }

            final short type = buffer.getShort(typeOffset(partitionOffset));
            if (type == TYPE_PADDING)
            {
                partitionOffset += alignedLength(length);

                if (blockLength == 0)
                {
                    if (partitionOffset >= partition.getPartitionSize())
                    {
                        position.proposeMaxOrdered(position(1 + partitionId, 0));
                    }
                    else
                    {
                        position.proposeMaxOrdered(position(partitionId, partitionOffset));
                    }
                }

                break;
            }
            else
            {
                if (isStreamAware)
                {
                    final int streamId = buffer.getInt(streamIdOffset(partitionOffset));
                    if (blockLength == 0)
                    {
                        initialStreamId = streamId;
                    }
                    else
                    {
                        if (streamId != initialStreamId)
                        {
                            break;
                        }
                    }
                }

                final int alignedFrameLength = alignedLength(length);

                if (alignedFrameLength <= maxBlockSize - blockLength)
                {
                    partitionOffset += alignedFrameLength;
                    blockLength += alignedFrameLength;
                }
                else
                {
                    break;
                }
            }
        }
        while (maxBlockSize - blockLength > HEADER_LENGTH);

        if (blockLength > 0)
        {
            final int absoluteOffset = bufferOffset + firstFragmentOffset;

            availableBlock.setBlock(
                rawBuffer,
                position,
                initialStreamId,
                partitionId,
                firstFragmentOffset,
                absoluteOffset,
                blockLength);
        }

        return blockLength;
    }

    public int getSubscriberId()
    {
        return subscriberId;
    }
}
