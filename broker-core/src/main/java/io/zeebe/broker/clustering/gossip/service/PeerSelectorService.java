package io.zeebe.broker.clustering.gossip.service;

import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.gossip.data.PeerSelector;
import io.zeebe.broker.clustering.gossip.protocol.util.SimplePeerSelector;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class PeerSelectorService implements Service<PeerSelector>
{
    private final Injector<PeerList> peerListInjector = new Injector<>();

    private PeerSelector peerSelector;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final PeerList peers = peerListInjector.getValue();
        this.peerSelector = new SimplePeerSelector(peers);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
    }

    @Override
    public PeerSelector get()
    {
        return peerSelector;
    }

    public Injector<PeerList> getPeerListInjector()
    {
        return peerListInjector;
    }

}
