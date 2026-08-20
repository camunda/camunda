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
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.stubbing.Answer;

final class BpmnProcessFilterTest {

  private static final String INCLUDED_ID = "order-process";
  private static final String EXCLUDED_ID = "deprecated-process";
  private static final String OTHER_ID = "other-process";

  // ---------------------------------------------------------------------------
  // Basic behavior
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptAllWhenNoInclusionOrExclusionConfigured() {
    // given
    final var filter = new BpmnProcessFilter(List.of(), List.of());

    final var recordWithProcess =
        recordWithValue(valueWithBpmnProcessId(ProcessInstanceRecordValue.class, INCLUDED_ID));

    // when / then
    assertThat(filter.accept(recordWithProcess))
        .as("No inclusion/exclusion configured -> record should be accepted")
        .isTrue();
  }

  @Test
  void shouldFilterByInclusionOnly() {
    // given
    final var filter = new BpmnProcessFilter(List.of(INCLUDED_ID), List.of());

    final var includedRecord =
        recordWithValue(valueWithBpmnProcessId(ProcessInstanceRecordValue.class, INCLUDED_ID));
    final var otherRecord =
        recordWithValue(valueWithBpmnProcessId(ProcessInstanceRecordValue.class, OTHER_ID));

    // when / then
    assertThat(filter.accept(includedRecord))
        .as("Record for included BPMN process id should be accepted")
        .isTrue();
    assertThat(filter.accept(otherRecord))
        .as("Record for non-included BPMN process id should be rejected")
        .isFalse();
  }

  @Test
  void shouldFilterByExclusionOnly() {
    // given
    final var filter = new BpmnProcessFilter(List.of(), List.of(EXCLUDED_ID));

    final var excludedRecord =
        recordWithValue(valueWithBpmnProcessId(ProcessInstanceRecordValue.class, EXCLUDED_ID));
    final var otherRecord =
        recordWithValue(valueWithBpmnProcessId(ProcessInstanceRecordValue.class, OTHER_ID));

    // when / then
    assertThat(filter.accept(excludedRecord))
        .as("Record for excluded BPMN process id should be rejected")
        .isFalse();
    assertThat(filter.accept(otherRecord))
        .as("Record for other BPMN process ids should be accepted")
        .isTrue();
  }

  @Test
  void shouldLetExclusionOverrideInclusionWhenBothPresent() {
    // given
    final var filter =
        new BpmnProcessFilter(
            /* inclusion */ List.of(INCLUDED_ID, EXCLUDED_ID), /* exclusion */
            List.of(EXCLUDED_ID));

    final var includedRecord =
        recordWithValue(valueWithBpmnProcessId(ProcessInstanceRecordValue.class, INCLUDED_ID));
    final var excludedRecord =
        recordWithValue(valueWithBpmnProcessId(ProcessInstanceRecordValue.class, EXCLUDED_ID));

    // when / then
    assertThat(filter.accept(includedRecord))
        .as("ID present in inclusion but not in exclusion should remain allowed")
        .isTrue();
    assertThat(filter.accept(excludedRecord))
        .as(
            "ID present in both inclusion and exclusion should be removed from allowed set "
                + "and therefore rejected")
        .isFalse();
  }

  @Test
  void shouldAcceptSupportedRecordsWithNullBpmnProcessId() {
    // given
    final var filter = new BpmnProcessFilter(List.of(INCLUDED_ID), List.of());

    final var value = valueWithBpmnProcessId(ProcessInstanceRecordValue.class, null);
    final var record = recordWithValue(value);

    // when / then
    assertThat(filter.accept(record))
        .as("Records with null BPMN process id should be accepted")
        .isTrue();
  }

  @Test
  void shouldAcceptSupportedRecordsWithEmptyBpmnProcessId() {
    // given
    final var filter = new BpmnProcessFilter(List.of(INCLUDED_ID), List.of());

    final var value = valueWithBpmnProcessId(ProcessInstanceRecordValue.class, "");
    final var record = recordWithValue(value);

    // when / then
    assertThat(filter.accept(record))
        .as("Records with empty BPMN process id should be accepted")
        .isTrue();
  }

  @Test
  void shouldAcceptUnsupportedRecordValues() {
    // given
    final var filter = new BpmnProcessFilter(List.of(), List.of(EXCLUDED_ID));

    // A plain RecordValue that does not implement any of the supported interfaces
    final RecordValue unsupportedValue = mock(RecordValue.class);
    final var record = recordWithValue(unsupportedValue);

    // when / then
    assertThat(filter.accept(record))
        .as("Unsupported record values should always be accepted (filter is no-op)")
        .isTrue();
  }

  // ---------------------------------------------------------------------------
  // Coverage for all supported value types
  // ---------------------------------------------------------------------------

  @Test
  void shouldExtractBpmnProcessIdFromAllSupportedRecordValues() {
    // given
    final var filter = new BpmnProcessFilter(List.of(), List.of(EXCLUDED_ID));

    final var values =
        List.of(
            valueWithBpmnProcessId(ConditionalSubscriptionRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(DecisionEvaluationRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(IncidentRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(JobRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(MessageStartEventSubscriptionRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(MessageSubscriptionRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(ProcessInstanceCreationRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(ProcessInstanceRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(ProcessInstanceResultRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(ProcessMessageSubscriptionRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(ProcessMetadataValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(SignalSubscriptionRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(UserTaskRecordValue.class, EXCLUDED_ID),
            valueWithBpmnProcessId(VariableRecordValue.class, EXCLUDED_ID));

    // when / then
    for (final RecordValue value : values) {
      final Record<?> record = recordWithValue(value);

      assertThat(filter.accept(record))
          .as(
              "Record for %s should be rejected when its BPMN process id is excluded",
              value.getClass().getSimpleName())
          .isFalse();
    }
  }

  @Test
  void shouldExposeMinimumBrokerVersion() {
    final var filter = new BpmnProcessFilter(List.of(), List.of());

    assertThat(filter.minRecordBrokerVersion()).isEqualTo(new SemanticVersion(8, 9, 0, null, null));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> recordWithValue(final T value) {
    final Record<T> record = (Record<T>) mock(Record.class);
    when(record.getValue()).thenReturn(value);
    return record;
  }

  private static <T extends RecordValue> T valueWithBpmnProcessId(
      final Class<T> type, final String bpmnProcessId) {

    final Answer<Object> defaults = Answers.RETURNS_DEFAULTS;

    return mock(
        type,
        invocation -> {
          if ("getBpmnProcessId".equals(invocation.getMethod().getName())
              && invocation.getArguments().length == 0) {
            return bpmnProcessId;
          }
          return defaults.answer(invocation);
        });
  }
}
