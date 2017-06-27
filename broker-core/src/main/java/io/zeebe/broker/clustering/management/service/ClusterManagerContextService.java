package io.zeebe.broker.clustering.management.service;

import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.management.ClusterManagerContext;
import io.zeebe.broker.logstreams.LogStreamsManager;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.actor.ActorScheduler;

public class ClusterManagerContextService implements Service<ClusterManagerContext>
{
    private final Injector<ClientTransport> clientTransportInjector = new Injector<>();
    private final Injector<BufferingServerTransport> managementApiTransportInjector = new Injector<>();
    private final Injector<PeerList> peerListInjector = new Injector<>();
    private final Injector<Peer> localPeerInjector = new Injector<>();
    private final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    private final Injector<LogStreamsManager> logStreamsManagerInjector = new Injector<>();

    private ClusterManagerContext context;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ClientTransport clientTransport = clientTransportInjector.getValue();
        final BufferingServerTransport serverTransport = managementApiTransportInjector.getValue();
        final PeerList peers = peerListInjector.getValue();
        final Peer localPeer = localPeerInjector.getValue();
        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
        final LogStreamsManager logStreamsManager = logStreamsManagerInjector.getValue();

        context = new ClusterManagerContext();
        context.setActorScheduler(actorScheduler);
        context.setLocalPeer(localPeer);
        context.setClientTransport(clientTransport);
        context.setServerTransport(serverTransport);
        context.setPeers(peers);
        context.setLogStreamsManager(logStreamsManager);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
    }

    @Override
    public ClusterManagerContext get()
    {
        return context;
    }

    public Injector<PeerList> getPeerListInjector()
    {
        return peerListInjector;
    }

    public Injector<Peer> getLocalPeerInjector()
    {
        return localPeerInjector;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public Injector<LogStreamsManager> getLogStreamsManagerInjector()
    {
        return logStreamsManagerInjector;
    }

    public Injector<BufferingServerTransport> getManagementApiTransportInjector()
    {
        return managementApiTransportInjector;
    }

    public Injector<ClientTransport> getClientTransportInjector()
    {
        return clientTransportInjector;
    }

}
