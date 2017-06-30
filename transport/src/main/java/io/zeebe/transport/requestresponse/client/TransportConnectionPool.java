package io.zeebe.transport.requestresponse.client;

import io.zeebe.transport.Transport;

public interface TransportConnectionPool extends AutoCloseable
{

    /**
     * Non-blocking attempt to open a connection.
     * Returns null if no connection is immediately available.
     *
     * @return the connection
     */
    TransportConnection openConnection();

    /**
     * closes all connections in the pool awaiting all open requests
     * to terminate or time-out.
     */
    void close();

    static TransportConnectionPool newFixedCapacityPool(Transport transport, int numberOfConnections, int maxNumberOfRequests)
    {
        return new TransportConnectionPoolImpl(transport, numberOfConnections, maxNumberOfRequests);
    }

    static TransportConnectionPool newFixedCapacityPool(Transport transport, int numberOfConnections, TransportRequestPool requestPool)
    {
        return new TransportConnectionPoolImpl(transport, numberOfConnections, requestPool);
    }

}