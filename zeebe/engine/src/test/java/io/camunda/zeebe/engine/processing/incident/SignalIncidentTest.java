/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class SignalIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String SIGNAL_NAME_EXPRESSION = "=signalNameLookup";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldNameSignalNameExpressionOfIntermediateThrowEventInIncident() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var eventId = Strings.newRandomValidBpmnId();

    // when
    final long processInstanceKey =
        deployAndCreateInstance(
            processId,
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateThrowEvent(eventId, e -> e.signal(SIGNAL_NAME_EXPRESSION))
                .endEvent()
                .done());

    // then
    assertSignalNameIncident(processInstanceKey, eventId);
  }

  @Test
  public void shouldNameSignalNameExpressionOfEndEventInIncident() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var eventId = Strings.newRandomValidBpmnId();

    // when
    final long processInstanceKey =
        deployAndCreateInstance(
            processId,
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .endEvent(eventId, e -> e.signal(SIGNAL_NAME_EXPRESSION))
                .done());

    // then
    assertSignalNameIncident(processInstanceKey, eventId);
  }

  @Test
  public void shouldNameSignalNameExpressionOfIntermediateCatchEventInIncident() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var eventId = Strings.newRandomValidBpmnId();

    // when
    final long processInstanceKey =
        deployAndCreateInstance(
            processId,
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(eventId)
                .signal(s -> s.name(SIGNAL_NAME_EXPRESSION))
                .endEvent()
                .done());

    // then
    assertSignalNameIncident(processInstanceKey, eventId);
  }

  private static long deployAndCreateInstance(
      final String processId, final BpmnModelInstance process) {
    ENGINE.deployment().withXmlResource(process).deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }

  private static void assertSignalNameIncident(
      final long processInstanceKey, final String eventId) {
    final var event =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(eventId)
            .getFirst();

    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the signal name expression 'signalNameLookup' of event '%s' to be \
            'STRING', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'signalNameLookup'"""
                .formatted(eventId))
        .hasElementId(eventId)
        .hasElementInstanceKey(event.getKey())
        .hasVariableScopeKey(event.getKey());
  }
}
