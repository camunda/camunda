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

import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import java.util.ArrayList;
import java.util.List;

public class PartitionNodes {

  private final PartitionInfo partitionInfo;
  private NodeInfo leader;
  private final List<NodeInfo> followers;

  private final List<NodeInfo> nodes;

  public PartitionNodes(PartitionInfo partitionInfo) {
    this.partitionInfo = partitionInfo;
    this.followers = new ArrayList<>();
    this.nodes = new ArrayList<>();
  }

  public int getPartitionId() {
    return partitionInfo.getPartitionId();
  }

  public PartitionInfo getPartitionInfo() {
    return partitionInfo;
  }

  public List<NodeInfo> getFollowers() {
    return followers;
  }

  public void addFollowers(List<NodeInfo> newNodes) {
    followers.addAll(newNodes);
    nodes.addAll(newNodes);
  }

  public NodeInfo getLeader() {
    return leader;
  }

  public void setLeader(NodeInfo newLeader) {
    if (leader != null) {
      nodes.remove(leader);
    }
    if (newLeader != null) {
      this.leader = newLeader;
      nodes.add(newLeader);
    }
  }

  public List<NodeInfo> getNodes() {
    return nodes;
  }
}
