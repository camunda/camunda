/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

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

  int getLeaderForPartition(int partition);

  Set<Integer> getFollowersForPartition(int partition);

  Set<Integer> getInactiveNodesForPartition(int partition);

  /**
   * @return the node id of a random broker or {@link BrokerClusterState#UNKNOWN_NODE_ID} if no
   *     brokers are known
   */
  int getRandomBroker();

  List<Integer> getPartitions();

  List<Integer> getBrokers();

  String getBrokerAddress(int brokerId);

  String getBrokerVersion(int brokerId);

  /**
   * Returns the composite member ID for the given broker. In zone-aware setups this is {@code
   * "$zone/$nodeId"} (e.g. {@code "us-east/0"}); otherwise it falls back to the bare node ID as a
   * string (e.g. {@code "0"}).
   *
   * @param brokerId the integer node ID of the broker
   * @return the composite member ID string, or {@code String.valueOf(brokerId)} if no member ID is
   *     known
   */
  default String getBrokerMemberId(final int brokerId) {
    return String.valueOf(brokerId);
  }

  PartitionHealthStatus getPartitionHealth(int brokerId, int partition);

  long getLastCompletedChangeId();

  String getClusterId();
}
