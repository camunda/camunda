package io.zeebe.broker.clustering.management.service;

import io.zeebe.broker.clustering.gossip.data.Peer;
import io.zeebe.broker.clustering.gossip.data.PeerList;
import io.zeebe.broker.clustering.management.ClusterManagerContext;
import io.zeebe.broker.logstreams.LogStreamsManager;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ChannelManager;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;
import io.zeebe.util.actor.ActorScheduler;

public class ClusterManagerContextService implements Service<ClusterManagerContext>
{
    private final Injector<ChannelManager> clientChannelManagerInjector = new Injector<>();
    private final Injector<TransportConnectionPool> transportConnectionPoolInjector = new Injector<>();
    private final Injector<Subscription> subscriptionInjector = new Injector<>();
    private final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    private final Injector<PeerList> peerListInjector = new Injector<>();
    private final Injector<Peer> localPeerInjector = new Injector<>();
    private final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    private final Injector<LogStreamsManager> logStreamsManagerInjector = new Injector<>();

    private ClusterManagerContext context;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ChannelManager clientChannelManager = clientChannelManagerInjector.getValue();
        final TransportConnectionPool connectionPool = transportConnectionPoolInjector.getValue();
        final Subscription subscription = subscriptionInjector.getValue();
        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final PeerList peers = peerListInjector.getValue();
        final Peer localPeer = localPeerInjector.getValue();
        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
        final LogStreamsManager logStreamsManager = logStreamsManagerInjector.getValue();

        context = new ClusterManagerContext();
        context.setActorScheduler(actorScheduler);
        context.setLocalPeer(localPeer);
        context.setClientChannelPool(clientChannelManager);
        context.setConnections(connectionPool);
        context.setSubscription(subscription);
        context.setSendBuffer(sendBuffer);
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

    public Injector<ChannelManager> getClientChannelManagerInjector()
    {
        return clientChannelManagerInjector;
    }

    public Injector<TransportConnectionPool> getTransportConnectionPoolInjector()
    {
        return transportConnectionPoolInjector;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }

    public Injector<Subscription> getSubscriptionInjector()
    {
        return subscriptionInjector;
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

}
