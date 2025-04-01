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

import io.camunda.exporter.handlers.UserTaskCompletionVariableHandler.SnapshotTaskVariableBatch;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class UserTaskCompletionVariableHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-tasklist-task";
  private final UserTaskCompletionVariableHandler underTest =
      new UserTaskCompletionVariableHandler(indexName, 100, TestObjectMapper.objectMapper());

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.USER_TASK);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(SnapshotTaskVariableBatch.class);
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
    assertThat(idList).containsExactlyInAnyOrder(String.valueOf(elementInstanceKey));
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
    final SnapshotTaskVariableEntity inputEntity =
        new SnapshotTaskVariableEntity().setId("111").setValue("value").setIsPreview(false);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(new SnapshotTaskVariableBatch("123", List.of(inputEntity)), mockRequest);

    // then
    final Map<String, Object> updateFieldsMap = new HashMap<>();
    updateFieldsMap.put(SnapshotTaskVariableTemplate.VALUE, inputEntity.getValue());
    updateFieldsMap.put(SnapshotTaskVariableTemplate.FULL_VALUE, inputEntity.getFullValue());
    updateFieldsMap.put(SnapshotTaskVariableTemplate.IS_PREVIEW, inputEntity.getIsPreview());

    verify(mockRequest, times(1))
        .upsert(indexName, inputEntity.getId(), inputEntity, updateFieldsMap);
  }

  @Test
  void shouldUpdateEntityFromRecordForTaskScopedVariable() {
    // given
    final long scopeKey = 456;
    final var taskRecord = generateRecordWithVariables(scopeKey, Map.of("var1", "val1"));

    // when
    final var batch = new SnapshotTaskVariableBatch(String.valueOf(scopeKey), new ArrayList<>());
    underTest.updateEntity(taskRecord, batch);

    // then
    final var variableEntity = batch.variables().getFirst();
    assertThat(variableEntity.getId()).isEqualTo(scopeKey + "-" + "var1");
    assertThat(variableEntity.getKey()).isEqualTo(taskRecord.getKey());
    assertThat(variableEntity.getName()).isEqualTo("var1");
    assertThat(variableEntity.getTenantId()).isEqualTo(taskRecord.getValue().getTenantId());
    assertThat(variableEntity.getValue()).isEqualTo("\"val1\"");
    assertThat(variableEntity.getProcessInstanceKey())
        .isEqualTo(taskRecord.getValue().getProcessInstanceKey());
    assertThat(variableEntity.getIsPreview()).isFalse();
    assertThat(variableEntity.getFullValue()).isEqualTo("\"val1\"");
  }

  @Test
  void shouldUpdateEntityFromRecordWithFullValue() {
    // given
    final long scopeKey = 456;
    final var taskRecord =
        generateRecordWithVariables(
            scopeKey, Map.of("var1", "v".repeat(underTest.variableSizeThreshold + 1)));

    // when
    final var batch = new SnapshotTaskVariableBatch(String.valueOf(scopeKey), new ArrayList<>());
    underTest.updateEntity(taskRecord, batch);

    // then
    final var variableEntity = batch.variables().getFirst();
    assertThat(variableEntity.getValue())
        .isEqualTo("\"" + "v".repeat(underTest.variableSizeThreshold - 1));
    assertThat(variableEntity.getFullValue())
        .isEqualTo("\"" + "v".repeat(underTest.variableSizeThreshold + 1) + "\"");
    assertThat(variableEntity.getIsPreview()).isTrue();
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
                        .withUserTaskKey(elementInstanceKey)
                        .withElementInstanceKey(elementInstanceKey)
                        .withVariables(variables)
                        .build()));
  }
}
