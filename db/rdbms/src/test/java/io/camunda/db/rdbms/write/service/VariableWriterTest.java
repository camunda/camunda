/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.ProcessDefinitionVariableNameLookupMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionVariableNameLookupDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableWriterTest {

  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final VariableMapper mapper = mock(VariableMapper.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final RdbmsWriterConfig config = mock(RdbmsWriterConfig.class, RETURNS_DEEP_STUBS);
  private final ProcessDefinitionVariableNameLookupMapper lookupMapper =
      mock(ProcessDefinitionVariableNameLookupMapper.class);
  private final VariableWriter writer =
      new VariableWriter(executionQueue, mapper, vendorDatabaseProperties, config, lookupMapper);

  @Test
  void shouldCreateVariable() {
    when(vendorDatabaseProperties.variableValuePreviewSize()).thenReturn(1000);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(4000);

    final var model = mock(VariableDbModel.class);
    final var truncatedModel = mock(VariableDbModel.class);
    when(model.truncateValue(anyInt(), anyInt())).thenReturn(truncatedModel);
    when(model.variableKey()).thenReturn(123L);
    when(truncatedModel.variableKey()).thenReturn(123L);
    // processDefinitionKey is null/0 → no lookup insert
    when(model.processDefinitionKey()).thenReturn(null);

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            argThat(
                queueItem ->
                    queueItem.contextType() == ContextType.VARIABLE
                        && queueItem.statementType() == WriteStatementType.INSERT
                        && queueItem
                            .statementId()
                            .equals("io.camunda.db.rdbms.sql.VariableMapper.insert")
                        && queueItem.parameter() instanceof BatchInsertDto
                        && ((BatchInsertDto) queueItem.parameter()).dbModels().size() == 1
                        && ((BatchInsertDto) queueItem.parameter())
                            .dbModels()
                            .getFirst()
                            .equals(truncatedModel)));
    verify(executionQueue, never())
        .executeInQueue(
            argThat(item -> item.contextType() == ContextType.PROCESS_DEF_VAR_NAME_LOOKUP));
  }

  @Test
  void shouldQueueLookupInsertOnFirstSeenVariableName() {
    when(vendorDatabaseProperties.variableValuePreviewSize()).thenReturn(1000);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(4000);
    when(lookupMapper.findVariableNames(456L)).thenReturn(List.of());

    final var model = mock(VariableDbModel.class);
    final var truncatedModel = mock(VariableDbModel.class);
    when(model.truncateValue(anyInt(), anyInt())).thenReturn(truncatedModel);
    when(model.variableKey()).thenReturn(123L);
    when(truncatedModel.variableKey()).thenReturn(123L);
    when(model.processDefinitionKey()).thenReturn(456L);
    when(model.name()).thenReturn("amount");

    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            argThat(
                queueItem ->
                    queueItem.contextType() == ContextType.PROCESS_DEF_VAR_NAME_LOOKUP
                        && queueItem.statementType() == WriteStatementType.INSERT
                        && queueItem
                            .statementId()
                            .equals(
                                "io.camunda.db.rdbms.sql.ProcessDefinitionVariableNameLookupMapper.insertIfNotExists")
                        && queueItem.parameter()
                            instanceof ProcessDefinitionVariableNameLookupDbModel m
                        && m.processDefinitionKey() == 456L
                        && "amount".equals(m.varName())));
  }

  @Test
  void shouldNotQueueLookupInsertForAlreadyCachedVariableName() {
    when(vendorDatabaseProperties.variableValuePreviewSize()).thenReturn(1000);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(4000);
    when(lookupMapper.findVariableNames(anyLong())).thenReturn(List.of());

    final var model = mock(VariableDbModel.class);
    final var truncatedModel = mock(VariableDbModel.class);
    when(model.truncateValue(anyInt(), anyInt())).thenReturn(truncatedModel);
    when(model.variableKey()).thenReturn(123L);
    when(truncatedModel.variableKey()).thenReturn(123L);
    when(model.processDefinitionKey()).thenReturn(456L);
    when(model.name()).thenReturn("amount");

    // first create — primes the cache
    writer.create(model);
    // second create with same name — must NOT queue another lookup insert
    when(model.variableKey()).thenReturn(124L);
    when(truncatedModel.variableKey()).thenReturn(124L);
    writer.create(model);

    verify(executionQueue)
        .executeInQueue(
            argThat(item -> item.contextType() == ContextType.PROCESS_DEF_VAR_NAME_LOOKUP));
  }

  @Test
  void shouldUpdateVariable() {
    when(vendorDatabaseProperties.variableValuePreviewSize()).thenReturn(1000);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(4000);

    final var model = mock(VariableDbModel.class);
    final var truncatedModel = mock(VariableDbModel.class);
    when(model.truncateValue(anyInt(), anyInt())).thenReturn(truncatedModel);
    when(model.variableKey()).thenReturn(123L);

    writer.update(model);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.VARIABLE,
                    WriteStatementType.UPDATE,
                    123L,
                    "io.camunda.db.rdbms.sql.VariableMapper.update",
                    truncatedModel)));
  }
}
