/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.EndEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class BpmnEventTypeTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "test";
  private static final String ERROR_CODE = "ERROR";
  private static final String MESSAGE_NAME = "message";
  private static final String LINK_NAME = "linkA";
  private static final String CORRELATION_KEY = "key";
  private static final BpmnModelInstance ERROR_BOUNDARY_EVENT =
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
  public void testErrorBoundaryEvent() {
    // given
    ENGINE.deployment().withXmlResource(ERROR_BOUNDARY_EVENT).deploy();

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
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SERVICE_TASK,
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SERVICE_TASK,
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.BOUNDARY_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.BOUNDARY_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.BOUNDARY_EVENT,
                ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.BOUNDARY_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.BOUNDARY_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void testCatchErrorEventsByErrorCode() {
    // given
    final BpmnModelInstance process =
        process(
            serviceTask -> {
              serviceTask.boundaryEvent("error-1", b -> b.error("error-1").endEvent());
              serviceTask.boundaryEvent("error-2", b -> b.error("error-2").endEvent());
            });

    ENGINE.deployment().withXmlResource(process).deploy();

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
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SERVICE_TASK,
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.BOUNDARY_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey2)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SERVICE_TASK,
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.BOUNDARY_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void testCatchErrorFromChildInstance() {
    // given
    final BpmnModelInstance processChild =
        Bpmn.createExecutableProcess("wf-child")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    final BpmnModelInstance processParent =
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
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SERVICE_TASK,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SERVICE_TASK,
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_TERMINATED));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(parentProcessInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.CALL_ACTIVITY,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.CALL_ACTIVITY,
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.BOUNDARY_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void testThrowErrorOnEndEvent() {
    // given
    final BpmnModelInstance process =
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
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.BOUNDARY_EVENT,
                ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                BpmnEventType.ERROR,
                BpmnElementType.BOUNDARY_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void testTerminateOnEndEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .endEvent("terminate-end", EndEventBuilder::terminate)
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.TERMINATE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void testCatchTimerEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT0S"))
            .endEvent()
            .done();

    // when
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.TIMER,
                BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void testMessageStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .message(MESSAGE_NAME)
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    ENGINE.message().withCorrelationKey(CORRELATION_KEY).withName(MESSAGE_NAME).publish();

    // when
    final var processInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .filterRootScope()
            .getFirst();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstance.getValue().getProcessInstanceKey())
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.MESSAGE,
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SERVICE_TASK,
                ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void testMessageEndEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .endEvent()
            .addExtensionElement(ZeebeTaskDefinition.class, e -> e.setType(JOB_TYPE))
            .message(MESSAGE_NAME)
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.message().withCorrelationKey(CORRELATION_KEY).withName(MESSAGE_NAME).publish();
    ENGINE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.MESSAGE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void testMessageThrowEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .manualTask()
            .intermediateThrowEvent()
            .addExtensionElement(ZeebeTaskDefinition.class, e -> e.setType(JOB_TYPE))
            .message(MESSAGE_NAME)
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.message().withCorrelationKey(CORRELATION_KEY).withName(MESSAGE_NAME).publish();
    ENGINE.job().ofInstance(processInstanceKey).withType(JOB_TYPE).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.MANUAL_TASK,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.MESSAGE,
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void testReceiveTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .receiveTask("receive-message")
            .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_KEY))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    ENGINE.message().withName(MESSAGE_NAME).withCorrelationKey("order-123").publish();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(CORRELATION_KEY, "order-123")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.MESSAGE,
                BpmnElementType.RECEIVE_TASK,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void testLinkEvents() {
    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
    processBuilder.startEvent().intermediateThrowEvent("throw", b -> b.link(LINK_NAME));
    final BpmnModelInstance process =
        processBuilder.linkCatchEvent("catch").link(LINK_NAME).manualTask().endEvent().done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getBpmnEventType(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.START_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.LINK,
                BpmnElementType.INTERMEDIATE_THROW_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.LINK,
                BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.MANUAL_TASK,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.SEQUENCE_FLOW,
                ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(
                BpmnEventType.NONE,
                BpmnElementType.END_EVENT,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnEventType.UNSPECIFIED,
                BpmnElementType.PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED));
  }
}
