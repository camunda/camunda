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

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.RaftState;
import io.zeebe.transport.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

/**
 * Represents this node's view of the cluster. Includes info about known nodes as well as partitions
 * and their current distribution to nodes.
 */
public class Topology {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final NodeInfo local;

  private final Int2ObjectHashMap<PartitionInfo> partitions = new Int2ObjectHashMap<>();
  private final List<NodeInfo> members = new ArrayList<>();

  private final Int2ObjectHashMap<NodeInfo> partitionLeaders = new Int2ObjectHashMap<>();
  private final Int2ObjectHashMap<List<NodeInfo>> partitionFollowers = new Int2ObjectHashMap<>();

  private final int clusterSize;
  private final int partitionsCount;
  private final int replicationFactor;

  public Topology(
      NodeInfo localBroker, int clusterSize, int partitionsCount, int replicationFactor) {
    this.local = localBroker;
    this.clusterSize = clusterSize;
    this.partitionsCount = partitionsCount;
    this.replicationFactor = replicationFactor;
    this.addMember(localBroker);
  }

  public NodeInfo getLocal() {
    return local;
  }

  public int getClusterSize() {
    return clusterSize;
  }

  public int getPartitionsCount() {
    return partitionsCount;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  public NodeInfo getMember(int nodeId) {
    NodeInfo member = null;

    for (int i = 0; i < members.size() && member == null; i++) {
      final NodeInfo current = members.get(i);

      if (nodeId == current.getNodeId()) {
        member = current;
      }
    }

    return member;
  }

  protected NodeInfo getMemberByApi(
      Function<NodeInfo, SocketAddress> apiAddressMapper, SocketAddress apiAddress) {
    NodeInfo member = null;

    for (int i = 0; i < members.size() && member == null; i++) {
      final NodeInfo current = members.get(i);

      if (apiAddressMapper.apply(current).equals(apiAddress)) {
        member = current;
      }
    }

    return member;
  }

  public List<NodeInfo> getMembers() {
    return members;
  }

  public NodeInfo getLeader(int partitionId) {
    return partitionLeaders.get(partitionId);
  }

  public List<NodeInfo> getFollowers(int partitionId) {
    return partitionFollowers.getOrDefault(partitionId, Collections.emptyList());
  }

  public Collection<PartitionInfo> getPartitions() {
    return new ArrayList<>(partitions.values());
  }

  public boolean addMember(NodeInfo member) {

    if (!members.contains(member)) {
      LOG.debug("Adding {} to list of known members", member);
      return members.add(member);
    }
    return false;
  }

  public void removeMember(NodeInfo member) {
    LOG.debug("Removing {} from list of known members", member);

    for (PartitionInfo partition : member.getFollowers()) {
      final List<NodeInfo> followers = partitionFollowers.get(partition.getPartitionId());

      if (followers != null) {
        followers.remove(member);
      }
    }

    for (PartitionInfo partition : member.getLeaders()) {
      partitionLeaders.remove(partition.getPartitionId());
    }

    members.remove(member);
  }

  public void removePartitionForMember(int partitionId, NodeInfo memberInfo) {
    final PartitionInfo partition = partitions.get(partitionId);
    if (partition == null) {
      return;
    }

    LOG.debug("Removing {} list of known partitions", partition);

    memberInfo.removeLeader(partition);
    memberInfo.removeFollower(partition);

    final List<NodeInfo> followers = partitionFollowers.get(partitionId);
    if (followers != null) {
      followers.remove(memberInfo);
    }

    final NodeInfo member = partitionLeaders.get(partitionId);
    if (member != null && member.equals(memberInfo)) {
      partitionLeaders.remove(partitionId);
    }
  }

  public PartitionInfo updatePartition(
      int partitionId, int replicationFactor, NodeInfo member, RaftState state) {
    List<NodeInfo> followers = partitionFollowers.get(partitionId);

    PartitionInfo partition = partitions.get(partitionId);
    if (partition == null) {
      partition = new PartitionInfo(partitionId, replicationFactor);
      partitions.put(partitionId, partition);
    }

    LOG.debug(
        "Updating partition information for partition {} on {} with state {}",
        partition,
        member,
        state);

    if (state != null) {
      switch (state) {
        case LEADER:
          if (followers != null) {
            followers.remove(member);
          }
          partitionLeaders.put(partitionId, member);

          member.removeFollower(partition);
          member.addLeader(partition);
          break;

        case FOLLOWER:
          if (member.equals(partitionLeaders.get(partitionId))) {
            partitionLeaders.remove(partitionId);
          }
          if (followers == null) {
            followers = new ArrayList<>();
            partitionFollowers.put(partitionId, followers);
          }
          if (!followers.contains(member)) {
            followers.add(member);
          }

          member.removeLeader(partition);
          member.addFollower(partition);
          break;
      }
    }

    return partition;
  }
}
