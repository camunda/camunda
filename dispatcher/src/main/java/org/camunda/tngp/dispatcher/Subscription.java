package org.camunda.tngp.dispatcher;

import static org.agrona.BitUtil.align;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.partitionId;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.partitionOffset;
import static org.camunda.tngp.dispatcher.impl.PositionUtil.position;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.FRAME_ALIGNMENT;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.TYPE_PADDING;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.flagFailed;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.flagsOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.typeOffset;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.Position;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.dispatcher.impl.log.LogBuffer;
import org.camunda.tngp.dispatcher.impl.log.LogBufferPartition;

public class Subscription
{
    protected final Position position;
    protected final LogBuffer logBuffer;
    protected final Dispatcher dispatcher;
    protected final int id;
    protected final String name;

    public Subscription(Position position, int id, String name, Dispatcher dispatcher)
    {
        this.position = position;
        this.id = id;
        this.name = name;
        this.dispatcher = dispatcher;
        this.logBuffer = dispatcher.getLogBuffer();
    }

    public long getPosition()
    {
        return position.get();
    }

    /**
     * Read fragments from the buffer and invoke the given handler for each
     * fragment. Consume the fragments (i.e. update the subscription position)
     * after all fragments are handled.
     *
     * @return the amount of read fragments
     */
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
                    partitionId,
                    partitionOffset,
                    maxNumOfFragments,
                    limit,
                    false);
        }

        return fragmentsRead;
    }

    protected int pollFragments(
            final LogBufferPartition partition,
            final FragmentHandler frgHandler,
            int partitionId,
            int fragmentOffset,
            final int maxNumOfFragments,
            final long limit,
            boolean handlerControlled)
    {
        final UnsafeBuffer buffer = partition.getDataBuffer();

        int fragmentsConsumed = 0;

        int fragmentResult = FragmentHandler.CONSUME_FRAGMENT_RESULT;
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
                final int flagsOffset = flagsOffset(fragmentOffset);
                final byte flags = buffer.getByte(flagsOffset);
                try
                {
                    final boolean isMarkedAsFailed = flagFailed(flags);

                    final int handlerResult = frgHandler.onFragment(buffer, messageOffset(fragmentOffset), length, streamId, isMarkedAsFailed);

                    if (handlerResult == FragmentHandler.FAILED_FRAGMENT_RESULT && !isMarkedAsFailed)
                    {
                        buffer.putByte(flagsOffset, DataFrameDescriptor.enableFlagFailed(flags));
                    }

                    if (handlerControlled)
                    {
                        fragmentResult = handlerResult;
                    }

                }
                catch (RuntimeException e)
                {
                    // TODO!
                    e.printStackTrace();
                }

                if (fragmentResult != FragmentHandler.POSTPONE_FRAGMENT_RESULT)
                {
                    ++fragmentsConsumed;
                    fragmentOffset += align(length + HEADER_LENGTH, FRAME_ALIGNMENT);
                }
            }
        }
        while (fragmentResult != FragmentHandler.POSTPONE_FRAGMENT_RESULT && fragmentsConsumed < maxNumOfFragments && position(partitionId, fragmentOffset) < limit);

        position.setOrdered(position(partitionId, fragmentOffset));

        return fragmentsConsumed;
    }

    /**
     * Sequentially read fragments from the buffer and invoke the given handler for each
     * fragment. Consume the fragments (i.e. update the subscription position)
     * depending on the return value of {@link FragmentHandler#onFragment(org.agrona.DirectBuffer, int, int, int, boolean)}.
     * If a fragment is not consumed then no following fragments are read.
     *
     * @return the amount of read fragments
     */
    public int peekAndConsume(FragmentHandler frgHandler, int maxNumOfFragments)
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
                    partitionId,
                    partitionOffset,
                    maxNumOfFragments,
                    limit,
                    true);

        }

        return fragmentsRead;
    }

    public void close()
    {
        closeAsnyc().join();
    }

    public CompletableFuture<Void> closeAsnyc()
    {
        final CompletableFuture<Void> future = dispatcher.closeSubscriptionAsync(this);
        future.thenRun(() -> position.close());
        return future;
    }

    /**
     * Read fragments from the buffer as block. Use
     * {@link BlockPeek#getBuffer()} to consume the fragments and finish the
     * operation using {@link BlockPeek#markCompleted()} or
     * {@link BlockPeek#markFailed()}.
     *
     * @param isStreamAware
     *            if <code>true</code>, it stops reading fragments when a
     *            fragment has a different stream id than the previous one
     *
     * @return amount of read bytes
     */
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
                    partitionId,
                    partitionOffset,
                    maxBlockSize,
                    limit,
                    isStreamAware);
        }

        return bytesAvailable;
    }

    protected int peekBlock(
            final LogBufferPartition partition,
            final BlockPeek availableBlock,
            int partitionId,
            int partitionOffset,
            final int maxBlockSize,
            long limit,
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

                if (partitionOffset >= partition.getPartitionSize())
                {
                    partitionId += 1;
                    partitionOffset = 0;
                }

                if (blockLength == 0)
                {
                    position.proposeMaxOrdered(position(partitionId, partitionOffset));
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
        while (maxBlockSize - blockLength > HEADER_LENGTH && position(partitionId, partitionOffset) < limit);

        if (blockLength > 0)
        {
            final int absoluteOffset = bufferOffset + firstFragmentOffset;

            availableBlock.setBlock(
                rawBuffer,
                position,
                initialStreamId,
                absoluteOffset,
                blockLength,
                partitionId,
                partitionOffset);
        }

        return blockLength;
    }

    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("Subscription [id=");
        builder.append(id);
        builder.append(", name=");
        builder.append(name);
        builder.append("]");
        return builder.toString();
    }

}
