/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBufferedCommandRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Verifies the primary suspension gate in {@code Engine.process} (see #57521): commands targeting a
 * suspended process instance are either buffered, rejected, or passed through, depending on the
 * classification of their {@code TypedRecordProcessor}.
 */
public final class SuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectJobCompleteWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(job.getKey()).expectRejection().complete();

    // then
    Assertions.assertThat(rejection)
        .hasIntent(JobIntent.COMPLETE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    // the reason must reference the real process instance key, not the -1 job commands carry on the
    // wire (regression guard for the key resolved by the gate being threaded into the rejection)
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldCompleteCancellationWhileSuspended() {
    // given - cancellation (PROCESS category) must be able to complete on a suspended instance
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final Record<ProcessInstanceRecordValue> terminated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(processId)
            .getFirst();
    Assertions.assertThat(terminated.getValue()).hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldBufferInternalCommandWhileSuspended() {
    // given - a real timer whose CANCEL command (BUFFER category) is targeted at the process
    // instance while it is suspended
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT10S"))
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final Record<TimerRecordValue> timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .timer(TimerIntent.CANCEL, timerCreated.getValue())
            .key(timerCreated.getKey()));

    // then - the command is buffered instead of being handed to TimerCancelProcessor
    final Record<RecordValue> buffered =
        RecordingExporter.records()
            .withValueType(ValueType.PROCESS_INSTANCE_BUFFERED_COMMAND)
            .withIntent(ProcessInstanceBufferedCommandIntent.BUFFERED)
            .filter(
                r ->
                    ((ProcessInstanceBufferedCommandRecordValue) r.getValue())
                            .getProcessInstanceKey()
                        == processInstanceKey)
            .getFirst();
    final var bufferedValue = (ProcessInstanceBufferedCommandRecordValue) buffered.getValue();
    assertThat(bufferedValue.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(bufferedValue.getElementInstanceKey()).isEqualTo(timerCreated.getKey());
    assertThat(bufferedValue.getIntent()).isEqualTo(TimerIntent.CANCEL);

    // and no forward progress was made: the timer still exists, i.e. it was never actually
    // canceled by TimerCancelProcessor
    final var timerValue = timerCreated.getValue();
    assertThat(
            ((MutableProcessingState) ENGINE.getProcessingState())
                .getTimerState()
                .get(timerValue.getElementInstanceKey(), timerCreated.getKey()))
        .isNotNull();
  }

  @Test
  public void shouldTriggerTimerAfterResume() {
    // given - an instance waiting on a timer that comes due while the instance is suspended
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT10S"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - the timer becomes due while suspended (skipped by the checker, no TRIGGER), then the
    // instance is resumed
    ENGINE.increaseTime(Duration.ofMinutes(1));
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();
    // advance again so the checker pass that RESUME re-armed actually runs
    ENGINE.increaseTime(Duration.ofMinutes(1));

    // then - resuming re-arms the due-date checker and the timer finally fires
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldPassThroughBufferCategoryCommandWhileResuming() {
    // given - seed the RESUMING marker directly since it is currently unreachable in production
    // (draining is implemented in a follow-up issue, #57792)
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT10S"))
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final Record<TimerRecordValue> timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    ((MutableProcessingState) ENGINE.getProcessingState())
        .getSuspensionState()
        .setSuspensionState(processInstanceKey, State.RESUMING);

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .timer(TimerIntent.CANCEL, timerCreated.getValue())
            .key(timerCreated.getKey()));

    // then - the BUFFER-category command passes through and is actually processed
    final Record<TimerRecordValue> canceled =
        RecordingExporter.timerRecords(TimerIntent.CANCELED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    Assertions.assertThat(canceled.getValue()).hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldRejectMessageCorrelateWhileSuspended() {
    // given - an instance waiting on an intermediate message catch event, then suspended
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<MessageCorrelationRecordValue> rejection =
        ENGINE
            .messageCorrelation()
            .withName(messageName)
            .withCorrelationKey(correlationKey)
            .expectRejection()
            .correlate();

    // then - the correlate command is rejected, referencing the suspended instance
    Assertions.assertThat(rejection)
        .hasIntent(MessageCorrelationIntent.CORRELATE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectMessageCorrelateWhileResuming() {
    // given - an instance waiting on a message catch event, with the RESUMING marker seeded
    // directly (draining is implemented in a follow-up issue, #57792)
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    ((MutableProcessingState) ENGINE.getProcessingState())
        .getSuspensionState()
        .setSuspensionState(processInstanceKey, State.RESUMING);

    // when
    final Record<MessageCorrelationRecordValue> rejection =
        ENGINE
            .messageCorrelation()
            .withName(messageName)
            .withCorrelationKey(correlationKey)
            .expectRejection()
            .correlate();

    // then - the correlate command is rejected just like while SUSPENDED
    Assertions.assertThat(rejection)
        .hasIntent(MessageCorrelationIntent.CORRELATE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldCorrelateMessageWhileNotSuspended() {
    // given - an instance waiting on an intermediate message catch event, not suspended
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(processId, messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .messageCorrelation()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .correlate();

    // then - correlation proceeds normally
    final Record<MessageCorrelationRecordValue> correlated =
        RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.CORRELATED)
            .withCorrelationKey(correlationKey)
            .getFirst();
    Assertions.assertThat(correlated.getValue()).hasCorrelationKey(correlationKey);
  }

  @Test
  public void shouldRejectMessageCorrelateWhenOneOfMultipleTargetsSuspended() {
    // given - two instances of different processes waiting on the same message, one suspended
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    final long suspendedInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(
            Strings.newRandomValidBpmnId(), messageName, correlationKey);
    final long activeInstanceKey =
        deployAndStartProcessWithMessageCatchEvent(
            Strings.newRandomValidBpmnId(), messageName, correlationKey);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(suspendedInstanceKey)
        .await();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(activeInstanceKey)
        .await();
    ENGINE.processInstance().withInstanceKey(suspendedInstanceKey).suspend();

    // when
    final Record<MessageCorrelationRecordValue> rejection =
        ENGINE
            .messageCorrelation()
            .withName(messageName)
            .withCorrelationKey(correlationKey)
            .expectRejection()
            .correlate();

    // then - the entire correlate is rejected (all-or-nothing) referencing the suspended instance;
    // because the gate rejects before any state write, the active instance is left untouched and
    // the
    // message is preserved for a post-resume retry
    Assertions.assertThat(rejection)
        .hasIntent(MessageCorrelationIntent.CORRELATE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + suspendedInstanceKey + "'");
  }

  private long deployAndStartProcessWithMessageCatchEvent(
      final String processId, final String messageName, final String correlationKey) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "msg",
                    e ->
                        e.message(
                            m ->
                                m.name(messageName)
                                    .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent()
                .done())
        .deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }
}
