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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import org.junit.jupiter.api.Test;

final class OptimizeModeFilterTest {

  private final OptimizeModeFilter filter = new OptimizeModeFilter();

  // ---------------------------------------------------------------------------
  // PROCESS
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptOnlyCreatedProcessRecords() {
    final var created = processRecord(ProcessIntent.CREATED);
    final var deleting = processRecord(ProcessIntent.DELETING);

    assertThat(filter.accept(created)).as("PROCESS.CREATED should be accepted").isTrue();
    assertThat(filter.accept(deleting)).as("PROCESS.DELETING should be rejected").isFalse();
  }

  // ---------------------------------------------------------------------------
  // PROCESS_INSTANCE
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptSelectedProcessInstanceIntentsForNonExcludedElementTypes() {
    final var elementCompleted =
        processInstanceRecord(
            ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.SERVICE_TASK);
    final var elementTerminated =
        processInstanceRecord(
            ProcessInstanceIntent.ELEMENT_TERMINATED, BpmnElementType.START_EVENT);
    final var elementActivating =
        processInstanceRecord(ProcessInstanceIntent.ELEMENT_ACTIVATING, BpmnElementType.END_EVENT);

    assertThat(filter.accept(elementCompleted))
        .as("ELEMENT_COMPLETED for normal BPMN element should be accepted")
        .isTrue();
    assertThat(filter.accept(elementTerminated))
        .as("ELEMENT_TERMINATED for normal BPMN element should be accepted")
        .isTrue();
    assertThat(filter.accept(elementActivating))
        .as("ELEMENT_ACTIVATING for normal BPMN element should be accepted")
        .isTrue();
  }

  @Test
  void shouldRejectProcessInstanceRecordsWithNonAllowedIntents() {
    final var allowed =
        processInstanceRecord(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, BpmnElementType.SERVICE_TASK);
    final var notAllowed =
        processInstanceRecord(
            ProcessInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.SERVICE_TASK);

    assertThat(filter.accept(allowed))
        .as("ELEMENT_ACTIVATING is allowed for normal elements")
        .isTrue();
    assertThat(filter.accept(notAllowed))
        .as("ELEMENT_ACTIVATED is not in the allowed intent set and must be rejected")
        .isFalse();
  }

  @Test
  void shouldRejectProcessInstanceRecordsForExcludedElementTypes() {
    final var unspecified =
        processInstanceRecord(ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.UNSPECIFIED);
    final var sequenceFlow =
        processInstanceRecord(
            ProcessInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.SEQUENCE_FLOW);

    assertThat(filter.accept(unspecified))
        .as("UNSPECIFIED element type should be excluded")
        .isFalse();
    assertThat(filter.accept(sequenceFlow))
        .as("SEQUENCE_FLOW element type should be excluded")
        .isFalse();
  }

  // ---------------------------------------------------------------------------
  // INCIDENT
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptOnlyCreatedAndResolvedIncidents() {
    final var created = incidentRecord(IncidentIntent.CREATED);
    final var resolved = incidentRecord(IncidentIntent.RESOLVED);
    final var resolveCommand = incidentRecord(IncidentIntent.RESOLVE);

    assertThat(filter.accept(created)).as("INCIDENT.CREATED should be accepted").isTrue();
    assertThat(filter.accept(resolved)).as("INCIDENT.RESOLVED should be accepted").isTrue();
    assertThat(filter.accept(resolveCommand)).as("INCIDENT.RESOLVE should be rejected").isFalse();
  }

  // ---------------------------------------------------------------------------
  // USER_TASK
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptOnlySelectedUserTaskIntents() {
    final var creating = userTaskRecord(UserTaskIntent.CREATING);
    final var assigned = userTaskRecord(UserTaskIntent.ASSIGNED);
    final var canceled = userTaskRecord(UserTaskIntent.CANCELED);
    final var completed = userTaskRecord(UserTaskIntent.COMPLETED);
    final var completeCommand = userTaskRecord(UserTaskIntent.COMPLETE);

    assertThat(filter.accept(creating)).as("USER_TASK.CREATING should be accepted").isTrue();
    assertThat(filter.accept(assigned)).as("USER_TASK.ASSIGNED should be accepted").isTrue();
    assertThat(filter.accept(canceled)).as("USER_TASK.CANCELED should be accepted").isTrue();
    assertThat(filter.accept(completed)).as("USER_TASK.COMPLETED should be accepted").isTrue();
    assertThat(filter.accept(completeCommand))
        .as("USER_TASK.COMPLETE is not in the allowed set and should be rejected")
        .isFalse();
  }

  // ---------------------------------------------------------------------------
  // VARIABLE
  // ---------------------------------------------------------------------------

  @Test
  void shouldAcceptOnlyCreatedAndUpdatedVariables() {
    final var created = variableRecord(VariableIntent.CREATED);
    final var updated = variableRecord(VariableIntent.UPDATED);
    final var migrated = variableRecord(VariableIntent.MIGRATED);

    assertThat(filter.accept(created)).as("VARIABLE.CREATED should be accepted").isTrue();
    assertThat(filter.accept(updated)).as("VARIABLE.UPDATED should be accepted").isTrue();
    assertThat(filter.accept(migrated)).as("VARIABLE.MIGRATED should be rejected").isFalse();
  }

  // ---------------------------------------------------------------------------
  // DEFAULT CASE
  // ---------------------------------------------------------------------------

  @Test
  void shouldRejectUnsupportedValueTypes() {
    @SuppressWarnings("unchecked")
    final Record<?> jobRecord = (Record<?>) mock(Record.class);
    when(jobRecord.getValueType()).thenReturn(ValueType.JOB);

    assertThat(filter.accept(jobRecord))
        .as("Unsupported value types should be rejected by default")
        .isFalse();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static Record<?> processRecord(final ProcessIntent intent) {
    final Record<?> record = (Record<?>) mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.PROCESS);
    when(record.getIntent()).thenReturn(intent);
    return record;
  }

  @SuppressWarnings("unchecked")
  private static Record<ProcessInstanceRecordValue> processInstanceRecord(
      final ProcessInstanceIntent intent, final BpmnElementType elementType) {

    final Record<ProcessInstanceRecordValue> record =
        (Record<ProcessInstanceRecordValue>) mock(Record.class);
    final ProcessInstanceRecordValue value = mock(ProcessInstanceRecordValue.class);

    when(record.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(record.getIntent()).thenReturn(intent);
    when(record.getValue()).thenReturn(value);
    when(value.getBpmnElementType()).thenReturn(elementType);

    return record;
  }

  @SuppressWarnings("unchecked")
  private static Record<?> incidentRecord(final IncidentIntent intent) {
    final Record<?> record = (Record<?>) mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.INCIDENT);
    when(record.getIntent()).thenReturn(intent);
    return record;
  }

  @SuppressWarnings("unchecked")
  private static Record<?> userTaskRecord(final UserTaskIntent intent) {
    final Record<?> record = (Record<?>) mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.USER_TASK);
    when(record.getIntent()).thenReturn(intent);
    return record;
  }

  @SuppressWarnings("unchecked")
  private static Record<?> variableRecord(final VariableIntent intent) {
    final Record<?> record = (Record<?>) mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.VARIABLE);
    when(record.getIntent()).thenReturn(intent);
    return record;
  }
}
