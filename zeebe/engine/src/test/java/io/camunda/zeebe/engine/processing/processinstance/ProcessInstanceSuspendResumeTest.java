/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Smoke tests for the process instance suspend/resume POC (#56552): verifies the core mechanism —
 * forward-progress element commands are diverted into the buffer while suspended, and drained in
 * order on resume — before layering the auxiliary gates and cancel/variable behavior on top.
 */
public final class ProcessInstanceSuspendResumeTest {

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "test";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldBufferForwardProgressCommandWhileSuspendedAndDrainOnResume() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var taskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();
    final long taskElementInstanceKey = taskActivated.getKey();

    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when: simulate a forward-progress command arriving while suspended (e.g. what job
    // completion would eventually trigger) by writing it directly
    final var completeElementCommand =
        new ProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.SERVICE_TASK)
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(taskActivated.getValue().getProcessDefinitionKey())
            .setElementId("task")
            .setFlowScopeKey(taskActivated.getValue().getFlowScopeKey())
            .setBpmnProcessId(PROCESS_ID)
            .setVersion(taskActivated.getValue().getVersion());
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, completeElementCommand)
            .key(taskElementInstanceKey));

    // then: the command is buffered, not processed — no ELEMENT_COMPLETING is produced yet
    final var buffered =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMMAND_BUFFERED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(buffered).isNotNull();
    RecordingExporter.expectNoMatchingRecords(
        records ->
            records
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETING)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .findFirst());

    // when: resume — the buffered COMPLETE_ELEMENT drains and the task completes normally
    engine.processInstance().withInstanceKey(processInstanceKey).resume();

    // then: the drained COMPLETE_ELEMENT (SERVICE_TASK) leads to the process completing normally,
    // proving the buffer->resume->drain path reproduces ordinary forward progress
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.PROCESS))
        .extracting(Record::getIntent)
        .containsSubsequence(
            ProcessInstanceIntent.SUSPENDED,
            ProcessInstanceIntent.RESUMED,
            ProcessInstanceIntent.ELEMENT_COMPLETED);
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectJobCompleteWhileSuspended() {
    // given
    final long processInstanceKey = createServiceTaskProcessInstance();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final var rejection = engine.job().withKey(jobKey).expectRejection().complete();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    RecordingExporter.expectNoMatchingRecords(
        records ->
            records.jobRecords().withIntent(JobIntent.COMPLETED).withRecordKey(jobKey).findFirst());
  }

  @Test
  public void shouldRejectJobFailWhileSuspended() {
    // given
    final long processInstanceKey = createServiceTaskProcessInstance();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final var rejection = engine.job().withKey(jobKey).expectRejection().fail();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    RecordingExporter.expectNoMatchingRecords(
        records ->
            records.jobRecords().withIntent(JobIntent.FAILED).withRecordKey(jobKey).findFirst());
  }

  @Test
  public void shouldNotFireTimerWhileSuspendedAndFireOnResume() {
    // given: a genuinely short-lived timer so the real due-date checker (not a simulated command)
    // drives both the rejection-while-suspended and the fire-on-resume paths
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .intermediateCatchEvent("timer", i -> i.timerWithDuration("PT0.2S"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when: the due-date checker naturally re-attempts TRIGGER once the timer becomes due —
    // rejected because the instance is suspended; the timer stays scheduled (not consumed)
    final var rejection =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .onlyCommandRejections()
            .withRecordKey(timerCreated.getKey())
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    RecordingExporter.expectNoMatchingRecords(
        records ->
            records
                .timerRecords()
                .withIntent(TimerIntent.TRIGGERED)
                .withRecordKey(timerCreated.getKey())
                .findFirst());

    // when: resume — the nudge re-checks the (already past) real due date and fires immediately
    engine.processInstance().withInstanceKey(processInstanceKey).resume();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withRecordKey(timerCreated.getKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectMessageCorrelationWhileSuspended() {
    // given
    final String correlationKey = "order-1";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .intermediateCatchEvent(
                    "msg",
                    i ->
                        i.message(
                            m -> m.name("order-placed").zeebeCorrelationKeyExpression("orderId")))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("orderId", correlationKey)
            .create();

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();

    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when: publishing succeeds (that step is unrelated to suspension) — correlation is the step
    // that is rejected
    engine.message().withName("order-placed").withCorrelationKey(correlationKey).publish();

    // then: correlation is rejected — no side effects while suspended, catch event never completes
    final var correlateRejection =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CORRELATE)
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(correlateRejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    RecordingExporter.expectNoMatchingRecords(
        records ->
            records
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("msg")
                .findFirst());
  }

  @Test
  public void shouldTerminateAndDiscardBufferWhenCancellingSuspendedInstance() {
    // given
    final long processInstanceKey = createServiceTaskProcessInstance();
    final var taskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferServiceTaskCompleteCommand(processInstanceKey, taskActivated);

    // when: cancel proceeds even though the instance is suspended and has a buffered command
    engine.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then: the instance terminates cleanly and the buffered command never drains
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
    RecordingExporter.expectNoMatchingRecords(
        records ->
            records
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETING)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .findFirst());
  }

  @Test
  public void shouldAllowVariableUpdateWhileSuspended() {
    // given
    final long processInstanceKey = createServiceTaskProcessInstance();
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final var updated =
        engine
            .variables()
            .ofScope(processInstanceKey)
            .withDocument(Map.of("approved", true))
            .update();

    // then
    assertThat(updated.getIntent()).isEqualTo(VariableDocumentIntent.UPDATED);
  }

  @Test
  public void shouldRecoverSuspensionAndBufferAfterRestartAndReplayIdentically() {
    // given
    final long processInstanceKey = createServiceTaskProcessInstance();
    final var taskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    engine.processInstance().withInstanceKey(processInstanceKey).suspend();
    bufferServiceTaskCompleteCommand(processInstanceKey, taskActivated);

    // when: restart and replay the log — suspended flag and buffer must be rebuilt purely from
    // the SUSPENDED/ELEMENT_COMMAND_BUFFERED events, proving the determinism guarantee
    engine.reprocess();

    // then: resume still drains the buffer and completes the process, exactly as before restart
    engine.processInstance().withInstanceKey(processInstanceKey).resume();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  private long createServiceTaskProcessInstance() {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    return engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
  }

  private void bufferServiceTaskCompleteCommand(
      final long processInstanceKey, final Record<ProcessInstanceRecordValue> taskActivated) {
    final var taskActivatedValue = taskActivated.getValue();
    final var completeElementCommand =
        new ProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.SERVICE_TASK)
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(taskActivatedValue.getProcessDefinitionKey())
            .setElementId("task")
            .setFlowScopeKey(taskActivatedValue.getFlowScopeKey())
            .setBpmnProcessId(PROCESS_ID)
            .setVersion(taskActivatedValue.getVersion());
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, completeElementCommand)
            .key(taskActivated.getKey()));
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMMAND_BUFFERED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }
}
