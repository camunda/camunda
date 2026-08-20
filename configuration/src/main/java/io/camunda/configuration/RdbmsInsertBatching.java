/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.db.rdbms.write.RdbmsWriterConfig.InsertBatchingConfig;

public class RdbmsInsertBatching {
  private int maxVariableInsertBatchSize = InsertBatchingConfig.DEFAULT_VARIABLE_INSERT_BATCH_SIZE;
  private int maxAuditLogInsertBatchSize = InsertBatchingConfig.DEFAULT_AUDIT_LOG_INSERT_BATCH_SIZE;
  private int maxJobInsertBatchSize = InsertBatchingConfig.DEFAULT_JOB_INSERT_BATCH_SIZE;
  private int maxFlowNodeInsertBatchSize = InsertBatchingConfig.DEFAULT_FLOW_NODE_INSERT_BATCH_SIZE;

  public int getMaxVariableInsertBatchSize() {
    return maxVariableInsertBatchSize;
  }

  public void setMaxVariableInsertBatchSize(final int maxVariableInsertBatchSize) {
    this.maxVariableInsertBatchSize = maxVariableInsertBatchSize;
  }

  public int getMaxAuditLogInsertBatchSize() {
    return maxAuditLogInsertBatchSize;
  }

  public void setMaxAuditLogInsertBatchSize(final int maxAuditLogInsertBatchSize) {
    this.maxAuditLogInsertBatchSize = maxAuditLogInsertBatchSize;
  }

  public int getMaxJobInsertBatchSize() {
    return maxJobInsertBatchSize;
  }

  public void setMaxJobInsertBatchSize(final int maxJobInsertBatchSize) {
    this.maxJobInsertBatchSize = maxJobInsertBatchSize;
  }

  public int getMaxFlowNodeInsertBatchSize() {
    return maxFlowNodeInsertBatchSize;
  }

  public void setMaxFlowNodeInsertBatchSize(final int maxFlowNodeInsertBatchSize) {
    this.maxFlowNodeInsertBatchSize = maxFlowNodeInsertBatchSize;
  }
}
