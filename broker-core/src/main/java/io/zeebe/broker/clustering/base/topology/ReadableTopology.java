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
package io.zeebe.broker.clustering.base.topology;

import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import io.zeebe.transport.SocketAddress;
import java.util.Collection;
import java.util.List;

public interface ReadableTopology {
  NodeInfo getLocal();

  NodeInfo getMember(int nodeId);

  NodeInfo getMemberByClientApi(SocketAddress apiAddress);

  NodeInfo getMemberByManagementApi(SocketAddress apiAddress);

  NodeInfo getMemberByReplicationApi(SocketAddress apiAddress);

  List<NodeInfo> getMembers();

  PartitionInfo getPartition(int partitionId);

  NodeInfo getLeader(int partitionId);

  List<NodeInfo> getFollowers(int partitionId);

  Collection<PartitionInfo> getPartitions();

  TopologyResponseDto asDto();
}
