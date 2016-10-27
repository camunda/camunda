package org.camunda.tngp.transport.requestresponse.server;

import java.util.Queue;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.util.BoundedArrayQueue;

/**
 * Utility for deferring responses which wait on some async processing (usually io)
 * to complete before they are sent.
 */
public class DeferredResponsePool
{
    protected UnsafeBuffer flyweight = new UnsafeBuffer(0, 0);

    protected BoundedArrayQueue<DeferredResponse> pooled;

    protected Queue<DeferredResponse> deferred;

    protected final Dispatcher sendBuffer;

    protected int capacity;

    public DeferredResponsePool(Dispatcher sendBuffer, int capacity)
    {
        this.sendBuffer = sendBuffer;
        this.capacity = capacity;
        this.pooled = new BoundedArrayQueue<>(capacity);
        this.deferred = new BoundedArrayQueue<>(capacity);

        for (int i = 0; i < capacity; i++)
        {
            pooled.offer(new DeferredResponse(sendBuffer, this));
        }
    }

    public DeferredResponse open(int channelId, long connectionId, long requestId)
    {
        final DeferredResponse response = pooled.poll();

        if (response != null)
        {
            response.setIsReclaimed(false);
            response.open(channelId, connectionId, requestId);
        }

        return response;
    }

    public int getCapacity()
    {
        return capacity;
    }

    public int getDeferredCount()
    {
        return deferred.size();
    }

    public int getPooledCount()
    {
        return pooled.size();
    }

    public void reclaim(DeferredResponse response)
    {
        response.reset();

        if (!response.isReclaimed())
        {
            pooled.offer(response);
        }
        response.setIsReclaimed(true);
    }

    public DeferredResponse popDeferred()
    {
        return deferred.remove();
    }

    public void offerDeferred(DeferredResponse response)
    {
        deferred.offer(response);
    }
}
