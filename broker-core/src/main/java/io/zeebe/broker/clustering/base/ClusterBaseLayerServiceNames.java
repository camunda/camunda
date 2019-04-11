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
package io.zeebe.broker.clustering.base;

import io.atomix.core.Atomix;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.partitions.PartitionLeaderElection;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.distributedlog.StorageConfigurationManager;
import io.zeebe.gateway.Gateway;
import io.zeebe.servicecontainer.ServiceName;

public class ClusterBaseLayerServiceNames {
  public static final ServiceName<Void> CLUSTERING_BASE_LAYER =
      ServiceName.newServiceName("cluster.base.bootstrapped", Void.class);

  public static final ServiceName<TopologyManager> TOPOLOGY_MANAGER_SERVICE =
      ServiceName.newServiceName("cluster.base.topologyManager", TopologyManager.class);
  public static final ServiceName<Void> REMOTE_ADDRESS_MANAGER_SERVICE =
      ServiceName.newServiceName("cluster.base.remoteAddrManager", Void.class);

  public static final ServiceName<Atomix> ATOMIX_SERVICE =
      ServiceName.newServiceName("cluster.base.atomix", Atomix.class);
  public static final ServiceName<Void> ATOMIX_JOIN_SERVICE =
      ServiceName.newServiceName("cluster.base.atomix.join", Void.class);
  public static final ServiceName<PartitionLeaderElection> LEADERSHIP_SERVICE_GROUP =
      ServiceName.newServiceName("cluster.base.leadership.service", PartitionLeaderElection.class);

  public static final ServiceName<Void> DISTRIBUTED_LOG_CREATE_SERVICE =
      ServiceName.newServiceName("cluster.base.atomix.distributed.log", Void.class);

  public static final ServiceName<Gateway> GATEWAY_SERVICE =
      ServiceName.newServiceName("gateway", Gateway.class);

  public static final ServiceName<Void> PARTITIONS_BOOTSTRAP_SERVICE =
      ServiceName.newServiceName("cluster.base.partitions.bootstrap", Void.class);
  public static final ServiceName<StorageConfigurationManager> RAFT_CONFIGURATION_MANAGER =
      ServiceName.newServiceName(
          "cluster.base.raft.configurationManager", StorageConfigurationManager.class);

  public static final ServiceName<Void> RAFT_SERVICE_GROUP =
      ServiceName.newServiceName("cluster.base.raft.service", Void.class);

  public static ServiceName<Void> raftInstallServiceName(int partitionId) {
    return ServiceName.newServiceName(
        String.format("cluster.base.raft.install.partition-%d", partitionId), Void.class);
  }

  public static final ServiceName<Partition> LEADER_PARTITION_GROUP_NAME =
      ServiceName.newServiceName("cluster.base.leaderGroup", Partition.class);
  public static final ServiceName<Partition> FOLLOWER_PARTITION_GROUP_NAME =
      ServiceName.newServiceName("cluster.base.followerGroup", Partition.class);
}
