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

import io.zeebe.raft.state.RaftState;
import java.util.HashMap;
import java.util.Map;

public class TopologyDistributionInfo {

  // static configurations
  private int nodeId;
  private int partitionsCount;
  private int clusterSize;
  private int replicationFactor;
  private Map<String, String> addresses;

  // dynamic topology info
  private Map<Integer, RaftState> partitionRoles;

  public TopologyDistributionInfo() {
    addresses = new HashMap<>();
    partitionRoles = new HashMap<>();
  }

  public TopologyDistributionInfo(
      int nodeId, int partitionsCount, int clusterSize, int replicationFactor) {
    this();
    this.nodeId = nodeId;
    this.partitionsCount = partitionsCount;
    this.clusterSize = clusterSize;
    this.replicationFactor = replicationFactor;
  }

  public TopologyDistributionInfo setApiAddress(String apiName, String address) {
    addresses.put(apiName, address);
    return this;
  }

  public TopologyDistributionInfo setPartitionRole(int partition, RaftState role) {
    partitionRoles.put(partition, role);
    return this;
  }

  public Map<String, String> getAddresses() {
    return addresses;
  }

  public Map<Integer, RaftState> getPartitionRoles() {
    return partitionRoles;
  }

  public String getApiAddress(String apiName) {
    return addresses.get(apiName);
  }

  public RaftState getPartitionNodeRole(int partition) {
    return partitionRoles.get(partition);
  }

  public int getNodeId() {
    return nodeId;
  }

  public int getPartitionsCount() {
    return partitionsCount;
  }

  public int getClusterSize() {
    return clusterSize;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  public void clearPartitions() {
    partitionRoles.clear();
  }
}
