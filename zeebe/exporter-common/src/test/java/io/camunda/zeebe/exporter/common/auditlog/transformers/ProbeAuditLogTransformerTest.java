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
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProbeRecordValue;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

final class ProbeAuditLogTransformerTest {

  private static final String PROBE_ID = "validate-configuration/io.camunda:aws-credential:1";

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProbeAuditLogTransformer transformer = new ProbeAuditLogTransformer();

  @ParameterizedTest(name = "{0} -> {1} {2}")
  @CsvSource({
    "CREATED,   CREATE,   SUCCESS",
    "TIMED_OUT, CANCEL,   FAIL",
    "CANCELED,  CANCEL,   FAIL",
  })
  void shouldAuditCreationAndEndWithoutAnswer(
      final ProbeIntent intent,
      final AuditLogOperationType operationType,
      final AuditLogOperationResult result) {
    // given
    final var record = probeRecord(intent, ProbeOutcome.UNSPECIFIED);

    // when
    final var entry = transformer.create(record);

    // then
    assertThat(transformer.supports(record)).isTrue();
    assertThat(entry.getOperationType()).isEqualTo(operationType);
    assertThat(entry.getResult()).isEqualTo(result);
  }

  @ParameterizedTest(name = "ANSWERED with {0} -> COMPLETE {1}")
  @CsvSource({"OK, SUCCESS", "UNSUPPORTED, SUCCESS", "ERROR, FAIL"})
  void shouldAuditAnswerWithResultOfOutcome(
      final ProbeOutcome outcome, final AuditLogOperationResult result) {
    // given
    final var record = probeRecord(ProbeIntent.ANSWERED, outcome);

    // when
    final var entry = transformer.create(record);

    // then
    assertThat(transformer.supports(record)).isTrue();
    assertThat(entry.getOperationType()).isEqualTo(AuditLogOperationType.COMPLETE);
    assertThat(entry.getResult()).isEqualTo(result);
  }

  @ParameterizedTest
  @EnumSource(
      value = ProbeIntent.class,
      names = {"CREATED", "ANSWERED", "TIMED_OUT", "CANCELED"})
  void shouldDescribeProbeByKeyAndProbeId(final ProbeIntent intent) {
    // given
    final var record = probeRecord(intent, ProbeOutcome.OK);

    // when
    final var entry = transformer.create(record);

    // then
    assertThat(entry.getEntityType()).isEqualTo(AuditLogEntityType.PROBE);
    assertThat(entry.getCategory()).isEqualTo(AuditLogOperationCategory.ADMIN);
    assertThat(entry.getEntityKey()).isEqualTo(String.valueOf(record.getKey()));
    assertThat(entry.getEntityDescription()).isEqualTo(PROBE_ID);
    assertThat(entry.getTenant())
        .hasValueSatisfying(
            tenant -> assertThat(tenant.tenantId()).isEqualTo(record.getValue().getTenantId()));
  }

  @ParameterizedTest
  @EnumSource(
      value = ProbeIntent.class,
      names = {"CREATED", "ANSWERED", "TIMED_OUT", "CANCELED"})
  void shouldScheduleCleanupOnEveryAuditedEvent(final ProbeIntent intent) {
    // given
    final var record = probeRecord(intent, ProbeOutcome.OK);

    // when
    final var triggersCleanUp = transformer.triggersCleanUp(record);

    // then
    assertThat(triggersCleanUp).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProbeIntent.class,
      names = {"CREATED", "ANSWERED", "TIMED_OUT", "CANCELED"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldNotAuditOtherProbeEvents(final ProbeIntent intent) {
    // given
    final var record = probeRecord(intent, ProbeOutcome.UNSPECIFIED);

    // when
    final var supported = transformer.supports(record);

    // then
    assertThat(supported).isFalse();
  }

  @Test
  void shouldNotAuditRefusedCreate() {
    // given
    final Record<ProbeRecordValue> record =
        factory.generateRecord(
            ValueType.PROBE,
            r ->
                r.withRecordType(RecordType.COMMAND_REJECTION)
                    .withIntent(ProbeIntent.CREATE)
                    .withRejectionType(RejectionType.FORBIDDEN)
                    .withValue(probeValue(ProbeOutcome.UNSPECIFIED)));

    // when
    final var supported = transformer.supports(record);

    // then
    assertThat(supported).isFalse();
  }

  @ParameterizedTest(name = "{0} by {1} logged: {2}")
  @CsvSource({
    // a user-created probe gets its CREATE entry
    "CREATED,   USER,    true",
    // a probe created by an M2M client is not logged by default
    "CREATED,   CLIENT,  false",
    // the worker answers as a client
    "ANSWERED,  CLIENT,  false",
    // the deadline scheduler has no actor
    "TIMED_OUT, UNKNOWN, false",
    // a CREATE past its deadline times out under the creator's identity (AT22)
    "TIMED_OUT, USER,    true",
    // the end entry belongs to whoever ended the probe, not to its creator
    "CANCELED,  CLIENT,  false",
  })
  void shouldAttributeEntryToActorOfRecordWithDefaultActors(
      final ProbeIntent intent, final String actor, final boolean logged) {
    // given
    final var authorizations =
        switch (actor) {
          case "USER" -> Map.<String, Object>of(Authorization.AUTHORIZED_USERNAME, "alice");
          case "CLIENT" -> Map.<String, Object>of(Authorization.AUTHORIZED_CLIENT_ID, "worker");
          default -> Map.<String, Object>of();
        };
    final var record = probeRecord(intent, ProbeOutcome.UNSPECIFIED, authorizations);

    // when
    final var enabled = new AuditLogConfiguration().isEnabled(AuditLogInfo.of(record));

    // then
    assertThat(enabled).isEqualTo(logged);
  }

  private Record<ProbeRecordValue> probeRecord(
      final ProbeIntent intent, final ProbeOutcome outcome) {
    return probeRecord(intent, outcome, Map.of());
  }

  private Record<ProbeRecordValue> probeRecord(
      final ProbeIntent intent,
      final ProbeOutcome outcome,
      final Map<String, Object> authorizations) {
    return factory.generateRecord(
        ValueType.PROBE,
        r ->
            r.withRecordType(RecordType.EVENT)
                .withIntent(intent)
                .withAuthorizations(authorizations)
                .withValue(probeValue(outcome)));
  }

  private ProbeRecordValue probeValue(final ProbeOutcome outcome) {
    return ImmutableProbeRecordValue.builder()
        .from(factory.generateObject(ProbeRecordValue.class))
        .withProbeId(PROBE_ID)
        .withOutcome(outcome)
        .build();
  }
}
