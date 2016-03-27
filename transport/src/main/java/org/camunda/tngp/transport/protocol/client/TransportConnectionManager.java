package org.camunda.tngp.transport.protocol.client;

import java.util.concurrent.atomic.AtomicLong;

import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportChannel;

import uk.co.real_logic.agrona.concurrent.ManyToManyConcurrentArrayQueue;

/**
 * Manages a number of pooled connections.
 *
 */
public class TransportConnectionManager
{
    protected final ManyToManyConcurrentArrayQueue<TransportConnectionImpl> connectionPool;
    protected final AtomicLong connectionIdSequence = new AtomicLong();
    protected final TransportConnectionImpl[] connections;

    /**
     * @param numberOfConnections the maximum number of pooled connections
     * @param maxNumberOfRequests the maximum number of concurrent in-flight requests per connection
     */
    public TransportConnectionManager(
        final Transport transport,
        final int numberOfConnections,
        final int maxNumberOfRequests)
    {
        connectionPool = new ManyToManyConcurrentArrayQueue<>(numberOfConnections);
        connections = new TransportConnectionImpl[numberOfConnections];

        for (int i = 0; i < numberOfConnections; i++)
        {
            final TransportConnectionImpl connection = new TransportConnectionImpl(
                    transport,
                    this,
                    maxNumberOfRequests);

            connections[i] = connection;
            connectionPool.offer(connection);
        }
    }

    public TransportConnection openConnection()
    {
        final TransportConnectionImpl connection = connectionPool.poll();

        if(connection != null)
        {
            long connectionId = connectionIdSequence.getAndIncrement();

            connection.open(connectionId);
        }

        return connection;
    }

    public void handleChannelClose(TransportChannel transportChannel)
    {
        for (TransportConnectionImpl c : connections)
        {
            if(c != null)
            {
                c.processChannelClosed(transportChannel);
            }
        }
    }

    public void onConnectionClosed(TransportConnectionImpl connection)
    {
        connectionPool.offer(connection);
    }

    public void closeAll()
    {
        for(int i = 0; i < connections.length; i++)
        {
            connections[i].close();
            connections[i] = null;
        }

    }

    public TransportConnectionImpl findConnection(long connectionId)
    {
        for (TransportConnectionImpl connection : connections)
        {
            if(connection.isOpen() && connection.getId() == connectionId)
            {
                return connection;
            }
        }

        return null;
    }

}
