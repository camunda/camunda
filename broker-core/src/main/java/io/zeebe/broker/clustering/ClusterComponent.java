package io.zeebe.broker.clustering;

import static io.zeebe.broker.clustering.ClusterServiceNames.CLUSTER_MANAGER_CONTEXT_SERVICE;
import static io.zeebe.broker.clustering.ClusterServiceNames.CLUSTER_MANAGER_SERVICE;
import static io.zeebe.broker.clustering.ClusterServiceNames.GOSSIP_CONTEXT_SERVICE;
import static io.zeebe.broker.clustering.ClusterServiceNames.GOSSIP_PEER_SELECTOR_SERVICE;
import static io.zeebe.broker.clustering.ClusterServiceNames.GOSSIP_SERVICE;
import static io.zeebe.broker.clustering.ClusterServiceNames.PEER_LIST_SERVICE;
import static io.zeebe.broker.clustering.ClusterServiceNames.PEER_LOCAL_SERVICE;
import static io.zeebe.broker.clustering.ClusterServiceNames.RAFT_SERVICE_GROUP;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.LOG_STREAMS_MANAGER_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;
import static io.zeebe.broker.transport.TransportServiceNames.MANAGEMENT_API_CLIENT_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.MANAGEMENT_API_SERVER_NAME;

import io.zeebe.broker.clustering.gossip.service.GossipContextService;
import io.zeebe.broker.clustering.gossip.service.GossipService;
import io.zeebe.broker.clustering.gossip.service.LocalPeerService;
import io.zeebe.broker.clustering.gossip.service.PeerListService;
import io.zeebe.broker.clustering.gossip.service.PeerSelectorService;
import io.zeebe.broker.clustering.management.service.ClusterManagerContextService;
import io.zeebe.broker.clustering.management.service.ClusterManagerService;
import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.broker.transport.cfg.SocketBindingCfg;
import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.transport.SocketAddress;

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
        final SocketAddress clientEndpoint = createEndpoint(config, config.clientApi);
        final SocketAddress managementEndpoint = createEndpoint(config, config.managementApi);
        final SocketAddress replicationEndpoint = createEndpoint(config, config.replicationApi);

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
//        final String component = "gossip";
//
//        final GossipConfiguration gossip = config.gossip;
//        int clientChannelManagerCapacity = gossip.numClientChannelMax;
//        if (clientChannelManagerCapacity <= 0)
//        {
        // TODO: sollte diese lustige Rechnung irgendwo drin aufgehen?
//            clientChannelManagerCapacity = gossip.disseminatorCapacity + (gossip.failureDetectionCapacity * gossip.failureDetectionProbeCapacity) + 1;
//        }

        // TODO: init server and client transport for receiving and making gossip requests
//
//        final TransportConnectionPoolService transportConnectionPoolService = new TransportConnectionPoolService();
//        final ServiceName<TransportConnectionPool> transportConnectionPoolServiceName = transportConnectionPoolName(component);
//        serviceContainer.createService(transportConnectionPoolServiceName, transportConnectionPoolService)
//            .dependency(TRANSPORT, transportConnectionPoolService.getTransportInjector())
//            .install();
//
//        final ClientChannelManagerService clientChannelManagerService = new ClientChannelManagerService(clientChannelManagerCapacity);
//        final ServiceName<ChannelManager> clientChannelManagerServiceName = clientChannelManagerName(component);
//        serviceContainer.createService(clientChannelManagerServiceName, clientChannelManagerService)
//            .dependency(TRANSPORT, clientChannelManagerService.getTransportInjector())
//            .dependency(transportConnectionPoolServiceName, clientChannelManagerService.getTransportConnectionPoolInjector())
//            .dependency(serverSocketBindingReceiveBufferName(MANAGEMENT_SOCKET_BINDING_NAME), clientChannelManagerService.getReceiveBufferInjector())
//            .install();
//
//        final SubscriptionService subscriptionService = new SubscriptionService();
//        final ServiceName<Subscription> subscriptionServiceName = subscriptionServiceName(component);
//        serviceContainer.createService(subscriptionServiceName, subscriptionService)
//            .dependency(serverSocketBindingReceiveBufferName(MANAGEMENT_SOCKET_BINDING_NAME), subscriptionService.getReceiveBufferInjector())
//            .install();

        final PeerSelectorService peerSelectorService = new PeerSelectorService();
        serviceContainer.createService(GOSSIP_PEER_SELECTOR_SERVICE, peerSelectorService)
            .dependency(PEER_LIST_SERVICE, peerSelectorService.getPeerListInjector())
            .install();

        final GossipContextService gossipContextService = new GossipContextService(config.gossip);
        serviceContainer.createService(GOSSIP_CONTEXT_SERVICE, gossipContextService)
            .dependency(PEER_LIST_SERVICE, gossipContextService.getPeerListInjector())
            .dependency(PEER_LOCAL_SERVICE, gossipContextService.getLocalPeerInjector())
            .dependency(GOSSIP_PEER_SELECTOR_SERVICE, gossipContextService.getPeerSelectorInjector())
            .dependency(TransportServiceNames.clientTransport(TransportServiceNames.MANAGEMENT_API_CLIENT_NAME), gossipContextService.getClientTransportInjector())
            .dependency(TransportServiceNames.bufferingServerTransport(TransportServiceNames.MANAGEMENT_API_SERVER_NAME), gossipContextService.getManagementApiTransportInjector())
            .install();

        final GossipService gossipService = new GossipService();
        serviceContainer.createService(GOSSIP_SERVICE, gossipService)
            .dependency(ACTOR_SCHEDULER_SERVICE, gossipService.getActorSchedulerInjector())
            .dependency(GOSSIP_CONTEXT_SERVICE, gossipService.getGossipContextInjector())
            .install();
    }

    protected void initClusterManager(final ServiceContainer serviceContainer, final TransportComponentCfg config)
    {
//        final TransportConnectionPoolService transportConnectionPoolService = new TransportConnectionPoolService();
//        final ServiceName<TransportConnectionPool> transportConnectionPoolServiceName = transportConnectionPoolName("management");
//        serviceContainer.createService(transportConnectionPoolServiceName, transportConnectionPoolService)
//            .dependency(TRANSPORT, transportConnectionPoolService.getTransportInjector())
//            .install();

        // TODO: make capacity of client channel manager configurable
//        final ClientChannelManagerService clientChannelManagerService = new ClientChannelManagerService(100);
//        final ServiceName<ChannelManager> clientChannelManagerServiceName = clientChannelManagerName("management");
//        serviceContainer.createService(clientChannelManagerServiceName, clientChannelManagerService)
//            .dependency(TRANSPORT, clientChannelManagerService.getTransportInjector())
//            .dependency(transportConnectionPoolServiceName, clientChannelManagerService.getTransportConnectionPoolInjector())
//            .dependency(serverSocketBindingReceiveBufferName(MANAGEMENT_SOCKET_BINDING_NAME), clientChannelManagerService.getReceiveBufferInjector())
//            .install();

//        final SubscriptionService subscriptionService = new SubscriptionService();
//        final ServiceName<Subscription> subscriptionServiceName = subscriptionServiceName("management");
//        serviceContainer.createService(subscriptionServiceName, subscriptionService)
//            .dependency(serverSocketBindingReceiveBufferName(MANAGEMENT_SOCKET_BINDING_NAME), subscriptionService.getReceiveBufferInjector())
//            .install();

        final ClusterManagerContextService clusterManagementContextService = new ClusterManagerContextService();
        serviceContainer.createService(CLUSTER_MANAGER_CONTEXT_SERVICE, clusterManagementContextService)
            .dependency(TransportServiceNames.bufferingServerTransport(MANAGEMENT_API_SERVER_NAME), clusterManagementContextService.getManagementApiTransportInjector())
            .dependency(TransportServiceNames.clientTransport(MANAGEMENT_API_CLIENT_NAME), clusterManagementContextService.getClientTransportInjector())
            .dependency(PEER_LIST_SERVICE, clusterManagementContextService.getPeerListInjector())
            .dependency(PEER_LOCAL_SERVICE, clusterManagementContextService.getLocalPeerInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, clusterManagementContextService.getActorSchedulerInjector())
            .dependency(LOG_STREAMS_MANAGER_SERVICE, clusterManagementContextService.getLogStreamsManagerInjector())
            .install();

        final ClusterManagerService clusterManagerService = new ClusterManagerService(serviceContainer, config.management);
        serviceContainer.createService(CLUSTER_MANAGER_SERVICE, clusterManagerService)
            .dependency(CLUSTER_MANAGER_CONTEXT_SERVICE, clusterManagerService.getClusterManagementContextInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, clusterManagerService.getActorSchedulerInjector())
            .groupReference(RAFT_SERVICE_GROUP, clusterManagerService.getRaftGroupReference())
            .install();
    }

    protected SocketAddress createEndpoint(final TransportComponentCfg config, final SocketBindingCfg socketConfig)
    {
        final int port = socketConfig.getPort();
        final String host = socketConfig.getHost(config.host);

        final SocketAddress endpoint = new SocketAddress();
        endpoint
            .host(host)
            .port(port);

        return new SocketAddress()
                .host(host)
                .port(port);
    }

}
