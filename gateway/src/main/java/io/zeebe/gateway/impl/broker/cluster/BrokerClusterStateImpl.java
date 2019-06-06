/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.impl.broker.cluster;

import static io.zeebe.transport.ClientTransport.UNKNOWN_NODE_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;

public class BrokerClusterStateImpl implements BrokerClusterState {

  private final Int2IntHashMap partitionLeaders;
  private final Int2ObjectHashMap<List<Integer>> partitionFollowers;
  private final Int2ObjectHashMap<String> brokerAddresses;
  private final IntArrayList brokers;
  private final IntArrayList partitions;
  private final Random randomBroker;
  private int clusterSize;
  private int partitionsCount;
  private int replicationFactor;

  public BrokerClusterStateImpl(BrokerClusterStateImpl topology) {
    this();
    if (topology != null) {
      partitionLeaders.putAll(topology.partitionLeaders);
      partitionFollowers.putAll(topology.partitionFollowers);
      brokerAddresses.putAll(topology.brokerAddresses);

      brokers.addAll(topology.brokers);
      partitions.addAll(topology.partitions);

      clusterSize = topology.clusterSize;
      partitionsCount = topology.partitionsCount;
      replicationFactor = topology.replicationFactor;
    }
  }

  public BrokerClusterStateImpl() {
    partitionLeaders = new Int2IntHashMap(NODE_ID_NULL);
    partitionFollowers = new Int2ObjectHashMap<>();
    brokerAddresses = new Int2ObjectHashMap<>();
    brokers = new IntArrayList(5, NODE_ID_NULL);
    partitions = new IntArrayList(32, PARTITION_ID_NULL);
    randomBroker = new Random();
  }

  public void setPartitionLeader(int partitionId, int leaderId) {
    partitionLeaders.put(partitionId, leaderId);
    final List<Integer> followers = partitionFollowers.get(partitionId);
    if (followers != null) {
      followers.removeIf(follower -> follower == leaderId);
    }
  }

  public void addPartitionFollower(int partitionId, int followerId) {
    partitionFollowers.computeIfAbsent(partitionId, ArrayList::new).add(followerId);
  }

  public void addPartitionIfAbsent(int partitionId) {
    if (partitions.indexOf(partitionId) == -1) {
      partitions.addInt(partitionId);
    }
  }

  public void addBrokerIfAbsent(int nodeId) {
    if (brokerAddresses.get(nodeId) == null) {
      brokerAddresses.put(nodeId, "");
      brokers.addInt(nodeId);
    }
  }

  public void setBrokerAddressIfPresent(int brokerId, String address) {
    if (brokerAddresses.get(brokerId) != null) {
      brokerAddresses.put(brokerId, address);
    }
  }

  public void removeBroker(int brokerId) {
    brokerAddresses.remove(brokerId);
    brokers.removeInt(brokerId);
    partitions.forEachOrderedInt(
        partitionId -> {
          if (partitionLeaders.get(partitionId) == brokerId) {
            partitionLeaders.remove(partitionId);
          }
          final List<Integer> followers = partitionFollowers.get(partitionId);
          if (followers != null) {
            followers.remove(new Integer(brokerId));
          }
        });
  }

  @Override
  public int getClusterSize() {
    return clusterSize;
  }

  public void setClusterSize(int clusterSize) {
    this.clusterSize = clusterSize;
  }

  @Override
  public int getPartitionsCount() {
    return partitionsCount;
  }

  public void setPartitionsCount(int partitionsCount) {
    this.partitionsCount = partitionsCount;
  }

  @Override
  public int getReplicationFactor() {
    return replicationFactor;
  }

  public void setReplicationFactor(int replicationFactor) {
    this.replicationFactor = replicationFactor;
  }

  @Override
  public int getLeaderForPartition(int partition) {
    return partitionLeaders.get(partition);
  }

  @Override
  public List<Integer> getFollowersForPartition(int partition) {
    return partitionFollowers.get(partition);
  }

  @Override
  public int getRandomBroker() {
    if (brokers.isEmpty()) {
      return UNKNOWN_NODE_ID;
    } else {
      return brokers.get(randomBroker.nextInt(brokers.size()));
    }
  }

  @Override
  public List<Integer> getPartitions() {
    return partitions;
  }

  @Override
  public List<Integer> getBrokers() {
    return brokers;
  }

  @Override
  public String getBrokerAddress(int brokerId) {
    return brokerAddresses.get(brokerId);
  }

  @Override
  public int getPartition(int index) {
    if (!partitions.isEmpty()) {
      return partitions.getInt(index % partitions.size());
    } else {
      return PARTITION_ID_NULL;
    }
  }

  @Override
  public String toString() {
    return "BrokerClusterStateImpl{"
        + "partitionLeaders="
        + partitionLeaders
        + ", brokers="
        + brokers
        + ", partitions="
        + partitions
        + ", clusterSize="
        + clusterSize
        + ", partitionsCount="
        + partitionsCount
        + ", replicationFactor="
        + replicationFactor
        + '}';
  }
}
