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
package io.zeebe.broker.clustering.orchestration;

import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class NodeLoad {
  private final NodeInfo nodeInfo;
  private Set<PartitionInfo> load;

  private Set<PartitionInfo> pendings;

  public NodeLoad(final NodeInfo nodeInfo) {
    this.nodeInfo = nodeInfo;
    this.load = new HashSet<>();
    this.pendings = new HashSet<>();
  }

  public NodeInfo getNodeInfo() {
    return nodeInfo;
  }

  public Set<PartitionInfo> getLoad() {
    return load;
  }

  public boolean addPartition(final PartitionInfo partitionInfo) {
    return load.add(partitionInfo);
  }

  public boolean addPendingPartiton(final PartitionInfo partitionInfo) {
    return pendings.add(partitionInfo);
  }

  public Set<PartitionInfo> getPendings() {
    return pendings;
  }

  public boolean removePending(PartitionInfo partitionInfo) {
    return pendings.remove(partitionInfo);
  }

  public boolean doesNotHave(PartitionInfo forPartitionInfo) {
    return !load.contains(forPartitionInfo) && !pendings.contains(forPartitionInfo);
  }

  @Override
  public String toString() {
    return "NodeLoad{" + "nodeInfo=" + nodeInfo + ", load=" + load + '}';
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final NodeLoad nodeLoad = (NodeLoad) o;
    return Objects.equals(nodeInfo, nodeLoad.nodeInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodeInfo);
  }
}
