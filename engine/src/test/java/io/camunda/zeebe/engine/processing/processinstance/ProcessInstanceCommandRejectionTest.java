/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public final class ProcessInstanceCommandRejectionTest {

  private static final String PROCESS_ID = "process";

  @Rule public final EngineRule engine = EngineRule.singlePartition().maxCommandsInBatch(1);

  @Test
  public void shouldRejectActivateIfFlowScopeIsActivating() {
    // given (synthetic situation - is not expected in regular processing)
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.zeebeInputExpression("notExisting", "x")
                            .embeddedSubProcess()
                            .startEvent("subprocess-start")
                            .endEvent())
                .done());

    final var subprocessActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .getFirst();

    // when
    final var startEventCommand =
        new ProcessInstanceRecord()
            .setProcessDefinitionKey(subprocessActivating.getValue().getProcessDefinitionKey())
            .setProcessInstanceKey(processInstanceKey)
            .setElementId("subprocess-start")
            .setBpmnElementType(BpmnElementType.START_EVENT)
            .setFlowScopeKey(subprocessActivating.getKey());

    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.ACTIVATE_ELEMENT, startEventCommand));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.START_EVENT)
            .withElementId("subprocess-start")
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected flow scope instance to be in state '%s' but was '%s'.",
            ProcessInstanceIntent.ELEMENT_ACTIVATED, ProcessInstanceIntent.ELEMENT_ACTIVATING));
  }

  @Test
  public void shouldRejectActivateIfFlowScopeIsCompleting() {
    // given (synthetic situation - is not expected in regular processing)
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.zeebeOutputExpression("notExisting", "x")
                            .embeddedSubProcess()
                            .startEvent("subprocess-start")
                            .endEvent())
                .done());

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETING)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SUB_PROCESS)
        .await();

    final var startEventActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.START_EVENT)
            .withElementId("subprocess-start")
            .getFirst();

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(
                ProcessInstanceIntent.ACTIVATE_ELEMENT, startEventActivated.getValue()));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.START_EVENT)
            .withElementId("subprocess-start")
            .skip(1) // the start event was already activated once
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected flow scope instance to be in state '%s' but was '%s'.",
            ProcessInstanceIntent.ELEMENT_ACTIVATED, ProcessInstanceIntent.ELEMENT_COMPLETING));
  }

  @Test
  public void shouldRejectActivateIfFlowScopeIsCompleted() {
    // given (synthetic situation - is not expected in regular processing)
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done());

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    final var startEventActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.START_EVENT)
            .getFirst();

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(
                ProcessInstanceIntent.ACTIVATE_ELEMENT, startEventActivated.getValue()));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.START_EVENT)
            .skip(1) // the start event was already activated once
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected flow scope instance with key '%d' to be present in state but not found.",
            processInstanceKey));
  }

  @Test
  public void shouldRejectActivateIfFlowScopeIsTerminating() {
    // given
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("a", t -> t.zeebeJobType("a"))
                .serviceTask("b", t -> t.zeebeJobType("b"))
                .parallelGateway("join")
                .moveToNode("fork")
                .serviceTask("c", t -> t.zeebeJobType("c"))
                .connectTo("join")
                .endEvent()
                .done());

    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("a")
            .getFirst();

    // when
    engine.writeRecords(
        jobCompleteCommand(jobCreated), cancelProcessInstanceCommand(processInstanceKey));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("b")
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected flow scope instance to be in state '%s' but was '%s'.",
            ProcessInstanceIntent.ELEMENT_ACTIVATED, ProcessInstanceIntent.ELEMENT_TERMINATING));
  }

  @Test
  public void shouldRejectActivateIfFlowScopeIsTerminated() {
    // given
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("a", t -> t.zeebeJobType("a"))
                .serviceTask("b", t -> t.zeebeJobType("b"))
                .endEvent()
                .done());

    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    engine.writeRecords(
        jobCompleteCommand(jobCreated), cancelProcessInstanceCommand(processInstanceKey));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("b")
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected flow scope instance with key '%d' to be present in state but not found.",
            processInstanceKey));
  }

  @Test
  public void shouldRejectActivateIfFlowScopeIsInterrupted() {
    // given
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "interrupt",
                    s -> s.startEvent().interrupting(true).timerWithDuration("PT1M").endEvent())
                .startEvent()
                .serviceTask("a", t -> t.zeebeJobType("a"))
                .serviceTask("b", t -> t.zeebeJobType("b"))
                .endEvent()
                .done());

    final var timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var taskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, taskActivated.getValue())
            .key(taskActivated.getKey()),
        triggerTimerCommand(timerCreated));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("b")
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected flow scope instance to be not interrupted but was interrupted by an event with id '%s'.",
            "interrupt"));
  }

  @Test
  public void shouldRejectCompleteIfFlowScopeIsTerminating() {
    // given
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("a", t -> t.zeebeJobType("a"))
                .serviceTask("b", t -> t.zeebeJobType("b"))
                .endEvent()
                .done());

    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    engine.writeRecords(
        cancelProcessInstanceCommand(processInstanceKey), jobCompleteCommand(jobCreated));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.COMPLETE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("a")
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected flow scope instance to be in state '%s' but was '%s'.",
            ProcessInstanceIntent.ELEMENT_ACTIVATED, ProcessInstanceIntent.ELEMENT_TERMINATING));
  }

  @Test
  public void shouldRejectCompleteIfFlowScopeIsInterrupted() {
    // given
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "interrupt",
                    s -> s.startEvent().interrupting(true).timerWithDuration("PT1M").endEvent())
                .startEvent()
                .serviceTask("a", t -> t.zeebeJobType("a"))
                .serviceTask("b", t -> t.zeebeJobType("b"))
                .endEvent()
                .done());

    final var timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    engine.writeRecords(jobCompleteCommand(jobCreated), triggerTimerCommand(timerCreated));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.COMPLETE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("a")
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected flow scope instance to be not interrupted but was interrupted by an event with id '%s'.",
            "interrupt"));
  }

  @Test
  public void shouldRejectCompleteIfElementIsActivating() {
    // given (synthetic situation - is not expected in regular processing)
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("a", t -> t.zeebeJobType("a").zeebeInputExpression("notExisting", "x"))
                .endEvent()
                .done());

    final var taskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, taskActivating.getValue())
            .key(taskActivating.getKey()));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.COMPLETE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected element instance to be in state '%s' or one of '%s' but was '%s'.",
            ProcessInstanceIntent.ELEMENT_ACTIVATED,
            List.of(ProcessInstanceIntent.ELEMENT_COMPLETING),
            ProcessInstanceIntent.ELEMENT_ACTIVATING));
  }

  @Test
  public void shouldRejectCompleteIfElementIsCompleted() {
    // given (synthetic situation - is not expected in regular processing)
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("a", t -> t.zeebeJobType("a"))
                .serviceTask("b", t -> t.zeebeJobType("b"))
                .endEvent()
                .done());

    final var taskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, taskActivated.getValue())
            .key(taskActivated.getKey()),
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, taskActivated.getValue())
            .key(taskActivated.getKey()));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.COMPLETE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("a")
            .skip(1) // the task was already completed once
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected element instance with key '%d' to be present in state but not found.",
            taskActivated.getKey()));
  }

  @Test
  public void shouldRejectCompleteIfElementIsTerminated() {
    // given
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("a", t -> t.zeebeJobType("a"))
                .boundaryEvent("interrupt", b -> b.cancelActivity(true).timerWithDuration("PT1M"))
                .endEvent()
                .done());

    final var timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    engine.writeRecords(triggerTimerCommand(timerCreated), jobCompleteCommand(jobCreated));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.COMPLETE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected element instance with key '%d' to be present in state but not found.",
            jobCreated.getValue().getElementInstanceKey()));
  }

  @Test
  public void shouldRejectTerminateIfElementIsCompleted() {
    // given
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("a", t -> t.zeebeJobType("a"))
                .done());

    final var taskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    // when
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, taskActivated.getValue())
            .key(taskActivated.getKey()),
        cancelProcessInstanceCommand(processInstanceKey));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.TERMINATE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected element instance with key '%d' to be present in state but not found.",
            processInstanceKey));
  }

  @Test
  public void shouldRejectTerminateIfElementIsTerminating() {
    // given
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("a", t -> t.zeebeJobType("a"))
                .done());

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    engine.writeRecords(
        cancelProcessInstanceCommand(processInstanceKey),
        cancelProcessInstanceCommand(processInstanceKey));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.TERMINATE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .skip(1) // the process was already terminated once
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected element instance to be in state '%s' or one of '%s' but was '%s'.",
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            List.of(
                ProcessInstanceIntent.ELEMENT_ACTIVATED, ProcessInstanceIntent.ELEMENT_COMPLETING),
            ProcessInstanceIntent.ELEMENT_TERMINATING));
  }

  @Test
  public void shouldRejectTerminateIfElementIsTerminated() {
    // given
    final var processInstanceKey =
        createProcessInstance(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("a", t -> t.zeebeJobType("a"))
                .boundaryEvent("interrupt", b -> b.cancelActivity(true).timerWithDuration("PT1M"))
                .endEvent()
                .done());

    final var serviceTaskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("a")
            .getFirst();

    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    engine.writeRecords(
        terminateElementCommand(serviceTaskActivated),
        terminateElementCommand(serviceTaskActivated));

    // then
    final var rejectedCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.TERMINATE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .skip(1) // the task was already terminated once
            .getFirst();

    assertThatCommandIsRejected(
        rejectedCommand,
        String.format(
            "Expected element instance with key '%d' to be present in state but not found.",
            jobCreated.getValue().getElementInstanceKey()));
  }

  private long createProcessInstance(final BpmnModelInstance process) {
    engine.deployment().withXmlResource(process).deploy();
    return engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
  }

  private RecordToWrite cancelProcessInstanceCommand(final long processInstanceKey) {
    return RecordToWrite.command()
        .processInstance(ProcessInstanceIntent.CANCEL, new ProcessInstanceRecord())
        .key(processInstanceKey);
  }

  private RecordToWrite jobCompleteCommand(final Record<JobRecordValue> job) {
    return RecordToWrite.command().job(JobIntent.COMPLETE, job.getValue()).key(job.getKey());
  }

  private RecordToWrite triggerTimerCommand(final Record<TimerRecordValue> timer) {
    return RecordToWrite.command().timer(TimerIntent.TRIGGER, timer.getValue()).key(timer.getKey());
  }

  private RecordToWrite terminateElementCommand(final Record<ProcessInstanceRecordValue> record) {
    return RecordToWrite.command()
        .processInstance(ProcessInstanceIntent.TERMINATE_ELEMENT, record.getValue())
        .key(record.getKey());
  }

  private void assertThatCommandIsRejected(
      final Record<ProcessInstanceRecordValue> command, final String rejectionReason) {

    final var rejection =
        RecordingExporter.processInstanceRecords()
            .onlyCommandRejections()
            .withProcessInstanceKey(command.getValue().getProcessInstanceKey())
            .getFirst();

    Assertions.assertThat(rejection)
        .hasIntent(command.getIntent())
        .hasSourceRecordPosition(command.getPosition())
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(rejectionReason);
  }
}
