package org.camunda.tngp.broker.clustering.management.service;

import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.management.ClusterManagerContext;
import org.camunda.tngp.broker.logstreams.LogStreamsManager;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.ChannelManager;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class ClusterManagerContextService implements Service<ClusterManagerContext>
{
    private final Injector<ChannelManager> clientChannelManagerInjector = new Injector<>();
    private final Injector<TransportConnectionPool> transportConnectionPoolInjector = new Injector<>();
    private final Injector<Subscription> subscriptionInjector = new Injector<>();
    private final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    private final Injector<PeerList> peerListInjector = new Injector<>();
    private final Injector<Peer> localPeerInjector = new Injector<>();
    private final Injector<AgentRunnerServices> agentRunnerInjector = new Injector<>();
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
        final AgentRunnerServices agentRunner = agentRunnerInjector.getValue();
        final LogStreamsManager logStreamsManager = logStreamsManagerInjector.getValue();

        context = new ClusterManagerContext();
        context.setAgentRunner(agentRunner);
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

    public Injector<AgentRunnerServices> getAgentRunnerInjector()
    {
        return agentRunnerInjector;
    }

    public Injector<LogStreamsManager> getLogStreamsManagerInjector()
    {
        return logStreamsManagerInjector;
    }

}
