package org.camunda.tngp.dispatcher;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.position;

import java.nio.ByteBuffer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.Position;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;

/**
 * Represents a block of fragments to read from.
 */
public class BlockPeek
{
    protected ByteBuffer byteBuffer;
    protected UnsafeBuffer bufferView = new UnsafeBuffer(0, 0);
    protected Position subscriberPosition;

    protected int streamId;

    protected int bufferOffset;
    protected int blockLength;

    protected int newPartitionId;
    protected int newPartitionOffset;

    public void setBlock(
            final ByteBuffer byteBuffer,
            final Position position,
            final int streamId,
            final int bufferOffset,
            final int blockLength,
            final int newPartitionId,
            final int newPartitionOffset)
    {
        this.byteBuffer = byteBuffer;
        this.subscriberPosition = position;
        this.streamId = streamId;
        this.bufferOffset = bufferOffset;
        this.blockLength = blockLength;
        this.newPartitionId = newPartitionId;
        this.newPartitionOffset = newPartitionOffset;

        byteBuffer.limit(bufferOffset + blockLength);
        byteBuffer.position(bufferOffset);

        bufferView.wrap(byteBuffer, bufferOffset, blockLength);
    }

    public ByteBuffer getRawBuffer()
    {
        return byteBuffer;
    }

    /**
     * Returns the buffer to read from.
     */
    public MutableDirectBuffer getBuffer()
    {
        return bufferView;
    }

    /**
     * Finish reading and consume the fragments (i.e. update the subscription
     * position). Mark all fragments as failed.
     */
    public void markFailed()
    {
        int fragmentOffset = 0;
        while (fragmentOffset < blockLength)
        {
            int fragmentLength = bufferView.getInt(DataFrameDescriptor.lengthOffset(fragmentOffset));

            if (fragmentLength < 0)
            {
                fragmentLength = -fragmentLength;
            }

            final int frameLength = DataFrameDescriptor.alignedLength(fragmentLength);
            final int flagsOffset = DataFrameDescriptor.flagsOffset(fragmentOffset);
            final byte flags = byteBuffer.get(flagsOffset);

            byteBuffer.put(flagsOffset, DataFrameDescriptor.enableFlagFailed(flags));

            fragmentOffset += frameLength;
        }

        updatePosition();
    }

    /**
     * Finish reading and consume the fragments (i.e. update the subscription
     * position).
     */
    public void markCompleted()
    {
        updatePosition();
    }

    protected void updatePosition()
    {
        subscriberPosition.proposeMaxOrdered(position(newPartitionId, newPartitionOffset));
    }

    public int getStreamId()
    {
        return streamId;
    }

    public int getBufferOffset()
    {
        return bufferOffset;
    }

    public int getBlockLength()
    {
        return blockLength;
    }

    public long getBlockPosition()
    {
        return position(newPartitionId, newPartitionOffset);
    }

}
