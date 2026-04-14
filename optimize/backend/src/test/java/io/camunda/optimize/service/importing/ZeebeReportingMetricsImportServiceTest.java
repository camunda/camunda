/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.importing.ReportingMetricsDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableDataDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ReportingMetricsWriter;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeReportingMetricsImportService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZeebeReportingMetricsImportServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ConfigurationService configurationService;

  @Mock private DatabaseClient databaseClient;

  private final AtomicReference<List<ReportingMetricsDto>> capturedDocs = new AtomicReference<>();

  private ZeebeReportingMetricsImportService underTest;

  @BeforeEach
  void setUp() {
    when(configurationService.getJobExecutorThreadCount()).thenReturn(1);
    when(configurationService.getJobExecutorQueueSize()).thenReturn(10);
    final ReportingMetricsWriter captureWriter =
        docs -> {
          capturedDocs.set(docs);
          return List.of();
        };
    underTest =
        new ZeebeReportingMetricsImportService(configurationService, captureWriter, databaseClient);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private ZeebeVariableRecordDto record(
      final long piKey,
      final VariableIntent intent,
      final long timestamp,
      final String varName,
      final String varValue) {
    final ZeebeVariableDataDto data = new ZeebeVariableDataDto();
    data.setProcessInstanceKey(piKey);
    data.setProcessDefinitionKey(100L);
    data.setTenantId("t1");
    data.setName(varName);
    data.setValue(varValue);

    final ZeebeVariableRecordDto dto = new ZeebeVariableRecordDto();
    dto.setValue(data);
    dto.setIntent(intent);
    dto.setTimestamp(timestamp);
    return dto;
  }

  // ── tests ─────────────────────────────────────────────────────────────────

  @Test
  void shouldFilterOutNonReportingVariables() {
    // given: one REPORTING_PROCESS_ variable and one unrelated variable
    final List<ZeebeVariableRecordDto> input =
        List.of(
            record(1L, VariableIntent.CREATED, 1000L, "REPORTING_PROCESS_totalCost", "42.5"),
            record(1L, VariableIntent.CREATED, 2000L, "someOtherVariable", "hello"));

    // when
    executeImport(input);

    // then: only the reporting doc is written
    assertThat(capturedDocs.get()).hasSize(1);
    assertThat(capturedDocs.get().getFirst().getTotalCost()).isEqualTo(42.5);
  }

  @Test
  void shouldGroupByProcessInstanceKey() {
    // given: two variables for two different PIs
    final List<ZeebeVariableRecordDto> input =
        List.of(
            record(1L, VariableIntent.CREATED, 1000L, "REPORTING_PROCESS_totalCost", "10.0"),
            record(2L, VariableIntent.CREATED, 2000L, "REPORTING_PROCESS_totalCost", "20.0"));

    // when
    executeImport(input);

    // then
    assertThat(capturedDocs.get()).hasSize(2);
  }

  @Test
  void shouldMergeMultipleVariablesForSameProcessInstance() {
    // given: several REPORTING_PROCESS_ vars for the same PI
    final List<ZeebeVariableRecordDto> input =
        List.of(
            record(1L, VariableIntent.CREATED, 1000L, "REPORTING_PROCESS_totalCost", "99.9"),
            record(1L, VariableIntent.CREATED, 1500L, "REPORTING_PROCESS_agentTaskCount", "3"),
            record(1L, VariableIntent.CREATED, 500L, "REPORTING_PROCESS_slaBreached", "true"),
            record(
                1L,
                VariableIntent.CREATED,
                2000L,
                "REPORTING_PROCESS_processLabel",
                "\"My Process\""));

    // when
    executeImport(input);

    // then
    assertThat(capturedDocs.get()).hasSize(1);
    final ReportingMetricsDto doc = capturedDocs.get().getFirst();
    assertThat(doc.getProcessInstanceKey()).isEqualTo("1");
    assertThat(doc.getProcessDefinitionKey()).isEqualTo("100");
    assertThat(doc.getTenantId()).isEqualTo("t1");
    assertThat(doc.getTotalCost()).isEqualTo(99.9);
    assertThat(doc.getAgentTaskCount()).isEqualTo(3);
    assertThat(doc.getSlaBreached()).isTrue();
    assertThat(doc.getProcessLabel()).isEqualTo("My Process");
  }

  @Test
  void shouldTrackFirstAndLastSeenAtFromTimestamps() {
    // given
    final List<ZeebeVariableRecordDto> input =
        List.of(
            record(1L, VariableIntent.CREATED, 3000L, "REPORTING_PROCESS_totalCost", "1.0"),
            record(1L, VariableIntent.UPDATED, 1000L, "REPORTING_PROCESS_valueCreated", "2.0"),
            record(1L, VariableIntent.CREATED, 2000L, "REPORTING_PROCESS_tokenUsage", "500"));

    // when
    executeImport(input);

    // then
    final ReportingMetricsDto doc = capturedDocs.get().getFirst();
    assertThat(doc.getFirstSeenAt()).isEqualTo(1000L);
    assertThat(doc.getLastSeenAt()).isEqualTo(3000L);
  }

  @Test
  void shouldIgnoreRecordsWithNonImportableIntents() {
    // given: only a MIGRATED intent record (not CREATED or UPDATED)
    final List<ZeebeVariableRecordDto> input =
        List.of(record(1L, VariableIntent.MIGRATED, 1000L, "REPORTING_PROCESS_totalCost", "42.0"));

    // when
    executeImport(input);

    // then: the async executor should never be called (no records match)
    // The callback is invoked directly since the filtered list is empty
    assertThat(capturedDocs.get()).isNull();
  }

  @Test
  void shouldDoNothingOnEmptyInput() {
    // given / when
    executeImport(List.of());

    // then: no docs captured
    assertThat(capturedDocs.get()).isNull();
  }

  @Test
  void shouldUnquoteStringVariableValues() {
    // given: processLabel stored as JSON-quoted string
    final List<ZeebeVariableRecordDto> input =
        List.of(
            record(
                1L,
                VariableIntent.CREATED,
                1000L,
                "REPORTING_PROCESS_processLabel",
                "\"Invoice Approval\""));

    // when
    executeImport(input);

    // then: surrounding quotes stripped
    assertThat(capturedDocs.get().getFirst().getProcessLabel()).isEqualTo("Invoice Approval");
  }

  @Test
  void shouldParseBooleanFlags() {
    // given
    final List<ZeebeVariableRecordDto> input =
        List.of(
            record(1L, VariableIntent.CREATED, 1000L, "REPORTING_PROCESS_escalated", "true"),
            record(1L, VariableIntent.CREATED, 1001L, "REPORTING_PROCESS_manualOverride", "false"));

    // when
    executeImport(input);

    // then
    final ReportingMetricsDto doc = capturedDocs.get().getFirst();
    assertThat(doc.getEscalated()).isTrue();
    assertThat(doc.getManualOverride()).isFalse();
  }

  private void executeImport(final List<ZeebeVariableRecordDto> input) {
    final CountDownLatch importDone = new CountDownLatch(1);
    underTest.executeImport(input, importDone::countDown);
    awaitImportCompletion(importDone);
  }

  private void awaitImportCompletion(final CountDownLatch importDone) {
    try {
      assertThat(importDone.await(2, TimeUnit.SECONDS)).isTrue();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting for import completion", e);
    }
  }
}
