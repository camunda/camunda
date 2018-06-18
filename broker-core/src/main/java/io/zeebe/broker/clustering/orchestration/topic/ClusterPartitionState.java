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
package io.zeebe.broker.clustering.orchestration.topic;

import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.clustering.base.topology.ReadableTopology;
import java.util.*;
import org.agrona.DirectBuffer;

public class ClusterPartitionState {
  final Map<DirectBuffer, List<PartitionNodes>> state = new HashMap<>();

  public static ClusterPartitionState computeCurrentState(final ReadableTopology topology) {
    final ClusterPartitionState currentState = new ClusterPartitionState();
    topology.getPartitions().forEach(partition -> currentState.addPartition(partition, topology));

    return currentState;
  }

  public void addPartition(final PartitionInfo partitionInfo, final ReadableTopology topology) {
    final List<PartitionNodes> listOfPartitionNodes =
        state.computeIfAbsent(partitionInfo.getTopicNameBuffer(), t -> new ArrayList<>());

    final PartitionNodes newPartitionNodes = new PartitionNodes(partitionInfo);
    newPartitionNodes.setLeader(topology.getLeader(partitionInfo.getPartitionId()));
    newPartitionNodes.addFollowers(topology.getFollowers(partitionInfo.getPartitionId()));

    listOfPartitionNodes.add(newPartitionNodes);
  }

  public List<PartitionNodes> getPartitions(final DirectBuffer topicName) {
    return state.getOrDefault(topicName, Collections.emptyList());
  }
}
