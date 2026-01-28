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
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class BatchOperationLifecycleManagementAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final BatchOperationLifecycleManagementAuditLogTransformer transformer =
      new BatchOperationLifecycleManagementAuditLogTransformer();

  @Test
  void shouldTransformBatchOperationLifecycleRecord() {
    // given
    final BatchOperationLifecycleManagementRecordValue recordValue =
        ImmutableBatchOperationLifecycleManagementRecordValue.builder()
            .from(factory.generateObject(BatchOperationLifecycleManagementRecordValue.class))
            .withBatchOperationKey(456L)
            .build();

    final Record<BatchOperationLifecycleManagementRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            r -> r.withIntent(BatchOperationIntent.SUSPENDED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.SUSPEND);
  }
}
