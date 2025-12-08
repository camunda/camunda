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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class ProcessInstanceCreatedAuditLogHandlerTest {

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
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var model = builder.build();

    // then
    assertThat(model.processDefinitionKey()).isEqualTo(456L);
    assertThat(model.tenantId()).isEqualTo("tenant-1");
    assertThat(model.processDefinitionId()).isEqualTo("test-process");
    assertThat(model.processInstanceKey()).isEqualTo(123L);
    assertThat(model.tenantScope()).isEqualTo(AuditLogEntity.AuditLogTenantScope.TENANT);
  }
}
