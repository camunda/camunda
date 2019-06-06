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

import io.zeebe.transport.SocketAddress;
import org.agrona.collections.IntHashSet;

public class NodeInfo {
  private final int nodeId;
  private final SocketAddress commandApiAddress;

  private final IntHashSet leaders = new IntHashSet();
  private final IntHashSet followers = new IntHashSet();

  public NodeInfo(int nodeId, final SocketAddress commandApiAddress) {
    this.nodeId = nodeId;
    this.commandApiAddress = commandApiAddress;
  }

  public int getNodeId() {
    return nodeId;
  }

  public SocketAddress getCommandApiAddress() {
    return commandApiAddress;
  }

  public IntHashSet getLeaders() {
    return leaders;
  }

  public boolean addLeader(final int partitionId) {
    return leaders.add(partitionId);
  }

  public boolean removeLeader(final int partitionId) {
    return leaders.remove(partitionId);
  }

  public IntHashSet getFollowers() {
    return followers;
  }

  public boolean addFollower(final int partitionId) {
    return followers.add(partitionId);
  }

  public boolean removeFollower(final int partitionId) {
    return followers.remove(partitionId);
  }

  @Override
  public String toString() {
    return String.format("Node{nodeId=%d, commandApi=%s}", nodeId, commandApiAddress);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((commandApiAddress == null) ? 0 : commandApiAddress.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final NodeInfo other = (NodeInfo) obj;
    if (commandApiAddress == null) {
      return other.commandApiAddress == null;
    } else {
      return commandApiAddress.equals(other.commandApiAddress);
    }
  }
}
