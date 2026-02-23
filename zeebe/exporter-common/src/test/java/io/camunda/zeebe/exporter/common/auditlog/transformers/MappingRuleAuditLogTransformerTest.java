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
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableMappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class MappingRuleAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final MappingRuleAuditLogTransformer transformer = new MappingRuleAuditLogTransformer();

  @Test
  void shouldTransformMappingRuleRecord() {
    // given
    final MappingRuleRecordValue recordValue =
        ImmutableMappingRuleRecordValue.builder()
            .from(factory.generateObject(MappingRuleRecordValue.class))
            .withMappingRuleId("mapping-rule-1")
            .withMappingRuleKey(123L)
            .withClaimName("department")
            .withClaimValue("engineering")
            .withName("Engineering Mapping")
            .build();

    final Record<MappingRuleRecordValue> record =
        factory.generateRecord(
            ValueType.MAPPING_RULE,
            r -> r.withIntent(MappingRuleIntent.CREATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("mapping-rule-1");
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.CREATE);
    assertThat(entity.getEntityDescription()).isEqualTo("Engineering Mapping");
  }

  @Test
  void shouldScheduleCleanUp() {
    // given
    final Record<MappingRuleRecordValue> record =
        factory.generateRecord(
            ValueType.MAPPING_RULE, r -> r.withIntent(MappingRuleIntent.DELETED));

    // then
    assertThat(transformer.triggersCleanUp(record)).isTrue();
  }
}
