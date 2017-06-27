package io.zeebe.broker.clustering.raft.service;

import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.actor.ActorScheduler;

public class RaftContextService implements Service<RaftContext>
{
    private final Injector<ClientTransport> clientTransportInjector = new Injector<>();
    private final Injector<BufferingServerTransport> replicationApiTransportInjector = new Injector<>();
    private final Injector<Peer> localPeerInjector = new Injector<>();
    private final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();

    private RaftContext raftContext;
    private ServiceContainer serviceContainer;

    public RaftContextService(ServiceContainer serviceContainer)
    {
        this.serviceContainer = serviceContainer;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ClientTransport clientTransport = clientTransportInjector.getValue();
        final BufferingServerTransport serverTransport = replicationApiTransportInjector.getValue();

        final Peer localPeer = localPeerInjector.getValue();
        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();

        raftContext = new RaftContext();
        raftContext.setClientTransport(clientTransport);
        raftContext.setServerTransport(serverTransport);
        raftContext.setRaftEndpoint(localPeer.replicationEndpoint());
        raftContext.setTaskScheduler(actorScheduler);
        raftContext.setServiceContainer(serviceContainer);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
    }

    @Override
    public RaftContext get()
    {
        return raftContext;
    }

    public Injector<ClientTransport> getClientTransportInjector()
    {
        return clientTransportInjector;
    }

    public Injector<BufferingServerTransport> getReplicationApiTransportInjector()
    {
        return replicationApiTransportInjector;
    }

    public Injector<Peer> getLocalPeerInjector()
    {
        return localPeerInjector;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }
}
