package org.camunda.tngp.dispatcher;

import java.nio.ByteBuffer;

import static org.camunda.tngp.dispatcher.impl.PositionUtil.*;

import uk.co.real_logic.agrona.concurrent.status.Position;

public class BlockPeek
{
    protected ByteBuffer byteBuffer;
    protected Position subscriberPosition;

    protected int streamId;

    protected int bufferOffset;
    protected int blockLength;

    protected int partitionId;
    protected int partitionOffset;

    public void setBlock(
            final ByteBuffer byteBuffer,
            final Position position,
            final int streamId,
            final int partitionId,
            final int partitionOffset,
            final int bufferOffset,
            final int blockLength)
    {
        this.byteBuffer = byteBuffer;
        this.subscriberPosition = position;
        this.streamId = streamId;
        this.partitionId = partitionId;
        this.partitionOffset = partitionOffset;
        this.bufferOffset = bufferOffset;
        this.blockLength = blockLength;

        byteBuffer.limit(bufferOffset + blockLength);
        byteBuffer.position(bufferOffset);
    }

    public ByteBuffer getBuffer()
    {
        return byteBuffer;
    }

    public void markFailed()
    {
        // TODO: mark messages as failed.
        updatePosition();
    }

    public void markCompleted()
    {
        updatePosition();
    }

    protected void updatePosition()
    {
        subscriberPosition.proposeMaxOrdered(position(partitionId, partitionOffset + blockLength));
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
        return position(partitionId, partitionOffset);
    }

    public void cancel()
    {
    }

}
