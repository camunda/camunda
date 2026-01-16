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
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for the variable name inclusion filtering in ElasticsearchRecordFilter. */
final class ElasticsearchRecordFilterVariableNameTest {

  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));

  @Test
  void shouldAcceptVariableWhenNameMatchesInclusionPrefix() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionStartWith = List.of("included", "allowed");
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("includedVariable")
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(variableValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldNotAcceptVariableWhenNameDoesNotMatchInclusionPrefix() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionStartWith = List.of("included", "allowed");
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("excludedVariable")
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(variableValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      value = {
        "included | includedVariable | true",
        "included | includedAnotherName | true",
        "included | included | true",
        "allowed | allowedVariable | true",
        "allowed | allowedAnotherName | true",
        "var | variable | true",
        "var | anotherVar | false"
      },
      delimiter = '|')
  void shouldFilterVariablesByNamePrefix(
      final String inclusionPrefix, final String variableName, final boolean expectedAccepted) {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionStartWith = List.of(inclusionPrefix);
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName(variableName)
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(variableValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isEqualTo(expectedAccepted);
  }

  @Test
  void shouldAcceptAllVariablesWhenInclusionIsEmpty() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionExact = List.of();
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("anyVariable")
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(variableValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldTrimWhitespaceFromInclusionPrefixes() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionStartWith = List.of("included", "allowed");
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("includedVariable")
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(variableValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldAcceptNonVariableRecords() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionExact = List.of("included");
    final var filter = createFilter(config);

    final ProcessInstanceRecordValue value =
        factory.generateObject(ProcessInstanceRecordValue.class);

    @SuppressWarnings("unchecked")
    final Record<ProcessInstanceRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(value);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @ParameterizedTest(name = "blank inclusion -> accept variable: [{0}]")
  @CsvSource({
    ",", // null
    "'',", // empty string
    "'   '," // whitespace-only
  })
  void shouldAcceptAllVariablesWhenInclusionIsBlank(final String inclusion) {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variable = true;
    config.index.variableNameInclusionExact =
        inclusion != null ? List.of(inclusion) : null; // may be null/empty/whitespace
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("anyVariable")
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(variableValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldAcceptVariablesWithCommaSeparatedPrefixes() {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variable = true;
    config.index.variableNameInclusionStartWith = List.of("included", "allowed");
    final var filter = createFilter(config);

    final var includedVar =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("includedVariable")
            .build();
    final var allowedVar =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName("allowedVariable")
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> includedRecord = mock(Record.class);
    when(includedRecord.getBrokerVersion()).thenReturn("8.9.0");
    when(includedRecord.getRecordType()).thenReturn(RecordType.EVENT);
    when(includedRecord.getValueType()).thenReturn(ValueType.VARIABLE);
    when(includedRecord.getIntent()).thenReturn(mock(Intent.class));
    when(includedRecord.getValue()).thenReturn(includedVar);

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> allowedRecord = mock(Record.class);
    when(allowedRecord.getBrokerVersion()).thenReturn("8.9.0");
    when(allowedRecord.getRecordType()).thenReturn(RecordType.EVENT);
    when(allowedRecord.getValueType()).thenReturn(ValueType.VARIABLE);
    when(allowedRecord.getIntent()).thenReturn(mock(Intent.class));
    when(allowedRecord.getValue()).thenReturn(allowedVar);

    // when
    final var includedAccepted = filter.acceptRecord(includedRecord);
    final var allowedAccepted = filter.acceptRecord(allowedRecord);

    // then
    assertThat(includedAccepted).isTrue();
    assertThat(allowedAccepted).isTrue();
  }

  @ParameterizedTest(name = "exact inclusion={0}, name={1} -> accepted={2}")
  @CsvSource(
      value = {
        "included | included       | true",
        "included | includedVar    | false",
        "foo      | foo            | true",
        "foo      | bar            | false",
        "var      | var            | true",
        "var      | anotherVar     | false"
      },
      delimiter = '|')
  void shouldFilterVariablesByExactName(
      final String exactName, final String variableName, final boolean expectedAccepted) {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionExact = List.of(exactName);
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName(variableName)
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(variableValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isEqualTo(expectedAccepted);
  }

  @ParameterizedTest(name = "suffix inclusion={0}, name={1} -> accepted={2}")
  @CsvSource(
      value = {
        "_total  | order_total     | true",
        "_total  | _total          | true",
        "_total  | total_order     | false",
        "_count  | item_count      | true",
        "_count  | count_item      | false",
        "_id     | process_id      | true",
        "_id     | id_process      | false"
      },
      delimiter = '|')
  void shouldFilterVariablesByNameSuffix(
      final String inclusionSuffix, final String variableName, final boolean expectedAccepted) {
    // given
    final var config = new ElasticsearchExporterConfiguration();
    config.index.variableNameInclusionEndWith = List.of(inclusionSuffix);
    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withName(variableName)
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(variableValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isEqualTo(expectedAccepted);
  }

  private ElasticsearchRecordFilter createFilter(final ElasticsearchExporterConfiguration config) {
    return new ElasticsearchRecordFilter(config);
  }
}
