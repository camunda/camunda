/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.List;
import java.util.Set;

public interface BrokerClusterState {

  int UNKNOWN_NODE_ID = -1;
  int NODE_ID_NULL = UNKNOWN_NODE_ID - 1;
  int PARTITION_ID_NULL = NODE_ID_NULL - 1;

  boolean isInitialized();

  int getClusterSize();

  int getPartitionsCount();

  int getReplicationFactor();

  int getLeaderForPartition(PartitionId partition);

  Set<Integer> getFollowersForPartition(PartitionId partition);

  Set<Integer> getInactiveNodesForPartition(PartitionId partition);

  /**
   * @return the node id of a random broker or {@link BrokerClusterState#UNKNOWN_NODE_ID} if no
   *     brokers are known
   */
  int getRandomBroker(String partitionGroup);

  List<Integer> getPartitions();

  List<Integer> getBrokers();

  String getBrokerAddress(int brokerId);

  String getBrokerVersion(int brokerId);

  PartitionHealthStatus getPartitionHealth(int brokerId, PartitionId partition);

  long getLastCompletedChangeId();

  String getClusterId();
}
