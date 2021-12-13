/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.camunda.zeebe.util.StringUtil.LIST_SANITIZER;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ClusterCfg implements ConfigurationEntry {

  public static final List<String> DEFAULT_CONTACT_POINTS = Collections.emptyList();
  public static final int DEFAULT_NODE_ID = 0;
  public static final int DEFAULT_PARTITIONS_COUNT = 1;
  public static final int DEFAULT_REPLICATION_FACTOR = 1;
  public static final int DEFAULT_CLUSTER_SIZE = 1;
  public static final String DEFAULT_CLUSTER_NAME = "zeebe-cluster";
  private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofMillis(250);
  private static final Duration DEFAULT_ELECTION_TIMEOUT = Duration.ofMillis(2500);

  private List<String> initialContactPoints = DEFAULT_CONTACT_POINTS;

  private List<Integer> partitionIds;
  private int nodeId = DEFAULT_NODE_ID;
  private int partitionsCount = DEFAULT_PARTITIONS_COUNT;
  private int replicationFactor = DEFAULT_REPLICATION_FACTOR;
  private int clusterSize = DEFAULT_CLUSTER_SIZE;
  private String clusterName = DEFAULT_CLUSTER_NAME;
  private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
  private Duration electionTimeout = DEFAULT_ELECTION_TIMEOUT;
  private MembershipCfg membership = new MembershipCfg();
  private RaftCfg raft = new RaftCfg();

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    initPartitionIds();
  }

  private void initPartitionIds() {
    partitionIds =
        IntStream.range(START_PARTITION_ID, START_PARTITION_ID + partitionsCount)
            .boxed()
            .collect(Collectors.toList());
  }

  public List<String> getInitialContactPoints() {
    return initialContactPoints;
  }

  public void setInitialContactPoints(final List<String> initialContactPoints) {
    this.initialContactPoints = LIST_SANITIZER.apply(initialContactPoints);
  }

  public int getNodeId() {
    return nodeId;
  }

  public void setNodeId(final int nodeId) {
    this.nodeId = nodeId;
  }

  public int getPartitionsCount() {
    return partitionsCount;
  }

  public void setPartitionsCount(final int partitionsCount) {
    this.partitionsCount = partitionsCount;
  }

  public List<Integer> getPartitionIds() {
    return partitionIds;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  public void setReplicationFactor(final int replicationFactor) {
    this.replicationFactor = replicationFactor;
  }

  public int getClusterSize() {
    return clusterSize;
  }

  public void setClusterSize(final int clusterSize) {
    this.clusterSize = clusterSize;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(final String clusterName) {
    this.clusterName = clusterName;
  }

  public MembershipCfg getMembership() {
    return membership;
  }

  public void setMembership(final MembershipCfg membership) {
    this.membership = membership;
  }

  public Duration getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public void setHeartbeatInterval(final Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  public RaftCfg getRaft() {
    return raft;
  }

  public void setRaft(final RaftCfg raft) {
    this.raft = raft;
  }

  @Override
  public String toString() {
    return "ClusterCfg{"
        + "initialContactPoints="
        + initialContactPoints
        + ", partitionIds="
        + partitionIds
        + ", nodeId="
        + nodeId
        + ", partitionsCount="
        + partitionsCount
        + ", replicationFactor="
        + replicationFactor
        + ", clusterSize="
        + clusterSize
        + ", clusterName='"
        + clusterName
        + '\''
        + ", membership="
        + membership
        + ", heartbeatInterval="
        + heartbeatInterval
        + ", electionTimeout="
        + electionTimeout
        + ", raft="
        + raft
        + '}';
  }

  public Duration getElectionTimeout() {
    return electionTimeout;
  }

  public void setElectionTimeout(final Duration electionTimeout) {
    this.electionTimeout = electionTimeout;
  }
}
