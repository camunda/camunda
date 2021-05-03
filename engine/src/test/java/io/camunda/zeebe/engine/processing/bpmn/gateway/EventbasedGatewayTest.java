/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class EventbasedGatewayTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final BpmnModelInstance PROCESS_WITH_TIMERS =
      Bpmn.createExecutableProcess("PROCESS_WITH_TIMERS")
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent("timer-1", c -> c.timerWithDuration("PT0.1S"))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent("timer-2", c -> c.timerWithDuration("PT10S"))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();
  private static final BpmnModelInstance PROCESS_WITH_EQUAL_TIMERS =
      Bpmn.createExecutableProcess("PROCESS_WITH_EQUAL_TIMERS")
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent("timer-1", c -> c.timerWithDuration("PT2S"))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent("timer-2", c -> c.timerWithDuration("PT2S"))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();
  private static final BpmnModelInstance PROCESS_WITH_MESSAGES =
      Bpmn.createExecutableProcess("PROCESS_WITH_MESSAGES")
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent(
              "message-1",
              c -> c.message(m -> m.name("msg-1").zeebeCorrelationKeyExpression("key")))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent(
              "message-2",
              c -> c.message(m -> m.name("msg-2").zeebeCorrelationKeyExpression("key")))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();
  private static final BpmnModelInstance PROCESS_WITH_TIMER_AND_MESSAGE =
      Bpmn.createExecutableProcess("PROCESS_WITH_TIMER_AND_MESSAGE")
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent(
              "message", c -> c.message(m -> m.name("msg").zeebeCorrelationKeyExpression("key")))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {
    ENGINE.deployment().withXmlResource(PROCESS_WITH_TIMERS).deploy();
    ENGINE.deployment().withXmlResource(PROCESS_WITH_EQUAL_TIMERS).deploy();
    ENGINE.deployment().withXmlResource(PROCESS_WITH_MESSAGES).deploy();
    ENGINE.deployment().withXmlResource(PROCESS_WITH_TIMER_AND_MESSAGE).deploy();
  }

  @Test
  public void testLifecycle() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS_WITH_TIMERS")
            .withVariable("key", "testLifecycle")
            .create();

    // when
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2)
                .exists())
        .isTrue();
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(
                    r -> r.getValue().getBpmnElementType() == BpmnElementType.EVENT_BASED_GATEWAY)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getElementId(), r.getIntent()))
        .containsSequence(
            tuple("gateway", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("gateway", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("gateway", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("gateway", ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple("gateway", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("gateway", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("timer-1", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("timer-1", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("timer-1", ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple("timer-1", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("timer-1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("to-end1", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN));
  }

  @Test
  public void shouldCreateTimer() {
    // given

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_WITH_TIMERS").create();

    // then
    final Record<ProcessInstanceRecordValue> gatewayEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.EVENT_BASED_GATEWAY)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final List<Record<TimerRecordValue>> timerEvents =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .asList();

    assertThat(timerEvents)
        .hasSize(2)
        .extracting(
            r -> tuple(r.getValue().getTargetElementId(), r.getValue().getElementInstanceKey()))
        .contains(tuple("timer-1", gatewayEvent.getKey()), tuple("timer-2", gatewayEvent.getKey()));
  }

  @Test
  public void shouldOpenProcessMessageSubscriptions() {
    // given

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS_WITH_MESSAGES")
            .withVariable("key", "shouldOpenProcessMessageSubscriptions")
            .create();

    // then
    final Record<ProcessInstanceRecordValue> gatewayEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.EVENT_BASED_GATEWAY)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final List<Record<ProcessMessageSubscriptionRecordValue>> subscriptionEvents =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .asList();

    assertThat(subscriptionEvents)
        .hasSize(2)
        .extracting(r -> tuple(r.getValue().getMessageName(), r.getValue().getElementInstanceKey()))
        .contains(tuple("msg-1", gatewayEvent.getKey()), tuple("msg-2", gatewayEvent.getKey()));
  }

  @Test
  public void shouldContinueWhenTimerIsTriggered() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS_WITH_TIMERS")
            .withVariable("key", "shouldContinueWhenTimerIsTriggered")
            .create();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2)
                .exists())
        .isTrue();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    final List<Record<ProcessInstanceRecordValue>> records =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitToProcessInstanceCompleted()
            .asList();

    assertThat(records)
        .extracting(Record::getIntent, r -> r.getValue().getElementId())
        .containsSubsequence(
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATING, "timer-1"),
            tuple(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN, "to-end1"),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, "PROCESS_WITH_TIMERS"));
  }

  @Test
  public void shouldOnlyExecuteOneBranchWithEqualTimers() {
    // given
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_WITH_EQUAL_TIMERS").create();

    // when
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).count()).isEqualTo(2);
    ENGINE.increaseTime(Duration.ofSeconds(2));

    // then
    final List<String> timers =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .map(r -> r.getValue().getTargetElementId())
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withHandlerNodeId(timers.get(0))
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();

    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGER)
                .withHandlerNodeId(timers.get(1))
                .withProcessInstanceKey(processInstanceKey)
                .onlyCommandRejections()
                .getFirst())
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRecordType(RecordType.COMMAND_REJECTION);
  }

  @Test
  public void shouldContinueWhenMessageIsCorrelated() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS_WITH_MESSAGES")
            .withVariable("key", "shouldContinueWhenMessageIsCorrelated")
            .create();

    // when
    ENGINE
        .message()
        .withName("msg-1")
        .withCorrelationKey("shouldContinueWhenMessageIsCorrelated")
        .publish();

    // then
    final List<Record<ProcessInstanceRecordValue>> records =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitToProcessInstanceCompleted()
            .asList();

    assertThat(records)
        .extracting(r -> r.getIntent(), r -> r.getValue().getElementId())
        .containsSubsequence(
            tuple(ProcessInstanceIntent.ELEMENT_ACTIVATING, "message-1"),
            tuple(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN, "to-end1"),
            tuple(ProcessInstanceIntent.ELEMENT_COMPLETED, "PROCESS_WITH_MESSAGES"));
  }

  @Test
  public void shouldCancelTimer() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS_WITH_TIMERS")
            .withVariable("key", "shouldCancelTimer")
            .create();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2)
                .exists())
        .isTrue();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withHandlerNodeId("timer-2")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCloseProcessMessageSubscription() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS_WITH_MESSAGES")
            .withVariable("key", "shouldCloseProcessMessageSubscription")
            .create();

    // when
    ENGINE
        .message()
        .withName("msg-1")
        .withCorrelationKey("shouldCloseProcessMessageSubscription")
        .publish();

    // then
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withMessageName("msg-2")
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCancelSubscriptionsWhenScopeIsTerminated() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS_WITH_TIMER_AND_MESSAGE")
            .withVariable("key", "shouldCancelSubscriptionsWhenScopeIsTerminated")
            .create();
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();
    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .exists())
        .isTrue();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withHandlerNodeId("timer")
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("msg")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldOnlyExecuteOneBranchWithSimultaneousMessages() {
    // given
    // when
    ENGINE
        .message()
        .withCorrelationKey("shouldOnlyExecuteOneBranchWithSimultaneousMessages")
        .withName("msg-1")
        .publish();
    ENGINE
        .message()
        .withCorrelationKey("shouldOnlyExecuteOneBranchWithSimultaneousMessages")
        .withName("msg-2")
        .publish();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS_WITH_MESSAGES")
            .withVariable("key", "shouldOnlyExecuteOneBranchWithSimultaneousMessages")
            .create();

    final List<String> messageNames =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CORRELATE)
            .withProcessInstanceKey(processInstanceKey)
            .limit(2)
            .map(r -> r.getValue().getMessageName())
            .collect(Collectors.toList());

    assertThat(messageNames).hasSize(2);

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withMessageName(messageNames.get(0))
                .exists())
        .isTrue();

    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATE)
                .withMessageName(messageNames.get(1))
                .onlyCommandRejections()
                .getFirst())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_STATE);
  }
}
