package org.camunda.tngp.broker.clustering.gossip.service;

import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.PeerSelector;
import org.camunda.tngp.broker.clustering.gossip.protocol.util.SimplePeerSelector;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

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
