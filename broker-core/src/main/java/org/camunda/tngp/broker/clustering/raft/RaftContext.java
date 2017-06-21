package org.camunda.tngp.broker.clustering.raft;

import org.camunda.tngp.broker.clustering.raft.state.LogStreamState;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.transport.ChannelManager;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.util.actor.ActorScheduler;

public class RaftContext
{
    private Raft raft;
    private LogStreamState logStreamState;

    private ServiceContainer serviceContainer;
    private ActorScheduler actorScheduler;
    private SocketAddress raftEndpoint;
    private Subscription subscription;
    private ChannelManager clientChannelManager;
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

    public SocketAddress getRaftEndpoint()
    {
        return raftEndpoint;
    }

    public void setRaftEndpoint(SocketAddress raftEndpoint)
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

    public ChannelManager getClientChannelPool()
    {
        return clientChannelManager;
    }

    public void setClientChannelPool(ChannelManager clientChannelManager)
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

    public ActorScheduler getTaskScheduler()
    {
        return actorScheduler;
    }

    public void setTaskScheduler(ActorScheduler actorScheduler)
    {
        this.actorScheduler = actorScheduler;
    }

    public ServiceContainer getServiceContainer()
    {
        return serviceContainer;
    }

    public void setServiceContainer(ServiceContainer serviceContainer)
    {
        this.serviceContainer = serviceContainer;
    }

}
