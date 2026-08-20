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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class ProcessInstanceCancelAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProcessInstanceCancelAuditLogTransformer transformer =
      new ProcessInstanceCancelAuditLogTransformer();

  @Test
  void shouldTransformProcessInstanceCancelRecord() {
    // given
    final ProcessInstanceRecordValue recordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .withProcessDefinitionKey(123L)
            .withBpmnProcessId("bpmn-process-id")
            .withProcessInstanceKey(234L)
            .withTenantId("tenant-1")
            .build();

    final Record<ProcessInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r -> r.withIntent(ProcessInstanceIntent.CANCEL).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getProcessDefinitionId()).isEqualTo("bpmn-process-id");
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(123L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(234L);
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.CANCEL);
    assertThat(entity.getRootProcessInstanceKey())
        .isPositive()
        .isEqualTo(record.getValue().getRootProcessInstanceKey());
  }
}
