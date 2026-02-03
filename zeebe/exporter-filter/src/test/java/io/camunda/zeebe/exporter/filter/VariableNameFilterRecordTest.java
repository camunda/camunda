/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.filter.NameRule.Type;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class VariableNameFilterRecordTest {

  @Test
  void shouldAcceptNonVariableRecords() {
    // given
    final var filter = new VariableNameFilterRecord(List.of(), List.of());

    @SuppressWarnings("unchecked")
    final Record<ProcessInstanceRecordValue> record = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue value = Mockito.mock(ProcessInstanceRecordValue.class);
    Mockito.when(record.getValue()).thenReturn(value);

    // when
    final boolean accepted = filter.accept(record);

    // then
    Assertions.assertThat(accepted).isTrue();
  }

  @Test
  void shouldFilterVariableNamesUsingInclusionRules() {
    // given: only variables with exact name "foo" are allowed
    final var inclusionRules =
        VariableNameFilterRecord.parseRules(List.of("foo"), NameRule.Type.EXACT);

    final var filter = new VariableNameFilterRecord(inclusionRules, List.of());

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> matchingRecord = Mockito.mock(Record.class);
    final VariableRecordValue matchingValue = Mockito.mock(VariableRecordValue.class);
    Mockito.when(matchingRecord.getValue()).thenReturn(matchingValue);
    Mockito.when(matchingValue.getName()).thenReturn("foo");

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> nonMatchingRecord = Mockito.mock(Record.class);
    final VariableRecordValue nonMatchingValue = Mockito.mock(VariableRecordValue.class);
    Mockito.when(nonMatchingRecord.getValue()).thenReturn(nonMatchingValue);
    Mockito.when(nonMatchingValue.getName()).thenReturn("bar");

    // when
    final boolean matchingAccepted = filter.accept(matchingRecord);
    final boolean nonMatchingAccepted = filter.accept(nonMatchingRecord);

    // then
    Assertions.assertThat(matchingAccepted).isTrue();
    Assertions.assertThat(nonMatchingAccepted).isFalse();
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
    final Record<VariableRecordValue> allowedRecord = Mockito.mock(Record.class);
    final VariableRecordValue allowedValue = Mockito.mock(VariableRecordValue.class);
    Mockito.when(allowedRecord.getValue()).thenReturn(allowedValue);
    Mockito.when(allowedValue.getName()).thenReturn("biz_total");

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> excludedRecord = Mockito.mock(Record.class);
    final VariableRecordValue excludedValue = Mockito.mock(VariableRecordValue.class);
    Mockito.when(excludedRecord.getValue()).thenReturn(excludedValue);
    Mockito.when(excludedValue.getName()).thenReturn("biz_debug");

    @SuppressWarnings("unchecked")
    final Record<VariableRecordValue> notIncludedRecord = Mockito.mock(Record.class);
    final VariableRecordValue notIncludedValue = Mockito.mock(VariableRecordValue.class);
    Mockito.when(notIncludedRecord.getValue()).thenReturn(notIncludedValue);
    Mockito.when(notIncludedValue.getName()).thenReturn("other");

    // when
    final boolean allowedAccepted = filter.accept(allowedRecord);
    final boolean excludedAccepted = filter.accept(excludedRecord);
    final boolean notIncludedAccepted = filter.accept(notIncludedRecord);

    // then
    Assertions.assertThat(allowedAccepted).as("name matches inclusion and not exclusion").isTrue();
    Assertions.assertThat(excludedAccepted)
        .as("name matches inclusion and exclusion -> excluded")
        .isFalse();
    Assertions.assertThat(notIncludedAccepted)
        .as("name does not match inclusion -> rejected")
        .isFalse();
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
