/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.execution;

import static io.camunda.zeebe.engine.processing.job.JobThrowErrorProcessor.ERROR_REJECTION_MESSAGE;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  static final String PROCESS_ID = "process";
  static final String SUB_PROCESS_ID = "sub_".concat(PROCESS_ID);
  static final String START_EL_TYPE = "start_execution_listener_job";
  static final String END_EL_TYPE = "end_execution_listener_job";
  static final String SERVICE_TASK_TYPE = "test_service_task";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectErrorThrowingFromExecutionListenerJob() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "service_task",
                    t ->
                        t.zeebeJobType(SERVICE_TASK_TYPE)
                            .zeebeStartExecutionListener(START_EL_TYPE))
                .boundaryEvent("error_boundary", b -> b.error("err"))
                .endEvent("error_end")
                .moveToActivity("service_task")
                .endEvent("main_end")
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: attempt to throw a BPMN error from the start execution listener job
    final Record<JobRecordValue> error =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(START_EL_TYPE)
            .withErrorCode("err")
            .throwError();

    // then: verify the rejection of the BPMN error
    final String expectedRejectionReason =
        String.format(
            ERROR_REJECTION_MESSAGE,
            JobKind.EXECUTION_LISTENER,
            error.getKey(),
            START_EL_TYPE,
            processInstanceKey);
    assertThat(jobRecords().withRecordType(RecordType.COMMAND_REJECTION).getFirst())
        .extracting(
            r -> r.getValue().getType(), Record::getRejectionType, Record::getRejectionReason)
        .containsExactly(START_EL_TYPE, RejectionType.INVALID_STATE, expectedRejectionReason);
  }

  @Test
  public void shouldCompleteEmbeddedSubProcessWithMultipleExecutionListeners() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .manualTask()
                .subProcess(
                    SUB_PROCESS_ID,
                    s ->
                        s.zeebeStartExecutionListener(START_EL_TYPE + "_sub_1")
                            .zeebeStartExecutionListener(START_EL_TYPE + "_sub_2")
                            .zeebeEndExecutionListener(END_EL_TYPE + "_sub_1")
                            .zeebeEndExecutionListener(END_EL_TYPE + "_sub_2")
                            .embeddedSubProcess()
                            .startEvent()
                            .manualTask()
                            .endEvent())
                .manualTask()
                .endEvent()
                .done());

    // complete sub-process start/end EL jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_sub_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_sub_2").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_sub_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_sub_2").complete();

    // then: EL jobs completed in expected order
    assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .withElementId(SUB_PROCESS_ID)
                .limit(4))
        .extracting(r -> r.getValue().getType())
        .containsExactly(
            START_EL_TYPE + "_sub_1",
            START_EL_TYPE + "_sub_2",
            END_EL_TYPE + "_sub_1",
            END_EL_TYPE + "_sub_2");

    // assert the process instance has completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldAllowSubsequentElementToAccessVariableProducedBySubprocessEndListenerJob() {
    // given: deploy process with embedded subprocess having end EL and another task following it
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    SUB_PROCESS_ID,
                    s ->
                        s.zeebeEndExecutionListener(END_EL_TYPE)
                            .embeddedSubProcess()
                            .startEvent()
                            .manualTask()
                            .endEvent())
                .serviceTask("subsequent_task", tb -> tb.zeebeJobType("subsequent_service_task"))
                .endEvent()
                .done());

    // when: complete the end EL job with a variable 'end_el_var'
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(END_EL_TYPE)
        .withVariable("end_el_var", "baz")
        .complete();

    // then: assert the variable 'end_el_var' is accessible by the subsequent service task element
    final var subsequentServiceTaskJob =
        ENGINE.jobs().withType("subsequent_service_task").activate().getValue().getJobs().stream()
            .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(subsequentServiceTaskJob)
        .hasValueSatisfying(
            job -> assertThat(job.getVariables()).contains(entry("end_el_var", "baz")));
    ENGINE.job().ofInstance(processInstanceKey).withType("subsequent_service_task").complete();
  }

  @Test
  public void shouldCancelActiveStartElJobForEmbeddedSubProcessAfterProcessInstanceCancellation() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .manualTask()
                .subProcess(
                    SUB_PROCESS_ID,
                    s ->
                        s.zeebeStartExecutionListener(START_EL_TYPE + "_sub")
                            .embeddedSubProcess()
                            .startEvent()
                            .manualTask()
                            .endEvent())
                .manualTask()
                .endEvent()
                .done());
    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(START_EL_TYPE + "_sub")
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then: start EL job should be canceled
    assertThat(
            jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .onlyEvents()
                .getFirst())
        .extracting(r -> r.getValue().getType())
        .isEqualTo(START_EL_TYPE + "_sub");
  }

  @Test
  public void shouldAccessVariableFromEmbeddedSubProcessStartListenerInSubProcessServiceTask() {
    // given
    final long processInstanceKey =
        createProcessInstance(
            ENGINE,
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    SUB_PROCESS_ID,
                    s ->
                        s.zeebeStartExecutionListener(START_EL_TYPE + "_sub")
                            .embeddedSubProcess()
                            .startEvent()
                            .serviceTask("task", b -> b.zeebeJobType(SERVICE_TASK_TYPE + "_sub"))
                            .endEvent())
                .manualTask()
                .endEvent()
                .done());

    // when: complete subprocess start EL job with variables
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(START_EL_TYPE + "_sub")
        .withVariable("baz", 42)
        .complete();

    // then: assert the variable was created after start EL completion
    assertThat(records().withValueTypes(ValueType.JOB, ValueType.VARIABLE).onlyEvents().limit(3))
        .extracting(Record::getValueType, Record::getIntent)
        .containsExactly(
            tuple(ValueType.JOB, JobIntent.CREATED),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.VARIABLE, VariableIntent.CREATED));

    // `baz` variable accessible in subprocess service task job
    final Optional<JobRecordValue> jobActivated =
        ENGINE.jobs().withType(SERVICE_TASK_TYPE + "_sub").activate().getValue().getJobs().stream()
            .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(jobActivated)
        .hasValueSatisfying(job -> assertThat(job.getVariables()).contains(entry("baz", 42)));
  }

  @Test
  public void shouldCompleteCallActivitySubProcessWithMultipleExecutionListeners() {
    // given
    final var childProcess =
        Bpmn.createExecutableProcess(SUB_PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType(SERVICE_TASK_TYPE + "_sub"))
            .endEvent()
            .done();

    final var parentProcess =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeStartExecutionListener(START_EL_TYPE)
            .startEvent()
            .callActivity(SUB_PROCESS_ID, c -> c.zeebeProcessId(SUB_PROCESS_ID))
            .zeebeStartExecutionListener(START_EL_TYPE + "_sub")
            .zeebeEndExecutionListener(END_EL_TYPE + "_sub_1")
            .zeebeEndExecutionListener(END_EL_TYPE + "_sub_2")
            .manualTask()
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("parent.xml", parentProcess)
        .withXmlResource("child.xml", childProcess)
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when: complete parent process start EL job
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE).complete();
    // complete sub-process EL and service task jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_sub").complete();
    completeJobFromSubProcess(SERVICE_TASK_TYPE + "_sub");
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_sub_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_sub_2").complete();

    // then: jobs completed in expected order
    assertThat(RecordingExporter.jobRecords().withIntent(JobIntent.COMPLETED).limit(5))
        .extracting(r -> r.getValue().getElementId(), r -> r.getValue().getType())
        .containsExactly(
            tuple(PROCESS_ID, START_EL_TYPE),
            tuple(SUB_PROCESS_ID, START_EL_TYPE + "_sub"),
            tuple("task", SERVICE_TASK_TYPE + "_sub"),
            tuple(SUB_PROCESS_ID, END_EL_TYPE + "_sub_1"),
            tuple(SUB_PROCESS_ID, END_EL_TYPE + "_sub_2"));

    // assert the process instance with call activity completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.CALL_ACTIVITY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelActiveStartElJobForCallActivityAfterProcessInstanceCancellation() {
    // given
    final var childProcess =
        Bpmn.createExecutableProcess(SUB_PROCESS_ID)
            .startEvent()
            .manualTask("task")
            .endEvent()
            .done();

    final var parentProcess =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity(SUB_PROCESS_ID, c -> c.zeebeProcessId(SUB_PROCESS_ID))
            .zeebeStartExecutionListener(START_EL_TYPE + "_sub")
            .manualTask()
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("parent.xml", parentProcess)
        .withXmlResource("child.xml", childProcess)
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(START_EL_TYPE + "_sub")
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then: start EL job should be canceled
    assertThat(
            jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .onlyEvents()
                .getFirst())
        .extracting(r -> r.getValue().getType())
        .isEqualTo(START_EL_TYPE + "_sub");
  }

  @Test
  public void shouldCompleteEventSubProcessWithMultipleExecutionListeners() {
    final var messageName = "subprocess-event";

    final String messageSubprocessId = "message-event-subprocess";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    messageSubprocessId,
                    sub ->
                        sub.zeebeStartExecutionListener(START_EL_TYPE + "_sub_1")
                            .zeebeStartExecutionListener(START_EL_TYPE + "_sub_2")
                            .zeebeEndExecutionListener(END_EL_TYPE + "_sub")
                            .startEvent("startEvent_sub")
                            .interrupting(false)
                            .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key"))
                            .serviceTask(
                                "task_sub", t -> t.zeebeJobType(SERVICE_TASK_TYPE + "_sub"))
                            .endEvent("endEvent_sub"))
                .startEvent("startEvent")
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent("endEvent")
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withStartInstruction("task")
            .withVariable("key", "key-1")
            .create();

    // when
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    ENGINE.message().withName(messageName).withCorrelationKey("key-1").publish();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();

    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_sub_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_sub_2").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE + "_sub").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_sub").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            record -> record.getValue().getElementId(),
            record -> record.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSequence(
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ACTIVATE_ELEMENT))
        .containsSubsequence(
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(
                "task_sub", BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(
                messageSubprocessId,
                BpmnElementType.EVENT_SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelActiveStartElJobForEventSubProcessAfterProcessInstanceCancellation() {
    // given
    final var messageName = "subprocess-event";

    final String messageSubprocessId = "message-event-subprocess";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    messageSubprocessId,
                    sub ->
                        sub.zeebeStartExecutionListener(START_EL_TYPE + "_sub")
                            .startEvent("startEvent_sub")
                            .interrupting(false)
                            .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key"))
                            .serviceTask(
                                "task_sub", t -> t.zeebeJobType(SERVICE_TASK_TYPE + "_sub"))
                            .endEvent("endEvent_sub"))
                .startEvent("startEvent")
                .serviceTask("task", t -> t.zeebeJobType(SERVICE_TASK_TYPE))
                .endEvent("endEvent")
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withStartInstruction("task")
            .withVariable("key", "key-1")
            .create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    ENGINE.message().withName(messageName).withCorrelationKey("key-1").publish();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    ENGINE.job().ofInstance(processInstanceKey).withType(SERVICE_TASK_TYPE).complete();

    jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(START_EL_TYPE + "_sub")
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then: start EL job should be canceled
    assertThat(
            jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .onlyEvents()
                .getFirst())
        .extracting(r -> r.getValue().getType())
        .isEqualTo(START_EL_TYPE + "_sub");
  }

  @Test
  public void shouldCreateListenerJobsWithCustomHeadersContainingTaskHeadersOnProcessStart() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_1wnykb0" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.37.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.7.0">
              <bpmn:process id="Process_1x1wunc" isExecutable="true">
                <bpmn:extensionElements>
                  <zeebe:executionListeners>
                    <zeebe:executionListener eventType="start" type="start" />
                    <zeebe:executionListener eventType="end" type="end" />
                  </zeebe:executionListeners>
                  <zeebe:taskHeaders>
                    <zeebe:header key="foo" value="bar" />
                  </zeebe:taskHeaders>
                </bpmn:extensionElements>
                <bpmn:startEvent id="StartEvent_1">
                  <bpmn:outgoing>Flow_0g4i5lz</bpmn:outgoing>
                </bpmn:startEvent>
                <bpmn:endEvent id="Event_0ykkkx4">
                  <bpmn:incoming>Flow_0g4i5lz</bpmn:incoming>
                </bpmn:endEvent>
                <bpmn:sequenceFlow id="Flow_0g4i5lz" sourceRef="StartEvent_1" targetRef="Event_0ykkkx4" />
              </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1x1wunc">
                  <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
                    <dc:Bounds x="182" y="82" width="36" height="36" />
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNShape id="Event_0ykkkx4_di" bpmnElement="Event_0ykkkx4">
                    <dc:Bounds x="272" y="82" width="36" height="36" />
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNEdge id="Flow_0g4i5lz_di" bpmnElement="Flow_0g4i5lz">
                    <di:waypoint x="218" y="100" />
                    <di:waypoint x="272" y="100" />
                  </bpmndi:BPMNEdge>
                </bpmndi:BPMNPlane>
              </bpmndi:BPMNDiagram>
            </bpmn:definitions>
            """
                .getBytes(StandardCharsets.UTF_8))
        .deploy();

    // when both start and end execution listeners are created and completed
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("Process_1x1wunc").create();
    ENGINE.job().ofInstance(processInstanceKey).withType("start").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("end").complete();

    // then
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.CREATED)
                .limit(2))
        .hasSize(2)
        .allSatisfy(
            r ->
                assertThat(r.getValue().getCustomHeaders())
                    .describedAs("Expect that the created jobs have the task headers")
                    .isEqualTo(Map.of("foo", "bar")));
    assertThat(
            jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .limit(2))
        .hasSize(2)
        .allSatisfy(
            r ->
                assertThat(r.getValue().getCustomHeaders())
                    .describedAs("Expect that the completed jobs also have the task headers")
                    .isEqualTo(Map.of("foo", "bar")));
  }

  // test util methods
  static long createProcessInstance(
      final EngineRule engineRule, final BpmnModelInstance modelInstance) {
    return createProcessInstance(engineRule, modelInstance, Collections.emptyMap());
  }

  static long createProcessInstance(
      final EngineRule engineRule,
      final BpmnModelInstance modelInstance,
      final Map<String, Object> variables) {
    engineRule.deployment().withXmlResource(modelInstance).deploy();
    return engineRule
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariables(variables)
        .create();
  }

  static void completeRecreatedJobWithType(
      final EngineRule engineRule, final long processInstanceKey, final String jobType) {
    final long jobKey =
        jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(jobType)
            .skip(1)
            .getFirst()
            .getKey();
    engineRule.job().ofInstance(processInstanceKey).withKey(jobKey).complete();
  }

  private void completeJobFromSubProcess(final String jobType) {
    ENGINE
        .jobs()
        .withType(jobType)
        .activate()
        .getValue()
        .getJobKeys()
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());
  }

  static void assertExecutionListenerJobsCompletedForElement(
      final long processInstanceKey, final String elementId, final String... elJobTypes) {
    assertThat(
            RecordingExporter.jobRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withJobKind(JobKind.EXECUTION_LISTENER)
                .withIntent(JobIntent.COMPLETED)
                .withElementId(elementId)
                .limit(elJobTypes.length))
        .extracting(r -> r.getValue().getType())
        .containsExactly(elJobTypes);
  }
}
