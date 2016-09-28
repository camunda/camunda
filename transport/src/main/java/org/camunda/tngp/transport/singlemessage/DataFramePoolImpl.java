package org.camunda.tngp.transport.singlemessage;

import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.camunda.tngp.dispatcher.Dispatcher;

public class DataFramePoolImpl implements DataFramePool
{
    protected final ManyToManyConcurrentArrayQueue<OutgoingDataFrameImpl> framePool;

    public DataFramePoolImpl(int capacity, Dispatcher sendBuffer)
    {
        framePool = new ManyToManyConcurrentArrayQueue<>(capacity);
        final int actualCapacity = framePool.capacity();

        for (int i = 0; i < actualCapacity; i++)
        {
            framePool.offer(new OutgoingDataFrameImpl(this, sendBuffer));
        }
    }

    @Override
    public OutgoingDataFrame openFrame(int messageLength, int channelId)
    {
        final OutgoingDataFrameImpl frame = framePool.poll();

        final boolean success = frame.open(messageLength, channelId);

        if (success)
        {
            return frame;
        }
        else
        {
            return null;
        }
    }

    public void reclaim(OutgoingDataFrameImpl frame)
    {
        framePool.offer(frame);
    }

}
