/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.handlers.UserTaskVariableHandler.UserTaskVariableBatch;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class UserTaskVariableHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-tasklist-task";
  private final UserTaskVariableHandler underTest = new UserTaskVariableHandler(indexName, 100);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.VARIABLE);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(UserTaskVariableBatch.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    Arrays.stream(VariableIntent.values())
        .filter(i -> i != VariableIntent.MIGRATED)
        .forEach(
            intent -> {
              final Record<VariableRecordValue> variableRecord =
                  factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(intent));
              // when - then
              assertThat(underTest.handlesRecord(variableRecord)).isTrue();
            });
  }

  @Test
  void shouldNotHandleRecord() {
    // given
    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(VariableIntent.MIGRATED));
    // when - then
    assertThat(underTest.handlesRecord(variableRecord)).isFalse();
  }

  @Test
  void shouldGenerateId() {
    // given
    final long processInstanceKey = 123L;
    final String name = "name";
    final Record<VariableRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.VARIABLE,
            r ->
                r.withIntent(VariableIntent.CREATED)
                    .withValue(
                        ImmutableVariableRecordValue.builder()
                            .withName("name")
                            .withProcessInstanceKey(processInstanceKey)
                            .withScopeKey(processInstanceKey)
                            .build()));

    // when
    final var idList = underTest.generateIds(processInstanceRecord);

    // then
    assertThat(idList).containsExactly(processInstanceKey + "-" + name);
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final String id = "id";
    final UserTaskVariableBatch variableBatch =
        new UserTaskVariableBatch().setId(id).setVariables(new ArrayList<>());

    final TaskVariableEntity processVariableEntity =
        new TaskVariableEntity()
            .setId(id)
            .setValue("value")
            .setIsTruncated(false)
            .setProcessInstanceId(123L)
            .setScopeKey(123L);

    final TaskVariableEntity taskVariableEntity =
        new TaskVariableEntity()
            .setId(id + TaskTemplate.LOCAL_VARIABLE_SUFFIX)
            .setValue("value")
            .setIsTruncated(false)
            .setProcessInstanceId(123L)
            .setScopeKey(456L);

    variableBatch.getVariables().addAll(List.of(processVariableEntity, taskVariableEntity));
    final BatchRequest mockRequest = mock(BatchRequest.class);

    final ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<TaskVariableEntity> entityCaptor =
        ArgumentCaptor.forClass(TaskVariableEntity.class);
    final ArgumentCaptor<Map<String, Object>> updateFieldsCaptor =
        ArgumentCaptor.forClass(Map.class);
    final ArgumentCaptor<String> routingCaptor = ArgumentCaptor.forClass(String.class);

    // when
    underTest.flush(variableBatch, mockRequest);

    // then
    assertThat(variableBatch.getVariables()).hasSize(2);
    verify(mockRequest, times(2))
        .upsertWithRouting(
            eq(indexName),
            idCaptor.capture(),
            entityCaptor.capture(),
            updateFieldsCaptor.capture(),
            routingCaptor.capture());

    // Get the captured values
    final List<String> capturedIds = idCaptor.getAllValues();
    final List<TaskVariableEntity> capturedEntities = entityCaptor.getAllValues();
    final List<Map<String, Object>> capturedUpdateFields = updateFieldsCaptor.getAllValues();
    final List<String> capturedRoutings = routingCaptor.getAllValues();

    final Map<String, Object> updateFieldsMap = new HashMap<>();
    updateFieldsMap.put(TaskTemplate.VARIABLE_FULL_VALUE, null);
    updateFieldsMap.put(TaskTemplate.VARIABLE_VALUE, "value");
    updateFieldsMap.put(TaskTemplate.IS_TRUNCATED, false);

    // Perform assertions without considering the order
    assertThat(capturedIds).containsExactlyInAnyOrder(id, id + TaskTemplate.LOCAL_VARIABLE_SUFFIX);
    assertThat(capturedEntities)
        .containsExactlyInAnyOrder(processVariableEntity, taskVariableEntity);
    assertThat(capturedUpdateFields).containsExactlyInAnyOrder(updateFieldsMap, updateFieldsMap);
    assertThat(capturedRoutings).containsExactlyInAnyOrder("123", "123");
  }

  @Test
  void shouldUpdateEntityFromRecordForTaskScopedVariable() {
    // given
    final long processInstanceKey = 123;
    final long variableScopeKey = 456;
    final VariableRecordValue variableRecordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("v".repeat(underTest.variableSizeThreshold))
            .withProcessInstanceKey(processInstanceKey)
            .withScopeKey(variableScopeKey)
            .build();

    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(variableRecordValue));

    // when
    final var batch = new UserTaskVariableBatch().setVariables(new ArrayList<>()).setId("id");

    underTest.updateEntity(variableRecord, batch);

    // then
    assertThat(batch.getVariables()).hasSize(2);
    final var processVariable =
        batch.getVariables().stream()
            .filter(
                v ->
                    TaskJoinRelationshipType.PROCESS_VARIABLE
                        .getType()
                        .equals(v.getJoin().getName()))
            .findFirst()
            .get();

    final var localVariable =
        batch.getVariables().stream()
            .filter(
                v ->
                    TaskJoinRelationshipType.LOCAL_VARIABLE.getType().equals(v.getJoin().getName()))
            .findFirst()
            .get();

    assertThat(processVariable).isNotNull();
    assertThat(localVariable).isNotNull();

    // Local Variable Assertions
    assertThat(localVariable.getId())
        .isEqualTo(
            variableRecordValue.getScopeKey()
                + "-"
                + variableRecordValue.getName()
                + TaskTemplate.LOCAL_VARIABLE_SUFFIX);

    assertThat(localVariable.getKey()).isEqualTo(variableRecord.getKey());
    assertThat(localVariable.getName()).isEqualTo(variableRecordValue.getName());
    assertThat(localVariable.getScopeKey()).isEqualTo(variableRecordValue.getScopeKey());
    assertThat(localVariable.getTenantId()).isEqualTo(variableRecordValue.getTenantId());
    assertThat(localVariable.getValue()).isEqualTo(variableRecordValue.getValue());
    assertThat(localVariable.getProcessInstanceId()).isEqualTo(processInstanceKey);
    assertThat(localVariable.getIsTruncated()).isFalse();
    assertThat(localVariable.getFullValue()).isNull();
    assertThat(localVariable.getPosition()).isEqualTo(variableRecord.getPosition());
    assertThat(localVariable.getJoin()).isNotNull();
    assertThat(localVariable.getJoin().getParent()).isEqualTo(variableRecordValue.getScopeKey());
    assertThat(localVariable.getJoin().getName())
        .isEqualTo(TaskJoinRelationshipType.LOCAL_VARIABLE.getType());

    // Process Variable Assertions
    assertThat(processVariable.getId())
        .isEqualTo(variableRecordValue.getScopeKey() + "-" + variableRecordValue.getName());

    assertThat(processVariable.getKey()).isEqualTo(variableRecord.getKey());
    assertThat(processVariable.getName()).isEqualTo(variableRecordValue.getName());
    assertThat(processVariable.getScopeKey()).isEqualTo(variableRecordValue.getScopeKey());
    assertThat(processVariable.getTenantId()).isEqualTo(variableRecordValue.getTenantId());
    assertThat(processVariable.getValue()).isEqualTo(variableRecordValue.getValue());
    assertThat(processVariable.getProcessInstanceId()).isEqualTo(processInstanceKey);
    assertThat(processVariable.getIsTruncated()).isFalse();
    assertThat(processVariable.getFullValue()).isNull();
    assertThat(processVariable.getPosition()).isEqualTo(variableRecord.getPosition());
    assertThat(processVariable.getJoin()).isNotNull();
    assertThat(processVariable.getJoin().getParent())
        .isEqualTo(variableRecordValue.getProcessInstanceKey());
    assertThat(processVariable.getJoin().getName())
        .isEqualTo(TaskJoinRelationshipType.PROCESS_VARIABLE.getType());
  }

  @Test
  void shouldUpdateEntityFromRecordForProcessScopedVariable() {
    // given
    final long processInstanceKey = 123;
    final VariableRecordValue variableRecordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("v".repeat(underTest.variableSizeThreshold))
            .withProcessInstanceKey(processInstanceKey)
            .withScopeKey(processInstanceKey)
            .build();

    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(variableRecordValue));

    // when
    final UserTaskVariableBatch batch = new UserTaskVariableBatch().setVariables(new ArrayList<>());
    underTest.updateEntity(variableRecord, batch);

    // then
    assertThat(batch.getVariables()).hasSize(1);
    final var variableEntity = batch.getVariables().get(0);
    assertThat(variableEntity).isNotNull();
    assertThat(variableEntity.getId())
        .isEqualTo(variableRecordValue.getScopeKey() + "-" + variableRecordValue.getName());
    assertThat(variableEntity.getKey()).isEqualTo(variableRecord.getKey());
    assertThat(variableEntity.getName()).isEqualTo(variableRecordValue.getName());
    assertThat(variableEntity.getScopeKey()).isEqualTo(variableRecordValue.getScopeKey());
    assertThat(variableEntity.getTenantId()).isEqualTo(variableRecordValue.getTenantId());
    assertThat(variableEntity.getValue()).isEqualTo(variableRecordValue.getValue());
    assertThat(variableEntity.getProcessInstanceId()).isEqualTo(processInstanceKey);
    assertThat(variableEntity.getIsTruncated()).isFalse();
    assertThat(variableEntity.getFullValue()).isNull();
    assertThat(variableEntity.getPosition()).isEqualTo(variableRecord.getPosition());
    assertThat(variableEntity.getJoin()).isNotNull();
    assertThat(variableEntity.getJoin().getParent())
        .isEqualTo(variableRecordValue.getProcessInstanceKey());
    assertThat(variableEntity.getJoin().getName())
        .isEqualTo(TaskJoinRelationshipType.PROCESS_VARIABLE.getType());
  }

  @Test
  void shouldUpdateEntityFromRecordWithFullValue() {
    // given
    final VariableRecordValue variableRecordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("v".repeat(underTest.variableSizeThreshold + 1))
            .withScopeKey(123)
            .withProcessInstanceKey(123)
            .build();

    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(variableRecordValue));

    // when
    final UserTaskVariableBatch batch = new UserTaskVariableBatch().setVariables(new ArrayList<>());
    underTest.updateEntity(variableRecord, batch);

    // then
    assertThat(batch.getVariables()).hasSize(1);
    final var variableEntity = batch.getVariables().get(0);
    assertThat(variableEntity.getValue()).isEqualTo("v".repeat(underTest.variableSizeThreshold));
    assertThat(variableEntity.getFullValue())
        .isEqualTo("v".repeat(underTest.variableSizeThreshold + 1));
    assertThat(variableEntity.getIsTruncated()).isTrue();
  }
}
