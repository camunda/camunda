/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.config.operate;

public class ClusterNodeProperties {

  private Integer[] partitionIds = {};

  /** Overall number of import nodes. */
  private Integer nodeCount;

  /** Id of current node, starts from 0. */
  private Integer currentNodeId;

  public Integer[] getPartitionIds() {
    return partitionIds;
  }

  public void setPartitionIds(final Integer[] partitionIds) {
    this.partitionIds = partitionIds;
  }

  public Integer getNodeCount() {
    return nodeCount;
  }

  public void setNodeCount(final Integer nodeCount) {
    this.nodeCount = nodeCount;
  }

  public Integer getCurrentNodeId() {
    return currentNodeId;
  }

  public void setCurrentNodeId(final Integer currentNodeId) {
    this.currentNodeId = currentNodeId;
  }
}
