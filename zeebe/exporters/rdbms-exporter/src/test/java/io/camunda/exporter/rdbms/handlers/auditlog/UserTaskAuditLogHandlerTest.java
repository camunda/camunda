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
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UserTaskAuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final UserTaskAuditLogTransformer transformer = new UserTaskAuditLogTransformer();

  public static Stream<Arguments> getIntentMappings() {
    return Stream.of(
        Arguments.of(UserTaskIntent.UPDATED, AuditLogOperationType.UPDATE),
        Arguments.of(UserTaskIntent.ASSIGNED, AuditLogOperationType.ASSIGN),
        Arguments.of(UserTaskIntent.COMPLETED, AuditLogOperationType.COMPLETE),
        Arguments.of(UserTaskIntent.UPDATE, AuditLogOperationType.UPDATE),
        Arguments.of(UserTaskIntent.ASSIGN, AuditLogOperationType.ASSIGN),
        Arguments.of(UserTaskIntent.COMPLETE, AuditLogOperationType.COMPLETE));
  }

  @MethodSource("getIntentMappings")
  @ParameterizedTest
  void shouldTransformUserTaskRecord(
      final UserTaskIntent intent, final AuditLogOperationType operationType) {
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
            ValueType.USER_TASK, r -> r.withIntent(intent).withValue(recordValue));

    // when
    final AuditLogDbModel.Builder builder = new AuditLogDbModel.Builder();
    transformer.transform(record, builder);
    final var model = builder.build();

    // then
    assertThat(model.userTaskKey()).isEqualTo(123L);
    assertThat(model.processInstanceKey()).isEqualTo(456L);
    assertThat(model.processDefinitionKey()).isEqualTo(789L);
    assertThat(model.processDefinitionId()).isEqualTo("test-process");
    assertThat(model.elementInstanceKey()).isEqualTo(111L);
    assertThat(model.tenantId()).isEqualTo("tenant-1");
    assertThat(model.tenantScope()).isEqualTo(AuditLogEntity.AuditLogTenantScope.TENANT);

    final AuditLogInfo auditLogInfo = AuditLogInfo.of(record);
    assertThat(auditLogInfo.operationType()).isEqualTo(operationType);
  }
}
