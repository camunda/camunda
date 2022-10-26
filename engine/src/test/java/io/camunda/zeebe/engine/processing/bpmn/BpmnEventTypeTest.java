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
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
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

  private static final String PROCESS_ID = "wf";
  private static final String JOB_TYPE = "test";
  private static final String ERROR_CODE = "ERROR";

  private static final BpmnModelInstance SINGLE_BOUNDARY_EVENT =
      process(
          serviceTask -> serviceTask.boundaryEvent("error", b -> b.error(ERROR_CODE)).endEvent());

  private static final String MESSAGE_NAME_1 = "a";

  private static final String CORRELATION_KEY_1 = "key-1";

  private static final BpmnModelInstance SINGLE_START_EVENT_1 =
      singleStartEvent(startEvent -> {}, MESSAGE_NAME_1);
  private static final BpmnModelInstance RECEIVE_TASK_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance singleStartEvent(
      final Consumer<StartEventBuilder> customizer, final String messageName) {
    final var startEventBuilder =
        Bpmn.createExecutableProcess("wf").startEvent("start").message(messageName);

    customizer.accept(startEventBuilder);

    return startEventBuilder.serviceTask("task", t -> t.zeebeJobType("test")).done();
  }

  private static BpmnModelInstance process(final Consumer<ServiceTaskBuilder> customizer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE));

    customizer.accept(builder);

    return builder.endEvent().done();
  }

  @Test
  public void shouldTriggerErrorEventOnBoundaryEvent() {
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
  public void shouldTerminateOnEndEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .endEvent("terminate-end", EndEventBuilder::terminate)
                .done())
        .deploy();

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
  public void shouldCatchTimerEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("testLifeCycle")
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT0S"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("testLifeCycle").create();

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
  public void shouldCorrelateMessageToStartEvent() {
    // given
    ENGINE.deployment().withXmlResource(SINGLE_START_EVENT_1).deploy();

    // when
    ENGINE.message().withCorrelationKey(CORRELATION_KEY_1).withName(MESSAGE_NAME_1).publish();

    // then
    final var processInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .filterRootScope()
            .getFirst();

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
  public void testReceiveTaskLifeCycle() {
    // given
    ENGINE.deployment().withXmlResource(RECEIVE_TASK_PROCESS).deploy();
    ENGINE.message().withName("message").withCorrelationKey("order-123").publish();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
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
}
