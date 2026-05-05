/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.service.ProcessInstanceWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessInstanceIncidentExportHandlerTest {

  private static final long PARENT_PI_KEY = 100L;
  private static final long CALL_ACTIVITY_KEY = 200L;
  private static final long CHILD_PI_KEY = 300L;
  private static final long CHILD_ELEMENT_KEY = 400L;

  private ProcessInstanceWriter writer;
  private ProcessInstanceIncidentExportHandler handler;

  @BeforeEach
  void setUp() {
    writer = mock(ProcessInstanceWriter.class);
    handler = new ProcessInstanceIncidentExportHandler(writer);
  }

  @Test
  void shouldCreateIncidentForRootProcessInstance() {
    // given
    final var record =
        mockIncidentRecord(
            IncidentIntent.CREATED,
            PARENT_PI_KEY,
            List.of(List.of(PARENT_PI_KEY, CHILD_ELEMENT_KEY)));

    // when
    handler.export(record);

    // then
    verify(writer).createIncident(PARENT_PI_KEY);
    verifyNoMoreInteractions(writer);
  }

  @Test
  void shouldResolveIncidentForRootProcessInstance() {
    // given
    final var record =
        mockIncidentRecord(
            IncidentIntent.RESOLVED,
            PARENT_PI_KEY,
            List.of(List.of(PARENT_PI_KEY, CHILD_ELEMENT_KEY)));

    // when
    handler.export(record);

    // then
    verify(writer).resolveIncident(PARENT_PI_KEY);
    verifyNoMoreInteractions(writer);
  }

  // Regression test for https://github.com/camunda/camunda/issues/50014:
  // an incident raised in the child PI must mark BOTH the parent and the child PI as
  // having an incident, regardless of when the incident occurs in the child's lifecycle.
  @Test
  void shouldCreateIncidentOnEveryProcessInstanceInTheCallHierarchy() {
    // given an elementInstancePath spanning parent PI + child PI (e.g. failing service task
    // in a called process, possibly after the child has already advanced past its first task)
    final var record =
        mockIncidentRecord(
            IncidentIntent.CREATED,
            CHILD_ELEMENT_KEY,
            List.of(
                List.of(PARENT_PI_KEY, CALL_ACTIVITY_KEY),
                List.of(CHILD_PI_KEY, CHILD_ELEMENT_KEY)));

    // when
    handler.export(record);

    // then both PIs in the call hierarchy must have their incident counters incremented
    verify(writer).createIncident(PARENT_PI_KEY);
    verify(writer).createIncident(CHILD_PI_KEY);
    verifyNoMoreInteractions(writer);
  }

  @Test
  void shouldResolveIncidentOnEveryProcessInstanceInTheCallHierarchy() {
    // given
    final var record =
        mockIncidentRecord(
            IncidentIntent.RESOLVED,
            CHILD_ELEMENT_KEY,
            List.of(
                List.of(PARENT_PI_KEY, CALL_ACTIVITY_KEY),
                List.of(CHILD_PI_KEY, CHILD_ELEMENT_KEY)));

    // when
    handler.export(record);

    // then
    verify(writer).resolveIncident(PARENT_PI_KEY);
    verify(writer).resolveIncident(CHILD_PI_KEY);
    verifyNoMoreInteractions(writer);
  }

  @Test
  void shouldNotExportWhenProcessInstanceKeyIsMissing() {
    // given a record without a process instance key (must be filtered out by canExport)
    final var value = mock(IncidentRecordValue.class);
    when(value.getProcessInstanceKey()).thenReturn(0L);
    @SuppressWarnings("unchecked")
    final Record<IncidentRecordValue> record = (Record<IncidentRecordValue>) mock(Record.class);
    when(record.getValue()).thenReturn(value);
    when(record.getIntent()).thenReturn(IncidentIntent.CREATED);

    // when - then
    org.assertj.core.api.Assertions.assertThat(handler.canExport(record)).isFalse();
  }

  @SuppressWarnings("unchecked")
  private Record<IncidentRecordValue> mockIncidentRecord(
      final IncidentIntent intent,
      final long elementInstanceKey,
      final List<List<Long>> elementInstancePath) {
    final var value = mock(IncidentRecordValue.class);
    when(value.getProcessInstanceKey()).thenReturn(elementInstancePath.getLast().get(0));
    when(value.getElementInstanceKey()).thenReturn(elementInstanceKey);
    when(value.getElementInstancePath()).thenReturn(elementInstancePath);

    final var record = (Record<IncidentRecordValue>) mock(Record.class);
    when(record.getIntent()).thenReturn(intent);
    when(record.getValue()).thenReturn(value);
    return record;
  }
}
