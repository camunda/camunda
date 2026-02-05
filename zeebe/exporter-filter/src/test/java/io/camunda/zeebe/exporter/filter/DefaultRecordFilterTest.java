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

import io.camunda.zeebe.exporter.filter.config.TestConfiguration;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DefaultRecordFilterTest {

  @Test
  void shouldAcceptOnlyConfiguredRecordTypes() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedRecordType(RecordType.COMMAND);

    final var filter = new DefaultRecordFilter(configuration);

    // when / then
    assertThat(filter.acceptType(RecordType.EVENT)).isTrue();
    assertThat(filter.acceptType(RecordType.COMMAND)).isTrue();

    assertThat(filter.acceptType(RecordType.COMMAND_REJECTION)).isFalse();
  }

  @Test
  void shouldAcceptOnlyConfiguredValueTypes() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedValueType(ValueType.VARIABLE)
            .withIndexedValueType(ValueType.PROCESS_INSTANCE);

    final var filter = new DefaultRecordFilter(configuration);

    // when / then
    assertThat(filter.acceptValue(ValueType.VARIABLE)).isTrue();
    assertThat(filter.acceptValue(ValueType.PROCESS_INSTANCE)).isTrue();

    assertThat(filter.acceptValue(ValueType.JOB)).isFalse();
  }

  @Test
  void shouldNotTreatRequiredOnlyValueTypesAsNormalIndexed() {
    // given
    final var configuration =
        new TestConfiguration()
            // Only mark JOB as "required", not normal
            .withRequiredValueType(ValueType.JOB);

    final var filter = new DefaultRecordFilter(configuration);

    // when / then
    assertThat(filter.acceptValue(ValueType.JOB))
        .as("Required-only value types should not be accepted as normal indexed types")
        .isFalse();

    assertThat(configuration.shouldIndexRequiredValueType(ValueType.JOB)).isTrue();
  }

  @Test
  void shouldAcceptAllRecordsWithEmptyFilterChain() {
    // given
    final var configuration = new TestConfiguration();
    final var filter = new DefaultRecordFilter(configuration);
    final Record<?> record = mock(Record.class);

    // when
    final boolean accepted = filter.acceptRecord(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldApplyVariableNameInclusionRulesFromTestConfiguration() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration.testIndexConfig().withVariableNameInclusionExact(List.of("included"));

    final var filter = new DefaultRecordFilter(configuration);

    final var included = variableRecord("included");
    final var other = variableRecord("other");

    // when / then
    assertThat(filter.acceptRecord(included)).isTrue();
    assertThat(filter.acceptRecord(other)).isFalse();
  }

  @Test
  void shouldApplyVariableNameExclusionRulesFromTestConfiguration() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    // No inclusion rules -> all variable names are included by default,
    // but we explicitly exclude the exact name "excluded".
    configuration.testIndexConfig().withVariableNameExclusionExact(List.of("excluded"));

    final var filter = new DefaultRecordFilter(configuration);

    final var excluded = variableRecord("excluded");
    final var other = variableRecord("other");

    // when / then
    assertThat(filter.acceptRecord(excluded))
        .as("name 'excluded' should be rejected by exclusion rules")
        .isFalse();
    assertThat(filter.acceptRecord(other))
        .as("name 'other' is not excluded and should be accepted")
        .isTrue();
  }

  @Test
  void shouldApplyVariableNameInclusionStartWithRulesFromTestConfiguration() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration.testIndexConfig().withVariableNameInclusionStartWith(List.of("incl"));

    final var filter = new DefaultRecordFilter(configuration);

    final var included = variableRecord("includeMe");
    final var other = variableRecord("other");

    // when / then
    assertThat(filter.acceptRecord(included)).isTrue();
    assertThat(filter.acceptRecord(other)).isFalse();
  }

  @Test
  void shouldApplyVariableNameInclusionEndWithRulesFromTestConfiguration() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration.testIndexConfig().withVariableNameInclusionEndWith(List.of("_suffix"));

    final var filter = new DefaultRecordFilter(configuration);

    final var included = variableRecord("name_suffix");
    final var other = variableRecord("name_other");

    // when / then
    assertThat(filter.acceptRecord(included)).isTrue();
    assertThat(filter.acceptRecord(other)).isFalse();
  }

  @Test
  void shouldApplyVariableNameExclusionStartWithRulesFromTestConfiguration() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    // No inclusion rules -> all variable names are included by default,
    // but we explicitly exclude names starting with "secret_".
    configuration.testIndexConfig().withVariableNameExclusionStartWith(List.of("secret_"));

    final var filter = new DefaultRecordFilter(configuration);

    final var excluded = variableRecord("secret_token");
    final var other = variableRecord("public_value");

    // when / then
    assertThat(filter.acceptRecord(excluded))
        .as("names starting with 'secret_' should be rejected by exclusion rules")
        .isFalse();
    assertThat(filter.acceptRecord(other))
        .as("name 'public_value' does not start with 'secret_' and should be accepted")
        .isTrue();
  }

  @Test
  void shouldApplyVariableNameExclusionEndWithRulesFromTestConfiguration() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    // No inclusion rules -> all variable names are included by default,
    // but we explicitly exclude names ending with "_tmp".
    configuration.testIndexConfig().withVariableNameExclusionEndWith(List.of("_tmp"));

    final var excluded = variableRecord("value_tmp");
    final var other = variableRecord("value");

    final var filter = new DefaultRecordFilter(configuration);

    // when / then
    assertThat(filter.acceptRecord(excluded))
        .as("names ending with '_tmp' should be rejected by exclusion rules")
        .isFalse();
    assertThat(filter.acceptRecord(other))
        .as("name 'value' does not end with '_tmp' and should be accepted")
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------
  @SuppressWarnings("unchecked")
  private static Record<VariableRecordValue> variableRecord(final String name) {
    final var record = (Record<VariableRecordValue>) mock(Record.class);
    final var value = mock(VariableRecordValue.class);

    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getBrokerVersion()).thenReturn("8.9.0"); // satisfies VariableNameFilterRecord
    when(record.getValue()).thenReturn(value);
    when(value.getName()).thenReturn(name);

    return record;
  }
}
