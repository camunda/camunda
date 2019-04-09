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
package io.zeebe.protocol.impl.data.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.impl.Loggers;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class BrokerInfo {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static final String PROPERTY_NAME = "brokerInfo";
  public static final String CLIENT_API_PROPERTY = "client";
  public static final String MANAGEMENT_API_PROPERTY = "management";
  public static final String REPLICATION_API_PROPERTY = "replication";
  public static final String SUBSCRIPTION_API_PROPERTY = "subscription";

  // static configurations
  private int nodeId;
  private int partitionsCount;
  private int clusterSize;
  private int replicationFactor;
  private Map<String, String> addresses;

  // dynamic topology info
  private Map<Integer, Boolean> partitionRoles;

  public static BrokerInfo fromProperties(Properties properties) {
    BrokerInfo brokerInfo = null;

    final String property = properties.getProperty(PROPERTY_NAME);
    if (property != null) {
      try {
        brokerInfo = OBJECT_MAPPER.readValue(property, BrokerInfo.class);
      } catch (IOException e) {
        Loggers.PROTOCOL_LOGGER.warn(
            "Failed to deserialize broker info from property: {}", property, e);
      }
    }

    return brokerInfo;
  }

  public BrokerInfo() {
    addresses = new HashMap<>();
    partitionRoles = new HashMap<>();
  }

  public BrokerInfo(int nodeId, int partitionsCount, int clusterSize, int replicationFactor) {
    this();
    this.nodeId = nodeId;
    this.partitionsCount = partitionsCount;
    this.clusterSize = clusterSize;
    this.replicationFactor = replicationFactor;
  }

  public BrokerInfo setApiAddress(String apiName, String address) {
    addresses.put(apiName, address);
    return this;
  }

  public BrokerInfo setPartitionRole(int partition, boolean isLeader) {
    partitionRoles.put(partition, isLeader);
    return this;
  }

  public Map<String, String> getAddresses() {
    return addresses;
  }

  public Map<Integer, Boolean> getPartitionRoles() {
    return partitionRoles;
  }

  public String getApiAddress(String apiName) {
    return addresses.get(apiName);
  }

  public Boolean getPartitionNodeRole(int partition) {
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

  @Override
  public String toString() {
    return "BrokerInfo{"
        + "nodeId="
        + nodeId
        + ", partitionsCount="
        + partitionsCount
        + ", clusterSize="
        + clusterSize
        + ", replicationFactor="
        + replicationFactor
        + ", addresses="
        + addresses
        + ", partitionRoles="
        + partitionRoles
        + '}';
  }
}
