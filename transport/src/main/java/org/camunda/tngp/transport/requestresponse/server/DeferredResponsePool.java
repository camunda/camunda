package org.camunda.tngp.transport.requestresponse.server;

import java.util.Queue;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.util.BoundedArrayQueue;

import org.agrona.concurrent.UnsafeBuffer;

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
            pooled.offer(new DeferredResponse(sendBuffer, new DeferredResponseControl()
                {
                    @Override
                    public void defer(DeferredResponse r)
                    {
                        deferred.offer(r);
                    }

                    @Override
                    public void reclaim(DeferredResponse r)
                    {
                        DeferredResponsePool.this.reclaim(r);
                    }
                }));
        }
    }

    public DeferredResponse open(int channelId, long connectionId, long requestId)
    {
        final DeferredResponse response = pooled.poll();

        if (response != null)
        {
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
        pooled.offer(response);
    }

    public DeferredResponse popDeferred()
    {
        return deferred.remove();
    }

    public interface DeferredResponseControl
    {
        void defer(DeferredResponse r);

        void reclaim(DeferredResponse r);

    }

}
