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
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableTenantRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class TenantAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final TenantAuditLogTransformer transformer = new TenantAuditLogTransformer();

  @Test
  void shouldTransformTenantRecord() {
    // given
    final TenantRecordValue recordValue =
        ImmutableTenantRecordValue.builder()
            .from(factory.generateObject(TenantRecordValue.class))
            .withTenantId("test-tenant")
            .withTenantKey(456L)
            .build();

    final Record<TenantRecordValue> record =
        factory.generateRecord(
            ValueType.TENANT, r -> r.withIntent(TenantIntent.CREATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("test-tenant");
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.CREATE);
  }

  @Test
  void shouldScheduleCleanUp() {
    // given
    final Record<TenantRecordValue> record =
        factory.generateRecord(ValueType.TENANT, r -> r.withIntent(TenantIntent.DELETED));

    // then
    assertThat(transformer.triggersCleanUp(record)).isTrue();
  }
}
