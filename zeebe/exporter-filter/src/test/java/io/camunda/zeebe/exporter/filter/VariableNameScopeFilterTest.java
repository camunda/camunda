/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import static io.camunda.zeebe.exporter.filter.NameFilterRule.parseRules;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.filter.NameFilterRule.Type;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VariableNameScopeFilterTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;

  // ---------------------------------------------------------------------------
  // Non-variable records
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptNonVariableRecord() {
    // given
    final var filter = new VariableNameScopeFilter(List.of(), List.of(), List.of(), List.of());
    final var record = nonVariableRecord();

    // when / then
    assertThat(filter.accept(record)).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Local-scope variables
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptLocalVariableMatchedByLocalInclusionRule() {
    // given
    final var localInclusion = parseRules(List.of("localVar"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(localInclusion, List.of(), List.of(), List.of());

    // when / then
    assertThat(filter.accept(localVariableRecord("localVar"))).isTrue();
  }

  @Test
  void shouldRejectLocalVariableNotMatchedByLocalInclusionRule() {
    // given
    final var localInclusion = parseRules(List.of("localVar"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(localInclusion, List.of(), List.of(), List.of());

    // when / then
    assertThat(filter.accept(localVariableRecord("other"))).isFalse();
  }

  @Test
  void shouldRejectLocalVariableMatchedByLocalExclusionRule() {
    // given
    final var localExclusion = parseRules(List.of("debug"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(List.of(), localExclusion, List.of(), List.of());

    // when / then
    assertThat(filter.accept(localVariableRecord("debug"))).isFalse();
  }

  @Test
  void shouldAcceptLocalVariableNotMatchedByLocalExclusionRule() {
    // given
    final var localExclusion = parseRules(List.of("debug"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(List.of(), localExclusion, List.of(), List.of());

    // when / then
    assertThat(filter.accept(localVariableRecord("localVar"))).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Root-scope variables
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptRootVariableMatchedByRootInclusionRule() {
    // given
    final var rootInclusion = parseRules(List.of("rootVar"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(List.of(), List.of(), rootInclusion, List.of());

    // when / then
    assertThat(filter.accept(rootVariableRecord("rootVar"))).isTrue();
  }

  @Test
  void shouldRejectRootVariableNotMatchedByRootInclusionRule() {
    // given
    final var rootInclusion = parseRules(List.of("rootVar"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(List.of(), List.of(), rootInclusion, List.of());

    // when / then
    assertThat(filter.accept(rootVariableRecord("other"))).isFalse();
  }

  @Test
  void shouldRejectRootVariableMatchedByRootExclusionRule() {
    // given
    final var rootExclusion = parseRules(List.of("secret"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(List.of(), List.of(), List.of(), rootExclusion);

    // when / then
    assertThat(filter.accept(rootVariableRecord("secret"))).isFalse();
  }

  @Test
  void shouldAcceptRootVariableNotMatchedByRootExclusionRule() {
    // given
    final var rootExclusion = parseRules(List.of("secret"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(List.of(), List.of(), List.of(), rootExclusion);

    // when / then
    assertThat(filter.accept(rootVariableRecord("rootVar"))).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Null variable names
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptLocalVariableWithNullNameAndNoRules() {
    // given
    final var filter = new VariableNameScopeFilter(List.of(), List.of(), List.of(), List.of());

    // when / then
    assertThat(filter.accept(localVariableRecord(null))).isTrue();
  }

  @Test
  void shouldAcceptLocalVariableWithNullNameWhenLocalInclusionConfigured() {
    // given
    final var localInclusion = parseRules(List.of("localVar"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(localInclusion, List.of(), List.of(), List.of());

    // when / then — NameFilter.accept(null) returns true, so null names always pass
    assertThat(filter.accept(localVariableRecord(null))).isTrue();
  }

  @Test
  void shouldAcceptRootVariableWithNullNameAndNoRules() {
    // given
    final var filter = new VariableNameScopeFilter(List.of(), List.of(), List.of(), List.of());

    // when / then
    assertThat(filter.accept(rootVariableRecord(null))).isTrue();
  }

  @Test
  void shouldAcceptRootVariableWithNullNameWhenRootInclusionConfigured() {
    // given
    final var rootInclusion = parseRules(List.of("rootVar"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(List.of(), List.of(), rootInclusion, List.of());

    // when / then — NameFilter.accept(null) returns true, so null names always pass
    assertThat(filter.accept(rootVariableRecord(null))).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Scope isolation
  // ---------------------------------------------------------------------------

  @Test
  void shouldNotApplyLocalRulesToRootVariables() {
    // given: local rules configured, no root rules
    final var localInclusion = parseRules(List.of("localVar"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(localInclusion, List.of(), List.of(), List.of());

    // a root variable with a name that would fail local inclusion must still pass
    assertThat(filter.accept(rootVariableRecord("other"))).isTrue();
  }

  @Test
  void shouldNotApplyRootRulesToLocalVariables() {
    // given: root rules configured, no local rules
    final var rootInclusion = parseRules(List.of("rootVar"), Type.EXACT);
    final var filter = new VariableNameScopeFilter(List.of(), List.of(), rootInclusion, List.of());

    // a local variable with a name that would fail root inclusion must still pass
    assertThat(filter.accept(localVariableRecord("other"))).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Version constraint
  // ---------------------------------------------------------------------------

  @Test
  void shouldExposeMinRecordBrokerVersion() {
    final var filter = new VariableNameScopeFilter(List.of(), List.of(), List.of(), List.of());

    assertThat(filter.minRecordBrokerVersion().toString()).isEqualTo("8.10.0");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static Record<ProcessInstanceRecordValue> nonVariableRecord() {
    final var record = (Record<ProcessInstanceRecordValue>) mock(Record.class);
    when(record.getValue()).thenReturn(mock(ProcessInstanceRecordValue.class));
    return record;
  }

  /** A local variable has a scopeKey that differs from the processInstanceKey. */
  @SuppressWarnings("unchecked")
  private static Record<VariableRecordValue> localVariableRecord(final String name) {
    final var record = (Record<VariableRecordValue>) mock(Record.class);
    final var value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(value.getScopeKey()).thenReturn(PROCESS_INSTANCE_KEY + 1);
    when(value.getName()).thenReturn(name);
    return record;
  }

  /** A root variable has a scopeKey equal to the processInstanceKey. */
  @SuppressWarnings("unchecked")
  private static Record<VariableRecordValue> rootVariableRecord(final String name) {
    final var record = (Record<VariableRecordValue>) mock(Record.class);
    final var value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(value.getScopeKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(value.getName()).thenReturn(name);
    return record;
  }
}
