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

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.impl.Loggers;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

public class BrokerInfo {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    // access properties instead of getters
    OBJECT_MAPPER.setVisibility(PropertyAccessor.ALL, NONE);
    OBJECT_MAPPER.setVisibility(PropertyAccessor.FIELD, ANY);
  }

  public static final String PROPERTY_NAME = "brokerInfo";
  public static final String CLIENT_API_PROPERTY = "client";

  // static configurations
  private int nodeId;
  private int partitionsCount;
  private int clusterSize;
  private int replicationFactor;
  private final Map<String, String> addresses;

  // dynamic topology info
  private final Map<Integer, Boolean> partitionRoles;

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

  public static void writeIntoProperties(Properties memberProperties, BrokerInfo distributionInfo) {
    try {
      memberProperties.setProperty(
          PROPERTY_NAME, OBJECT_MAPPER.writeValueAsString(distributionInfo));
    } catch (JsonProcessingException e) {
      Loggers.PROTOCOL_LOGGER.error(
          "Couldn't write broker info {} into member properties {}",
          distributionInfo,
          memberProperties,
          e);
    }
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

  public void setApiAddress(String apiName, String address) {
    addresses.put(apiName, address);
  }

  public Set<Integer> getPartitions() {
    return partitionRoles.keySet();
  }

  public void addLeaderForPartition(int partition) {
    partitionRoles.put(partition, true);
  }

  public void addFollowerForPartition(int partition) {
    partitionRoles.put(partition, false);
  }

  public BrokerInfo consumePartitions(
      Consumer<Integer> leaderPartitionConsumer, Consumer<Integer> followerPartitionsConsumer) {
    return consumePartitions(p -> {}, leaderPartitionConsumer, followerPartitionsConsumer);
  }

  public BrokerInfo consumePartitions(
      Consumer<Integer> partitionConsumer,
      Consumer<Integer> leaderPartitionConsumer,
      Consumer<Integer> followerPartitionsConsumer) {
    partitionRoles.forEach(
        (partition, state) -> {
          partitionConsumer.accept(partition);
          if (state) {
            leaderPartitionConsumer.accept(partition);
          } else {
            followerPartitionsConsumer.accept(partition);
          }
        });
    return this;
  }

  public String getApiAddress(String apiName) {
    return addresses.get(apiName);
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
