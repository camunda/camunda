/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VariableAddUpdateAuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final VariableAddUpdateAuditLogTransformer transformer =
      new VariableAddUpdateAuditLogTransformer();

  @ParameterizedTest
  @EnumSource(
      value = VariableIntent.class,
      names = {"CREATED", "UPDATED"})
  void shouldHandleRecord(final VariableIntent intent) {
    // given
    final Record<VariableRecordValue> record =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(intent));

    // when & then
    assertThat(transformer.supports(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = VariableIntent.class,
      names = {"MIGRATED"})
  void shouldNotHandleRecord(final VariableIntent intent) {
    // given
    final Record<VariableRecordValue> record =
        factory.generateRecord(ValueType.VARIABLE, r -> r.withIntent(intent));

    // when & then
    assertThat(transformer.supports(record)).isFalse();
  }

  @Test
  void shouldTransformVariableRecord() {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withProcessDefinitionKey(456L)
            .withBpmnProcessId("bpmn-process-id")
            .withTenantId("tenant-1")
            .withProcessInstanceKey(123L)
            .withScopeKey(789L)
            .build();

    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE, r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    // when
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var model = builder.build();

    // then
    assertThat(model.processDefinitionKey()).isEqualTo(456L);
    assertThat(model.tenantId()).isEqualTo("tenant-1");
    assertThat(model.processDefinitionId()).isEqualTo("bpmn-process-id");
    assertThat(model.processInstanceKey()).isEqualTo(123L);
    assertThat(model.elementInstanceKey()).isEqualTo(789L);
    assertThat(model.tenantScope()).isEqualTo(AuditLogTenantScope.TENANT);
  }
}
