/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration.partitioning;

import java.util.ArrayList;
import java.util.List;

/**
 * Bean configuration for the {@link Scheme#FIXED} partitioning scheme. Allows users to define a
 * mapping between a partition with a given {@link #partitionId} and a set of {@link #nodes}, as
 * defined in {@link NodeCfg}.
 */
public final class FixedPartitionCfg {

  /**
   * The default partition ID is 1, taken from the single node, single partition deployment commonly
   * used for development.
   */
  private static final int DEFAULT_PARTITION_ID = 1;

  private int partitionId = DEFAULT_PARTITION_ID;
  private List<NodeCfg> nodes = new ArrayList<>();

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public List<NodeCfg> getNodes() {
    return nodes;
  }

  public void setNodes(final List<NodeCfg> nodes) {
    this.nodes = nodes;
  }

  @Override
  public String toString() {
    return "FixedPartitionCfg{" + "partitionId=" + partitionId + ", nodes=" + nodes + '}';
  }

  /**
   * Bean configuration class which lets users configure a {@link #priority} for a given node with
   * ID {@link #nodeId}. Note that the priority is only useful if you use the priority election;
   * otherwise it should be left as the default priority.
   */
  public static final class NodeCfg {

    /**
     * The default node ID is 0, taken from the single node deployment, commonly used for
     * development.
     */
    private static final int DEFAULT_NODE_ID = 0;

    /**
     * The default priority is 1. When priority election is not enabled, this has no impact on the
     * system, and can be left as is. If priority election is enabled, all nodes can have priority
     * 1, but then the election is essentially the same as a normal election without any priorities.
     */
    private static final int DEFAULT_PRIORITY = 1;

    private int nodeId = DEFAULT_NODE_ID;
    private int priority = DEFAULT_PRIORITY;

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

    @Override
    public String toString() {
      return "NodeCfg{" + "nodeId=" + nodeId + ", priority=" + priority + '}';
    }
  }
}
