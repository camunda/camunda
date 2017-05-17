package org.camunda.tngp.broker.clustering.gossip.service;

import org.camunda.tngp.broker.clustering.gossip.GossipContext;
import org.camunda.tngp.broker.clustering.gossip.config.GossipConfiguration;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.data.PeerSelector;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.ChannelManager;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class GossipContextService implements Service<GossipContext>
{
    private final Injector<ChannelManager> clientChannelManagerInjector = new Injector<>();
    private final Injector<TransportConnectionPool> transportConnectionPoolInjector = new Injector<>();
    private final Injector<Subscription> subscriptionInjector = new Injector<>();
    private final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    private final Injector<PeerList> peerListInjector = new Injector<>();
    private final Injector<Peer> localPeerInjector = new Injector<>();
    private final Injector<PeerSelector> peerSelectorInjector = new Injector<>();

    private final GossipConfiguration config;

    private GossipContext context;

    public GossipContextService(final GossipConfiguration config)
    {
        this.config = config;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ChannelManager clientChannelManager = clientChannelManagerInjector.getValue();
        final TransportConnectionPool connectionPool = transportConnectionPoolInjector.getValue();
        final Subscription subscription = subscriptionInjector.getValue();
        final Dispatcher dispatcher = sendBufferInjector.getValue();
        final PeerList peers = peerListInjector.getValue();
        final Peer localPeer = localPeerInjector.getValue();
        final PeerSelector peerSelector = peerSelectorInjector.getValue();

        context = new GossipContext();
        context.setLocalPeer(localPeer);
        context.setPeers(peers);
        context.setConfig(config);
        context.setClientChannelPool(clientChannelManager);
        context.setConnections(connectionPool);
        context.setSubscription(subscription);
        context.setSendBuffer(dispatcher);
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

    public Injector<ChannelManager> getClientChannelManagerInjector()
    {
        return clientChannelManagerInjector;
    }

    public Injector<TransportConnectionPool> getTransportConnectionPoolInjector()
    {
        return transportConnectionPoolInjector;
    }

    public Injector<Subscription> getSubscriptionInjector()
    {
        return subscriptionInjector;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
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

}
