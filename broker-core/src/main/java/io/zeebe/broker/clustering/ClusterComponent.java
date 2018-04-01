/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering;

import static io.zeebe.broker.transport.TransportServiceNames.*;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.api.ManagementApiRequestHandlerService;
import io.zeebe.broker.clustering.base.bootstrap.*;
import io.zeebe.broker.clustering.base.connections.RemoteAddressManager;
import io.zeebe.broker.clustering.base.gossip.GossipJoinService;
import io.zeebe.broker.clustering.base.gossip.GossipService;
import io.zeebe.broker.clustering.base.raft.config.RaftPersistentConfigurationManagerService;
import io.zeebe.broker.clustering.base.topology.TopologyManagerService;
import io.zeebe.broker.logstreams.cfg.LogStreamsCfg;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.*;
import static io.zeebe.broker.system.SystemServiceNames.*;

import io.zeebe.broker.system.*;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.broker.util.BrokerArguments;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.ServiceContainer;
import org.slf4j.Logger;

/**
 * Installs the clustering component into the broker.
 */
public class ClusterComponent implements Component
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    @Override
    public void init(final SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();
        final ConfigurationManager configurationManager = context.getConfigurationManager();
        final TransportComponentCfg transportCfg = configurationManager.readEntry("network", TransportComponentCfg.class);
        final GlobalConfiguration globalCfg = configurationManager.readEntry("global", GlobalConfiguration.class);
        final LogStreamsCfg logsCfg = configurationManager.readEntry("logs", LogStreamsCfg.class);

        initClusteringBaseLayer(context, serviceContainer, transportCfg, logsCfg);
        initBootstrapSystemPartition(context, serviceContainer, globalCfg);
    }

    private void initClusteringBaseLayer(final SystemContext context, final ServiceContainer serviceContainer, final TransportComponentCfg config, LogStreamsCfg logsCfg)
    {
        final CompositeServiceBuilder baseLayerInstall = serviceContainer.createComposite(CLUSTERING_BASE_LAYER);

        final TopologyManagerService topologyManagerService = new TopologyManagerService(config);
        baseLayerInstall.createService(TOPOLOGY_MANAGER_SERVICE, topologyManagerService)
            .dependency(GOSSIP_SERVICE, topologyManagerService.getGossipInjector())
            .groupReference(LEADER_PARTITION_GROUP_NAME, topologyManagerService.getPartitionsReference())
            .groupReference(LEADER_PARTITION_SYSTEM_GROUP_NAME, topologyManagerService.getPartitionsReference())
            .groupReference(FOLLOWER_PARTITION_SYSTEM_GROUP_NAME, topologyManagerService.getPartitionsReference())
            .groupReference(FOLLOWER_PARTITION_GROUP_NAME, topologyManagerService.getPartitionsReference())
            .install();

        final RemoteAddressManager remoteAddressManager = new RemoteAddressManager();
        baseLayerInstall.createService(REMOTE_ADDRESS_MANAGER_SERVICE, remoteAddressManager)
            .dependency(TOPOLOGY_MANAGER_SERVICE, remoteAddressManager.getTopologyManagerInjector())
            .install();

        final ManagementApiRequestHandlerService managementApiRequestHandlerService = new ManagementApiRequestHandlerService();
        baseLayerInstall.createService(MANAGEMENT_API_REQUEST_HANDLER_SERVICE_NAME, managementApiRequestHandlerService)
            .dependency(bufferingServerTransport(MANAGEMENT_API_SERVER_NAME), managementApiRequestHandlerService.getServerTransportInjector())
            .dependency(RAFT_CONFIGURATION_MANAGER, managementApiRequestHandlerService.getRaftPersistentConfigurationManagerInjector())
            .dependency(WORKFLOW_REQUEST_MESSAGE_HANDLER_SERVICE, managementApiRequestHandlerService.getWorkflowRequestMessageHandlerInjector())
            .install();

        initGossip(baseLayerInstall, config);
        initRaft(baseLayerInstall, config, logsCfg);

        context.addRequiredStartAction(baseLayerInstall.install());
    }

    private void initGossip(CompositeServiceBuilder baseLayerInstall, final TransportComponentCfg config)
    {
        final GossipService gossipService = new GossipService(config);
        baseLayerInstall.createService(GOSSIP_SERVICE, gossipService)
            .dependency(TransportServiceNames.clientTransport(TransportServiceNames.MANAGEMENT_API_CLIENT_NAME), gossipService.getClientTransportInjector())
            .dependency(TransportServiceNames.bufferingServerTransport(TransportServiceNames.MANAGEMENT_API_SERVER_NAME), gossipService.getBufferingServerTransportInjector())
            .install();

        // TODO: decide whether failure to join gossip cluster should result in broker startup fail
        final GossipJoinService gossipJoinService = new GossipJoinService(config);
        baseLayerInstall.createService(GOSSIP_JOIN_SERVICE, gossipJoinService)
            .dependency(GOSSIP_SERVICE, gossipJoinService.getGossipInjector())
            .install();
    }

    private void initRaft(CompositeServiceBuilder baseLayerInstall, final TransportComponentCfg transportCfg, LogStreamsCfg logsCfg)
    {
        final RaftPersistentConfigurationManagerService raftConfigurationManagerService = new RaftPersistentConfigurationManagerService(transportCfg, logsCfg);
        baseLayerInstall.createService(RAFT_CONFIGURATION_MANAGER, raftConfigurationManagerService)
            .install();

        final BootstrapLocalPartitions raftBootstrapService = new BootstrapLocalPartitions();
        baseLayerInstall.createService(RAFT_BOOTSTRAP_SERVICE, raftBootstrapService)
            .dependency(RAFT_CONFIGURATION_MANAGER, raftBootstrapService.getConfigurationManagerInjector())
            .install();
    }

    private void initBootstrapSystemPartition(SystemContext context, ServiceContainer serviceContainer, GlobalConfiguration configuration)
    {
        if (configuration.standalone)
        {
            LOG.info("Starting standalone broker.");

            final BootstrapSystemTopic systemPartitionBootstrapService = new BootstrapSystemTopic(1);

            serviceContainer.createService(SYSTEM_PARTITION_BOOTSTRAP_SERVICE_NAME, systemPartitionBootstrapService)
                .dependency(RAFT_BOOTSTRAP_SERVICE)
                .dependency(RAFT_CONFIGURATION_MANAGER, systemPartitionBootstrapService.getRaftPersistentConfigurationManagerInjector())
                .install();
        }
        else
        {
            LOG.info("Starting clustered broker.");

            final BrokerArguments brokerArguments = context.getBrokerArguments();
            final Integer expectedNodeCount = brokerArguments.getBoostrapExpectCount();

            if (expectedNodeCount != null)
            {
                LOG.info("Node started in bootstrap mode. Expecting {} nodes to join the cluster before bootstrap.", expectedNodeCount);

                final int replicationFactor = brokerArguments.getReplicationFactor();

                if (replicationFactor > expectedNodeCount)
                {
                    throw new RuntimeException(String.format("Configuration error: cannot bootstrap system topic.\n" +
                            "Configured replication factor is %d but expecting fewer nodes to join the cluster before bootstrap (%d).\n" +
                            "Either start more nodes or use '-system-topic-replication-factor' to configure a lower replication factor for the system topic.", replicationFactor, expectedNodeCount));
                }

                final BootstrapExpectNodes bootstrapExpectService = new BootstrapExpectNodes(replicationFactor, expectedNodeCount);
                serviceContainer.createService(SYSTEM_PARTITION_BOOTSTRAP_EXPECTED_SERVICE_NAME, bootstrapExpectService)
                    .dependency(TOPOLOGY_MANAGER_SERVICE, bootstrapExpectService.getTopologyManagerInjector())
                    .install();
            }
        }
    }
}
