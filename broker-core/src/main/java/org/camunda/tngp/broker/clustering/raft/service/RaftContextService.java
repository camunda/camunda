package org.camunda.tngp.broker.clustering.raft.service;

import org.camunda.tngp.broker.clustering.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.raft.RaftContext;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class RaftContextService implements Service<RaftContext>
{
    private final Injector<ClientChannelManager> clientChannelManagerInjector = new Injector<>();
    private final Injector<TransportConnectionPool> transportConnectionPoolInjector = new Injector<>();
    private final Injector<Subscription> subscriptionInjector = new Injector<>();
    private final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    private final Injector<Peer> localPeerInjector = new Injector<>();
    private final Injector<AgentRunnerServices> agentRunnerInjector = new Injector<>();

    private RaftContext raftContext;
    private ServiceContainer serviceContainer;

    public RaftContextService(ServiceContainer serviceContainer)
    {
        this.serviceContainer = serviceContainer;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ClientChannelManager clientChannelManager = clientChannelManagerInjector.getValue();
        final TransportConnectionPool connectionPool = transportConnectionPoolInjector.getValue();
        final Subscription subscription = subscriptionInjector.getValue();
        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final Peer localPeer = localPeerInjector.getValue();
        final AgentRunnerServices agentRunner = agentRunnerInjector.getValue();

        raftContext = new RaftContext();
        raftContext.setClientChannelManager(clientChannelManager);
        raftContext.setConnections(connectionPool);
        raftContext.setSubscription(subscription);
        raftContext.setSendBuffer(sendBuffer);
        raftContext.setRaftEndpoint(localPeer.replicationEndpoint());
        raftContext.setAgentRunner(agentRunner);
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

    public Injector<ClientChannelManager> getClientChannelManagerInjector()
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

    public Injector<AgentRunnerServices> getAgentRunnerInjector()
    {
        return agentRunnerInjector;
    }
}
