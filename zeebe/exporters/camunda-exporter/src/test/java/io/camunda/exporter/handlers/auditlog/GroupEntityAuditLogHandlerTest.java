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
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableGroupRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class GroupEntityAuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final GroupEntityAuditLogTransformer transformer = new GroupEntityAuditLogTransformer();

  @Test
  void shouldTransformGroupEntityRecord() {
    // given
    final GroupRecordValue recordValue =
        ImmutableGroupRecordValue.builder()
            .from(factory.generateObject(GroupRecordValue.class))
            .withGroupId("group-123")
            .withEntityId("entity-456")
            .withEntityType(EntityType.USER)
            .build();

    final Record<GroupRecordValue> record =
        factory.generateRecord(
            ValueType.GROUP, r -> r.withIntent(GroupIntent.ENTITY_ADDED).withValue(recordValue));

    // when
    final AuditLogEntity entity = new AuditLogEntity();
    transformer.transform(record, entity);

    // then
    assertThat(entity.getTenantScope()).isEqualTo(AuditLogTenantScope.GLOBAL);
  }
}
