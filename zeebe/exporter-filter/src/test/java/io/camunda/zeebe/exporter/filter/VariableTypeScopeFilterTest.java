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

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class VariableTypeScopeFilterTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;

  // ---------------------------------------------------------------------------
  // Non-variable records
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptNonVariableRecord() {
    // given
    final var filter = new VariableTypeScopeFilter(Set.of(), Set.of(), Set.of(), Set.of());
    final var record = nonVariableRecord();

    // when / then
    assertThat(filter.accept(record)).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Local-scope variables
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptLocalVariableWithMatchingLocalInclusionType() {
    // given
    final var filter =
        new VariableTypeScopeFilter(Set.of(VariableValueType.NUMBER), Set.of(), Set.of(), Set.of());

    // when / then
    assertThat(filter.accept(localVariableRecord("42"))).isTrue();
  }

  @Test
  void shouldRejectLocalVariableWithNonMatchingLocalInclusionType() {
    // given
    final var filter =
        new VariableTypeScopeFilter(Set.of(VariableValueType.NUMBER), Set.of(), Set.of(), Set.of());

    // when / then
    assertThat(filter.accept(localVariableRecord("\"text\""))).isFalse();
  }

  @Test
  void shouldRejectLocalVariableWithMatchingLocalExclusionType() {
    // given
    final var filter =
        new VariableTypeScopeFilter(
            Set.of(), Set.of(VariableValueType.BOOLEAN), Set.of(), Set.of());

    // when / then
    assertThat(filter.accept(localVariableRecord("true"))).isFalse();
  }

  @Test
  void shouldAcceptLocalVariableNotMatchingLocalExclusionType() {
    // given
    final var filter =
        new VariableTypeScopeFilter(
            Set.of(), Set.of(VariableValueType.BOOLEAN), Set.of(), Set.of());

    // when / then
    assertThat(filter.accept(localVariableRecord("42"))).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Root-scope variables
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptRootVariableWithMatchingRootInclusionType() {
    // given
    final var filter =
        new VariableTypeScopeFilter(Set.of(), Set.of(), Set.of(VariableValueType.STRING), Set.of());

    // when / then
    assertThat(filter.accept(rootVariableRecord("\"hello\""))).isTrue();
  }

  @Test
  void shouldRejectRootVariableWithNonMatchingRootInclusionType() {
    // given
    final var filter =
        new VariableTypeScopeFilter(Set.of(), Set.of(), Set.of(VariableValueType.STRING), Set.of());

    // when / then
    assertThat(filter.accept(rootVariableRecord("42"))).isFalse();
  }

  @Test
  void shouldRejectRootVariableWithMatchingRootExclusionType() {
    // given
    final var filter =
        new VariableTypeScopeFilter(Set.of(), Set.of(), Set.of(), Set.of(VariableValueType.OBJECT));

    // when / then
    assertThat(filter.accept(rootVariableRecord("{\"a\":1}"))).isFalse();
  }

  @Test
  void shouldAcceptRootVariableNotMatchingRootExclusionType() {
    // given
    final var filter =
        new VariableTypeScopeFilter(Set.of(), Set.of(), Set.of(), Set.of(VariableValueType.OBJECT));

    // when / then
    assertThat(filter.accept(rootVariableRecord("\"text\""))).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Scope isolation
  // ---------------------------------------------------------------------------

  @Test
  void shouldNotApplyLocalRulesToRootVariables() {
    // given: local rules exclude BOOLEAN, no root rules
    final var filter =
        new VariableTypeScopeFilter(
            Set.of(), Set.of(VariableValueType.BOOLEAN), Set.of(), Set.of());

    // a root boolean variable must still pass because root has no rules
    assertThat(filter.accept(rootVariableRecord("true"))).isTrue();
  }

  @Test
  void shouldNotApplyRootRulesToLocalVariables() {
    // given: root rules include only STRING, no local rules
    final var filter =
        new VariableTypeScopeFilter(Set.of(), Set.of(), Set.of(VariableValueType.STRING), Set.of());

    // a local number variable must still pass because local has no rules
    assertThat(filter.accept(localVariableRecord("42"))).isTrue();
  }

  // ---------------------------------------------------------------------------
  // Version constraint
  // ---------------------------------------------------------------------------

  @Test
  void shouldExposeMinRecordBrokerVersion() {
    final var filter = new VariableTypeScopeFilter(Set.of(), Set.of(), Set.of(), Set.of());

    assertThat(filter.minRecordBrokerVersion())
        .isEqualTo(new SemanticVersion(8, 10, 0, null, null));
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
  private static Record<VariableRecordValue> localVariableRecord(final String jsonValue) {
    final var record = (Record<VariableRecordValue>) mock(Record.class);
    final var value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(value.getScopeKey()).thenReturn(PROCESS_INSTANCE_KEY + 1);
    when(value.getValue()).thenReturn(jsonValue);
    return record;
  }

  /** A root variable has a scopeKey equal to the processInstanceKey. */
  @SuppressWarnings("unchecked")
  private static Record<VariableRecordValue> rootVariableRecord(final String jsonValue) {
    final var record = (Record<VariableRecordValue>) mock(Record.class);
    final var value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(value.getScopeKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(value.getValue()).thenReturn(jsonValue);
    return record;
  }
}
