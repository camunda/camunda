package org.camunda.tngp.broker.clustering;

import static org.camunda.tngp.broker.clustering.ClusterServiceNames.*;
import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.*;
import static org.camunda.tngp.broker.system.SystemServiceNames.*;
import static org.camunda.tngp.broker.transport.TransportServiceNames.*;

import org.camunda.tngp.broker.clustering.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.channel.ClientChannelManagerService;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.broker.clustering.gossip.config.GossipConfiguration;
import org.camunda.tngp.broker.clustering.gossip.service.GossipContextService;
import org.camunda.tngp.broker.clustering.gossip.service.GossipService;
import org.camunda.tngp.broker.clustering.gossip.service.LocalPeerService;
import org.camunda.tngp.broker.clustering.gossip.service.PeerListService;
import org.camunda.tngp.broker.clustering.gossip.service.PeerSelectorService;
import org.camunda.tngp.broker.clustering.management.service.ClusterManagerContextService;
import org.camunda.tngp.broker.clustering.management.service.ClusterManagerService;
import org.camunda.tngp.broker.clustering.service.SubscriptionService;
import org.camunda.tngp.broker.clustering.service.TransportConnectionPoolService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.transport.cfg.SocketBindingCfg;
import org.camunda.tngp.broker.transport.cfg.TransportComponentCfg;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class ClusterComponent implements Component
{
    public static final String WORKER_NAME = "management-worker.0";

    @Override
    public void init(final SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();
        final ConfigurationManager configurationManager = context.getConfigurationManager();

        final TransportComponentCfg config = configurationManager.readEntry("network", TransportComponentCfg.class);

        initLocalPeer(serviceContainer, config);
        initPeers(serviceContainer, config);
        initGossip(serviceContainer, config);
        initClusterManager(serviceContainer, config);
    }

    protected void initLocalPeer(final ServiceContainer serviceContainer, final TransportComponentCfg config)
    {
        final Endpoint clientEndpoint = createEndpoint(config, config.clientApi);
        final Endpoint managementEndpoint = createEndpoint(config, config.managementApi);
        final Endpoint replicationEndpoint = createEndpoint(config, config.replicationApi);

        final LocalPeerService localPeerService = new LocalPeerService(clientEndpoint, managementEndpoint, replicationEndpoint);
        serviceContainer.createService(PEER_LOCAL_SERVICE, localPeerService).install();
    }

    protected void initPeers(final ServiceContainer serviceContainer, final TransportComponentCfg config)
    {
        final PeerListService peersService = new PeerListService(config.gossip);
        serviceContainer.createService(PEER_LIST_SERVICE, peersService)
            .dependency(PEER_LOCAL_SERVICE, peersService.getLocalPeerInjector())
            .install();
    }

    protected void initGossip(final ServiceContainer serviceContainer, final TransportComponentCfg config)
    {
        final String component = "gossip";

        final GossipConfiguration gossip = config.gossip;
        int clientChannelManagerCapacity = gossip.numClientChannelMax;
        if (clientChannelManagerCapacity <= 0)
        {
            clientChannelManagerCapacity = gossip.disseminatorCapacity + (gossip.failureDetectionCapacity * gossip.failureDetectionProbeCapacity) + 1;
        }

        final TransportConnectionPoolService transportConnectionPoolService = new TransportConnectionPoolService();
        final ServiceName<TransportConnectionPool> transportConnectionPoolServiceName = transportConnectionPoolName(component);
        serviceContainer.createService(transportConnectionPoolServiceName, transportConnectionPoolService)
            .dependency(TRANSPORT, transportConnectionPoolService.getTransportInjector())
            .install();

        final ClientChannelManagerService clientChannelManagerService = new ClientChannelManagerService(clientChannelManagerCapacity);
        final ServiceName<ClientChannelManager> clientChannelManagerServiceName = clientChannelManagerName(component);
        serviceContainer.createService(clientChannelManagerServiceName, clientChannelManagerService)
            .dependency(TRANSPORT, clientChannelManagerService.getTransportInjector())
            .dependency(transportConnectionPoolServiceName, clientChannelManagerService.getTransportConnectionPoolInjector())
            .dependency(serverSocketBindingReceiveBufferName(MANAGEMENT_SOCKET_BINDING_NAME), clientChannelManagerService.getReceiveBufferInjector())
            .install();

        final SubscriptionService subscriptionService = new SubscriptionService();
        final ServiceName<Subscription> subscriptionServiceName = subscriptionServiceName(component);
        serviceContainer.createService(subscriptionServiceName, subscriptionService)
            .dependency(serverSocketBindingReceiveBufferName(MANAGEMENT_SOCKET_BINDING_NAME), subscriptionService.getReceiveBufferInjector())
            .install();

        final PeerSelectorService peerSelectorService = new PeerSelectorService();
        serviceContainer.createService(GOSSIP_PEER_SELECTOR_SERVICE, peerSelectorService)
            .dependency(PEER_LIST_SERVICE, peerSelectorService.getPeerListInjector())
            .install();

        final GossipContextService gossipContextService = new GossipContextService(config.gossip);
        serviceContainer.createService(GOSSIP_CONTEXT_SERVICE, gossipContextService)
            .dependency(PEER_LIST_SERVICE, gossipContextService.getPeerListInjector())
            .dependency(PEER_LOCAL_SERVICE, gossipContextService.getLocalPeerInjector())
            .dependency(GOSSIP_PEER_SELECTOR_SERVICE, gossipContextService.getPeerSelectorInjector())
            .dependency(TRANSPORT_SEND_BUFFER, gossipContextService.getSendBufferInjector())
            .dependency(clientChannelManagerServiceName, gossipContextService.getClientChannelManagerInjector())
            .dependency(transportConnectionPoolServiceName, gossipContextService.getTransportConnectionPoolInjector())
            .dependency(subscriptionServiceName, gossipContextService.getSubscriptionInjector())
            .install();

        final GossipService gossipService = new GossipService();
        serviceContainer.createService(GOSSIP_SERVICE, gossipService)
            .dependency(AGENT_RUNNER_SERVICE, gossipService.getAgentRunnerInjector())
            .dependency(GOSSIP_CONTEXT_SERVICE, gossipService.getGossipContextInjector())
            .install();

    }

    protected void initClusterManager(final ServiceContainer serviceContainer, final TransportComponentCfg config)
    {
        final TransportConnectionPoolService transportConnectionPoolService = new TransportConnectionPoolService();
        final ServiceName<TransportConnectionPool> transportConnectionPoolServiceName = transportConnectionPoolName("management");
        serviceContainer.createService(transportConnectionPoolServiceName, transportConnectionPoolService)
            .dependency(TRANSPORT, transportConnectionPoolService.getTransportInjector())
            .install();

        // TODO: make capacity of client channel manager configurable
        final ClientChannelManagerService clientChannelManagerService = new ClientChannelManagerService(100);
        final ServiceName<ClientChannelManager> clientChannelManagerServiceName = clientChannelManagerName("management");
        serviceContainer.createService(clientChannelManagerServiceName, clientChannelManagerService)
            .dependency(TRANSPORT, clientChannelManagerService.getTransportInjector())
            .dependency(transportConnectionPoolServiceName, clientChannelManagerService.getTransportConnectionPoolInjector())
            .dependency(serverSocketBindingReceiveBufferName(MANAGEMENT_SOCKET_BINDING_NAME), clientChannelManagerService.getReceiveBufferInjector())
            .install();

        final SubscriptionService subscriptionService = new SubscriptionService();
        final ServiceName<Subscription> subscriptionServiceName = subscriptionServiceName("management");
        serviceContainer.createService(subscriptionServiceName, subscriptionService)
            .dependency(serverSocketBindingReceiveBufferName(MANAGEMENT_SOCKET_BINDING_NAME), subscriptionService.getReceiveBufferInjector())
            .install();

        final ClusterManagerContextService clusterManagementContextService = new ClusterManagerContextService();
        serviceContainer.createService(CLUSTER_MANAGER_CONTEXT_SERVICE, clusterManagementContextService)
            .dependency(TRANSPORT_SEND_BUFFER, clusterManagementContextService.getSendBufferInjector())
            .dependency(PEER_LIST_SERVICE, clusterManagementContextService.getPeerListInjector())
            .dependency(PEER_LOCAL_SERVICE, clusterManagementContextService.getLocalPeerInjector())
            .dependency(AGENT_RUNNER_SERVICE, clusterManagementContextService.getAgentRunnerInjector())
            .dependency(LOG_STREAMS_MANAGER_SERVICE, clusterManagementContextService.getLogStreamsManagerInjector())
            .dependency(clientChannelManagerServiceName, clusterManagementContextService.getClientChannelManagerInjector())
            .dependency(transportConnectionPoolServiceName, clusterManagementContextService.getTransportConnectionPoolInjector())
            .dependency(subscriptionServiceName, clusterManagementContextService.getSubscriptionInjector())
            .install();

        final ClusterManagerService clusterManagerService = new ClusterManagerService(serviceContainer, config.management);
        serviceContainer.createService(CLUSTER_MANAGER_SERVICE, clusterManagerService)
            .dependency(CLUSTER_MANAGER_CONTEXT_SERVICE, clusterManagerService.getClusterManagementContextInjector())
            .dependency(AGENT_RUNNER_SERVICE, clusterManagerService.getAgentRunnerInjector())
            .groupReference(RAFT_SERVICE_GROUP, clusterManagerService.getRaftGroupReference())
            .install();
    }

    protected Endpoint createEndpoint(final TransportComponentCfg config, final SocketBindingCfg socketConfig)
    {
        final int port = socketConfig.port;

        String host = socketConfig.host;
        if (host == null || host.isEmpty())
        {
            host = config.host;
        }

        final Endpoint endpoint = new Endpoint();
        endpoint
            .host(host)
            .port(port);

        return new Endpoint()
                .host(host)
                .port(port);
    }

}
