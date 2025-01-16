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
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class VariableHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "variable";
  private final int variableSizeThreshold = 10;
  private final VariableHandler underTest = new VariableHandler(indexName, variableSizeThreshold);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.VARIABLE);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(VariableEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = VariableIntent.class,
      names = {"MIGRATED"},
      mode = Mode.EXCLUDE)
  void shouldHandleRecord(final VariableIntent intent) {
    // given
    final Record<VariableRecordValue> decisionRecord =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(decisionRecord)).isTrue();
  }

  @Test
  void shouldNotHandleRecord() {
    // given
    final Record<VariableRecordValue> decisionRecord =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(VariableIntent.MIGRATED));

    // when - then
    assertThat(underTest.handlesRecord(decisionRecord)).isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final VariableRecordValue variableRecordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .build();

    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(VariableIntent.CREATED).withValue(variableRecordValue));

    // when
    final var idList = underTest.generateIds(variableRecord);

    // then
    assertThat(idList)
        .containsExactly(variableRecordValue.getScopeKey() + "-" + variableRecordValue.getName());
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
    final VariableEntity inputEntity =
        new VariableEntity()
            .setId("id")
            .setValue("value")
            .setBpmnProcessId("procId")
            .setProcessDefinitionKey(123L);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @Test
  void shouldAddMigratedEntityOnFlush() {
    // given
    final VariableEntity inputEntity =
        new VariableEntity()
            .setId("id")
            .setValue("null")
            .setBpmnProcessId("procId")
            .setProcessDefinitionKey(123L);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final VariableRecordValue variableRecordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("v".repeat(variableSizeThreshold))
            .withTenantId("tenantId")
            .build();

    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(VariableIntent.CREATED).withValue(variableRecordValue));

    // when
    final VariableEntity variableEntity = new VariableEntity();
    underTest.updateEntity(variableRecord, variableEntity);

    // then
    assertThat(variableEntity.getId())
        .isEqualTo(variableRecordValue.getScopeKey() + "-" + variableRecordValue.getName());
    assertThat(variableEntity.getKey()).isEqualTo(variableRecord.getKey());
    assertThat(variableEntity.getName()).isEqualTo(variableRecordValue.getName());
    assertThat(variableEntity.getScopeKey()).isEqualTo(variableRecordValue.getScopeKey());
    assertThat(variableEntity.getTenantId()).isEqualTo(variableRecordValue.getTenantId());
    assertThat(variableEntity.getProcessDefinitionKey())
        .isEqualTo(variableRecordValue.getProcessDefinitionKey());
    assertThat(variableEntity.getProcessInstanceKey())
        .isEqualTo(variableRecordValue.getProcessInstanceKey());
    assertThat(variableEntity.getBpmnProcessId()).isEqualTo(variableRecordValue.getBpmnProcessId());
    assertThat(variableEntity.getPosition()).isEqualTo(variableRecord.getPosition());
    assertThat(variableEntity.getValue()).isEqualTo(variableRecordValue.getValue());
    assertThat(variableEntity.getIsPreview()).isFalse();
    assertThat(variableEntity.getFullValue()).isNull();
  }

  @Test
  void shouldUpdateEntityFromRecordWithFullValue() {
    // given
    final VariableRecordValue variableRecordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("v".repeat(variableSizeThreshold + 1))
            .build();

    final Record<VariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.VARIABLE,
            r -> r.withIntent(VariableIntent.CREATED).withValue(variableRecordValue));

    // when
    final VariableEntity variableEntity = new VariableEntity();
    underTest.updateEntity(variableRecord, variableEntity);

    // then
    assertThat(variableEntity.getValue()).isEqualTo("v".repeat(variableSizeThreshold));
    assertThat(variableEntity.getFullValue()).isEqualTo("v".repeat(variableSizeThreshold + 1));
    assertThat(variableEntity.getIsPreview()).isTrue();
  }
}
