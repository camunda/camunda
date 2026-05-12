/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.service.VariableWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VariableExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private VariableWriter variableWriter;

  @Captor private ArgumentCaptor<VariableDbModel> variableDbModelCaptor;

  private VariableExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new VariableExportHandler(variableWriter);
  }

  @ParameterizedTest(name = "Should be able to export record with intent: {0}")
  @EnumSource(VariableIntent.class)
  void shouldExportRecord(final VariableIntent intent) {
    // given
    final Record<VariableRecordValue> record =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(intent));

    // when - then
    assertThat(handler.canExport(record)).isTrue();
  }

  @Test
  void shouldHandleCreatedVariable() {
    // given
    final Record<VariableRecordValue> record =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(VariableIntent.CREATED));

    // when
    handler.export(record);

    // then
    verify(variableWriter).create(variableDbModelCaptor.capture());
    assertVariableDbModelFieldsEqualToRecord(variableDbModelCaptor.getValue(), record);
    verifyNoMoreInteractions(variableWriter);
  }

  @Test
  void shouldHandleUpdatedVariable() {
    // given
    final Record<VariableRecordValue> record =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(VariableIntent.UPDATED));

    // when
    handler.export(record);

    // then
    verify(variableWriter).update(variableDbModelCaptor.capture());
    assertVariableDbModelFieldsEqualToRecord(variableDbModelCaptor.getValue(), record);
    verifyNoMoreInteractions(variableWriter);
  }

  @Test
  void shouldHandleMigratedVariable() {
    // given
    final Record<VariableRecordValue> record =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(VariableIntent.MIGRATED));
    final var recordValue = record.getValue();

    // when
    handler.export(record);

    // then
    verify(variableWriter)
        .migrateToProcess(
            record.getKey(), recordValue.getBpmnProcessId(), recordValue.getProcessDefinitionKey());
    verifyNoMoreInteractions(variableWriter);
  }

  private static void assertVariableDbModelFieldsEqualToRecord(
      final VariableDbModel model, final Record<VariableRecordValue> record) {
    final var recordValue = record.getValue();
    assertThat(model.variableKey()).isEqualTo(record.getKey());
    assertThat(model.name()).isEqualTo(recordValue.getName());
    assertThat(model.value()).isEqualTo(recordValue.getValue());
    assertThat(model.scopeKey()).isEqualTo(recordValue.getScopeKey());
    assertThat(model.processInstanceKey()).isEqualTo(recordValue.getProcessInstanceKey());
    assertThat(model.rootProcessInstanceKey()).isEqualTo(recordValue.getRootProcessInstanceKey());
    assertThat(model.processDefinitionId()).isEqualTo(recordValue.getBpmnProcessId());
    assertThat(model.processDefinitionKey()).isEqualTo(recordValue.getProcessDefinitionKey());
    assertThat(model.tenantId()).isEqualTo(recordValue.getTenantId());
    assertThat(model.elementInstanceKey()).isEqualTo(recordValue.getElementInstanceKey());
    assertThat(model.partitionId()).isEqualTo(record.getPartitionId());
  }
}
