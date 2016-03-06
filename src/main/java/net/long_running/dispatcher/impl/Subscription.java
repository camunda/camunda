package net.long_running.dispatcher.impl;

import static net.long_running.dispatcher.impl.PositionUtil.*;
import static net.long_running.dispatcher.impl.log.DataFrameDescriptor.*;
import static uk.co.real_logic.agrona.BitUtil.*;

import java.nio.ByteBuffer;

import net.long_running.dispatcher.BlockHandler;
import net.long_running.dispatcher.FragmentHandler;
import net.long_running.dispatcher.impl.log.LogBufferPartition;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.concurrent.status.Position;

public class Subscription
{
    protected final Position position;

    public Subscription(Position position)
    {
        this.position = position;
    }

    public long getPosition()
    {
        return position.get();
    }

    public int pollFragments(
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
            if(length <= 0)
            {
                break;
            }

            final short type = buffer.getShort(typeOffset(fragmentOffset));
            if(type == TYPE_PADDING)
            {
                ++partitionId;
                fragmentOffset = 0;
                break;
            }
            else
            {
                final int streamId = buffer.getInt(streamIdOffset(fragmentOffset));
                try
                {
                    frgHandler.onFragment(buffer, messageOffset(fragmentOffset), length, streamId);
                }
                catch(RuntimeException e)
                {
                    // TODO!
                    e.printStackTrace();
                }

                fragmentOffset += align(length + HEADER_LENGTH, FRAME_ALIGNMENT);
                ++fragmentsRead;
            }
        }
        while(fragmentsRead < maxNumOfFragments);

        position.setOrdered(position(partitionId, fragmentOffset));

        return fragmentsRead;
    }

    public int pollBlock(
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

        int fragmentsRead = 0;

        int firstFragmentOffset = partitionOffset;
        int blockLength = 0;
        int initialStreamId = -1;

        // scan buffer for block
        do
        {
            final int length = buffer.getIntVolatile(lengthOffset(partitionOffset));
            if(length <= 0)
            {
                break;
            }

            final short type = buffer.getShort(typeOffset(partitionOffset));
            if(type == TYPE_PADDING)
            {
                ++partitionId;
                partitionOffset = 0;
                break;
            }
            else
            {
                if(isStreamAware)
                {
                    final int streamId = buffer.getInt(streamIdOffset(partitionOffset));
                    if(fragmentsRead == 0)
                    {
                        initialStreamId = streamId;
                    }
                    else
                    {
                        if(streamId != initialStreamId)
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
        while(fragmentsRead < maxNumOfFragments);

        if(fragmentsRead > 0)
        {
            final int absoluteOffset = bufferOffset + firstFragmentOffset;
            try
            {
                blockHandler.onBlockAvailable(rawBuffer, absoluteOffset, blockLength, initialStreamId);
            }
            catch(Exception e)
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
        position.close();
    }

}
