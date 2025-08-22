/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Map;
import java.util.Set;

public class Cluster {

  private static final String PREFIX = "camunda.cluster";

  private static final Map<String, String> LEGACY_GATEWAY_PROPERTIES =
      Map.of("messageCompression", "zeebe.gateway.cluster.messageCompression");

  private static final Map<String, String> LEGACY_BROKER_PROPERTIES =
      Map.of(
          "nodeId", "zeebe.broker.cluster.nodeId",
          "partitionsCount", "zeebe.broker.cluster.partitionsCount",
          "replicationFactor", "zeebe.broker.cluster.replicationFactor",
          "clusterSize", "zeebe.broker.cluster.clusterSize",
          "messageCompression", "zeebe.broker.cluster.messageCompression");

  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_PROPERTIES;

  /** Configuration for the distributed metadata manager in the cluster. */
  private Metadata metadata = new Metadata();

  /** Network configuration for cluster communication. */
  private Network network = new Network();

  /**
   * Specifies the unique id of this broker node in a cluster. The id should be between 0 and number
   * of nodes in the cluster (exclusive).
   */
  private int nodeId = 0;

  /** The number of partitions in the cluster. */
  private int partitionCount = 1;

  /**
   * The number of replicas for each partition in the cluster. The replication factor cannot be
   * greater than the number of nodes in the cluster.
   */
  private int replicationFactor = 1;

  /** The number of nodes in the cluster. */
  private int size = 1;

  /** Configuration for the Raft protocol in the cluster. */
  private Raft raft = new Raft();

  /**
   * Configure compression algorithm for all message sent between the brokers and between the broker
   * and the gateway. Available options are NONE, GZIP and SNAPPY. This feature is useful when the
   * network latency between the brokers is very high (for example when the brokers are deployed in
   * different data centers). When latency is high, the network bandwidth is severely reduced. Hence
   * enabling compression helps to improve the throughput.
   *
   * <p>Note: When there is no latency enabling this may have a performance impact.
   */
  private CompressionAlgorithm compressionAlgorithm = CompressionAlgorithm.NONE;

  /** Monitoring configuration. */
  private Monitoring monitoring = new Monitoring();

  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(final Metadata metadata) {
    this.metadata = metadata;
  }

  public Network getNetwork() {
    return network;
  }

  public void setNetwork(final Network network) {
    this.network = network;
  }

  public int getNodeId() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".node-id",
        nodeId,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("nodeId")));
  }

  public void setNodeId(final int nodeId) {
    this.nodeId = nodeId;
  }

  public int getPartitionCount() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".partition-count",
        partitionCount,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("partitionsCount")));
  }

  public void setPartitionCount(final int partitionCount) {
    this.partitionCount = partitionCount;
  }

  public int getReplicationFactor() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".replication-factor",
        replicationFactor,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("replicationFactor")));
  }

  public void setReplicationFactor(final int replicationFactor) {
    this.replicationFactor = replicationFactor;
  }

  public int getSize() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".size",
        size,
        Integer.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("clusterSize")));
  }

  public void setSize(final int size) {
    this.size = size;
  }

  public Raft getRaft() {
    return raft;
  }

  public void setRaft(final Raft raft) {
    this.raft = raft;
  }

  public CompressionAlgorithm getCompressionAlgorithm() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".compression-algorithm",
        compressionAlgorithm,
        CompressionAlgorithm.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        Set.of(legacyPropertiesMap.get("messageCompression")));
  }

  public void setCompressionAlgorithm(final CompressionAlgorithm compressionAlgorithm) {
    this.compressionAlgorithm = compressionAlgorithm;
  }

  public Monitoring getMonitoring() {
    return monitoring;
  }

  public void setMonitoring(final Monitoring monitoring) {
    this.monitoring = monitoring;
  }

  @Override
  public Cluster clone() {
    final Cluster copy = new Cluster();
    copy.metadata = metadata;
    copy.network = network.clone();
    copy.nodeId = nodeId;
    copy.partitionCount = partitionCount;
    copy.replicationFactor = replicationFactor;
    copy.size = size;
    copy.raft = raft;
    copy.compressionAlgorithm = compressionAlgorithm;
    copy.monitoring = monitoring;

    return copy;
  }

  public Cluster withBrokerProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_BROKER_PROPERTIES;
    return copy;
  }

  public Cluster withGatewayProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_GATEWAY_PROPERTIES;
    return copy;
  }

  public enum CompressionAlgorithm {
    GZIP,
    NONE,
    SNAPPY
  }
}
