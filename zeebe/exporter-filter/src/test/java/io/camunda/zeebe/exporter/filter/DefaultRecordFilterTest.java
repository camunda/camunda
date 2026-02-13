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

    configuration.filterIndexConfig().withVariableNameInclusionExact(List.of("included"));

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
    configuration.filterIndexConfig().withVariableNameExclusionExact(List.of("excluded"));

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

    configuration.filterIndexConfig().withVariableNameInclusionStartWith(List.of("incl"));

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

    configuration.filterIndexConfig().withVariableNameInclusionEndWith(List.of("_suffix"));

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
    configuration.filterIndexConfig().withVariableNameExclusionStartWith(List.of("secret_"));

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
    configuration.filterIndexConfig().withVariableNameExclusionEndWith(List.of("_tmp"));

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

  @Test
  void shouldApplyVariableValueTypeInclusionFromTestConfiguration() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    // Only STRING variables should be included
    configuration.filterIndexConfig().withVariableValueTypeInclusion(List.of("STRING"));

    final var filter = new DefaultRecordFilter(configuration);

    // JSON string -> STRING
    final var stringVar = variableRecord("v_string", "\"foo\"");
    // JSON number -> DOUBLE
    final var numberVar = variableRecord("v_number", "42");

    // when / then
    assertThat(filter.acceptRecord(stringVar))
        .as("STRING variable should be accepted by inclusion type filter")
        .isTrue();
    assertThat(filter.acceptRecord(numberVar))
        .as("non-STRING variable should be rejected by inclusion type filter")
        .isFalse();
  }

  @Test
  void shouldApplyVariableValueTypeExclusionFromTestConfiguration() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    // Exclude DOUBLE variables
    configuration.filterIndexConfig().withVariableValueTypeExclusion(List.of("NUMBER"));

    final var filter = new DefaultRecordFilter(configuration);

    // JSON string -> STRING
    final var stringVar = variableRecord("v_string", "\"foo\"");
    // JSON number -> DOUBLE
    final var numberVar = variableRecord("v_number", "42");

    // when / then
    assertThat(filter.acceptRecord(numberVar))
        .as("DOUBLE variable should be rejected by exclusion type filter")
        .isFalse();
    assertThat(filter.acceptRecord(stringVar))
        .as("non-DOUBLE variable should be accepted by exclusion type filter")
        .isTrue();
  }

  @Test
  void shouldNotApplyVariableNameFilterWhenNoNameRulesConfigured() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    final var filter = new DefaultRecordFilter(configuration);

    final var record1 = variableRecord("anyName");

    // when / then
    assertThat(filter.acceptRecord(record1))
        .as("Without name inclusion/exclusion rules, all variable names should be accepted")
        .isTrue();
  }

  @Test
  void shouldApplyOptimizeModeFilterWhenOptimizeModeEnabled() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            // We pick JOB because OptimizeModeFilter never accepts JOB records
            .withIndexedValueType(ValueType.JOB);

    // Enable Optimize mode on the index config so that createRecordFilters()
    // adds an OptimizeModeFilter to the chain.
    configuration.filterIndexConfig().withOptimizeModeEnabled(true);

    final var filter = new DefaultRecordFilter(configuration);

    @SuppressWarnings("unchecked")
    final Record<?> jobRecord = (Record<?>) mock(Record.class);
    when(jobRecord.getRecordType()).thenReturn(RecordType.EVENT);
    when(jobRecord.getValueType()).thenReturn(ValueType.JOB);

    // when / then
    assertThat(filter.acceptRecord(jobRecord))
        .as("With Optimize mode enabled, non-Optimize value types like JOB should be rejected")
        .isFalse();
  }

  @Test
  void shouldNotApplyVariableValueTypeFilterWhenNoTypeRulesConfigured() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    final var filter = new DefaultRecordFilter(configuration);

    final var stringVar = variableRecord("v_string", "\"foo\"");

    // when / then
    assertThat(filter.acceptRecord(stringVar))
        .as("Without type inclusion/exclusion rules, STRING variables should be accepted")
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------
  private static Record<VariableRecordValue> variableRecord(final String name) {
    return variableRecord(name, null);
  }

  @SuppressWarnings("unchecked")
  private static Record<VariableRecordValue> variableRecord(
      final String name, final String rawValue) {

    final Record<VariableRecordValue> record = (Record<VariableRecordValue>) mock(Record.class);
    final var value = mock(VariableRecordValue.class);

    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getValue()).thenReturn(value);
    when(value.getName()).thenReturn(name);
    when(value.getValue()).thenReturn(rawValue);

    return record;
  }
}
