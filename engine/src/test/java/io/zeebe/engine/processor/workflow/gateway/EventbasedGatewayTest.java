/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.TimerRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class EventbasedGatewayTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final BpmnModelInstance WORKFLOW_WITH_TIMERS =
      Bpmn.createExecutableProcess("WORKFLOW_WITH_TIMERS")
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
  private static final BpmnModelInstance WORKFLOW_WITH_EQUAL_TIMERS =
      Bpmn.createExecutableProcess("WORKFLOW_WITH_EQUAL_TIMERS")
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
  private static final BpmnModelInstance WORKFLOW_WITH_MESSAGES =
      Bpmn.createExecutableProcess("WORKFLOW_WITH_MESSAGES")
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent(
              "message-1", c -> c.message(m -> m.name("msg-1").zeebeCorrelationKey("key")))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent(
              "message-2", c -> c.message(m -> m.name("msg-2").zeebeCorrelationKey("key")))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();
  private static final BpmnModelInstance WORKFLOW_WITH_TIMER_AND_MESSAGE =
      Bpmn.createExecutableProcess("WORKFLOW_WITH_TIMER_AND_MESSAGE")
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent(
              "message", c -> c.message(m -> m.name("msg").zeebeCorrelationKey("key")))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {
    ENGINE.deployment().withXmlResource(WORKFLOW_WITH_TIMERS).deploy();
    ENGINE.deployment().withXmlResource(WORKFLOW_WITH_EQUAL_TIMERS).deploy();
    ENGINE.deployment().withXmlResource(WORKFLOW_WITH_MESSAGES).deploy();
    ENGINE.deployment().withXmlResource(WORKFLOW_WITH_TIMER_AND_MESSAGE).deploy();
  }

  @Test
  public void testLifecycle() {
    // given
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("WORKFLOW_WITH_TIMERS")
            .withVariable("key", "testLifecycle")
            .create();

    // when
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2)
                .exists())
        .isTrue();
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .skipUntil(
                    r ->
                        r.getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED
                            && r.getValue().getBpmnElementType()
                                == BpmnElementType.EVENT_BASED_GATEWAY)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getElementId(), r.getIntent()))
        .containsExactly(
            tuple("gateway", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("gateway", WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple("gateway", WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple("gateway", WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple("timer-1", WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple("timer-1", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("timer-1", WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple("timer-1", WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple("to-end1", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("end1", WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple("end1", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("end1", WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple("end1", WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple("WORKFLOW_WITH_TIMERS", WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple("WORKFLOW_WITH_TIMERS", WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCreateTimer() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW_WITH_TIMERS").create();

    // then
    final Record<WorkflowInstanceRecordValue> gatewayEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.EVENT_BASED_GATEWAY)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final List<Record<TimerRecordValue>> timerEvents =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limit(2)
            .asList();

    assertThat(timerEvents)
        .hasSize(2)
        .extracting(
            r -> tuple(r.getValue().getTargetElementId(), r.getValue().getElementInstanceKey()))
        .contains(tuple("timer-1", gatewayEvent.getKey()), tuple("timer-2", gatewayEvent.getKey()));
  }

  @Test
  public void shouldOpenWorkflowInstanceSubscriptions() {
    // given

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("WORKFLOW_WITH_MESSAGES")
            .withVariable("key", "shouldOpenWorkflowInstanceSubscriptions")
            .create();

    // then
    final Record<WorkflowInstanceRecordValue> gatewayEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.EVENT_BASED_GATEWAY)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final List<Record<WorkflowInstanceSubscriptionRecordValue>> subscriptionEvents =
        RecordingExporter.workflowInstanceSubscriptionRecords(
                WorkflowInstanceSubscriptionIntent.OPENED)
            .withWorkflowInstanceKey(workflowInstanceKey)
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
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("WORKFLOW_WITH_TIMERS")
            .withVariable("key", "shouldContinueWhenTimerIsTriggered")
            .create();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2)
                .exists())
        .isTrue();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .asList();

    assertThat(records)
        .extracting(Record::getIntent, r -> r.getValue().getElementId())
        .containsSubsequence(
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "timer-1"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "to-end1"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "WORKFLOW_WITH_TIMERS"));
  }

  @Test
  public void shouldOnlyExecuteOneBranchWithEqualTimers() {
    // given
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW_WITH_EQUAL_TIMERS").create();

    // when
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).count()).isEqualTo(2);
    ENGINE.increaseTime(Duration.ofSeconds(2));

    // then
    final List<String> timers =
        RecordingExporter.timerRecords(TimerIntent.CREATE)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limit(2)
            .map(r -> r.getValue().getTargetElementId())
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withHandlerNodeId(timers.get(0))
                .withWorkflowInstanceKey(workflowInstanceKey)
                .exists())
        .isTrue();

    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGER)
                .withHandlerNodeId(timers.get(1))
                .withWorkflowInstanceKey(workflowInstanceKey)
                .onlyCommandRejections()
                .getFirst())
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRecordType(RecordType.COMMAND_REJECTION);
  }

  @Test
  public void shouldContinueWhenMessageIsCorrelated() {
    // given
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("WORKFLOW_WITH_MESSAGES")
            .withVariable("key", "shouldContinueWhenMessageIsCorrelated")
            .create();

    // when
    ENGINE
        .message()
        .withName("msg-1")
        .withCorrelationKey("shouldContinueWhenMessageIsCorrelated")
        .publish();

    // then
    final List<Record<WorkflowInstanceRecordValue>> records =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .asList();

    assertThat(records)
        .extracting(r -> r.getIntent(), r -> r.getValue().getElementId())
        .containsSubsequence(
            tuple(WorkflowInstanceIntent.ELEMENT_ACTIVATING, "message-1"),
            tuple(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, "to-end1"),
            tuple(WorkflowInstanceIntent.ELEMENT_COMPLETED, "WORKFLOW_WITH_MESSAGES"));
  }

  @Test
  public void shouldCancelTimer() {
    // given
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("WORKFLOW_WITH_TIMERS")
            .withVariable("key", "shouldCancelTimer")
            .create();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2)
                .exists())
        .isTrue();

    // when
    ENGINE.increaseTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withHandlerNodeId("timer-2")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCloseWorkflowInstanceSubscription() {
    // given
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("WORKFLOW_WITH_MESSAGES")
            .withVariable("key", "shouldCloseWorkflowInstanceSubscription")
            .create();

    // when
    ENGINE
        .message()
        .withName("msg-1")
        .withCorrelationKey("shouldCloseWorkflowInstanceSubscription")
        .publish();

    // then
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CLOSED)
                .withMessageName("msg-2")
                .withWorkflowInstanceKey(workflowInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCancelSubscriptionsWhenScopeIsTerminated() {
    // given
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("WORKFLOW_WITH_TIMER_AND_MESSAGE")
            .withVariable("key", "shouldCancelSubscriptionsWhenScopeIsTerminated")
            .create();
    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withHandlerNodeId("timer")
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CLOSED)
                .withWorkflowInstanceKey(workflowInstanceKey)
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
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("WORKFLOW_WITH_MESSAGES")
            .withVariable("key", "shouldOnlyExecuteOneBranchWithSimultaneousMessages")
            .create();

    final List<String> messageNames =
        RecordingExporter.workflowInstanceSubscriptionRecords(
                WorkflowInstanceSubscriptionIntent.CORRELATE)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limit(2)
            .map(r -> r.getValue().getMessageName())
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CORRELATED)
                .withMessageName(messageNames.get(0))
                .exists())
        .isTrue();

    Assertions.assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CORRELATE)
                .withMessageName(messageNames.get(1))
                .onlyCommandRejections()
                .getFirst())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_STATE);
  }
}
