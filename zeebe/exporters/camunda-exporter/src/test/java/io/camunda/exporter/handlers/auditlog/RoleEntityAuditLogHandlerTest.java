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
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.ImmutableRoleRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class RoleEntityAuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final RoleEntityAuditLogTransformer transformer = new RoleEntityAuditLogTransformer();

  @Test
  void shouldTransformRoleEntityRecord() {
    // given
    final RoleRecordValue recordValue =
        ImmutableRoleRecordValue.builder()
            .from(factory.generateObject(RoleRecordValue.class))
            .withRoleId("role-123")
            .withEntityType(EntityType.USER)
            .build();

    final Record<RoleRecordValue> record =
        factory.generateRecord(
            ValueType.ROLE, r -> r.withIntent(RoleIntent.ENTITY_ADDED).withValue(recordValue));

    // when
    final AuditLogEntity entity = new AuditLogEntity();
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("role-123");
    assertThat(entity.getTenantScope()).isEqualTo(AuditLogTenantScope.GLOBAL);
  }
}
