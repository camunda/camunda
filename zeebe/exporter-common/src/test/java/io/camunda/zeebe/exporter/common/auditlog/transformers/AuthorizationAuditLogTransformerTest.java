/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAuthorizationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuthorizationAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final AuthorizationAuditLogTransformer transformer =
      new AuthorizationAuditLogTransformer();

  private static Stream<Arguments> ownerTypeToEntityTypeProvider() {
    return Stream.of(
        Arguments.of(AuthorizationOwnerType.USER, AuditLogEntityType.USER),
        Arguments.of(AuthorizationOwnerType.CLIENT, AuditLogEntityType.CLIENT),
        Arguments.of(AuthorizationOwnerType.GROUP, AuditLogEntityType.GROUP),
        Arguments.of(AuthorizationOwnerType.ROLE, AuditLogEntityType.ROLE),
        Arguments.of(AuthorizationOwnerType.MAPPING_RULE, AuditLogEntityType.MAPPING_RULE),
        Arguments.of(AuthorizationOwnerType.UNSPECIFIED, null),
        Arguments.of(null, null));
  }

  @MethodSource("ownerTypeToEntityTypeProvider")
  @ParameterizedTest
  void shouldTransformAuthorizationRecord(
      final AuthorizationOwnerType ownerType, final AuditLogEntityType expectedRelatedEntityType) {
    // given
    final AuthorizationRecordValue recordValue =
        ImmutableAuthorizationRecordValue.builder()
            .from(factory.generateObject(AuthorizationRecordValue.class))
            .withOwnerId("owner-id")
            .withOwnerType(ownerType)
            .withAuthorizationKey(123L)
            .build();

    final Record<AuthorizationRecordValue> record =
        factory.generateRecord(
            ValueType.AUTHORIZATION,
            r -> r.withIntent(AuthorizationIntent.CREATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.CREATE);
    assertThat(entity.getRelatedEntityKey()).isEqualTo("owner-id");
    assertThat(entity.getRelatedEntityType()).isEqualTo(expectedRelatedEntityType);
  }

  @Test
  void shouldScheduleCleanUp() {
    // given
    final Record<AuthorizationRecordValue> record =
        factory.generateRecord(
            ValueType.AUTHORIZATION, r -> r.withIntent(AuthorizationIntent.DELETED));

    // then
    assertThat(transformer.triggersCleanUp(record)).isTrue();
  }
}
