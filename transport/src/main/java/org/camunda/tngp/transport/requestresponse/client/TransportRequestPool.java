package org.camunda.tngp.transport.requestresponse.client;

public interface TransportRequestPool
{
    /**
     * Non-blocking attempt to get a request from the pool.
     * Returns null in case no request is immediately available.
     */
    PooledTransportRequest getRequest();

    /**
     * Close the pool
     */
    void close();

    /**
     * Returns the capacity of the pool (number of how many requests are
     * managed by the pool.
     */
    int capacity();

    static TransportRequestPool newBoundedPool(int capacity, int responseBufferSize, int requestTimeout)
    {
        return new BoundedRequestPool(capacity, responseBufferSize, requestTimeout);
    }

}
