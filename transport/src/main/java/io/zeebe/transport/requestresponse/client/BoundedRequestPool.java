package io.zeebe.transport.requestresponse.client;

import io.zeebe.transport.Loggers;
import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.slf4j.Logger;

/**
 * Threadsafe request pool which can be shared by multiple threads.
 */
public class BoundedRequestPool implements TransportRequestPool
{
    public static final Logger LOG = Loggers.TRANSPORT_LOGGER;

    protected final ManyToManyConcurrentArrayQueue<PooledTransportRequest> pooledRequests;
    protected final PooledTransportRequest[] requests;
    protected final int capacity;

    public BoundedRequestPool(int capacity, int responseBufferSize, long requestTimeout)
    {
        this.capacity = capacity;
        pooledRequests = new ManyToManyConcurrentArrayQueue<>(capacity);
        requests = new PooledTransportRequest[capacity];

        for (int i = 0; i < requests.length; i++)
        {
            requests[i] = new PooledTransportRequestImpl(this, responseBufferSize, requestTimeout);
            pooledRequests.offer(requests[i]);
        }
    }

    @Override
    public PooledTransportRequest getRequest()
    {
        return pooledRequests.poll();
    }

    @Override
    public void close()
    {
        for (int i = 0; i < requests.length; i++)
        {
            try
            {
                requests[i].close();
            }
            catch (Exception e)
            {
                LOG.error("Failed to close request", e);
            }

            requests[i] = null;
        }
        pooledRequests.clear();
    }

    public void reclaim(PooledTransportRequestImpl pooledTransportRequest)
    {
        pooledRequests.offer(pooledTransportRequest);
    }

    @Override
    public int capacity()
    {
        return capacity;
    }

}
