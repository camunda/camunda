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
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
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
  void shouldApplyOptimizeModeFilterWhenOptimizeModeEnabled() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            // We pick JOB because OptimizeModeFilter never accepts JOB records
            .withIndexedValueType(ValueType.JOB);

    configuration.filterIndexConfig().withOptimizeModeEnabled(true);

    final var filter = new DefaultRecordFilter(configuration);

    @SuppressWarnings("unchecked")
    final Record<?> jobRecord = (Record<?>) mock(Record.class);
    when(jobRecord.getRecordType()).thenReturn(RecordType.EVENT);
    when(jobRecord.getValueType()).thenReturn(ValueType.JOB);
    when(jobRecord.getBrokerVersion()).thenReturn("8.9.0");

    // when / then
    assertThat(filter.acceptRecord(jobRecord))
        .as("With Optimize mode enabled, non-Optimize value types like JOB should be rejected")
        .isFalse();
  }

  @Test
  void shouldWireVariableNameFilterWhenNameRulesConfigured() {
    // given: an exact inclusion rule — any other name must be rejected
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration.filterIndexConfig().withVariableNameInclusionExact(List.of("allowed"));

    final var filter = new DefaultRecordFilter(configuration);

    // when / then
    assertThat(filter.acceptRecord(variableRecord("allowed")))
        .as("Variable matching the inclusion rule should be accepted")
        .isTrue();
    assertThat(filter.acceptRecord(variableRecord("other")))
        .as(
            "Variable not matching the inclusion rule should be rejected, proving the filter is wired")
        .isFalse();
  }

  @Test
  void shouldWireVariableNameFilterHandlingInclusionExclusionConflict() {
    // given: the same name is both included and excluded — exclusion takes precedence in NameFilter
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration
        .filterIndexConfig()
        .withVariableNameInclusionExact(List.of("conflicted"))
        .withVariableNameExclusionExact(List.of("conflicted"));

    final var filter = new DefaultRecordFilter(configuration);

    // when / then
    assertThat(filter.acceptRecord(variableRecord("conflicted")))
        .as("When a name matches both inclusion and exclusion rules, exclusion wins")
        .isFalse();
  }

  @Test
  void shouldWireVariableTypeFilterWhenTypeRulesConfigured() {
    // given: only NUMBER variables are included
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration.filterIndexConfig().withVariableValueTypeInclusion(List.of("NUMBER"));

    final var filter = new DefaultRecordFilter(configuration);

    // when / then
    assertThat(filter.acceptRecord(variableRecord("v", "42")))
        .as("NUMBER variable should be accepted by the inclusion type filter")
        .isTrue();
    assertThat(filter.acceptRecord(variableRecord("v", "\"text\"")))
        .as("STRING variable should be rejected, proving the type filter is wired")
        .isFalse();
  }

  @Test
  void shouldWireExportLocalVariablesFilterWhenFlagDisabled() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration.filterIndexConfig().withExportLocalVariablesEnabled(false);

    final var filter = new DefaultRecordFilter(configuration);

    // local variable (scopeKey != processInstanceKey) must be rejected
    assertThat(filter.acceptRecord(scopedVariableRecord("v", null, 100L, 200L)))
        .as("Local variables should be rejected when exportLocalVariables is disabled")
        .isFalse();

    // root variable (scopeKey == processInstanceKey) must pass
    assertThat(filter.acceptRecord(scopedVariableRecord("v", null, 100L, 100L)))
        .as("Root variables should still be accepted when exportLocalVariables is disabled")
        .isTrue();
  }

  @Test
  void shouldWireVariableNameScopeFilterWhenLocalNameRulesPresent() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration.filterIndexConfig().withLocalVariableNameInclusionExact(List.of("localOnly"));

    final var filter = new DefaultRecordFilter(configuration);

    // local variable matching inclusion rule → accepted
    assertThat(filter.acceptRecord(scopedVariableRecord("localOnly", null, 100L, 200L)))
        .as("Local variable matching local inclusion rule should be accepted")
        .isTrue();

    // local variable not matching inclusion rule → rejected
    assertThat(filter.acceptRecord(scopedVariableRecord("other", null, 100L, 200L)))
        .as("Local variable not matching local inclusion rule should be rejected")
        .isFalse();

    // root variable with no root rules → always accepted
    assertThat(filter.acceptRecord(scopedVariableRecord("other", null, 100L, 100L)))
        .as("Root variable should pass when only local rules are configured")
        .isTrue();
  }

  @Test
  void shouldWireVariableNameScopeFilterWhenRootNameRulesPresent() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration.filterIndexConfig().withRootVariableNameExclusionExact(List.of("secret"));

    final var filter = new DefaultRecordFilter(configuration);

    // root variable matching exclusion rule → rejected
    assertThat(filter.acceptRecord(scopedVariableRecord("secret", null, 100L, 100L)))
        .as("Root variable matching root exclusion rule should be rejected")
        .isFalse();

    // root variable not matching exclusion rule → accepted
    assertThat(filter.acceptRecord(scopedVariableRecord("public", null, 100L, 100L)))
        .as("Root variable not matching root exclusion rule should be accepted")
        .isTrue();

    // local variable with no local rules → always accepted
    assertThat(filter.acceptRecord(scopedVariableRecord("secret", null, 100L, 200L)))
        .as("Local variable should pass when only root rules are configured")
        .isTrue();
  }

  @Test
  void shouldWireVariableTypeScopeFilterWhenLocalTypeRulesPresent() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration.filterIndexConfig().withLocalVariableValueTypeInclusion(List.of("NUMBER"));

    final var filter = new DefaultRecordFilter(configuration);

    // local number variable → accepted by local inclusion
    assertThat(filter.acceptRecord(scopedVariableRecord("v", "42", 100L, 200L)))
        .as("Local NUMBER variable should be accepted by local type inclusion")
        .isTrue();

    // local string variable → rejected by local inclusion
    assertThat(filter.acceptRecord(scopedVariableRecord("v", "\"text\"", 100L, 200L)))
        .as("Local STRING variable should be rejected by local type inclusion of NUMBER")
        .isFalse();

    // root variable with no root rules → always accepted regardless of type
    assertThat(filter.acceptRecord(scopedVariableRecord("v", "\"text\"", 100L, 100L)))
        .as("Root variable should pass when only local type rules are configured")
        .isTrue();
  }

  @Test
  void shouldWireVariableTypeScopeFilterWhenRootTypeRulesPresent() {
    // given
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration.filterIndexConfig().withRootVariableValueTypeExclusion(List.of("BOOLEAN"));

    final var filter = new DefaultRecordFilter(configuration);

    // root boolean variable → rejected by root exclusion
    assertThat(filter.acceptRecord(scopedVariableRecord("v", "true", 100L, 100L)))
        .as("Root BOOLEAN variable should be rejected by root type exclusion")
        .isFalse();

    // root string variable → accepted
    assertThat(filter.acceptRecord(scopedVariableRecord("v", "\"text\"", 100L, 100L)))
        .as("Root STRING variable should be accepted when only BOOLEAN is excluded")
        .isTrue();

    // local variable with no local rules → always accepted
    assertThat(filter.acceptRecord(scopedVariableRecord("v", "true", 100L, 200L)))
        .as("Local variable should pass when only root type rules are configured")
        .isTrue();
  }

  @Test
  void shouldWireBpmnProcessFilterWhenProcessIdRulesConfigured() {
    // given: only "order-process" is included
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.PROCESS_INSTANCE);

    configuration.filterIndexConfig().withBpmnProcessIdInclusion(List.of("order-process"));

    final var filter = new DefaultRecordFilter(configuration);

    // when / then
    assertThat(filter.acceptRecord(processInstanceRecord("order-process")))
        .as("Record for the included BPMN process id should be accepted")
        .isTrue();
    assertThat(filter.acceptRecord(processInstanceRecord("other-process")))
        .as(
            "Record for a non-included BPMN process id should be rejected, proving the filter is wired")
        .isFalse();
  }

  @Test
  void shouldSkipVersionedFilterForRecordsFromOlderBroker() {
    // given: a scope name filter (requires broker >= 8.10.0) that would reject "other"
    final var configuration =
        new TestConfiguration()
            .withIndexedRecordType(RecordType.EVENT)
            .withIndexedValueType(ValueType.VARIABLE);

    configuration.filterIndexConfig().withLocalVariableNameInclusionExact(List.of("onlyThis"));

    final var filter = new DefaultRecordFilter(configuration);

    // when: record comes from a broker older than the filter's minimum version
    final var oldBrokerRecord = scopedVariableRecord("other", null, 100L, 200L, "8.9.0");

    // then: the scope filter is skipped, so the record passes
    assertThat(filter.acceptRecord(oldBrokerRecord))
        .as("Scope filter should be skipped for records from brokers older than 8.10.0")
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------

  private static Record<VariableRecordValue> variableRecord(final String name) {
    // rawValue is null here; only safe when no type filter is configured in the test
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

  private static Record<VariableRecordValue> scopedVariableRecord(
      final String name,
      final String rawValue,
      final long processInstanceKey,
      final long scopeKey) {
    return scopedVariableRecord(name, rawValue, processInstanceKey, scopeKey, "8.10.0");
  }

  @SuppressWarnings("unchecked")
  private static Record<VariableRecordValue> scopedVariableRecord(
      final String name,
      final String rawValue,
      final long processInstanceKey,
      final long scopeKey,
      final String brokerVersion) {

    final Record<VariableRecordValue> record = (Record<VariableRecordValue>) mock(Record.class);
    final var value = mock(VariableRecordValue.class);

    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getBrokerVersion()).thenReturn(brokerVersion);
    when(record.getValue()).thenReturn(value);
    when(value.getName()).thenReturn(name);
    when(value.getValue()).thenReturn(rawValue);
    when(value.getProcessInstanceKey()).thenReturn(processInstanceKey);
    when(value.getScopeKey()).thenReturn(scopeKey);

    return record;
  }

  @SuppressWarnings("unchecked")
  private static Record<ProcessInstanceRecordValue> processInstanceRecord(
      final String bpmnProcessId) {

    final Record<ProcessInstanceRecordValue> record =
        (Record<ProcessInstanceRecordValue>) mock(Record.class);
    final var value = mock(ProcessInstanceRecordValue.class);

    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(record.getBrokerVersion()).thenReturn("8.9.0");
    when(record.getValue()).thenReturn(value);
    when(value.getBpmnProcessId()).thenReturn(bpmnProcessId);

    return record;
  }
}
