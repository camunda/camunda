/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Cluster {

  /** Configuration for the distributed metadata manager in the cluster. */
  @NestedConfigurationProperty private Metadata metadata = new Metadata();

  /** Network configuration for cluster communication. */
  @NestedConfigurationProperty private Network network = new Network();

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

  /**
   * Configure parameters for SWIM protocol which is used to propagate cluster membership #
   * information among brokers and gateways
   */
  @NestedConfigurationProperty private Membership membership = new Membership();

  /** Configuration for the Raft protocol in the cluster. */
  @NestedConfigurationProperty private Raft raft = new Raft();

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
    return nodeId;
  }

  public void setNodeId(final int nodeId) {
    this.nodeId = nodeId;
  }

  public int getPartitionCount() {
    return partitionCount;
  }

  public void setPartitionCount(final int partitionCount) {
    this.partitionCount = partitionCount;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  public void setReplicationFactor(final int replicationFactor) {
    this.replicationFactor = replicationFactor;
  }

  public int getSize() {
    return size;
  }

  public void setSize(final int size) {
    this.size = size;
  }

  public Membership getMembership() {
    return membership;
  }

  public void setMembership(final Membership membership) {
    this.membership = membership;
  }

  public Raft getRaft() {
    return raft;
  }

  public void setRaft(final Raft raft) {
    this.raft = raft;
  }
}
