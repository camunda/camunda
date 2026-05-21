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

import io.camunda.db.rdbms.write.service.FlowNodeInstanceWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowNodeInstanceIncidentExportHandlerTest {

  private static final long PI_KEY = 100L;
  private static final long BODY_KEY = 200L;
  private static final long ITERATION_KEY = 300L;
  private static final long CALL_ACTIVITY_KEY = 400L;
  private static final long CHILD_PI_KEY = 500L;
  private static final long CHILD_ELEMENT_KEY = 600L;
  private static final long INCIDENT_KEY = 999L;

  private FlowNodeInstanceWriter writer;
  private FlowNodeInstanceIncidentExportHandler handler;

  @BeforeEach
  void setUp() {
    writer = mock(FlowNodeInstanceWriter.class);
    handler = new FlowNodeInstanceIncidentExportHandler(writer);
  }

  @Test
  void shouldCreateIncidentForElement() {
    // given
    final var record =
        mockIncidentRecord(IncidentIntent.CREATED, BODY_KEY, List.of(List.of(PI_KEY, BODY_KEY)));

    // when
    handler.export(record);

    // then
    verify(writer).createIncident(BODY_KEY, INCIDENT_KEY);
    verifyNoMoreInteractions(writer);
  }

  @Test
  void shouldResolveIncidentForElement() {
    // given
    final var record =
        mockIncidentRecord(IncidentIntent.RESOLVED, BODY_KEY, List.of(List.of(PI_KEY, BODY_KEY)));

    // when
    handler.export(record);

    // then
    verify(writer).resolveIncident(BODY_KEY);
    verifyNoMoreInteractions(writer);
  }

  @Test
  void shouldCreateIncidentOnInstanceAndSubprocessIncidentOnBodyForMultiInstance() {
    // given
    final var record =
        mockIncidentRecord(
            IncidentIntent.CREATED,
            ITERATION_KEY,
            List.of(List.of(PI_KEY, BODY_KEY, ITERATION_KEY)));

    // when
    handler.export(record);

    // then
    verify(writer).createSubprocessIncident(BODY_KEY);
    verify(writer).createIncident(ITERATION_KEY, INCIDENT_KEY);
    verifyNoMoreInteractions(writer);
  }

  @Test
  void shouldResolveIncidentOnInstanceAndSubprocessIncidentOnBodyForMultiInstance() {
    // given
    final var record =
        mockIncidentRecord(
            IncidentIntent.RESOLVED,
            ITERATION_KEY,
            List.of(List.of(PI_KEY, BODY_KEY, ITERATION_KEY)));

    // when
    handler.export(record);

    // then
    verify(writer).resolveSubprocessIncident(BODY_KEY);
    verify(writer).resolveIncident(ITERATION_KEY);
    verifyNoMoreInteractions(writer);
  }

  @Test
  void shouldCreateSubprocessIncidentOnCallActivityAndIncidentOnChildElement() {
    // given
    final var record =
        mockIncidentRecord(
            IncidentIntent.CREATED,
            CHILD_ELEMENT_KEY,
            List.of(List.of(PI_KEY, CALL_ACTIVITY_KEY), List.of(CHILD_PI_KEY, CHILD_ELEMENT_KEY)));

    // when
    handler.export(record);

    // then
    verify(writer).createSubprocessIncident(CALL_ACTIVITY_KEY);
    verify(writer).createIncident(CHILD_ELEMENT_KEY, INCIDENT_KEY);
    verifyNoMoreInteractions(writer);
  }

  @SuppressWarnings("unchecked")
  private Record<IncidentRecordValue> mockIncidentRecord(
      final IncidentIntent intent,
      final long elementInstanceKey,
      final List<List<Long>> elementInstancePath) {
    final var value = mock(IncidentRecordValue.class);
    when(value.getElementInstanceKey()).thenReturn(elementInstanceKey);
    when(value.getElementInstancePath()).thenReturn(elementInstancePath);

    final var record = (Record<IncidentRecordValue>) mock(Record.class);
    when(record.getValueType()).thenReturn(ValueType.INCIDENT);
    when(record.getIntent()).thenReturn(intent);
    when(record.getKey()).thenReturn(INCIDENT_KEY);
    when(record.getValue()).thenReturn(value);
    return record;
  }
}
