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

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.*;
import static io.zeebe.broker.clustering.orchestration.ClusterOrchestrationLayerServiceNames.*;
import static io.zeebe.broker.transport.TransportServiceNames.*;

import io.zeebe.broker.clustering.api.ManagementApiRequestHandlerService;
import io.zeebe.broker.clustering.base.connections.RemoteAddressManager;
import io.zeebe.broker.clustering.base.gossip.GossipJoinService;
import io.zeebe.broker.clustering.base.gossip.GossipService;
import io.zeebe.broker.clustering.base.partitions.BootstrapPartitions;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfigurationManagerService;
import io.zeebe.broker.clustering.base.snapshots.SnapshotReplicationInstallService;
import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.TopologyManagerService;
import io.zeebe.broker.clustering.orchestration.ReplicationFactorService;
import io.zeebe.broker.clustering.orchestration.nodes.NodeSelector;
import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.ServiceContainer;

/** Installs the clustering component into the broker. */
public class ClusterComponent implements Component {

  @Override
  public void init(final SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    initClusterBaseLayer(context, serviceContainer);
    initClusterOrchestrationLayer(context, serviceContainer);
  }

  private void initClusterBaseLayer(
      final SystemContext context, final ServiceContainer serviceContainer) {
    final BrokerCfg brokerConfig = context.getBrokerConfiguration();
    final NetworkCfg networkCfg = brokerConfig.getNetwork();
    final CompositeServiceBuilder baseLayerInstall =
        serviceContainer.createComposite(CLUSTERING_BASE_LAYER);

    final NodeInfo localMember =
        new NodeInfo(
            brokerConfig.getCluster().getNodeId(),
            networkCfg.getClient().toSocketAddress(),
            networkCfg.getManagement().toSocketAddress(),
            networkCfg.getReplication().toSocketAddress(),
            networkCfg.getSubscription().toSocketAddress());

    final TopologyManagerService topologyManagerService = new TopologyManagerService(localMember);

    baseLayerInstall
        .createService(TOPOLOGY_MANAGER_SERVICE, topologyManagerService)
        .dependency(GOSSIP_SERVICE, topologyManagerService.getGossipInjector())
        .groupReference(RAFT_SERVICE_GROUP, topologyManagerService.getRaftReference())
        .install();

    final RemoteAddressManager remoteAddressManager = new RemoteAddressManager();
    baseLayerInstall
        .createService(REMOTE_ADDRESS_MANAGER_SERVICE, remoteAddressManager)
        .dependency(TOPOLOGY_MANAGER_SERVICE, remoteAddressManager.getTopologyManagerInjector())
        .dependency(
            clientTransport(MANAGEMENT_API_CLIENT_NAME),
            remoteAddressManager.getManagementClientTransportInjector())
        .dependency(
            clientTransport(REPLICATION_API_CLIENT_NAME),
            remoteAddressManager.getReplicationClientTransportInjector())
        .dependency(
            clientTransport(SUBSCRIPTION_API_CLIENT_NAME),
            remoteAddressManager.getSubscriptionClientTransportInjector())
        .install();

    final ManagementApiRequestHandlerService managementApiRequestHandlerService =
        new ManagementApiRequestHandlerService(brokerConfig);
    baseLayerInstall
        .createService(
            MANAGEMENT_API_REQUEST_HANDLER_SERVICE_NAME, managementApiRequestHandlerService)
        .dependency(
            bufferingServerTransport(MANAGEMENT_API_SERVER_NAME),
            managementApiRequestHandlerService.getServerTransportInjector())
        .dependency(
            RAFT_CONFIGURATION_MANAGER,
            managementApiRequestHandlerService.getRaftPersistentConfigurationManagerInjector())
        .groupReference(
            LEADER_PARTITION_GROUP_NAME,
            managementApiRequestHandlerService.getLeaderPartitionsGroupReference())
        .install();

    final SnapshotReplicationInstallService snapshotReplicationInstallService =
        new SnapshotReplicationInstallService(brokerConfig);
    baseLayerInstall
        .createService(SNAPSHOT_REPLICATION_INSTALL_SERVICE_NAME, snapshotReplicationInstallService)
        .groupReference(
            FOLLOWER_PARTITION_GROUP_NAME,
            snapshotReplicationInstallService.getFollowerPartitionsGroupReference())
        .install();

    initGossip(baseLayerInstall, context, localMember);
    initPartitions(baseLayerInstall, context);

    context.addRequiredStartAction(baseLayerInstall.install());
  }

  private void initGossip(
      final CompositeServiceBuilder baseLayerInstall,
      final SystemContext context,
      final NodeInfo localMember) {
    final GossipService gossipService = new GossipService(context.getBrokerConfiguration());
    baseLayerInstall
        .createService(GOSSIP_SERVICE, gossipService)
        .dependency(
            TransportServiceNames.clientTransport(TransportServiceNames.MANAGEMENT_API_CLIENT_NAME),
            gossipService.getClientTransportInjector())
        .dependency(
            TransportServiceNames.bufferingServerTransport(
                TransportServiceNames.MANAGEMENT_API_SERVER_NAME),
            gossipService.getBufferingServerTransportInjector())
        .install();

    // TODO: decide whether failure to join gossip cluster should result in broker startup fail
    final GossipJoinService gossipJoinService =
        new GossipJoinService(context.getBrokerConfiguration().getCluster(), localMember);
    baseLayerInstall
        .createService(GOSSIP_JOIN_SERVICE, gossipJoinService)
        .dependency(GOSSIP_SERVICE, gossipJoinService.getGossipInjector())
        .install();
  }

  private void initPartitions(
      final CompositeServiceBuilder baseLayerInstall, final SystemContext context) {
    final RaftPersistentConfigurationManagerService raftConfigurationManagerService =
        new RaftPersistentConfigurationManagerService(context.getBrokerConfiguration());
    baseLayerInstall
        .createService(RAFT_CONFIGURATION_MANAGER, raftConfigurationManagerService)
        .install();

    final BootstrapPartitions raftBootstrapService =
        new BootstrapPartitions(context.getBrokerConfiguration());
    baseLayerInstall
        .createService(RAFT_BOOTSTRAP_SERVICE, raftBootstrapService)
        .dependency(
            RAFT_CONFIGURATION_MANAGER, raftBootstrapService.getConfigurationManagerInjector())
        .install();
  }

  private void initClusterOrchestrationLayer(
      final SystemContext systemContext, final ServiceContainer serviceContainer) {

    final CompositeServiceBuilder compositeInstall =
        serviceContainer.createComposite(CLUSTER_ORCHESTRATION_COMPOSITE_SERVICE_NAME);

    final NodeSelector nodeSelector = new NodeSelector();
    compositeInstall
        .createService(NODE_SELECTOR_SERVICE_NAME, nodeSelector)
        .dependency(TOPOLOGY_MANAGER_SERVICE, nodeSelector.getTopologyManagerInjector())
        .install();

    final ReplicationFactorService replicationFactorService = new ReplicationFactorService();
    compositeInstall
        .createService(REPLICATION_FACTOR_SERVICE_NAME, replicationFactorService)
        .dependency(TOPOLOGY_MANAGER_SERVICE, replicationFactorService.getTopologyManagerInjector())
        .dependency(
            NODE_SELECTOR_SERVICE_NAME,
            replicationFactorService.getNodeOrchestratingServiceInjector())
        .dependency(
            clientTransport(TransportServiceNames.MANAGEMENT_API_CLIENT_NAME),
            replicationFactorService.getManagementClientApiInjector())
        .install();

    systemContext.addRequiredStartAction(compositeInstall.install());
  }
}
