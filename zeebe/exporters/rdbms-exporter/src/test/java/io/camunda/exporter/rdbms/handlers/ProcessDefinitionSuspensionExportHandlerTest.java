/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.db.rdbms.write.service.ProcessDefinitionWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

class ProcessDefinitionSuspensionExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProcessDefinitionWriter processDefinitionWriter =
      mock(ProcessDefinitionWriter.class);
  private final ProcessDefinitionSuspensionExportHandler underTest =
      new ProcessDefinitionSuspensionExportHandler(processDefinitionWriter);

  @ParameterizedTest
  @EnumSource(
      value = ProcessDefinitionSuspensionIntent.class,
      names = {"SUSPENDED", "RESUMED"},
      mode = Mode.INCLUDE)
  void shouldExportHandledEvents(final ProcessDefinitionSuspensionIntent intent) {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(intent, 999L, List.of());

    // when - then
    assertThat(underTest.canExport(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessDefinitionSuspensionIntent.class,
      names = {"SUSPENDED", "RESUMED"},
      mode = Mode.EXCLUDE)
  void shouldNotExportCommands(final ProcessDefinitionSuspensionIntent intent) {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(intent, 999L, List.of());

    // when - then
    assertThat(underTest.canExport(record)).isFalse();
  }

  @Test
  void shouldMarkOnlySuspendedVersionsOnSuspendedRecord() {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(
            ProcessDefinitionSuspensionIntent.SUSPENDED,
            999L,
            List.of(
                version(1L, ProcessDefinitionSuspensionOutcome.SUSPENDED),
                version(2L, ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED)));

    // when
    underTest.export(record);

    // then
    verify(processDefinitionWriter).markSuspended(1L);
    verifyNoMoreInteractions(processDefinitionWriter);
  }

  @Test
  void shouldMarkOnlyResumedVersionsActiveOnResumedRecord() {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(
            ProcessDefinitionSuspensionIntent.RESUMED,
            999L,
            List.of(
                version(1L, ProcessDefinitionSuspensionOutcome.RESUMED),
                version(2L, ProcessDefinitionSuspensionOutcome.DRAINING),
                version(3L, ProcessDefinitionSuspensionOutcome.RESUMED)));

    // when
    underTest.export(record);

    // then
    verify(processDefinitionWriter).markActive(1L);
    verify(processDefinitionWriter).markActive(3L);
    verify(processDefinitionWriter, never()).markActive(2L);
    verifyNoMoreInteractions(processDefinitionWriter);
  }

  @Test
  void shouldNotTouchWriterWhenNoResolvedVersions() {
    // given
    final Record<ProcessDefinitionSuspensionRecordValue> record =
        suspensionRecord(ProcessDefinitionSuspensionIntent.SUSPENDED, 999L, List.of());

    // when
    underTest.export(record);

    // then
    verifyNoMoreInteractions(processDefinitionWriter);
  }

  private Record<ProcessDefinitionSuspensionRecordValue> suspensionRecord(
      final ProcessDefinitionSuspensionIntent intent,
      final long requestKey,
      final List<ProcessDefinitionSuspensionResolvedVersionValue> versions) {
    final ProcessDefinitionSuspensionRecordValue value =
        ImmutableProcessDefinitionSuspensionRecordValue.builder()
            .from(factory.generateObject(ImmutableProcessDefinitionSuspensionRecordValue.class))
            .withProcessDefinitionKey(requestKey)
            .withResolvedVersions(versions)
            .build();
    return factory.generateRecord(
        ValueType.PROCESS_DEFINITION_SUSPENSION, r -> r.withIntent(intent).withValue(value));
  }

  private ProcessDefinitionSuspensionResolvedVersionValue version(
      final long processDefinitionKey, final ProcessDefinitionSuspensionOutcome outcome) {
    return ImmutableProcessDefinitionSuspensionResolvedVersionValue.builder()
        .from(
            factory.generateObject(ImmutableProcessDefinitionSuspensionResolvedVersionValue.class))
        .withProcessDefinitionKey(processDefinitionKey)
        .withOutcome(outcome)
        .build();
  }
}
