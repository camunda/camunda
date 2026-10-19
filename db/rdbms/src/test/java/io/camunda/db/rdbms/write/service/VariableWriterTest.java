/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionVariableNameLookupDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel.ProtectionModeDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.QueueItemMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VariableWriterTest {

  final VariableDbModel model = mock(VariableDbModel.class);
  final VariableDbModel truncatedModel = mock(VariableDbModel.class);
  private final ExecutionQueue executionQueue = mock(ExecutionQueue.class);
  private final VariableMapper mapper = mock(VariableMapper.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final RdbmsWriterConfig config = mock(RdbmsWriterConfig.class, RETURNS_DEEP_STUBS);
  private final VariableWriter writer =
      new VariableWriter(executionQueue, mapper, vendorDatabaseProperties, config);

  @BeforeEach
  void setup() {
    when(vendorDatabaseProperties.variableValuePreviewSize()).thenReturn(1000);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(4000);
    when(model.truncateValue(anyInt(), anyInt())).thenReturn(truncatedModel);
  }

  @Test
  void shouldCreateVariable() {
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
  void shouldQueueProtectionModesInsertsForProtectedVariable() {
    // given
    when(model.variableKey()).thenReturn(123L);
    when(truncatedModel.variableKey()).thenReturn(123L);
    when(truncatedModel.protectionModes()).thenReturn(Set.of(ProtectionModeDbModel.REDACT));

    // when
    writer.create(model);

    // then
    verify(executionQueue)
        .executeInQueue(
            argThat(
                queueItem ->
                    queueItem.contextType() == ContextType.VARIABLE
                        && queueItem.statementType() == WriteStatementType.INSERT
                        && queueItem.order() > 0
                        && queueItem
                            .statementId()
                            .equals("io.camunda.db.rdbms.sql.VariableMapper.insertProtectionModes")
                        && queueItem
                            .parameter()
                            .equals(
                                new VariableMapper.ProtectionModesDto(
                                    123L, Set.of(ProtectionModeDbModel.REDACT)))));
  }

  @Test
  void shouldNotQueueProtectionModesInsertsForUnprotectedVariable() {
    // given
    when(model.variableKey()).thenReturn(123L);
    when(truncatedModel.variableKey()).thenReturn(123L);
    when(truncatedModel.protectionModes()).thenReturn(Set.of());

    // when
    writer.create(model);

    // then
    verify(executionQueue, never())
        .executeInQueue(
            argThat(
                queueItem ->
                    queueItem.statementId().endsWith("VariableMapper.insertProtectionModes")));
  }

  @Test
  void shouldQueueLookupInsertOnFirstSeenVariableName() {
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
                                "io.camunda.db.rdbms.sql.VariableMapper.insertLookupIfNotExists")
                        && queueItem.parameter()
                            instanceof final ProcessDefinitionVariableNameLookupDbModel m
                        && m.processDefinitionKey() == 456L
                        && "amount".equals(m.varName())));
  }

  @Test
  void shouldNotQueueLookupInsertForAlreadyCachedVariableName() {
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
  void shouldDeleteLookupByProcessDefinitionKeys() {
    // given
    final var processDefinitionKeys = List.of(456L, 789L);

    // when
    writer.deleteLookupByProcessDefinitionKeys(processDefinitionKeys, 1000);

    // then: direct mapper call so the delete is executed synchronously (not queued)
    verify(mapper).deleteLookupByProcessDefinitionKeys(processDefinitionKeys, 1000);
    verify(executionQueue, never())
        .executeInQueue(
            argThat(
                item ->
                    item.contextType() == ContextType.PROCESS_DEF_VAR_NAME_LOOKUP
                        && item.statementType() == WriteStatementType.DELETE));
  }

  @Test
  void shouldEvictCacheOnLookupDelete() {
    when(model.variableKey()).thenReturn(123L);
    when(truncatedModel.variableKey()).thenReturn(123L);
    when(model.processDefinitionKey()).thenReturn(456L);
    when(model.name()).thenReturn("amount");

    // given: first create primes the cache
    writer.create(model);
    // delete evicts the cache entry
    writer.deleteLookupByProcessDefinitionKeys(List.of(456L), 1000);
    // second create with same (pdKey, name) — must re-queue a lookup insert
    when(model.variableKey()).thenReturn(124L);
    when(truncatedModel.variableKey()).thenReturn(124L);
    writer.create(model);

    // then: mapper called once for the delete; lookup insert queued twice (before and after
    // eviction)
    verify(mapper).deleteLookupByProcessDefinitionKeys(List.of(456L), 1000);
    verify(executionQueue, org.mockito.Mockito.times(2))
        .executeInQueue(
            argThat(
                item ->
                    item.contextType() == ContextType.PROCESS_DEF_VAR_NAME_LOOKUP
                        && item.statementType() == WriteStatementType.INSERT));
  }

  @Test
  void shouldUpdateVariable() {
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

  @Test
  void shouldReplaceProtectionModesOnUpdate() {
    // given
    when(model.variableKey()).thenReturn(123L);
    when(model.protectionModes()).thenReturn(Set.of(ProtectionModeDbModel.REDACT));

    // when
    writer.update(model);

    // then
    final var inOrder = inOrder(executionQueue);
    inOrder
        .verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.VARIABLE,
                    WriteStatementType.UPDATE,
                    123L,
                    "io.camunda.db.rdbms.sql.VariableMapper.deleteProtectionModes",
                    123L,
                    1)));
    inOrder
        .verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.VARIABLE,
                    WriteStatementType.UPDATE,
                    123L,
                    "io.camunda.db.rdbms.sql.VariableMapper.insertProtectionModes",
                    new VariableMapper.ProtectionModesDto(
                        123L, Set.of(ProtectionModeDbModel.REDACT)),
                    2)));
  }

  @Test
  void shouldOnlyDeleteProtectionModesOnUpdateWithoutModes() {
    // given
    when(model.variableKey()).thenReturn(123L);
    when(model.protectionModes()).thenReturn(Set.of());

    // when
    writer.update(model);

    // then
    verify(executionQueue)
        .executeInQueue(
            argThat(
                queueItem ->
                    queueItem.statementId().endsWith("VariableMapper.deleteProtectionModes")));
    verify(executionQueue, never())
        .executeInQueue(
            argThat(
                queueItem ->
                    queueItem.statementId().endsWith("VariableMapper.insertProtectionModes")));
  }

  @Test
  void shouldReplaceQueuedProtectionModesInsertOfSameVariable() {
    // given
    when(model.variableKey()).thenReturn(123L);
    when(model.protectionModes()).thenReturn(Set.of());
    writer.update(model);
    final var merger = ArgumentCaptor.forClass(QueueItemMerger.class);
    verify(executionQueue).tryMergeWithExistingQueueItem(merger.capture());
    final var queuedInsert =
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.UPDATE,
            123L,
            "io.camunda.db.rdbms.sql.VariableMapper.insertProtectionModes",
            new VariableMapper.ProtectionModesDto(123L, Set.of(ProtectionModeDbModel.REDACT)),
            2);

    // when
    final var canBeMerged = merger.getValue().canBeMerged(queuedInsert);
    final var merged = merger.getValue().merge(queuedInsert);

    // then
    assertThat(canBeMerged).isTrue();
    assertThat(merged.statementId())
        .isEqualTo("io.camunda.db.rdbms.sql.VariableMapper.deleteProtectionModes");
    assertThat(merged.parameter()).isEqualTo(123L);
    assertThat(merged.order()).isEqualTo(2);
  }

  @Test
  void shouldReplaceModesOfQueuedProtectionModesInsert() {
    // given
    when(model.variableKey()).thenReturn(123L);
    when(model.protectionModes()).thenReturn(Set.of(ProtectionModeDbModel.REDACT));
    writer.update(model);
    final var merger = ArgumentCaptor.forClass(QueueItemMerger.class);
    verify(executionQueue).tryMergeWithExistingQueueItem(merger.capture());
    final var queuedNoop =
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.UPDATE,
            123L,
            "io.camunda.db.rdbms.sql.VariableMapper.deleteProtectionModes",
            123L,
            2);

    // when
    final var merged = merger.getValue().merge(queuedNoop);

    // then
    assertThat(merged.statementId())
        .isEqualTo("io.camunda.db.rdbms.sql.VariableMapper.insertProtectionModes");
    assertThat(merged.parameter())
        .isEqualTo(
            new VariableMapper.ProtectionModesDto(123L, Set.of(ProtectionModeDbModel.REDACT)));
  }

  @Test
  void shouldNotMergeProtectionModesIntoOtherQueueItems() {
    // given
    when(model.variableKey()).thenReturn(123L);
    when(model.protectionModes()).thenReturn(Set.of(ProtectionModeDbModel.REDACT));
    writer.update(model);
    final var merger = ArgumentCaptor.forClass(QueueItemMerger.class);
    verify(executionQueue).tryMergeWithExistingQueueItem(merger.capture());

    // then
    final var variableUpdate =
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.UPDATE,
            123L,
            "io.camunda.db.rdbms.sql.VariableMapper.update",
            truncatedModel);
    final var otherVariableInsert =
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.UPDATE,
            456L,
            "io.camunda.db.rdbms.sql.VariableMapper.insertProtectionModes",
            new VariableMapper.ProtectionModesDto(456L, Set.of(ProtectionModeDbModel.REDACT)),
            2);
    final var createInsert =
        new QueueItem(
            ContextType.VARIABLE,
            WriteStatementType.INSERT,
            123L,
            "io.camunda.db.rdbms.sql.VariableMapper.insertProtectionModes",
            new VariableMapper.ProtectionModesDto(123L, Set.of(ProtectionModeDbModel.REDACT)),
            2);
    assertThat(merger.getValue().canBeMerged(variableUpdate)).isFalse();
    assertThat(merger.getValue().canBeMerged(otherVariableInsert)).isFalse();
    assertThat(merger.getValue().canBeMerged(createInsert)).isFalse();
  }

  @Test
  void shouldNotQueueProtectionModesInsertWhenMergedIntoExistingItem() {
    // given
    when(model.variableKey()).thenReturn(123L);
    when(model.protectionModes()).thenReturn(Set.of(ProtectionModeDbModel.REDACT));
    when(executionQueue.tryMergeWithExistingQueueItem(any())).thenReturn(true);

    // when
    writer.update(model);

    // then
    verify(executionQueue, never())
        .executeInQueue(
            argThat(
                queueItem ->
                    queueItem.statementId().endsWith("VariableMapper.insertProtectionModes")));
  }
}
