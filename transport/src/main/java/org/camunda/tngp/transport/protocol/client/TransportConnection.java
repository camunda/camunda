package org.camunda.tngp.transport.protocol.client;

/**
 * Connection for request / response style communication over transport channels.
 * Connections are not threadsafe and must not be shared between threads.
 */
public interface TransportConnection extends AutoCloseable
{
    boolean isOpen();

    /**
     * Non blocking open of request in this connection to a request response message pair
     * over the provided channel. The request buffer is claimed in the transport's
     * send buffer allowing for zero-copy writes to the transport's send buffer.
     *
     * The request must be completed by calling {@link TransportRequest#commit()}
     * or {@link TransportRequest#abort()}
     *
     * Returns false if the request cannot be opened due to back pressure
     */
    boolean openRequest(TransportRequest request, int channelId, int length);

    boolean sendRequest(TransportRequest request, int channelId, int length);

}