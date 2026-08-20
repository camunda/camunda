/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class ClusterVariableWriter implements RdbmsWriter {

  private final ExecutionQueue executionQueue;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public ClusterVariableWriter(
      final ExecutionQueue executionQueue,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    this.executionQueue = executionQueue;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public void create(final ClusterVariableDbModel clusterVariable) {
    final var truncated = truncate(clusterVariable);
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CLUSTER_VARIABLE,
            WriteStatementType.INSERT,
            clusterVariable.id(),
            "io.camunda.db.rdbms.sql.ClusterVariableMapper.insert",
            truncated));
    enqueueMetadataInsert(clusterVariable.id(), truncated);
  }

  public void update(final ClusterVariableDbModel clusterVariable) {
    final var truncated = truncate(clusterVariable);
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CLUSTER_VARIABLE,
            WriteStatementType.UPDATE,
            clusterVariable.id(),
            "io.camunda.db.rdbms.sql.ClusterVariableMapper.update",
            truncated));
    // metadata is replaced via delete+insert, so the prior rows are always cleared first
    enqueueMetadataDelete(clusterVariable.id());
    enqueueMetadataInsert(clusterVariable.id(), truncated);
  }

  public void delete(final ClusterVariableDbModel clusterVariable) {
    enqueueMetadataDelete(clusterVariable.id());
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CLUSTER_VARIABLE,
            WriteStatementType.DELETE,
            clusterVariable.id(),
            "io.camunda.db.rdbms.sql.ClusterVariableMapper.delete",
            clusterVariable));
  }

  private ClusterVariableDbModel truncate(final ClusterVariableDbModel clusterVariable) {
    return clusterVariable.truncateValue(
        vendorDatabaseProperties.variableValuePreviewSize(),
        vendorDatabaseProperties.charColumnMaxBytes());
  }

  private void enqueueMetadataInsert(final String id, final ClusterVariableDbModel truncated) {
    if (truncated.metadata().isEmpty()) {
      return;
    }
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CLUSTER_VARIABLE,
            WriteStatementType.INSERT,
            id,
            "io.camunda.db.rdbms.sql.ClusterVariableMapper.insertMetadata",
            truncated));
  }

  private void enqueueMetadataDelete(final String id) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CLUSTER_VARIABLE,
            WriteStatementType.DELETE,
            id,
            "io.camunda.db.rdbms.sql.ClusterVariableMapper.deleteMetadata",
            id));
  }
}
