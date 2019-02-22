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
package io.zeebe.broker.clustering.base.partitions;

import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.partitionInstallServiceName;

import io.zeebe.broker.clustering.base.raft.RaftPersistentConfiguration;
import io.zeebe.broker.clustering.base.raft.RaftPersistentConfigurationManager;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import java.util.Collections;
import java.util.List;
import org.agrona.collections.IntArrayList;

/**
 * Always installed on broker startup: reads configuration of all locally available partitions and
 * starts the corresponding services (raft, logstream, partition ...)
 */
public class BootstrapPartitions implements Service<Void> {
  private final Injector<RaftPersistentConfigurationManager> configurationManagerInjector =
      new Injector<>();
  private final BrokerCfg brokerCfg;
  private final PartitionsLeaderMatrix partitionsLeaderMatrix;
  private final IntArrayList followingPartitions;
  private final IntArrayList leadingPartitions;
  private RaftPersistentConfigurationManager configurationManager;
  private ServiceStartContext startContext;

  public BootstrapPartitions(final BrokerCfg brokerCfg) {
    this.brokerCfg = brokerCfg;
    final ClusterCfg cluster = brokerCfg.getCluster();
    partitionsLeaderMatrix =
        new PartitionsLeaderMatrix(
            cluster.getPartitionsCount(), cluster.getClusterSize(), cluster.getReplicationFactor());
    final int nodeId = cluster.getNodeId();
    followingPartitions = partitionsLeaderMatrix.getFollowingPartitions(nodeId);
    leadingPartitions = partitionsLeaderMatrix.getLeadingPartitions(nodeId);
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    configurationManager = configurationManagerInjector.getValue();
    this.startContext = startContext;
    startContext.run(
        () -> {
          final List<RaftPersistentConfiguration> configurations =
              configurationManager.getConfigurations().join();

          for (final RaftPersistentConfiguration configuration : configurations) {
            installPartition(startContext, configuration);
            followingPartitions.removeInt(configuration.getPartitionId());
            leadingPartitions.removeInt(configuration.getPartitionId());
          }

          for (int i = 0; i < leadingPartitions.size(); i++) {
            installPartition(leadingPartitions.getInt(i), Collections.emptyList());
          }

          for (int i = 0; i < followingPartitions.size(); i++) {
            final IntArrayList membersForPartition =
                partitionsLeaderMatrix.getMembersForPartition(
                    brokerCfg.getCluster().getNodeId(), i);
            installPartition(followingPartitions.getInt(i), membersForPartition);
          }
        });
  }

  private void installPartition(final int partitionId, final List<Integer> members) {
    final RaftPersistentConfiguration configuration =
        configurationManager
            .createConfiguration(
                partitionId, brokerCfg.getCluster().getReplicationFactor(), members)
            .join();

    installPartition(startContext, configuration);
  }

  private void installPartition(
      final ServiceStartContext startContext, final RaftPersistentConfiguration configuration) {
    final String partitionName = Partition.getPartitionName(configuration.getPartitionId());
    final ServiceName<Void> partitionInstallServiceName =
        partitionInstallServiceName(partitionName);

    final PartitionInstallService partitionInstallService =
        new PartitionInstallService(configuration);

    startContext.createService(partitionInstallServiceName, partitionInstallService).install();
  }

  @Override
  public Void get() {
    return null;
  }

  public Injector<RaftPersistentConfigurationManager> getConfigurationManagerInjector() {
    return configurationManagerInjector;
  }
}
