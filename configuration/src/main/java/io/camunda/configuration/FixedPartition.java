/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg;
import java.util.Collections;
import java.util.List;

public class FixedPartition {

  /**
   * The default partition ID is 1, taken from the single node, single partition deployment commonly
   * used for development.
   */
  private static final int DEFAULT_PARTITION_ID = 1;

  /**
   * The partition ID for this fixed partition configuration. Defaults to {@link
   * #DEFAULT_PARTITION_ID} (1).
   */
  private int partitionId = DEFAULT_PARTITION_ID;

  /**
   * The list of nodes assigned to this fixed partition configuration. Initialized as an empty list.
   */
  private List<Node> nodes = Collections.emptyList();

  public FixedPartition() {}

  public FixedPartition(final int partitionId, final List<Node> nodes) {
    this.partitionId = partitionId;
    this.nodes = nodes;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public List<Node> getNodes() {
    return nodes;
  }

  public void setNodes(final List<Node> nodes) {
    this.nodes = nodes;
  }

  public FixedPartitionCfg toFixedPartitionCfg() {
    final var fixedPartitionCfg = new FixedPartitionCfg();
    fixedPartitionCfg.setPartitionId(partitionId);
    fixedPartitionCfg.setNodes(nodes.stream().map(Node::toNodeCfg).toList());
    return fixedPartitionCfg;
  }
}
