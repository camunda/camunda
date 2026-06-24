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
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class ProcessAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProcessAuditLogTransformer transformer = new ProcessAuditLogTransformer();

  @Test
  void shouldTransformProcessRecord() {
    // given
    final Process recordValue =
        ImmutableProcess.builder()
            .from(factory.generateObject(Process.class))
            .withResourceName("processResource")
            .withDeploymentKey(123L)
            .withProcessDefinitionKey(456L)
            .withBpmnProcessId("process-id")
            .withTenantId("tenant-1")
            .build();

    final Record<Process> record =
        factory.generateRecord(
            ValueType.PROCESS, r -> r.withIntent(ProcessIntent.CREATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("456");
    assertThat(entity.getDeploymentKey()).isEqualTo(123L);
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(456L);
    assertThat(entity.getProcessDefinitionId()).isEqualTo("process-id");
    assertThat(entity.getTenant().orElseThrow().tenantId()).isEqualTo("tenant-1");
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.CREATE);
    assertThat(entity.getEntityDescription()).isEqualTo("processResource");
  }
}
