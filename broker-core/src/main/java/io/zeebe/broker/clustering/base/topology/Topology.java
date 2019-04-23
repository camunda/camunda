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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntHashSet;
import org.slf4j.Logger;

/**
 * Represents this node's view of the cluster. Includes info about known nodes as well as partitions
 * and their current distribution to nodes.
 */
public class Topology {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final NodeInfo local;

  private final IntHashSet partitions = new IntHashSet();
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

  public List<NodeInfo> getMembers() {
    return members;
  }

  public NodeInfo getLeader(int partitionId) {
    return partitionLeaders.get(partitionId);
  }

  public List<NodeInfo> getFollowers(int partitionId) {
    return partitionFollowers.getOrDefault(partitionId, Collections.emptyList());
  }

  public IntHashSet getPartitions() {
    return partitions;
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

    for (int partitionId : member.getFollowers()) {
      final List<NodeInfo> followers = partitionFollowers.get(partitionId);

      if (followers != null) {
        followers.remove(member);
      }
    }

    for (int partitionId : member.getLeaders()) {
      partitionLeaders.remove(partitionId);
    }

    members.remove(member);
  }

  public void updatePartition(int partitionId, NodeInfo member, RaftState state) {
    List<NodeInfo> followers = partitionFollowers.get(partitionId);

    partitions.add(partitionId);
    LOG.debug(
        "Updating partition information for partition {} on {} with state {}",
        partitionId,
        member,
        state);

    if (state != null) {
      switch (state) {
        case LEADER:
          if (followers != null) {
            followers.remove(member);
          }
          partitionLeaders.put(partitionId, member);

          member.removeFollower(partitionId);
          member.addLeader(partitionId);
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

          member.removeLeader(partitionId);
          member.addFollower(partitionId);
          break;
      }
    }
  }
}
