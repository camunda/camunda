/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ErrorCatchEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String TASK_ELEMENT_ID = "task";
  private static final String PROCESS_ID = "wf";
  private static final String JOB_TYPE = "test";
  private static final String ERROR_CODE = "ERROR";

  @Parameter(0)
  public String description;

  @Parameter(1)
  public BpmnModelInstance process;

  @Parameter(2)
  public String expectedActivatedElement;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "boundary event on service task",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        "error-boundary-event"
      },
      {
        "boundary event on subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent())
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        "error-boundary-event"
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
                        .serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent())
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        "error-boundary-event"
      },
      {
        "boundary event on multi-instance service task",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                TASK_ELEMENT_ID,
                t ->
                    t.zeebeJobType(JOB_TYPE)
                        .multiInstance(m -> m.zeebeInputCollectionExpression("[1]")))
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        "error-boundary-event"
      },
      {
        "error event subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "error-event-subprocess",
                s ->
                    s.startEvent("error-start-event")
                        .error(ERROR_CODE)
                        .interrupting(true)
                        .endEvent())
            .startEvent()
            .serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done(),
        "error-event-subprocess"
      },
      {
        "favor boundary event on task over boundary event on subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .serviceTask(TASK_ELEMENT_ID, t -> t.zeebeJobType(JOB_TYPE))
                        .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
                        .endEvent())
            .boundaryEvent("error-boundary-event-on-subprocess", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        "error-boundary-event"
      },
      {
        "favor boundary event on task over error event subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "error-event-subprocess",
                s -> s.startEvent().error(ERROR_CODE).interrupting(true).endEvent())
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .boundaryEvent("error-boundary-event")
            .error(ERROR_CODE)
            .endEvent()
            .done(),
        "error-boundary-event"
      },
      {
        "favor error event subprocess over boundary event on subprocess",
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "sub",
                s ->
                    s.embeddedSubProcess()
                        .eventSubProcess(
                            "error-event-subprocess",
                            e ->
                                e.startEvent("error-start-event")
                                    .error(ERROR_CODE)
                                    .interrupting(true)
                                    .endEvent())
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent())
            .boundaryEvent("error", b -> b.error(ERROR_CODE))
            .endEvent()
            .done(),
        "error-event-subprocess"
      },
    };
  }

  @Test
  public void shouldTriggerEvent() {
    // given
    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(TASK_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(TASK_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(expectedActivatedElement, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(expectedActivatedElement, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldThrowErrorWithVariables() {
    // given
    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .withVariables("{'foo':'bar'}")
        .throwError();

    // then
    final List<Record<VariableRecordValue>> variableRecords =
        RecordingExporter.variableRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limit(r -> r.getValue().getName().equals("foo"))
            .collect(Collectors.toList());

    final List<Record<ProcessInstanceRecordValue>> errorEvents =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitToProcessInstanceCompleted()
            .withElementId(expectedActivatedElement)
            .withRecordType(RecordType.EVENT)
            .withElementType(BpmnElementType.BOUNDARY_EVENT)
            .asList();

    if (errorEvents.size() > 0) {
      assertThat(variableRecords)
          .filteredOn(r -> r.getValue().getName().equals("foo"))
          .extracting(
              r -> r.getValue().getName(),
              r -> r.getValue().getValue(),
              r -> r.getValue().getScopeKey(),
              Record::getIntent)
          .containsExactly(
              tuple("foo", "\"bar\"", errorEvents.get(0).getKey(), VariableIntent.CREATED));
    } else {
      assertThat(variableRecords)
          .filteredOn(r -> r.getValue().getName().equals("foo"))
          .extracting(
              r -> r.getValue().getName(),
              r -> r.getValue().getValue(),
              r -> r.getValue().getScopeKey(),
              Record::getIntent)
          .describedAs("With event sub process the variables are created at the process instance")
          .containsExactly(tuple("foo", "\"bar\"", processInstanceKey, VariableIntent.CREATED));
    }
  }
}
