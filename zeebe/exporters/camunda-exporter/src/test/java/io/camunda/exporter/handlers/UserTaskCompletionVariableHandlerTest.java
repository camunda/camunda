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
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.tasklist.TaskVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class UserTaskCompletionVariableHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-tasklist-task";
  private final UserTaskCompletionVariableHandler underTest =
      new UserTaskCompletionVariableHandler(indexName, 100);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.USER_TASK);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(TaskVariableEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<UserTaskRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.COMPLETED)
                    .withValue(
                        ImmutableUserTaskRecordValue.builder()
                            .withVariables(Map.of("var", "varVal"))
                            .build()));

    // when - then
    assertThat(underTest.handlesRecord(variableRecord)).isTrue();
  }

  @Test
  void shouldNotHandleRecord() {
    // given
    Arrays.stream(UserTaskIntent.values())
        .filter(intent -> !intent.name().equals(UserTaskIntent.COMPLETED.name()))
        .forEach(
            intent -> {
              final Record<UserTaskRecordValue> variableRecord =
                  factory.generateRecord(ValueType.USER_TASK, r -> r.withIntent(intent));
              // when - then
              assertThat(underTest.handlesRecord(variableRecord)).isFalse();
            });
  }

  @Test
  void shouldNotHandleRecordWithNullVariables() {
    // given
    final Record<UserTaskRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.COMPLETED)
                    .withValue(ImmutableUserTaskRecordValue.builder().build()));
    // when - then
    assertThat(underTest.handlesRecord(variableRecord)).isFalse();
  }

  @Test
  void shouldNotHandleRecordWithEmptyVariables() {
    // given
    final Record<UserTaskRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withIntent(UserTaskIntent.COMPLETED)
                    .withValue(
                        ImmutableUserTaskRecordValue.builder().withVariables(Map.of()).build()));
    // when - then
    assertThat(underTest.handlesRecord(variableRecord)).isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final long elementInstanceKey = 123L;
    final Record<UserTaskRecordValue> variableRecord =
        generateRecordWithVariables(elementInstanceKey, Map.of("var1", "val1", "var2", 2));
    // when
    final var idList = underTest.generateIds(variableRecord);

    // then
    assertThat(idList)
        .containsExactlyInAnyOrder(
            elementInstanceKey + "-" + "var1" + TaskTemplate.LOCAL_VARIABLE_SUFFIX,
            elementInstanceKey + "-" + "var2" + TaskTemplate.LOCAL_VARIABLE_SUFFIX);
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
    final var join = new TaskJoinRelationship();
    join.setName(TaskJoinRelationshipType.LOCAL_VARIABLE.getType());
    join.setParent(123L);
    final TaskVariableEntity inputEntity =
        new TaskVariableEntity()
            .setId("111")
            .setValue("value")
            .setIsTruncated(false)
            .setScopeKey(123L)
            .setJoin(join);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    final Map<String, Object> updateFieldsMap = new HashMap<>();
    updateFieldsMap.put(TaskTemplate.VARIABLE_FULL_VALUE, null);
    updateFieldsMap.put(TaskTemplate.VARIABLE_VALUE, "value");
    updateFieldsMap.put(TaskTemplate.IS_TRUNCATED, false);
    updateFieldsMap.put(TaskTemplate.JOIN_FIELD_NAME, join);

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
    final long scopeKey = 456;
    final var taskRecord = generateRecordWithVariables(scopeKey, Map.of("var1", "val1"));

    // when
    final TaskVariableEntity variableEntity =
        new TaskVariableEntity().setId(scopeKey + "-" + "var1");
    underTest.updateEntity(taskRecord, variableEntity);

    // then
    assertThat(variableEntity.getId()).isEqualTo(scopeKey + "-" + "var1");
    assertThat(variableEntity.getKey()).isEqualTo(taskRecord.getKey());
    assertThat(variableEntity.getName()).isEqualTo("var1");
    assertThat(variableEntity.getScopeKey()).isEqualTo(scopeKey);
    assertThat(variableEntity.getTenantId()).isEqualTo(taskRecord.getValue().getTenantId());
    assertThat(variableEntity.getPosition()).isEqualTo(taskRecord.getPosition());
    assertThat(variableEntity.getValue()).isEqualTo("\"val1\"");
    assertThat(variableEntity.getProcessInstanceId())
        .isEqualTo(taskRecord.getValue().getProcessInstanceKey());
    assertThat(variableEntity.getIsTruncated()).isFalse();
    assertThat(variableEntity.getFullValue()).isNull();
    assertThat(variableEntity.getJoin()).isNotNull();
    assertThat(variableEntity.getJoin().getParent()).isEqualTo(scopeKey);
    assertThat(variableEntity.getJoin().getName())
        .isEqualTo(TaskJoinRelationshipType.LOCAL_VARIABLE.getType());
  }

  @Test
  void shouldUpdateEntityFromRecordWithFullValue() {
    // given
    final long scopeKey = 456;
    final var taskRecord =
        generateRecordWithVariables(
            scopeKey, Map.of("var1", "v".repeat(underTest.variableSizeThreshold + 1)));

    // when
    final TaskVariableEntity variableEntity =
        new TaskVariableEntity().setId(scopeKey + "-" + "var1");
    underTest.updateEntity(taskRecord, variableEntity);

    // then
    assertThat(variableEntity.getValue())
        .isEqualTo("\"" + "v".repeat(underTest.variableSizeThreshold - 1));
    assertThat(variableEntity.getFullValue())
        .isEqualTo("\"" + "v".repeat(underTest.variableSizeThreshold + 1) + "\"");
    assertThat(variableEntity.getIsTruncated()).isTrue();
  }

  private Record<UserTaskRecordValue> generateRecordWithVariables(
      final long elementInstanceKey, final Map<String, Object> variables) {
    return factory.generateRecord(
        ValueType.USER_TASK,
        r ->
            r.withIntent(UserTaskIntent.COMPLETED)
                .withValue(
                    ImmutableUserTaskRecordValue.builder()
                        .from(factory.generateObject(UserTaskRecordValue.class))
                        .withElementInstanceKey(elementInstanceKey)
                        .withVariables(variables)
                        .build()));
  }
}
