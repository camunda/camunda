package org.camunda.tngp.broker.clustering.raft.service;

import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.servicecontainer.*;
import org.camunda.tngp.transport.ChannelManager;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.util.actor.ActorScheduler;

public class RaftContextService implements Service<RaftContext>
{
    private final Injector<ChannelManager> clientChannelManagerInjector = new Injector<>();
    private final Injector<TransportConnectionPool> transportConnectionPoolInjector = new Injector<>();
    private final Injector<Subscription> subscriptionInjector = new Injector<>();
    private final Injector<Dispatcher> sendBufferInjector = new Injector<>();
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
        final ChannelManager clientChannelManager = clientChannelManagerInjector.getValue();
        final TransportConnectionPool connectionPool = transportConnectionPoolInjector.getValue();
        final Subscription subscription = subscriptionInjector.getValue();
        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final Peer localPeer = localPeerInjector.getValue();
        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();

        raftContext = new RaftContext();
        raftContext.setClientChannelPool(clientChannelManager);
        raftContext.setConnections(connectionPool);
        raftContext.setSubscription(subscription);
        raftContext.setSendBuffer(sendBuffer);
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

    public Injector<Peer> getLocalPeerInjector()
    {
        return localPeerInjector;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }
}
