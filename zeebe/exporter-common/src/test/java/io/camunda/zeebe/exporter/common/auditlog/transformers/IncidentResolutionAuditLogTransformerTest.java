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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IncidentResolutionAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final IncidentResolutionAuditLogTransformer transformer =
      new IncidentResolutionAuditLogTransformer();

  public static Stream<Arguments> getIntentMapping() {
    return Stream.of(
        Arguments.of(IncidentIntent.RESOLVED, AuditLogOperationType.RESOLVE),
        Arguments.of(IncidentIntent.RESOLVE, AuditLogOperationType.RESOLVE));
  }

  @ParameterizedTest(name = "Should map intent: {0} to operationType: {1}")
  @MethodSource("getIntentMapping")
  void shouldTransformIncidentResolutionRecord(
      final IncidentIntent intent, final AuditLogOperationType operationType) {
    // given
    final IncidentRecordValue recordValue =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withBpmnProcessId("proc-1")
            .withProcessDefinitionKey(456L)
            .withProcessInstanceKey(123L)
            .withElementInstanceKey(789L)
            .withJobKey(222L)
            .withTenantId("tenant-1")
            .build();

    final Record<IncidentRecordValue> record =
        factory.generateRecord(
            ValueType.INCIDENT, r -> r.withIntent(intent).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getProcessDefinitionId()).isEqualTo("proc-1");
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(456L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(123L);
    assertThat(entity.getElementInstanceKey()).isEqualTo(789L);
    assertThat(entity.getJobKey()).isEqualTo(222L);
    assertThat(entity.getOperationType()).isEqualTo(operationType);
    assertThat(entity.getRootProcessInstanceKey())
        .isPositive()
        .isEqualTo(record.getValue().getRootProcessInstanceKey());
  }
}
