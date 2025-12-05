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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class ProcessInstanceModifiedAuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProcessInstanceModificationAuditLogTransformer transformer =
      new ProcessInstanceModificationAuditLogTransformer();

  @Test
  void shouldTransformProcessInstanceModificationRecord() {
    // given
    final ProcessInstanceModificationRecordValue recordValue =
        ImmutableProcessInstanceModificationRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceModificationRecordValue.class))
            .withProcessInstanceKey(123L)
            .withTenantId("tenant-1")
            .build();

    final Record<ProcessInstanceModificationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r -> r.withIntent(ProcessInstanceModificationIntent.MODIFIED).withValue(recordValue));

    // when
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var entity = builder.build();

    // then
    assertThat(entity.processInstanceKey()).isEqualTo(123L);
    assertThat(entity.tenantId()).isEqualTo("tenant-1");
    assertThat(entity.tenantScope()).isEqualTo(AuditLogTenantScope.TENANT);
  }
}
