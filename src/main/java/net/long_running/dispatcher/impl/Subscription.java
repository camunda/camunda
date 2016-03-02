package net.long_running.dispatcher.impl;

import static net.long_running.dispatcher.impl.PositionUtil.*;
import static net.long_running.dispatcher.impl.log.DataFrameDescriptor.*;
import static uk.co.real_logic.agrona.BitUtil.*;

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

    public int pollPartition(
            LogBufferPartition partition,
            FragmentHandler frgHandler,
            int maxNumOfFragments,
            int partitionId,
            int partitionOffset)
    {
        final UnsafeBuffer buffer = partition.getDataBuffer();

        int fragmentsRead = 0;

        do
        {
            final int length = buffer.getIntVolatile(frameLengthOffset(partitionOffset));
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
                frgHandler.onFragment(buffer, messageOffset(partitionOffset), length);
                partitionOffset += align(length + HEADER_LENGTH, FRAME_ALIGNMENT);
                ++fragmentsRead;
            }
        }
        while(fragmentsRead < maxNumOfFragments);

        position.setOrdered(position(partitionId, partitionOffset));

        return fragmentsRead;
    }

    public void close()
    {
        position.close();
    }

}
