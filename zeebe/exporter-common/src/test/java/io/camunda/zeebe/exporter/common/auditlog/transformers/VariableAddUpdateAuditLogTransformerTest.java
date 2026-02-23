/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class VariableAddUpdateAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final VariableAddUpdateAuditLogTransformer transformer =
      new VariableAddUpdateAuditLogTransformer();

  @Test
  void shouldTransformVariableRecord() {
    // given
    final VariableRecordValue recordValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("variable-name")
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
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(456L);
    assertThat(entity.getProcessDefinitionId()).isEqualTo("bpmn-process-id");
    assertThat(entity.getProcessInstanceKey()).isEqualTo(123L);
    assertThat(entity.getElementInstanceKey()).isEqualTo(789L);
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.CREATE);
    assertThat(entity.getTenant().get().tenantId()).isEqualTo("tenant-1");
    assertThat(entity.getRootProcessInstanceKey())
        .isPositive()
        .isEqualTo(record.getValue().getRootProcessInstanceKey());
    assertThat(entity.getEntityDescription()).isEqualTo("variable-name");
  }
}
