package org.camunda.tngp.broker.clustering.raft.protocol;

import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.singlemessage.DataFramePool;

public class RaftContext
{
    protected ClientChannelManager clientChannelManager;
    protected TransportConnection connection;
    protected DataFramePool dataFramePool;

    public ClientChannelManager clientChannelManager()
    {
        return clientChannelManager;
    }

    public RaftContext clientChannelManager(final ClientChannelManager clientChannelManager)
    {
        this.clientChannelManager = clientChannelManager;
        return this;
    }

    public TransportConnection connection()
    {
        return connection;
    }

    public RaftContext connection(final TransportConnection connection)
    {
        this.connection = connection;
        return this;
    }

    public DataFramePool dataFramePool()
    {
        return dataFramePool;
    }

    public RaftContext dataFramePool(final DataFramePool dataFramePool)
    {
        this.dataFramePool = dataFramePool;
        return this;
    }
}
