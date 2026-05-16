/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.exporter.kafka.config.RecordConfiguration;
import io.camunda.exporter.kafka.config.RecordsConfiguration;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RecordHandlerTest {

  @Test
  void shouldTransformRecordToKafkaRecord() {
    // given
    final RecordsConfiguration recordsConfiguration =
        new RecordsConfiguration(
            new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)),
            Map.of(ValueType.JOB, new RecordConfiguration("zeebe-job", Set.of(RecordType.EVENT))));
    final RecordHandler handler = new RecordHandler(recordsConfiguration);

    final Record<?> record = mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.JOB);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getIntent()).thenReturn(JobIntent.CREATED);
    when(record.getBrokerVersion()).thenReturn("8.10.0");
    when(record.getPartitionId()).thenReturn(2);
    when(record.getPosition()).thenReturn(42L);
    final JobRecordValue value = mock(JobRecordValue.class);
    when(value.toJson()).thenReturn("{\"foo\":\"bar\"}");
    when(record.getValue()).thenReturn(value);

    // when
    final var producerRecord = handler.toKafkaExportRecord(record);

    // then
    assertThat(handler.isAllowed(record)).isTrue();
    assertThat(producerRecord.topic()).isEqualTo("zeebe-job");
    assertThat(producerRecord.key()).isEqualTo("2-42");
    assertThat(producerRecord.value())
        .isEqualTo("{\"schemaVersion\":1,\"record\":{\"foo\":\"bar\"}}");
    assertThat(producerRecord.headers())
        .containsEntry("valueType", "JOB")
        .containsEntry("intent", "CREATED")
        .containsEntry("partitionId", "2")
        .containsEntry("recordType", "EVENT")
        .containsEntry("tenantId", "<default>")
        .containsEntry("brokerVersion", "8.10.0");
    assertThat(producerRecord.zeebePartitionId()).isEqualTo(2);
    assertThat(producerRecord.position()).isEqualTo(42L);
  }

  @Test
  void shouldFilterDisallowedRecordType() {
    // given
    final RecordsConfiguration config =
        new RecordsConfiguration(
            new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)), Map.of());
    final RecordHandler handler = new RecordHandler(config);
    final Record<?> record = mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.JOB);
    when(record.getRecordType()).thenReturn(RecordType.COMMAND);
    when(record.getPartitionId()).thenReturn(1);
    when(record.getValue()).thenReturn(mock(RecordValue.class));

    // when / then
    assertThat(handler.isAllowed(record)).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldFallBackToDefaultTenantIdWhenTenantIdIsBlankOrNull(final String tenantId) {
    // given
    final RecordHandler handler =
        new RecordHandler(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)), Map.of()));
    final Record<?> record = mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.JOB);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getIntent()).thenReturn(JobIntent.CREATED);
    when(record.getBrokerVersion()).thenReturn("8.10.0");
    when(record.getPartitionId()).thenReturn(1);
    when(record.getPosition()).thenReturn(1L);
    final JobRecordValue value = mock(JobRecordValue.class);
    when(value.toJson()).thenReturn("{}");
    when(value.getTenantId()).thenReturn(tenantId);
    when(record.getValue()).thenReturn(value);

    // when
    final var exportRecord = handler.toKafkaExportRecord(record);

    // then
    assertThat(exportRecord.headers()).containsEntry("tenantId", "<default>");
  }

  @Test
  void shouldUseDefaultTenantIdForNonTenantOwnedValues() {
    // given
    final RecordHandler handler =
        new RecordHandler(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)), Map.of()));
    final Record<?> record = mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.JOB);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getIntent()).thenReturn(JobIntent.CREATED);
    when(record.getBrokerVersion()).thenReturn("8.10.0");
    when(record.getPartitionId()).thenReturn(1);
    when(record.getPosition()).thenReturn(1L);
    // RecordValue that does NOT implement TenantOwned
    final RecordValue value = mock(RecordValue.class);
    when(value.toJson()).thenReturn("{}");
    when(record.getValue()).thenReturn(value);

    // when
    final var exportRecord = handler.toKafkaExportRecord(record);

    // then
    assertThat(exportRecord.headers()).containsEntry("tenantId", "<default>");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldFallBackToUnknownBrokerVersionWhenBlankOrNull(final String brokerVersion) {
    // given
    final RecordHandler handler =
        new RecordHandler(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)), Map.of()));
    final Record<?> record = mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.JOB);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getIntent()).thenReturn(JobIntent.CREATED);
    when(record.getBrokerVersion()).thenReturn(brokerVersion);
    when(record.getPartitionId()).thenReturn(1);
    when(record.getPosition()).thenReturn(1L);
    final RecordValue value = mock(RecordValue.class);
    when(value.toJson()).thenReturn("{}");
    when(record.getValue()).thenReturn(value);

    // when
    final var exportRecord = handler.toKafkaExportRecord(record);

    // then
    assertThat(exportRecord.headers()).containsEntry("brokerVersion", "unknown");
  }

  @Test
  void shouldUseActualTenantIdWhenPresent() {
    // given
    final RecordHandler handler =
        new RecordHandler(
            new RecordsConfiguration(
                new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)), Map.of()));
    final Record<?> record = mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.JOB);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getIntent()).thenReturn(JobIntent.CREATED);
    when(record.getBrokerVersion()).thenReturn("8.10.0");
    when(record.getPartitionId()).thenReturn(1);
    when(record.getPosition()).thenReturn(1L);
    final JobRecordValue value = mock(JobRecordValue.class);
    when(value.toJson()).thenReturn("{}");
    when(value.getTenantId()).thenReturn("my-tenant");
    when(record.getValue()).thenReturn(value);

    // when
    final var exportRecord = handler.toKafkaExportRecord(record);

    // then
    assertThat(exportRecord.headers()).containsEntry("tenantId", "my-tenant");
  }

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
  void shouldFilterAllPartitionOneScopedTypesOnOtherPartitions(final ValueType valueType) {
    // given
    final RecordsConfiguration config =
        new RecordsConfiguration(
            new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)), Map.of());
    final RecordHandler handler = new RecordHandler(config);
    final Record<?> record = mock(Record.class);
    when(record.getValueType()).thenReturn(valueType);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getPartitionId()).thenReturn(2);
    when(record.getValue()).thenReturn(mock(RecordValue.class));

    // when / then
    assertThat(handler.isAllowed(record)).isFalse();
  }

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
  void shouldAllowAllPartitionOneScopedTypesOnPartitionOne(final ValueType valueType) {
    // given
    final RecordsConfiguration config =
        new RecordsConfiguration(
            new RecordConfiguration("zeebe", Set.of(RecordType.EVENT)), Map.of());
    final RecordHandler handler = new RecordHandler(config);
    final Record<?> record = mock(Record.class);
    when(record.getValueType()).thenReturn(valueType);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getPartitionId()).thenReturn(1);
    when(record.getValue()).thenReturn(mock(RecordValue.class));

    // when / then
    assertThat(handler.isAllowed(record)).isTrue();
  }
}
