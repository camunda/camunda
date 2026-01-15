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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class ProcessInstanceCreatedAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProcessInstanceCreationAuditLogTransformer transformer =
      new ProcessInstanceCreationAuditLogTransformer();

  @Test
  void shouldTransformProcessInstanceCreationRecord() {
    // given
    final ProcessInstanceCreationRecordValue recordValue =
        ImmutableProcessInstanceCreationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceCreationRecordValue.class))
            .withProcessDefinitionKey(456L)
            .withBpmnProcessId("test-process")
            .withTenantId("tenant-1")
            .withProcessInstanceKey(123L)
            .build();

    final Record<ProcessInstanceCreationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_CREATION,
            r -> r.withIntent(ProcessInstanceCreationIntent.CREATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(456L);
    assertThat(entity.getProcessDefinitionId()).isEqualTo("test-process");
    assertThat(entity.getProcessInstanceKey()).isEqualTo(123L);
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.CREATE);
    assertThat(entity.getRootProcessInstanceKey())
        .isPositive()
        .isEqualTo(record.getValue().getRootProcessInstanceKey());
  }
}
