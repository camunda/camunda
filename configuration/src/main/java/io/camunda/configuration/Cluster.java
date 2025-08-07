/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Set;

public class Cluster {

  private static final String PREFIX = "camunda.cluster";

  private static final String LEGACY_NODEID_PROPERTY = "zeebe.broker.cluster.nodeId";
  private static final String LEGACY_PARTITION_COUNT_PROPERTY =
      "zeebe.broker.cluster.partitionsCount";
  private static final String LEGACY_REPLICATION_FACTOR_PROPERTY =
      "zeebe.broker.cluster.replicationFactor";
  private static final String LEGACY_SIZE_PROPERTY = "zeebe.broker.cluster.clusterSize";

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
        Set.of(LEGACY_NODEID_PROPERTY));
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
        Set.of(LEGACY_PARTITION_COUNT_PROPERTY));
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
        Set.of(LEGACY_REPLICATION_FACTOR_PROPERTY));
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
        Set.of(LEGACY_SIZE_PROPERTY));
  }

  public void setSize(final int size) {
    this.size = size;
  }
}
