/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

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

/** Unit tests for the variable type filtering in OpensearchRecordFilter. */
final class OpensearchRecordFilterVariableTypeTest {

  private final ProtocolFactory factory = new ProtocolFactory(b -> b.withAuthorizations(Map.of()));

  @Test
  void shouldAcceptVariableWhenTypeMatchesInclusion() {
    // given: only BOOLEAN variables should be accepted
    final var config = new OpensearchExporterConfiguration();
    config.index.variableValueTypeInclusion = List.of("BOOLEAN");
    final var filter = createFilter(config);

    final var booleanValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("true") // JSON boolean
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(booleanValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldRejectVariableWhenTypeDoesNotMatchInclusion() {
    // given: only BOOLEAN variables should be accepted
    final var config = new OpensearchExporterConfiguration();
    config.index.variableValueTypeInclusion = List.of("BOOLEAN");
    final var filter = createFilter(config);

    final var numberValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("42") // JSON number -> DOUBLE
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(numberValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isFalse();
  }

  @Test
  void shouldRejectVariableWhenTypeIsExcluded() {
    // given: exclude BOOLEAN, no inclusion list
    final var config = new OpensearchExporterConfiguration();
    config.index.variableValueTypeExclusion = List.of("BOOLEAN");
    final var filter = createFilter(config);

    final var booleanValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("false") // JSON boolean
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(booleanValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isFalse();
  }

  @Test
  void shouldAllowOtherTypesWhenOnlyOneTypeIsExcluded() {
    // given: exclude BOOLEAN, but allow everything else
    final var config = new OpensearchExporterConfiguration();
    config.index.variableValueTypeExclusion = List.of("BOOLEAN");
    final var filter = createFilter(config);

    final var stringValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("\"hello\"") // JSON string
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(stringValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @ParameterizedTest(name = "inclusion={0}, exclusion={1}, raw={2} -> accepted={3}")
  @CsvSource(
      value = {
        // inclusion-only cases
        "BOOLEAN        |                | true         | true",
        "BOOLEAN        |                | 42           | false",
        "DOUBLE         |                | 42           | true",
        "STRING         |                | '\"text\"'   | true",
        "OBJECT         |                | '{\"a\":1}'  | true",
        "OBJECT         |                | '[1,2,3]'    | true",
        // exclusion-only cases
        "               | BOOLEAN        | true         | false",
        "               | BOOLEAN        | 42           | true",
        // inclusion + exclusion together: include BOOLEAN & DOUBLE, but exclude BOOLEAN
        "BOOLEAN,DOUBLE | BOOLEAN        | true         | false",
        "BOOLEAN,DOUBLE | BOOLEAN        | 42           | true"
      },
      delimiter = '|')
  void shouldFilterVariablesByInferredType(
      final String inclusionCsv,
      final String exclusionCsv,
      final String rawValue,
      final boolean expectedAccepted) {

    // given
    final var config = new OpensearchExporterConfiguration();

    if (inclusionCsv != null && !inclusionCsv.isBlank()) {
      config.index.variableValueTypeInclusion = List.of(inclusionCsv.trim().split("\\s*,\\s*"));
    }
    if (exclusionCsv != null && !exclusionCsv.isBlank()) {
      config.index.variableValueTypeExclusion = List.of(exclusionCsv.trim().split("\\s*,\\s*"));
    }

    final var filter = createFilter(config);

    final var variableValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue(rawValue)
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
  void shouldAcceptAllVariablesWhenInclusionAndExclusionAreEmpty() {
    // given
    final var config = new OpensearchExporterConfiguration();
    config.index.variableValueTypeInclusion = List.of();
    config.index.variableValueTypeExclusion = List.of();
    final var filter = createFilter(config);

    final var anyValue =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withValue("true")
            .build();

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> record = mock(Record.class);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(mock(Intent.class));
    when(record.getValue()).thenReturn(anyValue);

    // when
    final var accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldAcceptNonVariableRecords() {
    // given
    final var config = new OpensearchExporterConfiguration();
    config.index.variableValueTypeInclusion = List.of("BOOLEAN");
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

  private OpensearchRecordFilter createFilter(final OpensearchExporterConfiguration config) {
    return new OpensearchRecordFilter(config);
  }
}
