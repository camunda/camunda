/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import org.junit.jupiter.api.Test;

class IncidentWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final IncidentMapper mapper = mock(IncidentMapper.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final IncidentWriter writer =
      new IncidentWriter(executionQueue, mapper, vendorDatabaseProperties);

  @Test
  void shouldCreateIncident() {
    when(vendorDatabaseProperties.errorMessageSize()).thenReturn(5000);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(20000);

    final var model = mock(IncidentDbModel.class);
    final var truncatedModel = mock(IncidentDbModel.class);
    when(model.truncateErrorMessage(anyInt(), anyInt())).thenReturn(truncatedModel);
    when(model.incidentKey()).thenReturn(123L);

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.INCIDENT,
                    WriteStatementType.INSERT,
                    123L,
                    "io.camunda.db.rdbms.sql.IncidentMapper.insert",
                    truncatedModel)));
  }

  @Test
  void shouldUpdateIncident() {
    when(vendorDatabaseProperties.errorMessageSize()).thenReturn(5000);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(20000);

    final var model = mock(IncidentDbModel.class);
    final var truncatedModel = mock(IncidentDbModel.class);
    when(model.truncateErrorMessage(anyInt(), anyInt())).thenReturn(truncatedModel);
    when(model.incidentKey()).thenReturn(123L);

    writer.update(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.INCIDENT,
                    WriteStatementType.UPDATE,
                    123L,
                    "io.camunda.db.rdbms.sql.IncidentMapper.update",
                    truncatedModel)));
  }

  @Test
  void shouldResolveIncidentWhenNotMerged() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(false);

    writer.resolve(123L);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.INCIDENT,
                    WriteStatementType.UPDATE,
                    123L,
                    "io.camunda.db.rdbms.sql.IncidentMapper.updateState",
                    new IncidentMapper.IncidentStateDto(
                        123L, IncidentState.RESOLVED, null, null))));
  }
}
