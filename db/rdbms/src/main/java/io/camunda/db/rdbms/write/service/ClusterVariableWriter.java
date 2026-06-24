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
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CLUSTER_VARIABLE,
            WriteStatementType.INSERT,
            clusterVariable.id(),
            "io.camunda.db.rdbms.sql.ClusterVariableMapper.insert",
            clusterVariable.truncateValue(
                vendorDatabaseProperties.variableValuePreviewSize(),
                vendorDatabaseProperties.charColumnMaxBytes())));
  }

  public void update(final ClusterVariableDbModel clusterVariable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CLUSTER_VARIABLE,
            WriteStatementType.UPDATE,
            clusterVariable.id(),
            "io.camunda.db.rdbms.sql.ClusterVariableMapper.update",
            clusterVariable.truncateValue(
                vendorDatabaseProperties.variableValuePreviewSize(),
                vendorDatabaseProperties.charColumnMaxBytes())));
  }

  public void delete(final ClusterVariableDbModel clusterVariable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CLUSTER_VARIABLE,
            WriteStatementType.DELETE,
            clusterVariable.id(),
            "io.camunda.db.rdbms.sql.ClusterVariableMapper.delete",
            clusterVariable));
  }
}
