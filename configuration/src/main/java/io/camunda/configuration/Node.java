/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.broker.system.configuration.partitioning.FixedPartitionCfg.NodeCfg;

public class Node {

  /**
   * The default node ID is 0, taken from the single node deployment, commonly used for development.
   */
  private static final int DEFAULT_NODE_ID = 0;

  /**
   * The default priority is 1. When priority election is not enabled, this has no impact on the
   * system, and can be left as is. If priority election is enabled, all nodes can have priority 1,
   * but then the election is essentially the same as a normal election without any priorities.
   */
  private static final int DEFAULT_PRIORITY = 1;

  /** The node ID for this node configuration. Defaults to {@link #DEFAULT_NODE_ID} (0). */
  private int nodeId = DEFAULT_NODE_ID;

  /** The priority for this node configuration. Defaults to {@link #DEFAULT_PRIORITY} (1) */
  private int priority = DEFAULT_PRIORITY;

  public Node() {}

  public Node(final int nodeId) {
    this.nodeId = nodeId;
  }

  public int getNodeId() {
    return nodeId;
  }

  public void setNodeId(final int nodeId) {
    this.nodeId = nodeId;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(final int priority) {
    this.priority = priority;
  }

  public NodeCfg toNodeCfg() {
    final NodeCfg nodeCfg = new NodeCfg();
    nodeCfg.setNodeId(nodeId);
    nodeCfg.setPriority(priority);
    return nodeCfg;
  }
}
