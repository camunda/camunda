package io.zeebe.broker.clustering.raft;

import io.zeebe.broker.clustering.raft.state.LogStreamState;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.transport.ChannelManager;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;
import io.zeebe.util.actor.ActorScheduler;

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
