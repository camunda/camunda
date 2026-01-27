/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import org.junit.jupiter.api.Test;

class ClusterVariableWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final ClusterVariableWriter writer =
      new ClusterVariableWriter(executionQueue, vendorDatabaseProperties);

  @Test
  void shouldCreateClusterVariable() {
    when(vendorDatabaseProperties.variableValuePreviewSize()).thenReturn(1000);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(4000);

    final var model = mock(ClusterVariableDbModel.class);
    final var truncatedModel = mock(ClusterVariableDbModel.class);
    when(model.truncateValue(anyInt(), anyInt())).thenReturn(truncatedModel);
    when(model.id()).thenReturn("var1");

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.CLUSTER_VARIABLE,
                    WriteStatementType.INSERT,
                    "var1",
                    "io.camunda.db.rdbms.sql.ClusterVariableMapper.insert",
                    truncatedModel)));
  }

  @Test
  void shouldDeleteClusterVariable() {
    final var model = mock(ClusterVariableDbModel.class);
    when(model.id()).thenReturn("var1");

    writer.delete(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.CLUSTER_VARIABLE,
                    WriteStatementType.DELETE,
                    "var1",
                    "io.camunda.db.rdbms.sql.ClusterVariableMapper.delete",
                    model)));
  }

  @Test
  void shouldUpdateClusterVariable() {
    when(vendorDatabaseProperties.variableValuePreviewSize()).thenReturn(1000);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(4000);

    final var model = mock(ClusterVariableDbModel.class);
    final var truncatedModel = mock(ClusterVariableDbModel.class);
    when(model.truncateValue(anyInt(), anyInt())).thenReturn(truncatedModel);
    when(model.id()).thenReturn("var1");

    writer.update(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.CLUSTER_VARIABLE,
                    WriteStatementType.UPDATE,
                    "var1",
                    "io.camunda.db.rdbms.sql.ClusterVariableMapper.update",
                    truncatedModel)));
  }
}
