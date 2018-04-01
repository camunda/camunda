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

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.raft.config.RaftPersistentConfigurationManager;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.Topology.NodeInfo;
import io.zeebe.gossip.Gossip;
import io.zeebe.raft.Raft;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;

public class ClusterBaseLayerServiceNames
{
    public static final ServiceName<Void> CLUSTERING_BASE_LAYER = ServiceName.newServiceName("cluster.base.bootstrapped", Void.class);

    public static final ServiceName<NodeInfo> LOCAL_NODE = ServiceName.newServiceName("cluster.base.localNode", NodeInfo.class);

    public static final ServiceName<Void> MANAGEMENT_API_REQUEST_HANDLER_SERVICE_NAME = ServiceName.newServiceName("cluster.base.managementApiRequestHandlerService", Void.class);
    public static final ServiceName<TopologyManager> TOPOLOGY_MANAGER_SERVICE = ServiceName.newServiceName("cluster.base.topologyManager", TopologyManager.class);
    public static final ServiceName<Object> REMOTE_ADDRESS_MANAGER_SERVICE = ServiceName.newServiceName("cluster.base.remoteAddrManager", Object.class);

    public static final ServiceName<Gossip> GOSSIP_SERVICE = ServiceName.newServiceName("cluster.base.gossip", Gossip.class);
    public static final ServiceName<Object> GOSSIP_JOIN_SERVICE = ServiceName.newServiceName("cluster.base.gossip.join", Object.class);

    public static final ServiceName<Object> RAFT_BOOTSTRAP_SERVICE = ServiceName.newServiceName("cluster.base.raft.bootstrap", Object.class);
    public static final ServiceName<RaftPersistentConfigurationManager> RAFT_CONFIGURATION_MANAGER = ServiceName.newServiceName("cluster.base.raft.configurationManager", RaftPersistentConfigurationManager.class);
    public static final ServiceName<Raft> RAFT_SERVICE_GROUP = ServiceName.newServiceName("cluster.base.raft.service", Raft.class);

    public static ServiceName<RemoteAddress> remoteAddressServiceName(SocketAddress socketAddress)
    {
        return ServiceName.newServiceName(String.format("cluster.base.remoteAddress.%s", socketAddress.toString()), RemoteAddress.class);
    }

    public static ServiceName<Void> raftInstallServiceName(final String topicName, int partitionId)
    {
        return ServiceName.newServiceName(String.format("cluster.base.raft.install.%s-%d", topicName, partitionId), Void.class);
    }

    public static ServiceName<Partition> leaderPartitionServiceName(final String partitionName)
    {
        return ServiceName.newServiceName(String.format("cluster.base.partition.%s.leader", partitionName), Partition.class);
    }

    public static ServiceName<Partition> followerPartitionServiceName(final String partitionName)
    {
        return ServiceName.newServiceName(String.format("cluster.base.partition.%s.follower", partitionName), Partition.class);
    }

    public static ServiceName<Void> partitionInstallServiceName(final String partitionName)
    {
        return ServiceName.newServiceName(String.format("cluster.base.partition.install.%s", partitionName), Void.class);
    }

    public static final ServiceName<Partition> LEADER_PARTITION_GROUP_NAME = ServiceName.newServiceName("cluster.base.leaderGroup", Partition.class);
    public static final ServiceName<Partition> FOLLOWER_PARTITION_GROUP_NAME = ServiceName.newServiceName("cluster.base.followerGroup", Partition.class);


    public static final ServiceName<Partition> LEADER_PARTITION_SYSTEM_GROUP_NAME = ServiceName.newServiceName("cluster.base.leaderGroup.system", Partition.class);
    public static final ServiceName<Partition> FOLLOWER_PARTITION_SYSTEM_GROUP_NAME = ServiceName.newServiceName("cluster.base.followerGroup.system", Partition.class);

    public static final ServiceName<Void> SYSTEM_PARTITION_BOOTSTRAP_SERVICE_NAME = ServiceName.newServiceName("cluster.base.system.partition.bootstrap", Void.class);
    public static final ServiceName<Void> SYSTEM_PARTITION_BOOTSTRAP_EXPECTED_SERVICE_NAME = ServiceName.newServiceName("cluster.base.system.partition.bootstrap.expect", Void.class);

}
