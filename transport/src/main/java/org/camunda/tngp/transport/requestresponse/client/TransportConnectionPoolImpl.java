package org.camunda.tngp.transport.requestresponse.client;

import java.util.concurrent.atomic.AtomicLong;

import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportChannel;

import uk.co.real_logic.agrona.concurrent.ManyToManyConcurrentArrayQueue;

/**
 * Manages a number of pooled connections to be reused by different threads
 */
public class TransportConnectionPoolImpl implements TransportConnectionPool
{
    protected final ManyToManyConcurrentArrayQueue<TransportConnectionImpl> connectionPool;
    protected final AtomicLong connectionIdSequence = new AtomicLong();
    protected final TransportConnectionImpl[] connections;
    protected final TransportRequestPool requestPool;

    public TransportConnectionPoolImpl(
            final Transport transport,
            final int numberOfConnections,
            final int maxNumberOfRequests)
    {
        this(transport,
                numberOfConnections,
                TransportRequestPool.newBoundedPool(maxNumberOfRequests, 1024, 30000));
    }

    public TransportConnectionPoolImpl(
            final Transport transport,
            final int numberOfConnections,
            final TransportRequestPool requestPool)
    {
        this.connectionPool = new ManyToManyConcurrentArrayQueue<>(numberOfConnections);
        this.connections = new TransportConnectionImpl[numberOfConnections];
        this.requestPool = requestPool;

        for (int i = 0; i < numberOfConnections; i++)
        {
            final TransportConnectionImpl connection = new TransportConnectionImpl(
                    transport,
                    this,
                    requestPool,
                    requestPool.capacity());

            connections[i] = connection;
            connectionPool.offer(connection);
        }
    }

    /**
     * Non blocking attempt to open a connection.
     * Returns null if no connection is immediately available.
     */
    @Override
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

    @Override
    public void close()
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
