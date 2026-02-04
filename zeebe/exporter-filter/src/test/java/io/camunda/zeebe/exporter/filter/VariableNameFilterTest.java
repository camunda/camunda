/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static io.camunda.zeebe.exporter.filter.VariableNameFilter.parseRules;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.filter.NameFilterRule.Type;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VariableNameFilterTest {

  // ---------------------------------------------------------------------------
  // Record-type behavior
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptNonVariableRecords() {
    // given
    final var filter = new VariableNameFilter(List.of(), List.of());
    final Record<ProcessInstanceRecordValue> record = nonVariableRecord();

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Inclusion rules
  // ---------------------------------------------------------------------------

  @Test
  void shouldFilterVariableNamesUsingInclusionRules() {
    // given: only variables with exact name "foo" are allowed
    final var inclusionRules = parseRules(List.of("foo"), Type.EXACT);
    final var filter = new VariableNameFilter(inclusionRules, List.of());

    final Record<VariableRecordValue> matchingRecord = variableRecord("foo");
    final Record<VariableRecordValue> nonMatchingRecord = variableRecord("bar");

    // when
    final boolean matchingAccepted = filter.accept(matchingRecord);
    final boolean nonMatchingAccepted = filter.accept(nonMatchingRecord);

    // then
    assertThat(matchingAccepted).isTrue();
    assertThat(nonMatchingAccepted).isFalse();
  }

  @Test
  void shouldApplyInclusionAndExclusionRulesTogether() {
    // given:
    //  - include names starting with "biz_"
    //  - exclude exact name "biz_debug"
    final var inclusionRules = parseRules(List.of("biz_"), Type.STARTS_WITH);
    final var exclusionRules = parseRules(List.of("biz_debug"), Type.EXACT);

    final var filter = new VariableNameFilter(inclusionRules, exclusionRules);

    final Record<VariableRecordValue> allowed = variableRecord("biz_total");
    final Record<VariableRecordValue> excluded = variableRecord("biz_debug");
    final Record<VariableRecordValue> notIncluded = variableRecord("other");

    // when
    final boolean allowedAccepted = filter.accept(allowed);
    final boolean excludedAccepted = filter.accept(excluded);
    final boolean notIncludedAccepted = filter.accept(notIncluded);

    // then
    assertThat(allowedAccepted).as("name matches inclusion and not exclusion").isTrue();
    assertThat(excludedAccepted)
        .as("name matches both inclusion and exclusion -> excluded")
        .isFalse();
    assertThat(notIncludedAccepted).as("name does not match inclusion -> rejected").isFalse();
  }

  // ---------------------------------------------------------------------------
  // parseRules behavior
  // ---------------------------------------------------------------------------

  @Test
  void parseRulesShouldReturnEmptyListForNull() {
    // when
    final var rules = parseRules(null, Type.EXACT);

    // then
    assertThat(rules).isEmpty();
  }

  @Test
  void parseRulesShouldTrimAndIgnoreEmptyEntries() {
    // given
    final var raw = List.of("  foo ", " ", "", "bar");

    // when
    final var rules = parseRules(raw, Type.EXACT);

    // then
    assertThat(rules).hasSize(2).allSatisfy(rule -> assertThat(rule.type()).isEqualTo(Type.EXACT));
    assertThat(rules.get(0).pattern()).isEqualTo("foo");
    assertThat(rules.get(1).pattern()).isEqualTo("bar");
  }

  // ---------------------------------------------------------------------------
  // Version constraint
  // ---------------------------------------------------------------------------

  @Test
  void shouldExposeMinRecordBrokerVersion() {
    final var filter = new VariableNameFilter(List.of(), List.of());

    assertThat(filter.minRecordBrokerVersion().toString()).isEqualTo("8.9.0");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static Record<ProcessInstanceRecordValue> nonVariableRecord() {
    final var record = (Record<ProcessInstanceRecordValue>) mock(Record.class);
    final var value = mock(ProcessInstanceRecordValue.class);
    when(record.getValue()).thenReturn(value);
    return record;
  }

  @SuppressWarnings("unchecked")
  private static Record<VariableRecordValue> variableRecord(final String name) {
    final var record = (Record<VariableRecordValue>) mock(Record.class);
    final var value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getName()).thenReturn(name);
    return record;
  }
}
