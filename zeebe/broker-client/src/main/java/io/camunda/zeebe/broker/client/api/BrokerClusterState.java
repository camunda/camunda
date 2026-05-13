/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface BrokerClusterState {

  int UNKNOWN_NODE_ID = -1;
  int NODE_ID_NULL = UNKNOWN_NODE_ID - 1;
  int PARTITION_ID_NULL = NODE_ID_NULL - 1;

  boolean isInitialized();

  int getClusterSize();

  int getPartitionsCount();

  int getReplicationFactor();

  @Nullable BrokerMemberId getLeaderForPartition(int partition);

  Set<BrokerMemberId> getFollowersForPartition(int partition);

  Set<BrokerMemberId> getInactiveNodesForPartition(int partition);

  /**
   * @return the node id of a random broker or {@link BrokerClusterState#UNKNOWN_NODE_ID} if no
   *     brokers are known
   */
  @Nullable BrokerMemberId getRandomBroker();

  List<Integer> getPartitions();

  List<BrokerMemberId> getBrokers();

  @Nullable String getBrokerAddress(BrokerMemberId brokerId);

  @Nullable String getBrokerVersion(BrokerMemberId brokerId);

  @Nullable PartitionHealthStatus getPartitionHealth(BrokerMemberId brokerId, int partition);

  long getLastCompletedChangeId();

  String getClusterId();
}
