/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for the record type filtering in ElasticsearchRecordFilter. */
final class ElasticsearchRecordFilterRecordTypeTest {

  @Test
  void shouldAcceptEventWhenEventIndexingEnabled() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.event = true;
    config.index.command = false;
    config.index.rejection = false;

    final var filter = new ElasticsearchRecordFilter(config);

    // when
    final var accepted = filter.acceptType(RecordType.EVENT);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldRejectEventWhenEventIndexingDisabled() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.event = false; // disable events
    final var filter = new ElasticsearchRecordFilter(config);

    // when
    final var accepted = filter.acceptType(RecordType.EVENT);

    // then
    assertThat(accepted).isFalse();
  }

  @Test
  void shouldAcceptCommandWhenCommandIndexingEnabled() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.command = true;
    config.index.event = false;
    config.index.rejection = false;

    final var filter = new ElasticsearchRecordFilter(config);

    // when
    final var accepted = filter.acceptType(RecordType.COMMAND);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldRejectCommandWhenCommandIndexingDisabled() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.command = false;
    final var filter = new ElasticsearchRecordFilter(config);

    // when
    final var accepted = filter.acceptType(RecordType.COMMAND);

    // then
    assertThat(accepted).isFalse();
  }

  @Test
  void shouldAcceptCommandRejectionWhenRejectionIndexingEnabled() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.rejection = true;
    config.index.event = false;
    config.index.command = false;

    final var filter = new ElasticsearchRecordFilter(config);

    // when
    final var accepted = filter.acceptType(RecordType.COMMAND_REJECTION);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldRejectCommandRejectionWhenRejectionIndexingDisabled() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.rejection = false;
    final var filter = new ElasticsearchRecordFilter(config);

    // when
    final var accepted = filter.acceptType(RecordType.COMMAND_REJECTION);

    // then
    assertThat(accepted).isFalse();
  }

  @ParameterizedTest(name = "recordType={0}, enabled={1} -> acceptRecord={2}")
  @CsvSource(
      value = {
        // recordType | enableEvent | enableCommand | enableRejection | expectedAccepted
        "EVENT            | true  | false | false | true",
        "EVENT            | false | false | false | false",
        "COMMAND          | false | true  | false | true",
        "COMMAND          | false | false | false | false",
        "COMMAND_REJECTION| false | false | true  | true",
        "COMMAND_REJECTION| false | false | false | false"
      },
      delimiter = '|')
  void shouldFilterRecordsByRecordTypeConfiguration(
      final RecordType recordType,
      final boolean enableEvent,
      final boolean enableCommand,
      final boolean enableRejection,
      final boolean expectedAccepted) {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.event = enableEvent;
    config.index.command = enableCommand;
    config.index.rejection = enableRejection;

    // keep value-type-related filters permissive by using a value type that is enabled by default
    // (PROCESS_INSTANCE is true by default in IndexConfiguration)
    final var filter = new ElasticsearchRecordFilter(config);

    @SuppressWarnings("unchecked")
    final Record<?> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(recordType);
    when(record.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(record.getIntent()).thenReturn(mock(Intent.class));

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isEqualTo(expectedAccepted);
  }
}
