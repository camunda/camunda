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

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_JOIN_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.ATOMIX_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.CLUSTERING_BASE_LAYER;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.DISTRIBUTED_LOG_CREATE_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.FOLLOWER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.GATEWAY_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.PARTITIONS_BOOTSTRAP_SERVICE;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.RAFT_CONFIGURATION_MANAGER;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.TOPOLOGY_MANAGER_SERVICE;

import io.zeebe.broker.clustering.base.EmbeddedGatewayService;
import io.zeebe.broker.clustering.base.gossip.AtomixJoinService;
import io.zeebe.broker.clustering.base.gossip.AtomixService;
import io.zeebe.broker.clustering.base.gossip.DistributedLogService;
import io.zeebe.broker.clustering.base.partitions.BootstrapPartitions;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfigurationManagerService;
import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.TopologyManagerService;
import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.ServiceContainer;

/** Installs the clustering component into the broker. */
public class ClusterComponent implements Component {

  @Override
  public void init(final SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    initClusterBaseLayer(context, serviceContainer);
  }

  private void initClusterBaseLayer(
      final SystemContext context, final ServiceContainer serviceContainer) {
    final BrokerCfg brokerConfig = context.getBrokerConfiguration();
    final NetworkCfg networkCfg = brokerConfig.getNetwork();
    final CompositeServiceBuilder baseLayerInstall =
        serviceContainer.createComposite(CLUSTERING_BASE_LAYER);

    final NodeInfo localMember =
        new NodeInfo(
            brokerConfig.getCluster().getNodeId(), networkCfg.getClient().toSocketAddress());

    /* A hack so that DistributedLogstream primitive can create logstream services using this serviceContainer */
    LogstreamConfig.putServiceContainer(
        String.valueOf(localMember.getNodeId()), context.getServiceContainer());

    final TopologyManagerService topologyManagerService =
        new TopologyManagerService(localMember, brokerConfig.getCluster());

    baseLayerInstall
        .createService(TOPOLOGY_MANAGER_SERVICE, topologyManagerService)
        .dependency(ATOMIX_SERVICE, topologyManagerService.getAtomixInjector())
        .groupReference(
            LEADER_PARTITION_GROUP_NAME, topologyManagerService.getLeaderInstallReference())
        .groupReference(
            FOLLOWER_PARTITION_GROUP_NAME, topologyManagerService.getFollowerInstallReference())
        .install();

    if (brokerConfig.getGateway().isEnable()) {
      initGateway(baseLayerInstall, brokerConfig);
    }

    initAtomix(baseLayerInstall, context);
    initPartitions(baseLayerInstall, context);

    context.addRequiredStartAction(baseLayerInstall.install());
  }

  private void initGateway(CompositeServiceBuilder baseLayerInstall, BrokerCfg brokerConfig) {
    final EmbeddedGatewayService gatewayService = new EmbeddedGatewayService(brokerConfig);
    baseLayerInstall
        .createService(GATEWAY_SERVICE, gatewayService)
        .dependency(ATOMIX_SERVICE, gatewayService.getAtomixClusterInjector())
        .install();
  }

  private void initAtomix(
      final CompositeServiceBuilder baseLayerInstall, final SystemContext context) {

    final AtomixService atomixService = new AtomixService(context.getBrokerConfiguration());
    baseLayerInstall
        .createService(ATOMIX_SERVICE, atomixService)
        .dependency(RAFT_CONFIGURATION_MANAGER) // data directories are created
        .install();

    final AtomixJoinService atomixJoinService = new AtomixJoinService();
    // With RaftPartitionGroup AtomixJoinService completes only when majority of brokers have
    // started and join the group. Hence don't add the service to the baselayer.
    context
        .getServiceContainer()
        .createService(ATOMIX_JOIN_SERVICE, atomixJoinService)
        .dependency(TOPOLOGY_MANAGER_SERVICE)
        .dependency(ATOMIX_SERVICE, atomixJoinService.getAtomixInjector())
        .install();

    // Create distributed log primitive. No need to wait until the partitions are created.
    // TODO: Move it somewhere else. Only one node has to create it.
    final DistributedLogService distributedLogService = new DistributedLogService();
    context
        .getServiceContainer()
        .createService(DISTRIBUTED_LOG_CREATE_SERVICE, distributedLogService)
        .dependency(ATOMIX_SERVICE, distributedLogService.getAtomixInjector())
        .dependency(ATOMIX_JOIN_SERVICE)
        .install();
  }

  private void initPartitions(
      final CompositeServiceBuilder baseLayerInstall, final SystemContext context) {
    final RaftPersistentConfigurationManagerService raftConfigurationManagerService =
        new RaftPersistentConfigurationManagerService(context.getBrokerConfiguration());
    baseLayerInstall
        .createService(RAFT_CONFIGURATION_MANAGER, raftConfigurationManagerService)
        .install();

    final BootstrapPartitions partitionBootstrapService =
        new BootstrapPartitions(context.getBrokerConfiguration());
    context
        .getServiceContainer()
        .createService(PARTITIONS_BOOTSTRAP_SERVICE, partitionBootstrapService)
        .dependency(ATOMIX_SERVICE, partitionBootstrapService.getAtomixInjector())
        .dependency(ATOMIX_JOIN_SERVICE)
        .dependency(
            RAFT_CONFIGURATION_MANAGER, partitionBootstrapService.getConfigurationManagerInjector())
        .install();
  }
}
