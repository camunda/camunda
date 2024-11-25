/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.tasklist.TaskVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
    assertThat(underTest.getEntityType()).isEqualTo(TaskVariableEntity.class);
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
  void shouldGenerateIds() {
    // given
    final long expectedId = 123;
    final String name = "name";
    final Record<VariableRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.VARIABLE,
            r ->
                r.withIntent(VariableIntent.CREATED)
                    .withValue(
                        ImmutableVariableRecordValue.builder()
                            .withName("name")
                            .withScopeKey(expectedId)
                            .build()));

    // when
    final var idList = underTest.generateIds(processInstanceRecord);

    // then
    assertThat(idList).containsExactly(expectedId + "-" + name);
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
    final TaskVariableEntity inputEntity =
        new TaskVariableEntity()
            .setId("111")
            .setValue("value")
            .setIsTruncated(false)
            .setScopeKey(123L);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    final Map<String, Object> updateFieldsMap = new HashMap<>();
    updateFieldsMap.put(TaskTemplate.VARIABLE_FULL_VALUE, null);
    updateFieldsMap.put(TaskTemplate.VARIABLE_VALUE, "value");
    updateFieldsMap.put(TaskTemplate.IS_TRUNCATED, false);

    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            inputEntity.getId(),
            inputEntity,
            updateFieldsMap,
            String.valueOf(inputEntity.getScopeKey()));
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
    final TaskVariableEntity variableEntity =
        new TaskVariableEntity().setId(variableScopeKey + "-" + variableRecordValue.getName());
    underTest.updateEntity(variableRecord, variableEntity);

    // then
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
    assertThat(variableEntity.getJoin().getParent()).isEqualTo(variableRecordValue.getScopeKey());
    assertThat(variableEntity.getJoin().getName())
        .isEqualTo(TaskJoinRelationshipType.LOCAL_VARIABLE.getType());
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
    final TaskVariableEntity variableEntity =
        new TaskVariableEntity().setId(processInstanceKey + "-" + variableRecordValue.getName());
    underTest.updateEntity(variableRecord, variableEntity);

    // then
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
    assertThat(variableEntity.getJoin().getParent()).isEqualTo(variableRecordValue.getScopeKey());
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
            .build();

    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(ProcessIntent.CREATED).withValue(variableRecordValue));

    // when
    final TaskVariableEntity variableEntity = new TaskVariableEntity();
    underTest.updateEntity(variableRecord, variableEntity);

    // then
    assertThat(variableEntity.getValue()).isEqualTo("v".repeat(underTest.variableSizeThreshold));
    assertThat(variableEntity.getFullValue())
        .isEqualTo("v".repeat(underTest.variableSizeThreshold + 1));
    assertThat(variableEntity.getIsTruncated()).isTrue();
  }
}
