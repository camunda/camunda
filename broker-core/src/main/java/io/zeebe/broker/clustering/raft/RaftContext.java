package io.zeebe.broker.clustering.raft;

import io.zeebe.broker.clustering.raft.state.LogStreamState;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.actor.ActorScheduler;

public class RaftContext
{
    private Raft raft;
    private LogStreamState logStreamState;

    private ServiceContainer serviceContainer;
    private ActorScheduler actorScheduler;
    private SocketAddress raftEndpoint;

    protected ClientTransport clientTransport;
    protected BufferingServerTransport serverTransport;

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

    public ClientTransport getClientTransport()
    {
        return clientTransport;
    }

    public void setClientTransport(ClientTransport clientTransport)
    {
        this.clientTransport = clientTransport;
    }

    public BufferingServerTransport getServerTransport()
    {
        return serverTransport;
    }

    public void setServerTransport(BufferingServerTransport serverTransport)
    {
        this.serverTransport = serverTransport;
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
