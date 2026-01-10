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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProcessInstanceModifiedAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProcessInstanceModificationAuditLogTransformer transformer =
      new ProcessInstanceModificationAuditLogTransformer();

  public static Stream<Arguments> getIntentMappings() {
    return Stream.of(
        Arguments.of(ProcessInstanceModificationIntent.MODIFIED, AuditLogOperationType.MODIFY));
  }

  @MethodSource("getIntentMappings")
  @ParameterizedTest
  void shouldTransformProcessInstanceModificationRecord(
      final ProcessInstanceModificationIntent intent, final AuditLogOperationType operationType) {
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
            r -> r.withIntent(intent).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getProcessInstanceKey()).isEqualTo(123L);
    assertThat(entity.getOperationType()).isEqualTo(operationType);
    assertThat(entity.getRootProcessInstanceKey())
        .isPositive()
        .isEqualTo(record.getValue().getRootProcessInstanceKey());
  }
}
