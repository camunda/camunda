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
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableGroupRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class GroupAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final GroupAuditLogTransformer transformer = new GroupAuditLogTransformer();

  @Test
  void shouldTransformGroupRecord() {
    // given
    final GroupRecordValue recordValue =
        ImmutableGroupRecordValue.builder()
            .from(factory.generateObject(GroupRecordValue.class))
            .withName("groupName")
            .withGroupId("test-group")
            .withGroupKey(789L)
            .build();

    final Record<GroupRecordValue> record =
        factory.generateRecord(
            ValueType.GROUP, r -> r.withIntent(GroupIntent.CREATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("test-group");
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.CREATE);
    assertThat(entity.getEntityDescription()).isEqualTo("groupName");
  }

  @Test
  void shouldScheduleCleanUp() {
    // given
    final Record<GroupRecordValue> record =
        factory.generateRecord(ValueType.GROUP, r -> r.withIntent(GroupIntent.DELETED));

    // then
    assertThat(transformer.triggersCleanUp(record)).isTrue();
  }
}
