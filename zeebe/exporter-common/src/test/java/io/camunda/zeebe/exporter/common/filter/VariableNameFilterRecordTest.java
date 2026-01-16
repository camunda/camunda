/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.common.filter.NameRule.Type;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VariableNameFilterRecordTest {

  @Test
  void shouldAcceptNonVariableRecords() {
    // given
    final var filter = new VariableNameFilterRecord(List.of(), List.of());

    @SuppressWarnings("unchecked")
    final Record<ProcessInstanceRecordValue> record = mock(Record.class);
    final ProcessInstanceRecordValue value = mock(ProcessInstanceRecordValue.class);
    when(record.getValue()).thenReturn(value);

    // when
    final boolean accepted = filter.accept(record);

    // then
    assertThat(accepted).isTrue();
  }

  @Test
  void shouldFilterVariableNamesUsingInclusionRules() {
    // given: only variables with exact name "foo" are allowed
    final var inclusionRules =
        VariableNameFilterRecord.parseRules(List.of("foo"), NameRule.Type.EXACT);

    final var filter = new VariableNameFilterRecord(inclusionRules, List.of());

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> matchingRecord = mock(Record.class);
    final VariableRecordValue matchingValue = mock(VariableRecordValue.class);
    when(matchingRecord.getValue()).thenReturn(matchingValue);
    when(matchingValue.getName()).thenReturn("foo");

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> nonMatchingRecord = mock(Record.class);
    final VariableRecordValue nonMatchingValue = mock(VariableRecordValue.class);
    when(nonMatchingRecord.getValue()).thenReturn(nonMatchingValue);
    when(nonMatchingValue.getName()).thenReturn("bar");

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
    final var inclusionRules =
        VariableNameFilterRecord.parseRules(List.of("biz_"), NameRule.Type.STARTS_WITH);
    final var exclusionRules =
        VariableNameFilterRecord.parseRules(List.of("biz_debug"), NameRule.Type.EXACT);

    final var filter = new VariableNameFilterRecord(inclusionRules, exclusionRules);

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> allowedRecord = mock(Record.class);
    final VariableRecordValue allowedValue = mock(VariableRecordValue.class);
    when(allowedRecord.getValue()).thenReturn(allowedValue);
    when(allowedValue.getName()).thenReturn("biz_total");

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> excludedRecord = mock(Record.class);
    final VariableRecordValue excludedValue = mock(VariableRecordValue.class);
    when(excludedRecord.getValue()).thenReturn(excludedValue);
    when(excludedValue.getName()).thenReturn("biz_debug");

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> notIncludedRecord = mock(Record.class);
    final VariableRecordValue notIncludedValue = mock(VariableRecordValue.class);
    when(notIncludedRecord.getValue()).thenReturn(notIncludedValue);
    when(notIncludedValue.getName()).thenReturn("other");

    // when
    final boolean allowedAccepted = filter.accept(allowedRecord);
    final boolean excludedAccepted = filter.accept(excludedRecord);
    final boolean notIncludedAccepted = filter.accept(notIncludedRecord);

    // then
    assertThat(allowedAccepted).as("name matches inclusion and not exclusion").isTrue();
    assertThat(excludedAccepted).as("name matches inclusion and exclusion -> excluded").isFalse();
    assertThat(notIncludedAccepted).as("name does not match inclusion -> rejected").isFalse();
  }

  @Test
  void parseRulesShouldReturnEmptyListForNull() {
    // when
    final var rules = VariableNameFilterRecord.parseRules(null, Type.EXACT);

    // then
    assertThat(rules).isEmpty();
  }

  @Test
  void parseRulesShouldTrimAndIgnoreEmptyEntries() {
    // given
    final var raw = List.of("  foo ", " ", "", "bar");

    // when
    final var rules = VariableNameFilterRecord.parseRules(raw, NameRule.Type.EXACT);

    // then
    assertThat(rules)
        .hasSize(2)
        .allSatisfy(
            rule -> {
              assertThat(rule.type()).isEqualTo(NameRule.Type.EXACT);
            });
    assertThat(rules.get(0).pattern()).isEqualTo("foo");
    assertThat(rules.get(1).pattern()).isEqualTo("bar");
  }

  @Test
  void shouldExposeMinRecordVersion() {
    final var filter = new VariableNameFilterRecord(List.of(), List.of());
    assertThat(filter.minRecordVersion()).isEqualTo("8.9.0");
  }
}
