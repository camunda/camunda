/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * A process instance created with held timers keeps its timers out of the due-date scheduler: the
 * engine never fires them when their due date passes, and only an explicit trigger command advances
 * them. This is what lets a recording tool step through an instance's timers on a cluster whose
 * clock keeps firing every other instance's timers.
 */
public final class HeldTimersTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldMarkTimerOfHeldInstanceAsHeld() {
    // given
    final String processId = deployTimerCatchProcess();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withHeldTimers().create();

    // then
    final Record<TimerRecordValue> created =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(created.getValue().isHeld())
        .describedAs("a timer of a held instance is created as held")
        .isTrue();
  }

  @Test
  public void shouldNotMarkTimerAsHeldForOrdinaryInstance() {
    // given
    final String processId = deployTimerCatchProcess();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final Record<TimerRecordValue> created =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(created.getValue().isHeld())
        .describedAs("a timer of an ordinary instance is not held")
        .isFalse();
  }

  @Test
  public void shouldNotFireHeldTimerWhenDueDatePasses() {
    // given a held instance and an ordinary instance of the same process
    final String processId = deployTimerCatchProcess();
    final long heldInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withHeldTimers().create();
    final long ordinaryInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(heldInstanceKey)
        .await();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(ordinaryInstanceKey)
        .await();

    // when the due date passes for both
    ENGINE.increaseTime(Duration.ofMinutes(1));

    // then the ordinary instance's timer fires and completes, but the held one does not fire
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(ordinaryInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    final long heldTriggeredCount =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValueType() == ValueType.PROCESS_INSTANCE
                        && r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                        && ((ProcessInstanceRecordValue) r.getValue()).getProcessInstanceKey()
                            == ordinaryInstanceKey
                        && ((ProcessInstanceRecordValue) r.getValue()).getBpmnElementType()
                            == BpmnElementType.PROCESS)
            .timerRecords()
            .withIntent(TimerIntent.TRIGGERED)
            .filter(r -> r.getValue().getProcessInstanceKey() == heldInstanceKey)
            .count();
    assertThat(heldTriggeredCount)
        .describedAs("the scheduler must not fire a held timer when its due date passes")
        .isZero();
  }

  @Test
  public void shouldFireHeldTimerOnExplicitTrigger() {
    // given a held instance whose timer stayed held past its due date
    final String processId = deployTimerCatchProcess();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withHeldTimers().create();
    final Record<TimerRecordValue> created =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    ENGINE.increaseTime(Duration.ofMinutes(1));

    // when the timer is triggered explicitly
    ENGINE.writeRecords(
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, created.getValue())
            .key(created.getKey()));

    // then the timer fires and the instance completes
    RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  @Test
  public void shouldFireHeldTimerWhenTriggeredByProcessElement() {
    // given a held instance whose timer stayed held past its due date
    final String processId = deployTimerCatchProcess();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withHeldTimers().create();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.increaseTime(Duration.ofMinutes(1));

    // when a client-issued command addresses the held timer by (processInstanceKey, elementId),
    // as the public REST endpoint does; the remaining value fields are placeholders resolved by
    // the engine
    final var trigger =
        new TimerRecord()
            .setElementInstanceKey(-1L)
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(-1L)
            .setDueDate(-1L)
            .setRepetitions(0)
            .setTargetElementId(BufferUtil.wrapString("timer"));
    final var command = RecordToWrite.command().timer(TimerIntent.TRIGGER, trigger);
    command.recordMetadata().requestId(1L).requestStreamId(1);
    ENGINE.writeRecords(command);

    // then the held timer is resolved by (processInstanceKey, elementId) and fires, completing the
    // instance
    RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  @Test
  public void shouldRejectTriggerForUnknownProcessElement() {
    // given a deployed process but no held timer for the process instance below
    deployTimerCatchProcess();

    // when a client-issued command references a process instance with no matching held timer
    final var trigger =
        new TimerRecord()
            .setElementInstanceKey(-1L)
            .setProcessInstanceKey(999_999L)
            .setProcessDefinitionKey(-1L)
            .setDueDate(-1L)
            .setRepetitions(0)
            .setTargetElementId(BufferUtil.wrapString("timer"));
    final var command = RecordToWrite.command().timer(TimerIntent.TRIGGER, trigger);
    command.recordMetadata().requestId(1L).requestStreamId(1);
    ENGINE.writeRecords(command);

    // then the command is rejected as not found
    final Record<TimerRecordValue> rejection =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .onlyCommandRejections()
            .filter(r -> r.getValue().getProcessInstanceKey() == 999_999L)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldHoldTimerOfChildProcessInstance() {
    // given a held parent instance that calls a child process holding a timer catch event
    final String childProcessId = Strings.newRandomValidBpmnId();
    final String parentProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            "child.bpmn",
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT1S"))
                .endEvent()
                .done())
        .withXmlResource(
            "parent.bpmn",
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .deploy();

    // when
    final long parentInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).withHeldTimers().create();

    // then the child's timer inherits the root instance's held flag
    final Record<TimerRecordValue> childTimer =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .filter(r -> r.getValue().getRootProcessInstanceKey() == parentInstanceKey)
            .filter(r -> r.getValue().getProcessInstanceKey() != parentInstanceKey)
            .getFirst();
    assertThat(childTimer.getValue().isHeld())
        .describedAs("a timer of a called child instance inherits the root's held flag")
        .isTrue();
  }

  @Test
  public void shouldKeepRepeatingHeldTimerHeldAfterTrigger() {
    // given a held instance with a repeating boundary timer
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(processId))
                .boundaryEvent("timer", b -> b.cancelActivity(false).timerWithCycle("R2/PT1S"))
                .endEvent()
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withHeldTimers().create();
    final Record<TimerRecordValue> firstTimer =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when the first occurrence is triggered explicitly
    ENGINE.writeRecords(
        RecordToWrite.command()
            .timer(TimerIntent.TRIGGER, firstTimer.getValue())
            .key(firstTimer.getKey()));

    // then the rescheduled timer is created held again
    final Record<TimerRecordValue> rescheduledTimer =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .getLast();
    assertThat(rescheduledTimer.getKey()).isNotEqualTo(firstTimer.getKey());
    assertThat(rescheduledTimer.getValue().isHeld())
        .describedAs("a rescheduled occurrence of a held repeating timer stays held")
        .isTrue();
  }

  @Test
  public void shouldKeepTimerHeldAcrossSuspendAndResume() {
    // given a held instance whose timer is past its due date
    final String processId = deployTimerCatchProcess();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withHeldTimers().create();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when the instance is suspended and resumed
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();
    ENGINE.increaseTime(Duration.ofMinutes(1));

    // then resuming must not hand the held timer back to the scheduler: an ordinary instance
    // created afterwards runs to completion while the held timer is still waiting
    final long ordinaryInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE.increaseTime(Duration.ofMinutes(1));
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(ordinaryInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    assertThat(
            RecordingExporter.records()
                .limit(
                    r ->
                        r.getValueType() == ValueType.PROCESS_INSTANCE
                            && r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                            && ((ProcessInstanceRecordValue) r.getValue()).getProcessInstanceKey()
                                == ordinaryInstanceKey
                            && ((ProcessInstanceRecordValue) r.getValue()).getBpmnElementType()
                                == BpmnElementType.PROCESS)
                .timerRecords()
                .withIntent(TimerIntent.TRIGGERED)
                .filter(r -> r.getValue().getProcessInstanceKey() == processInstanceKey)
                .count())
        .describedAs("resuming a suspended instance must not schedule its held timers")
        .isZero();
  }

  @Test
  public void shouldRejectClientTriggerWhileInstanceIsSuspended() {
    // given a suspended held instance
    final String processId = deployTimerCatchProcess();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withHeldTimers().create();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when a client-issued trigger arrives
    final var trigger =
        new TimerRecord()
            .setElementInstanceKey(-1L)
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(-1L)
            .setDueDate(-1L)
            .setRepetitions(0)
            .setTargetElementId(BufferUtil.wrapString("timer"));
    final var command = RecordToWrite.command().timer(TimerIntent.TRIGGER, trigger);
    command.recordMetadata().requestId(1L).requestStreamId(1);
    ENGINE.writeRecords(command);

    // then it is rejected rather than buffered, so the caller is not left waiting for a resume
    final Record<TimerRecordValue> rejection =
        RecordingExporter.timerRecords(TimerIntent.TRIGGER)
            .onlyCommandRejections()
            .filter(r -> r.getValue().getProcessInstanceKey() == processInstanceKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  private static String deployTimerCatchProcess() {
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT1S"))
                .endEvent()
                .done())
        .deploy();
    return processId;
  }
}
