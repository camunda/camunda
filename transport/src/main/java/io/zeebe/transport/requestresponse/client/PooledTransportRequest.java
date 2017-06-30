package io.zeebe.transport.requestresponse.client;

/**
 * A pooled transport request is put back into the pool on close
 */
public interface PooledTransportRequest extends TransportRequest
{

    /**
     * Close the request and put it back into the
     * {@link TransportRequestPool} for reuse.
     */
    void close();

}
