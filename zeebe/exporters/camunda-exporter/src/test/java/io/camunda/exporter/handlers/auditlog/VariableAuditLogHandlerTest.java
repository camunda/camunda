/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogTenantScope;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class VariableAuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final VariableAuditLogTransformer transformer = new VariableAuditLogTransformer();

  @Test
  void shouldTransformVariableCreatedRecord() {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withProcessDefinitionKey(456L)
            .withBpmnProcessId("test-process")
            .withTenantId("tenant-1")
            .withScopeKey(789L)
            .build();

    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE, r -> r.withIntent(VariableIntent.CREATED).withValue(recordValue));

    // when
    final AuditLogEntity entity = new AuditLogEntity();
    transformer.transform(record, entity);

    // then
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(456L);
    assertThat(entity.getTenantId()).isEqualTo("tenant-1");
    assertThat(entity.getProcessDefinitionId()).isEqualTo("test-process");
    assertThat(entity.getElementInstanceKey()).isEqualTo(789L);
    assertThat(entity.getTenantScope()).isEqualTo(AuditLogTenantScope.TENANT);
  }

  @Test
  void shouldTransformVariableUpdatedRecord() {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withProcessDefinitionKey(123L)
            .withBpmnProcessId("another-process")
            .withTenantId("tenant-2")
            .withScopeKey(456L)
            .build();

    final Record<VariableRecordValue> record =
        factory.generateRecord(
            ValueType.VARIABLE, r -> r.withIntent(VariableIntent.UPDATED).withValue(recordValue));

    // when
    final AuditLogEntity entity = new AuditLogEntity();
    transformer.transform(record, entity);

    // then
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(123L);
    assertThat(entity.getTenantId()).isEqualTo("tenant-2");
    assertThat(entity.getProcessDefinitionId()).isEqualTo("another-process");
    assertThat(entity.getElementInstanceKey()).isEqualTo(456L);
    assertThat(entity.getTenantScope()).isEqualTo(AuditLogTenantScope.TENANT);
  }
}
