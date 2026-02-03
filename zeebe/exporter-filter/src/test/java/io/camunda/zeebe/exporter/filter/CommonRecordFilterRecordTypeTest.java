/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.filter.CommonFilterConfiguration.IndexConfig;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for the record type filtering in {@link CommonRecordFilter}. */
final class CommonRecordFilterRecordTypeTest {

  @Test
  void shouldAcceptEventWhenEventIndexingEnabled() {
    // given
    final var filter = createFilter(true, false, false);

    // when
    final var accepted = filter.acceptType(RecordType.EVENT);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldRejectEventWhenEventIndexingDisabled() {
    // given
    final var filter = createFilter(false, false, false);

    // when
    final var accepted = filter.acceptType(RecordType.EVENT);

    // then
    assertThat(accepted).isFalse();
  }

  @Test
  void shouldAcceptCommandWhenCommandIndexingEnabled() {
    // given
    final var filter = createFilter(false, true, false);

    // when
    final var accepted = filter.acceptType(RecordType.COMMAND);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldRejectCommandWhenCommandIndexingDisabled() {
    // given
    final var filter = createFilter(false, false, false);

    // when
    final var accepted = filter.acceptType(RecordType.COMMAND);

    // then
    assertThat(accepted).isFalse();
  }

  @Test
  void shouldAcceptCommandRejectionWhenRejectionIndexingEnabled() {
    // given
    final var filter = createFilter(false, false, true);

    // when
    final var accepted = filter.acceptType(RecordType.COMMAND_REJECTION);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldRejectCommandRejectionWhenRejectionIndexingDisabled() {
    // given
    final var filter = createFilter(false, false, false);

    // when
    final var accepted = filter.acceptType(RecordType.COMMAND_REJECTION);

    // then
    assertThat(accepted).isFalse();
  }

  @ParameterizedTest(name = "recordType={0}, enabled={1} -> acceptRecord={4}")
  @CsvSource(
      value = {
        // recordType       | enableEvent | enableCommand | enableRejection | expectedAccepted
        "EVENT             | true  | false | false | true",
        "EVENT             | false | false | false | false",
        "COMMAND           | false | true  | false | true",
        "COMMAND           | false | false | false | false",
        "COMMAND_REJECTION | false | false | true  | true",
        "COMMAND_REJECTION | false | false | false | false"
      },
      delimiter = '|')
  void shouldFilterRecordsByRecordTypeConfiguration(
      final RecordType recordType,
      final boolean enableEvent,
      final boolean enableCommand,
      final boolean enableRejection,
      final boolean expectedAccepted) {

    // given
    final var filter =
        FilterBuilder.builder()
            .enableEvent(enableEvent)
            .enableCommand(enableCommand)
            .enableRejection(enableRejection)
            .build();

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

  private CommonRecordFilter createFilter(
      final boolean enableEvent, final boolean enableCommand, final boolean enableRejection) {
    return FilterBuilder.builder()
        .enableEvent(enableEvent)
        .enableCommand(enableCommand)
        .enableRejection(enableRejection)
        .build();
  }

  /**
   * Builder for constructing a {@link CommonRecordFilter} backed by Mockito-mocked {@link
   * CommonFilterConfiguration} and {@link IndexConfig}, with configurable record-type flags.
   */
  private static final class FilterBuilder {
    private boolean enableEvent;
    private boolean enableCommand;
    private boolean enableRejection;

    private FilterBuilder() {}

    static FilterBuilder builder() {
      return new FilterBuilder();
    }

    FilterBuilder enableEvent(final boolean enableEvent) {
      this.enableEvent = enableEvent;
      return this;
    }

    FilterBuilder enableCommand(final boolean enableCommand) {
      this.enableCommand = enableCommand;
      return this;
    }

    FilterBuilder enableRejection(final boolean enableRejection) {
      this.enableRejection = enableRejection;
      return this;
    }

    CommonRecordFilter build() {
      final var config = mock(CommonFilterConfiguration.class);
      final var indexConfig = mock(IndexConfig.class);

      // Index config: no variable-name/type rules; use empty lists instead of null
      when(config.filterIndexConfig()).thenReturn(indexConfig);
      when(indexConfig.isOptimizeModeEnabled()).thenReturn(false);

      // Record-type flags under test
      when(config.shouldIndexRecordType(RecordType.EVENT)).thenReturn(enableEvent);
      when(config.shouldIndexRecordType(RecordType.COMMAND)).thenReturn(enableCommand);
      when(config.shouldIndexRecordType(RecordType.COMMAND_REJECTION)).thenReturn(enableRejection);

      // Value-type filtering: make PROCESS_INSTANCE always allowed so record-type behavior is
      // isolated
      when(config.getIsIncludeEnabledRecords()).thenReturn(true);
      when(config.shouldIndexValueType(ValueType.PROCESS_INSTANCE)).thenReturn(true);
      when(config.shouldIndexRequiredValueType(ValueType.PROCESS_INSTANCE)).thenReturn(true);

      return new CommonRecordFilter(config);
    }
  }
}
