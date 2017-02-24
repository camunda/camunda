package org.camunda.tngp.broker.clustering.raft;

import org.camunda.tngp.broker.clustering.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.raft.state.LogStreamState;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class RaftContext
{
    private Raft raft;
    private LogStreamState logStreamState;

    private Endpoint raftEndpoint;
    private Subscription subscription;
    private ClientChannelManager clientChannelManager;
    private TransportConnectionPool connections;
    private Dispatcher sendBuffer;

    public Raft getRaft()
    {
        return raft;
    }

    public void setRaft(Raft raft)
    {
        this.raft = raft;
    }

    public LogStreamState getLogStreamState()
    {
        return logStreamState;
    }

    public void setLogStreamState(LogStreamState logStreamState)
    {
        this.logStreamState = logStreamState;
    }

    public Endpoint getRaftEndpoint()
    {
        return raftEndpoint;
    }

    public void setRaftEndpoint(Endpoint raftEndpoint)
    {
        this.raftEndpoint = raftEndpoint;
    }

    public Subscription getSubscription()
    {
        return subscription;
    }
    public void setSubscription(Subscription subscription)
    {
        this.subscription = subscription;
    }

    public ClientChannelManager getClientChannelManager()
    {
        return clientChannelManager;
    }

    public void setClientChannelManager(ClientChannelManager clientChannelManager)
    {
        this.clientChannelManager = clientChannelManager;
    }

    public TransportConnectionPool getConnections()
    {
        return connections;
    }

    public void setConnections(TransportConnectionPool connections)
    {
        this.connections = connections;
    }

    public Dispatcher getSendBuffer()
    {
        return sendBuffer;
    }

    public void setSendBuffer(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

}
