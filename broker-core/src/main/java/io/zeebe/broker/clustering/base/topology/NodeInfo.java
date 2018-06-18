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
import java.util.HashSet;
import java.util.Set;

public class NodeInfo {
  private final SocketAddress clientApiAddress;
  private final SocketAddress managementApiAddress;
  private final SocketAddress replicationApiAddress;

  private final Set<PartitionInfo> leaders = new HashSet<>();
  private final Set<PartitionInfo> followers = new HashSet<>();

  public NodeInfo(
      final SocketAddress clientApiAddress,
      final SocketAddress managementApiAddress,
      final SocketAddress replicationApiAddress) {
    this.clientApiAddress = clientApiAddress;
    this.managementApiAddress = managementApiAddress;
    this.replicationApiAddress = replicationApiAddress;
  }

  public SocketAddress getClientApiAddress() {
    return clientApiAddress;
  }

  public SocketAddress getManagementApiAddress() {
    return managementApiAddress;
  }

  public SocketAddress getReplicationApiAddress() {
    return replicationApiAddress;
  }

  public Set<PartitionInfo> getLeaders() {
    return leaders;
  }

  public boolean addLeader(final PartitionInfo leader) {
    return leaders.add(leader);
  }

  public boolean removeLeader(final PartitionInfo leader) {
    return leaders.remove(leader);
  }

  public Set<PartitionInfo> getFollowers() {
    return followers;
  }

  public boolean addFollower(final PartitionInfo follower) {
    return followers.add(follower);
  }

  public boolean removeFollower(final PartitionInfo follower) {
    return followers.remove(follower);
  }

  @Override
  public String toString() {
    return String.format(
        "Node{clientApi=%s, managementApi=%s, replicationApi=%s}",
        clientApiAddress, managementApiAddress, replicationApiAddress);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((clientApiAddress == null) ? 0 : clientApiAddress.hashCode());
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
    if (clientApiAddress == null) {
      return other.clientApiAddress == null;
    } else {
      return clientApiAddress.equals(other.clientApiAddress);
    }
  }
}
