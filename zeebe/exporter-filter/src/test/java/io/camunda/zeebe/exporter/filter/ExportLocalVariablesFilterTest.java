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
import org.junit.jupiter.api.Test;

final class ExportLocalVariablesFilterTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;

  @Test
  void shouldAcceptLocalVariableWhenExportLocalVariablesEnabled() {
    // given
    final var filter = new ExportLocalVariablesFilter(true);
    final var record = localVariableRecord();

    // when / then
    assertThat(filter.accept(record)).isTrue();
  }

  @Test
  void shouldAcceptRootVariableWhenExportLocalVariablesEnabled() {
    // given
    final var filter = new ExportLocalVariablesFilter(true);
    final var record = rootVariableRecord();

    // when / then
    assertThat(filter.accept(record)).isTrue();
  }

  @Test
  void shouldRejectLocalVariableWhenExportLocalVariablesDisabled() {
    // given
    final var filter = new ExportLocalVariablesFilter(false);
    final var record = localVariableRecord();

    // when / then
    assertThat(filter.accept(record)).isFalse();
  }

  @Test
  void shouldAcceptRootVariableWhenExportLocalVariablesDisabled() {
    // given
    final var filter = new ExportLocalVariablesFilter(false);
    final var record = rootVariableRecord();

    // when / then
    assertThat(filter.accept(record)).isTrue();
  }

  @Test
  void shouldAcceptNonVariableRecord() {
    // given
    final var filter = new ExportLocalVariablesFilter(false);
    final var record = nonVariableRecord();

    // when / then
    assertThat(filter.accept(record)).isTrue();
  }

  @Test
  void shouldExposeMinRecordBrokerVersion() {
    final var filter = new ExportLocalVariablesFilter(true);

    assertThat(filter.minRecordBrokerVersion().toString()).isEqualTo("8.10.0");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** A local variable has a scopeKey that differs from the processInstanceKey. */
  @SuppressWarnings("unchecked")
  private static Record<VariableRecordValue> localVariableRecord() {
    final var record = (Record<VariableRecordValue>) mock(Record.class);
    final var value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(value.getScopeKey()).thenReturn(PROCESS_INSTANCE_KEY + 1);
    return record;
  }

  /** A root variable has a scopeKey equal to the processInstanceKey. */
  @SuppressWarnings("unchecked")
  private static Record<VariableRecordValue> rootVariableRecord() {
    final var record = (Record<VariableRecordValue>) mock(Record.class);
    final var value = mock(VariableRecordValue.class);
    when(record.getValue()).thenReturn(value);
    when(value.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(value.getScopeKey()).thenReturn(PROCESS_INSTANCE_KEY);
    return record;
  }

  @SuppressWarnings("unchecked")
  private static Record<ProcessInstanceRecordValue> nonVariableRecord() {
    final var record = (Record<ProcessInstanceRecordValue>) mock(Record.class);
    when(record.getValue()).thenReturn(mock(ProcessInstanceRecordValue.class));
    return record;
  }
}
