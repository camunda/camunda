/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.escalation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.EscalationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class EscalationCatchEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "wf";
  private static final String THROW_ELEMENT_ID = "throw";
  private static final String ESCALATION_CODE = "ESCALATION";

  @Parameter(0)
  public String description;

  @Parameter(1)
  public BpmnModelInstance process;

  @Parameter(2)
  public String expectedThrowElementId;

  @Parameter(3)
  public String expectedCatchElementId;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "boundary event on subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent(
                            THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE))
                        .endEvent())
            .boundaryEvent("escalation-boundary-event", b -> b.escalation(ESCALATION_CODE))
            .endEvent()
            .done(),
        THROW_ELEMENT_ID,
        "escalation-boundary-event"
      },
      {
        "boundary event on multi-instance subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.multiInstance(m -> m.zeebeInputCollectionExpression("[1]"))
                        .embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent(
                            THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE))
                        .endEvent())
            .boundaryEvent("escalation-boundary-event", b -> b.escalation(ESCALATION_CODE))
            .endEvent()
            .done(),
        THROW_ELEMENT_ID,
        "escalation-boundary-event"
      },
      {
        "escalation event subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "escalation-event-subprocess",
                s ->
                    s.startEvent("escalation-start-event")
                        .escalation(ESCALATION_CODE)
                        .interrupting(true)
                        .endEvent())
            .startEvent()
            .intermediateThrowEvent(THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE))
            .endEvent()
            .done(),
        THROW_ELEMENT_ID,
        "escalation-start-event"
      },
      {
        "favor escalation event subprocess over boundary event on subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "sub",
                s ->
                    s.embeddedSubProcess()
                        .eventSubProcess(
                            "escalation-event-subprocess",
                            e ->
                                e.startEvent("escalation-start-event")
                                    .escalation(ESCALATION_CODE)
                                    .interrupting(true)
                                    .endEvent())
                        .startEvent()
                        .intermediateThrowEvent(
                            THROW_ELEMENT_ID, i -> i.escalation(ESCALATION_CODE))
                        .endEvent())
            .boundaryEvent("escalation", b -> b.escalation(ESCALATION_CODE))
            .endEvent()
            .done(),
        THROW_ELEMENT_ID,
        "escalation-start-event"
      },
    };
  }

  @Test
  public void shouldTriggerEvent() {
    // given
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(THROW_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(THROW_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(expectedCatchElementId, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(expectedCatchElementId, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertIsEscalated(processInstanceKey, expectedCatchElementId, expectedThrowElementId);
  }

  private void assertIsEscalated(
      final long processInstanceKey, final String catchElementId, final String throwElementId) {
    assertThat(
            RecordingExporter.escalationRecords(EscalationIntent.ESCALATED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchElementId(catchElementId)
                .withThrowElementId(throwElementId)
                .withEscalationCode(ESCALATION_CODE)
                .exists())
        .isTrue();
  }
}
