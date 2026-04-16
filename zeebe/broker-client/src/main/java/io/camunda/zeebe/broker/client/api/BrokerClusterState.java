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

  /**
   * Sentinel for partition-id methods when no partition can be determined. Kept as an int because
   * partition ids remain integer across the cluster.
   */
  int PARTITION_ID_NULL = -1;

  boolean isInitialized();

  int getClusterSize();

  int getPartitionsCount();

  int getReplicationFactor();

  /**
   * Returns the member id of the current leader for the given partition, or {@code null} if no
   * leader is known.
   */
  String getLeaderForPartition(int partition);

  Set<String> getFollowersForPartition(int partition);

  Set<String> getInactiveNodesForPartition(int partition);

  /** Returns the member id of a random broker, or {@code null} if no brokers are known. */
  String getRandomBroker();

  List<Integer> getPartitions();

  List<String> getBrokers();

  String getBrokerAddress(String memberId);

  String getBrokerVersion(String memberId);

  /**
   * Returns the region the broker belongs to, or {@code null} if not configured (non-region-aware
   * clusters).
   */
  String getBrokerRegion(String memberId);

  PartitionHealthStatus getPartitionHealth(String memberId, int partition);

  long getLastCompletedChangeId();

  String getClusterId();
}
