package org.camunda.tngp.transport.requestresponse.client;

public class PooledTransportRequestImpl extends TransportRequestImpl implements PooledTransportRequest
{
    protected final BoundedRequestPool pool;

    public PooledTransportRequestImpl(BoundedRequestPool simpleRequestPool, int responseBufferSize, int capacity)
    {
        super(responseBufferSize, capacity);
        this.pool = simpleRequestPool;
    }

    @Override
    public void close()
    {
        try
        {
            super.close();
        }
        finally
        {
            pool.reclaim(this);
        }
    }
}
