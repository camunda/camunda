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
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAuthorizationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuthorizationAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final AuthorizationAuditLogTransformer transformer =
      new AuthorizationAuditLogTransformer();

  public static Stream<Arguments> getIntentMappings() {
    return Stream.of(
        Arguments.of(AuthorizationIntent.CREATED, AuditLogOperationType.CREATE),
        Arguments.of(AuthorizationIntent.UPDATED, AuditLogOperationType.UPDATE),
        Arguments.of(AuthorizationIntent.DELETED, AuditLogOperationType.DELETE));
  }

  @MethodSource("getIntentMappings")
  @ParameterizedTest
  void shouldTransformAuthorizationRecord(
      final AuthorizationIntent intent, final AuditLogOperationType operationType) {
    // given
    final AuthorizationRecordValue recordValue =
        ImmutableAuthorizationRecordValue.builder()
            .from(factory.generateObject(AuthorizationRecordValue.class))
            .withAuthorizationKey(123L)
            .build();

    final Record<AuthorizationRecordValue> record =
        factory.generateRecord(
            ValueType.AUTHORIZATION, r -> r.withIntent(intent).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getOperationType()).isEqualTo(operationType);
  }
}
