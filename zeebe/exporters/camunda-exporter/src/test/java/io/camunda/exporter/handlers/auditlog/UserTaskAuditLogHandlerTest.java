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
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class UserTaskAuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final UserTaskAuditLogTransformer transformer = new UserTaskAuditLogTransformer();

  @Test
  void shouldTransformUserTaskRecord() {
    // given
    final UserTaskRecordValue recordValue =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withUserTaskKey(123L)
            .withProcessInstanceKey(456L)
            .withProcessDefinitionKey(789L)
            .withBpmnProcessId("test-process")
            .withElementInstanceKey(111L)
            .withTenantId("tenant-1")
            .build();

    final Record<UserTaskRecordValue> record =
        factory.generateRecord(
            ValueType.USER_TASK, r -> r.withIntent(UserTaskIntent.UPDATED).withValue(recordValue));

    // when
    final AuditLogEntity entity = new AuditLogEntity();
    transformer.transform(record, entity);

    // then
    assertThat(entity.getUserTaskKey()).isEqualTo(123L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(456L);
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(789L);
    assertThat(entity.getProcessDefinitionId()).isEqualTo("test-process");
    assertThat(entity.getElementInstanceKey()).isEqualTo(111L);
    assertThat(entity.getTenantId()).isEqualTo("tenant-1");
    assertThat(entity.getTenantScope()).isEqualTo(AuditLogTenantScope.TENANT);
  }
}
