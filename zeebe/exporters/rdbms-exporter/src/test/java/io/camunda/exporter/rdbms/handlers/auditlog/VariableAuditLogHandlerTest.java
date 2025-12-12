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
import io.camunda.search.entities.AuditLogEntity;
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
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var model = builder.build();

    // then
    assertThat(model.processDefinitionKey()).isEqualTo(456L);
    assertThat(model.tenantId()).isEqualTo("tenant-1");
    assertThat(model.processDefinitionId()).isEqualTo("test-process");
    assertThat(model.elementInstanceKey()).isEqualTo(789L);
    assertThat(model.tenantScope()).isEqualTo(AuditLogEntity.AuditLogTenantScope.TENANT);
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
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var model = builder.build();

    // then
    assertThat(model.processDefinitionKey()).isEqualTo(123L);
    assertThat(model.tenantId()).isEqualTo("tenant-2");
    assertThat(model.processDefinitionId()).isEqualTo("another-process");
    assertThat(model.elementInstanceKey()).isEqualTo(456L);
    assertThat(model.tenantScope()).isEqualTo(AuditLogEntity.AuditLogTenantScope.TENANT);
  }
}
