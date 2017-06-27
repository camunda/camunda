package io.zeebe.broker.clustering.gossip.service;

import io.zeebe.broker.clustering.gossip.GossipContext;
import io.zeebe.broker.clustering.gossip.config.GossipConfiguration;
import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerSelector;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;

public class GossipContextService implements Service<GossipContext>
{
    private final Injector<PeerList> peerListInjector = new Injector<>();
    private final Injector<Peer> localPeerInjector = new Injector<>();
    private final Injector<PeerSelector> peerSelectorInjector = new Injector<>();
    protected final Injector<ClientTransport> clientTransportInjector = new Injector<>();
    protected final Injector<BufferingServerTransport> managementApiTransportInjector = new Injector<>();

    private final GossipConfiguration config;

    private GossipContext context;

    public GossipContextService(final GossipConfiguration config)
    {
        this.config = config;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ClientTransport clientTransport = clientTransportInjector.getValue();
        final BufferingServerTransport serverTransport = managementApiTransportInjector.getValue();

        final PeerList peers = peerListInjector.getValue();
        final Peer localPeer = localPeerInjector.getValue();
        final PeerSelector peerSelector = peerSelectorInjector.getValue();

        context = new GossipContext();
        context.setLocalPeer(localPeer);
        context.setPeers(peers);
        context.setConfig(config);
        context.setClientTransport(clientTransport);
        context.setServerTransport(serverTransport);
        context.setPeerSelector(peerSelector);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
    }

    @Override
    public GossipContext get()
    {
        return context;
    }

    public Injector<PeerList> getPeerListInjector()
    {
        return peerListInjector;
    }

    public Injector<PeerSelector> getPeerSelectorInjector()
    {
        return peerSelectorInjector;
    }

    public Injector<Peer> getLocalPeerInjector()
    {
        return localPeerInjector;
    }

    public Injector<ClientTransport> getClientTransportInjector()
    {
        return clientTransportInjector;
    }

    public Injector<BufferingServerTransport> getManagementApiTransportInjector()
    {
        return managementApiTransportInjector;
    }

}
