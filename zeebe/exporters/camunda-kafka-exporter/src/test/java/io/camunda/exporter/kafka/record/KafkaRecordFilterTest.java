/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.record;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.kafka.config.RecordConfiguration;
import io.camunda.exporter.kafka.config.RecordsConfiguration;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class KafkaRecordFilterTest {

  // ---- acceptType ----

  @Test
  void shouldAcceptTypeFromDefaults() {
    // given
    final var filter =
        new KafkaRecordFilter(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)), Map.of()));

    // when / then
    assertThat(filter.acceptType(RecordType.EVENT)).isTrue();
  }

  @Test
  void shouldRejectTypeNotInDefaultsOrAnyOverride() {
    // given
    final var filter =
        new KafkaRecordFilter(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)), Map.of()));

    // when / then
    assertThat(filter.acceptType(RecordType.COMMAND)).isFalse();
  }

  @Test
  void shouldAcceptTypeAllowedOnlyInAPerTypeOverride() {
    // given — defaults only allow EVENT, but JOB override additionally allows COMMAND
    final var filter =
        new KafkaRecordFilter(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)),
                Map.of(
                    ValueType.JOB,
                    new RecordConfiguration("zeebe-job", Set.of(RecordType.COMMAND)))));

    // then — COMMAND must be accepted even though it is not in defaults
    assertThat(filter.acceptType(RecordType.COMMAND)).isTrue();
  }

  @Test
  void shouldAcceptTypeWhenMultipleOverridesContributeDistinctTypes() {
    // given
    final var filter =
        new KafkaRecordFilter(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)),
                Map.of(
                    ValueType.JOB,
                    new RecordConfiguration("zeebe-job", Set.of(RecordType.COMMAND)),
                    ValueType.INCIDENT,
                    new RecordConfiguration(
                        "zeebe-incident", Set.of(RecordType.COMMAND_REJECTION)))));

    assertThat(filter.acceptType(RecordType.EVENT)).isTrue();
    assertThat(filter.acceptType(RecordType.COMMAND)).isTrue();
    assertThat(filter.acceptType(RecordType.COMMAND_REJECTION)).isTrue();
  }

  // ---- acceptValue ----

  @Test
  void shouldAcceptValueTypeWithNonEmptyAllowedTypes() {
    // given
    final var filter =
        new KafkaRecordFilter(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)), Map.of()));

    // when / then — falls back to defaults, which has EVENT
    assertThat(filter.acceptValue(ValueType.JOB)).isTrue();
  }

  @Test
  void shouldRejectValueTypeWithEmptyAllowedTypes() {
    // given — JOB override has no allowed types
    final var filter =
        new KafkaRecordFilter(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)),
                Map.of(ValueType.JOB, new RecordConfiguration("zeebe-job", Set.of()))));

    // when / then
    assertThat(filter.acceptValue(ValueType.JOB)).isFalse();
  }

  @Test
  void shouldAcceptValueTypeWithPerTypeOverrideThatHasAllowedTypes() {
    // given
    final var filter =
        new KafkaRecordFilter(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of()),
                Map.of(
                    ValueType.JOB,
                    new RecordConfiguration("zeebe-job", Set.of(RecordType.EVENT)))));

    // when / then — defaults have empty types, but JOB override allows EVENT
    assertThat(filter.acceptValue(ValueType.JOB)).isTrue();
  }

  // ---- partition-1-scoped value types are accepted by the filter (filtering happens in
  // RecordHandler, not here) ----

  @ParameterizedTest
  @EnumSource(
      value = ValueType.class,
      names = {
        "PROCESS",
        "DECISION",
        "DECISION_REQUIREMENTS",
        "FORM",
        "USER",
        "TENANT",
        "ROLE",
        "GROUP",
        "AUTHORIZATION",
        "MAPPING_RULE"
      })
  void shouldAcceptPartitionOneScopedValueTypes(final ValueType valueType) {
    // given — the filter does not apply partition-1 scoping (that is RecordHandler's job)
    final var filter =
        new KafkaRecordFilter(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)), Map.of()));

    // when / then
    assertThat(filter.acceptValue(valueType)).isTrue();
  }
}
