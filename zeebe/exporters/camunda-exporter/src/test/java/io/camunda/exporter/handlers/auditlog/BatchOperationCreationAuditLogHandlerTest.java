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
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationCreationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class BatchOperationCreationAuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final BatchOperationCreationAuditLogTransformer transformer =
      new BatchOperationCreationAuditLogTransformer();

  @Test
  void shouldTransformBatchOperationCreationRecord() {
    // given
    final BatchOperationCreationRecordValue recordValue =
        ImmutableBatchOperationCreationRecordValue.builder()
            .from(factory.generateObject(BatchOperationCreationRecordValue.class))
            .withBatchOperationType(BatchOperationType.MODIFY_PROCESS_INSTANCE)
            .build();

    final Record<BatchOperationCreationRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r -> r.withIntent(BatchOperationIntent.CREATED).withValue(recordValue));

    // when
    final AuditLogEntity entity = new AuditLogEntity();
    transformer.transform(record, entity);

    // then
    assertThat(entity.getBatchOperationType())
        .isEqualTo(BatchOperationType.MODIFY_PROCESS_INSTANCE);
    assertThat(entity.getTenantScope()).isEqualTo(AuditLogTenantScope.GLOBAL);
  }
}
