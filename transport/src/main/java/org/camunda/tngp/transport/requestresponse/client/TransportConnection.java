package org.camunda.tngp.transport.requestresponse.client;

/**
 * Connection for request / response style communication over transport channels.
 * Connections are not threadsafe and must not be shared between threads.
 *
 * Connections are pooled in a {@link TransportConnectionPool}.
 */
public interface TransportConnection extends AutoCloseable
{
    /**
     * Return true if this connection is currently open.
     */
    boolean isOpen();

    /**
     * Non blocking attempt to open a pooled request in this connection.
     * The request buffer is claimed  in the transport's send buffer
     * allowing for zero-copy writes to the transport's send buffer.
     *
     * The request must be completed by calling {@link TransportRequest#commit()}
     * or {@link TransportRequest#abort()}
     *
     * @param the id of the channel over which the request should be performed
     * @param length the size (in bytes) of the send buffer to allocate.
     * @return the pooled request or null in case no resources are currently
     * available to open a request.
     */
    PooledTransportRequest openRequest(int channelId, int length);

    PooledTransportRequest openRequest(int channelId, int length, long requestTimeout);

    /**
     * Non blocking open of request. The request buffer is claimed in the transport's
     * send buffer allowing for zero-copy writes to the transport's send buffer.
     *
     * The request must be completed by calling {@link TransportRequest#commit()}
     * or {@link TransportRequest#abort()}
     *
     * Returns false if the request cannot be opened due to back pressure
     */
    boolean openRequest(TransportRequest request, int channelId, int length);

    boolean openRequest(TransportRequest request, int channelId, int length, long requestTimeout);

    void close();

}