/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractStartEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ErrorEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "wf";
  private static final String JOB_TYPE = "test";
  private static final String ERROR_CODE = "ERROR";
  private static final String ERROR_CODE_NUMBER = "404";

  private static final BpmnModelInstance SINGLE_BOUNDARY_EVENT =
      process(
          serviceTask -> serviceTask.boundaryEvent("error", b -> b.error(ERROR_CODE)).endEvent());

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance process(final Consumer<ServiceTaskBuilder> customizer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE));

    customizer.accept(builder);

    return builder.endEvent().done();
  }

  @Test
  public void shouldTriggerEvent() {
    // given
    ENGINE.deployment().withXmlResource(SINGLE_BOUNDARY_EVENT).deploy();

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
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCatchErrorEventsByErrorCode() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                serviceTask -> {
                  serviceTask.boundaryEvent("error-1", b -> b.error("error-1").endEvent());
                  serviceTask.boundaryEvent("error-2", b -> b.error("error-2").endEvent());
                }))
        .deploy();

    final var processInstanceKey1 = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var processInstanceKey2 = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey1)
        .withType(JOB_TYPE)
        .withErrorCode("error-1")
        .throwError();

    ENGINE
        .job()
        .ofInstance(processInstanceKey2)
        .withType(JOB_TYPE)
        .withErrorCode("error-2")
        .throwError();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey1)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.BOUNDARY_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("error-1");

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey2)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.BOUNDARY_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("error-2");
  }

  @Test
  public void shouldCatchErrorEventsByNumericErrorCode() {
    // Regression for https://github.com/camunda/camunda/issues/12326
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                serviceTask ->
                    serviceTask.boundaryEvent("error", b -> b.error(ERROR_CODE_NUMBER).endEvent())))
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE_NUMBER)
        .throwError();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SEQUENCE_FLOW, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCatchErrorEventsOnBoundaryEventWithoutErrorRef() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                serviceTask ->
                    serviceTask.boundaryEvent(
                        "error",
                        b -> b.errorEventDefinition().errorEventDefinitionDone().endEvent())))
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode("error")
        .throwError();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.BOUNDARY_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("error");
  }

  @Test
  public void shouldCatchErrorEventsOnBoundaryEventWithoutErrorCode() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(serviceTask -> serviceTask.boundaryEvent("error", b -> b.error().endEvent())))
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode("error")
        .throwError();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.BOUNDARY_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("error");
  }

  @Test
  public void shouldCatchErrorEventsOnBoundaryEventWithSpecificErrorCode() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                serviceTask -> {
                  serviceTask.boundaryEvent("catch-all", b -> b.error().endEvent());
                  serviceTask.boundaryEvent("code-specific", b -> b.error(ERROR_CODE).endEvent());
                }))
        .deploy();

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
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.BOUNDARY_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("code-specific");
  }

  @Test
  public void shouldCatchErrorEventsOnErrorStartEventWithoutErrorRef() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "sub",
                e ->
                    e.startEvent("error", AbstractStartEventBuilder::errorEventDefinition)
                        .endEvent())
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode("errorCode")
        .throwError();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence("start", "error");
  }

  @Test
  public void shouldCatchErrorEventsOnErrorStartEventWithEmptyErrorCode() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess("sub", e -> e.startEvent("error", s -> s.error(null)).endEvent())
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode("errorCode")
        .throwError();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence("start", "error");
  }

  @Test
  public void shouldCatchErrorEventsOnErrorStartEventWithoutErrorCode() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "sub", e -> e.startEvent("error", AbstractStartEventBuilder::error).endEvent())
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode("errorCode")
        .throwError();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence("start", "error");
  }

  @Test
  public void shouldCatchErrorEventsOnErrorStartEventWithSpecificErrorCode() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "sub-1",
                e -> e.startEvent("catch-all", AbstractStartEventBuilder::error).endEvent())
            .eventSubProcess(
                "sub-2", e -> e.startEvent("code-specific", s -> s.error(ERROR_CODE)).endEvent())
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
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
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.START_EVENT))
        .extracting(r -> r.getValue().getElementId())
        .containsSubsequence("start", "code-specific");
  }

  @Test
  public void shouldNotCancelJob() {
    // given
    ENGINE.deployment().withXmlResource(SINGLE_BOUNDARY_EVENT).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    // then
    assertThat(RecordingExporter.records().betweenProcessInstance(processInstanceKey).jobRecords())
        .extracting(Record::getIntent)
        .containsExactly(JobIntent.CREATED, JobIntent.THROW_ERROR, JobIntent.ERROR_THROWN);
  }

  @Test
  public void shouldCatchErrorFromChildInstance() {
    // given
    final var processChild =
        Bpmn.createExecutableProcess("wf-child")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    final var processParent =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId("wf-child"))
            .boundaryEvent("error", b -> b.error(ERROR_CODE).endEvent())
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("wf-child.bpmn", processChild)
        .withXmlResource("wf-parent.bpmn", processParent)
        .deploy();

    final var parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    // when
    ENGINE
        .job()
        .ofInstance(childProcessInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(childProcessInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(parentProcessInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.TERMINATE_ELEMENT),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCatchErrorInsideMultiInstanceSubprocess() {
    // given
    final Consumer<EventSubProcessBuilder> eventSubprocess =
        s -> s.startEvent("error-start-event").error(ERROR_CODE).endEvent();

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.multiInstance(m -> m.zeebeInputCollectionExpression("items"))
                        .embeddedSubProcess()
                        .eventSubProcess("error-subprocess", eventSubprocess)
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent())
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("items", List.of(1))
            .create();

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
            tuple("task", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple("error-subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("error-start-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("error-start-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("error-subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCatchErrorOutsideMultiInstanceSubprocess() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                s ->
                    s.multiInstance(m -> m.zeebeInputCollectionExpression("[1]"))
                        .embeddedSubProcess()
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent())
            .boundaryEvent("error-boundary-event", b -> b.error(ERROR_CODE))
            .endEvent()
            .done();

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
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldThrowErrorOnEndEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subProcess",
                subProcess ->
                    subProcess
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent("throw-error", e -> e.error(ERROR_CODE)))
            .boundaryEvent("catch-error", b -> b.error(ERROR_CODE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .onlyEvents())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldThrowErrorOnEndEventWithExpression() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subProcess",
                subProcess ->
                    subProcess
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent("throw-error", e -> e.errorExpression("error")))
            .boundaryEvent("catch-error", b -> b.error(ERROR_CODE))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("error", ERROR_CODE))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .onlyEvents())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldProvideErrorContextInOutputMappingsofBoundaryEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .boundaryEvent(
                    "boundary",
                    b ->
                        b.error(ERROR_CODE)
                            .zeebeOutputExpression("camunda.error.code", "code")
                            .zeebeOutputExpression("camunda.error.message", "message")
                            .zeebeOutputExpression("camunda.error", "error")
                            .endEvent())
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .withErrorMessage("my msg")
        .throwError();

    // then
    final var variables =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(3)
            .collect(Collectors.toMap(r -> r.getValue().getName(), c -> c.getValue().getValue()));

    final var expected =
        Map.of(
            "code",
            "\"ERROR\"",
            "message",
            "\"my msg\"",
            "error",
            "{\"code\":\"ERROR\",\"message\":\"my msg\"}");
    assertThat(variables).containsAllEntriesOf(expected);
  }
}
