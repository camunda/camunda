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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MappingRuleAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final MappingRuleAuditLogTransformer transformer = new MappingRuleAuditLogTransformer();

  public static Stream<Arguments> getIntentMappings() {
    return Stream.of(
        Arguments.of(MappingRuleIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(MappingRuleIntent.UPDATED, AuditLogOperationType.UPDATE),
        Arguments.of(MappingRuleIntent.DELETED, AuditLogOperationType.DELETE));
  }

  @MethodSource("getIntentMappings")
  @ParameterizedTest
  void shouldTransformMappingRuleRecord(
      final MappingRuleIntent intent, final AuditLogOperationType operationType) {
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
            ValueType.MAPPING_RULE, r -> r.withIntent(intent).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("mapping-rule-1");
    assertThat(entity.getOperationType()).isEqualTo(operationType);
  }
}
