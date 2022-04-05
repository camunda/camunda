/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

public class ClusterNodeProperties {

  private Integer[] partitionIds = {};

  /**
   * Overall number of import nodes.
   */
  private Integer nodeCount;

  /**
   * Id of current node, starts from 0.
   */
  private Integer currentNodeId;

  public Integer[] getPartitionIds() {
    return partitionIds;
  }

  public void setPartitionIds(Integer[] partitionIds) {
    this.partitionIds = partitionIds;
  }

  public Integer getNodeCount() {
    return nodeCount;
  }

  public void setNodeCount(Integer nodeCount) {
    this.nodeCount = nodeCount;
  }

  public Integer getCurrentNodeId() {
    return currentNodeId;
  }

  public void setCurrentNodeId(Integer currentNodeId) {
    this.currentNodeId = currentNodeId;
  }

}
