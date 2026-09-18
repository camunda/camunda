/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultActivateElement;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins the storage ordinal assignment end to end: with archiverless mode enabled the engine selects
 * the fixed provider, stamps the configured ordinal on the root process instance at creation, and
 * all follow-up records of the instance inherit that ordinal.
 */
public final class StorageOrdinalAssignmentTest {

  private static final int FIXED_ORDINAL = 1234;

  /**
   * Runs with a single command per batch so every follow-up command reaches the log before it is
   * processed. With the default batching, a follow-up command is processed in the same batch from
   * the live record object, so a processor that re-stamps the ordinal (e.g. the ad-hoc sub-process
   * ACTIVATE/COMPLETE processors) would already be visible on the logged command and hide a missing
   * assignment in the producing processor.
   */
  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .maxCommandsInBatch(1)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL));

  @Test
  public void shouldAssignConfiguredOrdinalToProcessInstanceRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("pi-records-process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("pi-records-process").create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToInstanceStartedByMessageStartEvent() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("message-start-process")
                .startEvent()
                .message("ordinal-start-message")
                .endEvent()
                .done())
        .deploy();

    // when
    engine.message().withName("ordinal-start-message").withCorrelationKey("").publish();

    // then
    final long processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId("message-start-process")
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);

    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withInstanceKey(processInstanceKey)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);

    assertProcessEventTriggeringCarriesOrdinal(processInstanceKey);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToInstanceStartedByTimerStartEvent() {
    // given
    final var deployedProcess =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("timer-start-process")
                    .startEvent()
                    .timerWithDuration("PT10S")
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessDefinitionKey(deployedProcess.getProcessDefinitionKey())
        .getFirst();

    // when
    engine.increaseTime(Duration.ofSeconds(11));

    // then
    final long processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId("timer-start-process")
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);

    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withInstanceKey(processInstanceKey)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);

    assertProcessEventTriggeringCarriesOrdinal(processInstanceKey);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToInstanceStartedBySignalStartEvent() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("signal-start-process")
                .startEvent()
                .signal("ordinal-start-signal")
                .endEvent()
                .done())
        .deploy();

    // when
    engine.signal().withSignalName("ordinal-start-signal").broadcast();

    // then
    final long processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId("signal-start-process")
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);

    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withInstanceKey(processInstanceKey)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);

    assertProcessEventTriggeringCarriesOrdinal(processInstanceKey);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToInstanceStartedByConditionalStartEvent() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("conditional-start-process")
                .startEvent("conditional-start")
                .condition(c -> c.condition("=x > 10"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine.conditionalEvaluation().withVariable("x", 11).evaluate();

    // then
    final long processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId("conditional-start-process")
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);

    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withInstanceKey(processInstanceKey)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);

    assertProcessEventTriggeringCarriesOrdinal(processInstanceKey);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToVariableRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("variable-records-process").startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("variable-records-process")
            .withVariable("ordinalVariable", 1)
            .create();

    // then
    final var variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("ordinalVariable")
            .getFirst();
    assertThat(variableCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToJobRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("job-records-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-job"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("job-records-process").create();

    // then
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(jobCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToUserTaskRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("user-task-records-process")
                .startEvent()
                .userTask("user-task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("user-task-records-process").create();

    // then
    final var userTaskCreated =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(userTaskCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToUserTaskLifecycleAndListenerRecords() {
    // given: a user task with a completing task listener, so completion runs through the
    // intermediate-state path and creates a listener job
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("user-task-lifecycle-process")
                .startEvent()
                .userTask("user-task")
                .zeebeUserTask()
                .zeebeTaskListener(l -> l.completing().type("ordinal-task-listener"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("user-task-lifecycle-process").create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue()
            .getUserTaskKey();

    // when
    engine.userTask().withKey(userTaskKey).complete();
    engine.job().ofInstance(processInstanceKey).withType("ordinal-task-listener").complete();

    // then: the task listener job carries the ordinal
    final var listenerJobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("ordinal-task-listener")
            .getFirst();
    assertThat(listenerJobCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    // and: every user task lifecycle record up to COMPLETED carries the ordinal
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.COMPLETED))
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToSignalSubscriptionRecords() {
    // given: an instance waiting at an intermediate signal catch event
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("signal-subscription-process")
                .startEvent()
                .intermediateCatchEvent("signal-catch", c -> c.signal("ordinal-signal"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("signal-subscription-process").create();

    // then: the subscription opened for the catch event carries the ordinal
    final var subscriptionCreated =
        RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
            .withSignalName("ordinal-signal")
            .getFirst();
    assertThat(subscriptionCreated.getValue().getProcessInstanceKey())
        .isEqualTo(processInstanceKey);
    assertThat(subscriptionCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    // when: the signal is broadcast
    engine.signal().withSignalName("ordinal-signal").broadcast();

    // then: the DELETED event, appended from the stored subscription, inherits the ordinal
    final var subscriptionDeleted =
        RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
            .withSignalName("ordinal-signal")
            .getFirst();
    assertThat(subscriptionDeleted.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToMessageSubscriptionRecords() {
    // given: an instance waiting at an intermediate message catch event
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("message-subscription-process")
                .startEvent()
                .intermediateCatchEvent("message-catch")
                .message(m -> m.name("ordinal-catch-message").zeebeCorrelationKeyExpression("key"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("message-subscription-process")
            .withVariable("key", "correlation-key-2")
            .create();

    // then: both the process-side and the message-partition-side subscriptions carry the ordinal
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);

    // when: the message is correlated
    engine
        .message()
        .withName("ordinal-catch-message")
        .withCorrelationKey("correlation-key-2")
        .publish();

    // then: every subscription EVENT up to correlation carries the ordinal on both sides; the
    // inter-partition CREATE/CORRELATE commands are excluded, commands are not exported
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("ordinal-catch-message")
                .limit(r -> r.getIntent() == ProcessMessageSubscriptionIntent.CORRELATED)
                .filter(r -> r.getRecordType() == RecordType.EVENT)
                .asList())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
    assertThat(
            RecordingExporter.messageSubscriptionRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("ordinal-catch-message")
                .limit(r -> r.getIntent() == MessageSubscriptionIntent.CORRELATED)
                .filter(r -> r.getRecordType() == RecordType.EVENT)
                .asList())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToConditionalSubscriptionRecords() {
    // given: a conditional boundary event whose condition is already fulfilled at subscription
    // time, so the subscription is created and immediately triggered
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("conditional-subscription-process")
                .startEvent()
                .userTask("wait-task")
                .zeebeUserTask()
                .boundaryEvent("conditional-boundary")
                .cancelActivity(true)
                .condition(c -> c.condition("=x > 10").zeebeVariableEvents("create"))
                .endEvent()
                .moveToActivity("wait-task")
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("conditional-subscription-process")
            .withVariable("x", 11)
            .create();

    // then: the subscription CREATED event carries the ordinal, and the TRIGGERED event, appended
    // from the TRIGGER command built off the subscription record, inherits it
    final var subscriptionCreated =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(subscriptionCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    final var subscriptionTriggered =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.TRIGGERED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(subscriptionTriggered.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToSignalTriggeredEventSubProcessRecords() {
    // given: an instance waiting in a user task, with an interrupting event sub-process started by
    // a signal
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("signal-event-sub-process")
                .eventSubProcess(
                    "signal-event-sub",
                    s ->
                        s.startEvent("signal-event-start")
                            .interrupting(true)
                            .signal("ordinal-event-sub-signal")
                            .endEvent())
                .startEvent()
                .userTask("wait-task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("signal-event-sub-process").create();
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName("ordinal-event-sub-signal")
        .getFirst();

    // when
    engine.signal().withSignalName("ordinal-event-sub-signal").broadcast();

    // then: the event sub-process records, the interrupted task and the completing process all
    // carry the ordinal
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("signal-event-sub")
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);

    assertProcessEventTriggeringCarriesOrdinal(processInstanceKey);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToConditionalTriggeredEventSubProcessRecords() {
    // given: an interrupting event sub-process whose conditional start is already fulfilled when
    // the instance is created, so it triggers as soon as the subscription opens
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("conditional-event-sub-process")
                .eventSubProcess(
                    "conditional-event-sub",
                    s ->
                        s.startEvent("conditional-event-start")
                            .interrupting(true)
                            .condition(c -> c.condition("=x > 10"))
                            .endEvent())
                .startEvent()
                .userTask("wait-task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("conditional-event-sub-process")
            .withVariable("x", 11)
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("conditional-event-sub")
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);

    assertProcessEventTriggeringCarriesOrdinal(processInstanceKey);
  }

  @Test
  public void shouldKeepDeploymentScopedStartEventSubscriptionsOnMainIndex() {
    // given: a definition with a signal start event and a conditional start event
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("start-subscriptions-process")
                    .startEvent("signal-start")
                    .signal("ordinal-definition-signal")
                    .endEvent()
                    .moveToProcess("start-subscriptions-process")
                    .startEvent("conditional-start")
                    .condition(c -> c.condition("=x > 10"))
                    .endEvent()
                    .done())
            .deploy();
    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // then: the start-event subscriptions opened for the definition carry no instance ordinal
    final var signalStartSubscription =
        RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .withCatchEventId("signal-start")
            .getFirst()
            .getValue();
    assertThat(signalStartSubscription.getCatchEventInstanceKey()).isEqualTo(-1L);
    assertThat(signalStartSubscription.getStorageOrdinal()).isZero();

    final var conditionalStartSubscription =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .withCatchEventId("conditional-start")
            .getFirst()
            .getValue();
    assertThat(conditionalStartSubscription.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(conditionalStartSubscription.getStorageOrdinal()).isZero();

    // when: a new version replaces the definition, the old subscriptions are closed
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("start-subscriptions-process")
                .startEvent()
                .endEvent()
                .done())
        .deploy();

    // then: the DELETED events, appended from the stored subscriptions, keep ordinal 0 as well
    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isZero();
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.DELETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isZero();
  }

  @Test
  public void shouldAssignConfiguredOrdinalToProcessEventRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-event-records-process")
                .startEvent()
                .intermediateCatchEvent("catch-event")
                .message(m -> m.name("ordinal-message").zeebeCorrelationKeyExpression("key"))
                .endEvent()
                .done())
        .deploy();
    engine
        .processInstance()
        .ofBpmnProcessId("process-event-records-process")
        .withVariable("key", "correlation-key-1")
        .create();

    // when
    engine.message().withName("ordinal-message").withCorrelationKey("correlation-key-1").publish();

    // then
    final var records =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValueType() == ValueType.PROCESS_EVENT
                        && r.getIntent() == ProcessEventIntent.TRIGGERING)
            .asList();
    final var processEventTriggering = records.get(records.size() - 1);
    assertThat(((ProcessEventRecordValue) processEventTriggering.getValue()).getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToProcessEventTriggeredByJobCompletion() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("job-completion-event-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-completing-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("job-completion-event-process").create();

    // when
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-completing-job")
        .withVariable("result", "done")
        .complete();

    // then
    assertProcessEventTriggeringCarriesOrdinal(processInstanceKey);
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToProcessEventTriggeredByUserTaskCompletion() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("user-task-completion-event-process")
                .startEvent()
                .userTask("user-task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("user-task-completion-event-process").create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue()
            .getUserTaskKey();

    // when
    engine.userTask().withKey(userTaskKey).withVariable("result", "done").complete();

    // then
    assertProcessEventTriggeringCarriesOrdinal(processInstanceKey);
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToNonInterruptingBoundaryEventRecords() {
    // given: a user task with a non-interrupting signal boundary event, so the trigger activates
    // the boundary event next to the still-running task
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("non-interrupting-boundary-process")
                .startEvent()
                .userTask("wait-task")
                .zeebeUserTask()
                .boundaryEvent("signal-boundary")
                .cancelActivity(false)
                .signal("ordinal-boundary-signal")
                .endEvent("boundary-end")
                .moveToActivity("wait-task")
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("non-interrupting-boundary-process").create();
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName("ordinal-boundary-signal")
        .await();

    // when
    engine.signal().withSignalName("ordinal-boundary-signal").broadcast();

    // then
    assertProcessEventTriggeringCarriesOrdinal(processInstanceKey);
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("boundary-end")
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(
                    r ->
                        "boundary-end".equals(r.getValue().getElementId())
                            && r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED))
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToCaughtErrorEventRecords() {
    // given: a service task with an error boundary event
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("caught-error-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-erroring-job"))
                .boundaryEvent("error-boundary", b -> b.error("ordinal-error"))
                .endEvent("error-end")
                .moveToActivity("service-task")
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("caught-error-process").create();

    // when
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-erroring-job")
        .withErrorCode("ordinal-error")
        .throwError();

    // then
    assertProcessEventTriggeringCarriesOrdinal(processInstanceKey);
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToTimerCatchEventRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("timer-catch-process")
                .startEvent()
                .intermediateCatchEvent("timer-catch", c -> c.timerWithDuration("PT10S"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("timer-catch-process").create();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    engine.increaseTime(Duration.ofSeconds(11));

    // then
    assertProcessEventTriggeringCarriesOrdinal(processInstanceKey);
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToProcessInstanceBatchRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("batch-records-process")
                .startEvent()
                .serviceTask(
                    "multi-instance-task",
                    t ->
                        t.zeebeJobType("ordinal-multi-instance")
                            .multiInstance(
                                m ->
                                    m.parallel()
                                        .zeebeInputCollectionExpression("items")
                                        .zeebeInputElement("item")))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("batch-records-process")
            .withVariable("items", List.of(1, 2, 3))
            .create();

    // then
    final var batchActivate =
        RecordingExporter.processInstanceBatchRecords(ProcessInstanceBatchIntent.ACTIVATE)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(batchActivate.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToDecisionEvaluationRecords() {
    // given
    engine
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .withXmlResource(
            Bpmn.createExecutableProcess("decision-records-process")
                .startEvent()
                .businessRuleTask(
                    "business-rule-task",
                    t -> t.zeebeCalledDecisionId("jedi_or_sith").zeebeResultVariable("result"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("decision-records-process")
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    final var decisionEvaluated =
        RecordingExporter.decisionEvaluationRecords()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(decisionEvaluated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToIncidentRecordsOfAJobWithoutRetries() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("job-fail-incident-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-failing-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("job-fail-incident-process").create();
    engine.jobs().withType("ordinal-failing-job").withMaxJobsToActivate(1).activate();

    // when
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-failing-job")
        .withRetries(0)
        .fail();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incidentCreated.getValue().getErrorType()).isEqualTo(ErrorType.JOB_NO_RETRIES);
    assertThat(incidentCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToResolvedIncidentRecords() {
    // given: an incident raised for a job without retries
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("incident-resolve-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-resolvable-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("incident-resolve-process").create();
    engine.jobs().withType("ordinal-resolvable-job").withMaxJobsToActivate(1).activate();
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-resolvable-job")
        .withRetries(0)
        .fail();
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-resolvable-job")
        .withRetries(1)
        .updateRetries();
    engine.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then: the RESOLVED event carries the ordinal of the incident stored in state
    final var incidentResolved =
        RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incidentResolved.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToIncidentRecordsOfAnUncaughtJobError() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("job-error-incident-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-error-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("job-error-incident-process").create();

    // when - the thrown error has no matching catch event
    engine
        .job()
        .ofInstance(processInstanceKey)
        .withType("ordinal-error-job")
        .withErrorCode("uncaught-error")
        .throwError();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incidentCreated.getValue().getErrorType())
        .isEqualTo(ErrorType.UNHANDLED_ERROR_EVENT);
    assertThat(incidentCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToIncidentRecordsOfAFailedSecretResolution() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("secret-incident-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("ordinal-secret-job"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("secret-incident-process").create();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    final var resolutionRequested =
        new SecretReferenceRecord()
            .setStoreId("ordinal-store")
            .setSecretReference("ordinal-secret");
    resolutionRequested.addJobKey(jobKey);
    final var batchCreateIncidents =
        new SecretReferenceRecord()
            .setStoreId("ordinal-store")
            .setSecretReference("ordinal-secret");
    batchCreateIncidents.addJobKey(jobKey);

    // when - the job is seeded as waiting on the secret reference and the drain is processed
    engine.stop();
    engine.writeRecords(
        RecordToWrite.event()
            .secretReference(SecretReferenceIntent.RESOLUTION_REQUESTED, resolutionRequested),
        RecordToWrite.command()
            .secretReference(SecretReferenceIntent.BATCH_CREATE_INCIDENTS, batchCreateIncidents));
    // starting the engine re-exports the whole partition log, so clear the previously seen records
    RecordingExporter.reset();
    engine.start();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withErrorType(ErrorType.SECRET_RESOLUTION_ERROR)
            .withJobKey(jobKey)
            .getFirst();
    assertThat(incidentCreated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToAdHocSubProcessInstructionActivatedRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("ahsp-activate-process")
                .startEvent()
                .adHocSubProcess("ad-hoc", adHocSubProcess -> adHocSubProcess.task("A"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("ahsp-activate-process").create();
    final long adHocSubProcessInstanceKey = adHocSubProcessInstanceKeyOf(processInstanceKey);

    // when - the command arrives from outside the engine, carrying no ordinal of its own
    engine
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
        .withElementIds("A")
        .activate();

    // then
    final var activated =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.ACTIVATED)
            .getFirst();
    assertThat(activated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToAdHocSubProcessInstructionCompletedRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("ahsp-complete-process")
                .startEvent()
                .adHocSubProcess("ad-hoc", adHocSubProcess -> adHocSubProcess.task("A"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("ahsp-complete-process").create();
    final long adHocSubProcessInstanceKey = adHocSubProcessInstanceKeyOf(processInstanceKey);

    // when - the command arrives from outside the engine, carrying no ordinal of its own
    engine.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessInstruction(
                AdHocSubProcessInstructionIntent.COMPLETE,
                new AdHocSubProcessInstructionRecord()
                    .setAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)));

    // then
    final var completed =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.COMPLETED)
            .getFirst();
    assertThat(completed.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToJobDrivenAdHocSubProcessInstructionRecords() {
    // given
    final var jobType = "ahsp-job-type";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("ahsp-job-process")
                .startEvent()
                .adHocSubProcess("ad-hoc", adHocSubProcess -> adHocSubProcess.task("A"))
                .zeebeJobType(jobType)
                .endEvent()
                .done())
        .deploy();
    engine.processInstance().ofBpmnProcessId("ahsp-job-process").create();

    // when - the ad-hoc sub-process job worker activates an element
    completeAdHocSubProcessJob(jobType, 1, false, new JobResultActivateElement().setElementId("A"));

    // then - the job-emitted command itself must carry the ordinal, not only the event
    final var activateCommand =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.ACTIVATE)
            .onlyCommands()
            .getFirst();
    assertThat(activateCommand.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
    final var activated =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.ACTIVATED)
            .getFirst();
    assertThat(activated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    // when - the job created after A completed reports the completion condition as fulfilled
    completeAdHocSubProcessJob(jobType, 2, true);

    // then
    final var completeCommand =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.COMPLETE)
            .onlyCommands()
            .getFirst();
    assertThat(completeCommand.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
    final var completed =
        RecordingExporter.adHocSubProcessInstructionRecords()
            .withIntent(AdHocSubProcessInstructionIntent.COMPLETED)
            .getFirst();
    assertThat(completed.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  private void assertProcessEventTriggeringCarriesOrdinal(final long processInstanceKey) {
    final var records =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValueType() == ValueType.PROCESS_EVENT
                        && r.getIntent() == ProcessEventIntent.TRIGGERING
                        && ((ProcessEventRecordValue) r.getValue()).getProcessInstanceKey()
                            == processInstanceKey)
            .asList();
    final var processEventTriggering = records.get(records.size() - 1);
    assertThat(((ProcessEventRecordValue) processEventTriggering.getValue()).getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);
  }

  private long adHocSubProcessInstanceKeyOf(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .getFirst()
        .getKey();
  }

  private void completeAdHocSubProcessJob(
      final String jobType,
      final long jobCounter,
      final boolean completionConditionFulfilled,
      final JobResultActivateElement... activateElements) {
    // follow-up commands reach the log one at a time, so wait for the job before completing it
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType(jobType)
            .skip(jobCounter - 1)
            .getFirst()
            .getKey();
    final var jobResult =
        new JobResult()
            .setActivateElements(List.of(activateElements))
            .setCompletionConditionFulfilled(completionConditionFulfilled);
    engine.job().withKey(jobKey).withResult(jobResult).complete();
  }
}
