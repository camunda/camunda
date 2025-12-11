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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class ProcessInstanceMigrationAuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProcessInstanceMigrationAuditLogTransformer transformer =
      new ProcessInstanceMigrationAuditLogTransformer();

  @Test
  void shouldTransformProcessInstanceMigrationRecord() {
    // given
    final ProcessInstanceMigrationRecordValue recordValue =
        ImmutableProcessInstanceMigrationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceMigrationRecordValue.class))
            .withTargetProcessDefinitionKey(123L)
            .withProcessInstanceKey(234L)
            .withTenantId("tenant-1")
            .build();

    final Record<ProcessInstanceMigrationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MIGRATION,
            r -> r.withIntent(ProcessInstanceMigrationIntent.MIGRATED).withValue(recordValue));

    // when
    final AuditLogEntity entity = new AuditLogEntity();
    transformer.transform(record, entity);

    // then
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(123L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(234L);
    assertThat(entity.getTenantId()).isEqualTo("tenant-1");
    assertThat(entity.getTenantScope()).isEqualTo(AuditLogTenantScope.TENANT);
  }
}
