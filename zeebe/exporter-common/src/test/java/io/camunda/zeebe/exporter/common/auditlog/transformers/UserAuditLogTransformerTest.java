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
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class UserAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final UserAuditLogTransformer transformer = new UserAuditLogTransformer();

  @Test
  void shouldTransformUserRecord() {
    // given
    final UserRecordValue recordValue =
        ImmutableUserRecordValue.builder()
            .from(factory.generateObject(UserRecordValue.class))
            .withUsername("testuser")
            .withUserKey(123L)
            .build();

    final Record<UserRecordValue> record =
        factory.generateRecord(
            ValueType.USER, r -> r.withIntent(UserIntent.CREATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("testuser");
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.CREATE);
  }

  @Test
  void shouldScheduleCleanUp() {
    // given
    final Record<UserRecordValue> record =
        factory.generateRecord(ValueType.USER, r -> r.withIntent(UserIntent.DELETED));

    // then
    assertThat(transformer.triggersCleanUp(record)).isTrue();
  }
}
