/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processing.message.MessageObserver;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;

public final class WorkflowInstanceStreamProcessorTest {

  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance SERVICE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .sequenceFlowId("flow1")
          .serviceTask("task", b -> b.zeebeJobType("taskType"))
          .sequenceFlowId("flow2")
          .endEvent("end")
          .done();
  private static final BpmnModelInstance SUB_PROCESS_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .subProcess("subProcess")
          .embeddedSubProcess()
          .startEvent()
          .serviceTask("task", b -> b.zeebeJobType("taskType"))
          .endEvent()
          .subProcessDone()
          .endEvent()
          .done();
  private static final BpmnModelInstance MESSAGE_CATCH_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent(
              "catch-event",
              c ->
                  c.message(m -> m.name("order canceled").zeebeCorrelationKeyExpression("orderId")))
          .done();
  private static final BpmnModelInstance TIMER_BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task1", b -> b.zeebeJobType("type"))
          .boundaryEvent("timer1")
          .cancelActivity(true)
          .timerWithDuration("PT1S")
          .endEvent("timer1End")
          .moveToActivity("task1")
          .endEvent("end")
          .done();
  @Rule public Timeout timeoutRule = new Timeout(2, TimeUnit.MINUTES);
  private final StreamProcessorRule envRule = new StreamProcessorRule();
  private final WorkflowInstanceStreamProcessorRule streamProcessorRule =
      new WorkflowInstanceStreamProcessorRule(envRule);
  @Rule public RuleChain chain = RuleChain.outerRule(envRule).around(streamProcessorRule);

  @Test
  public void shouldRejectCancellationInDirectSuccession() {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);

    final Record<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));
    waitUntil(() -> envRule.events().onlyJobRecords().withIntent(JobIntent.CREATE).exists());

    // when
    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());

    // then
    streamProcessorRule.awaitElementInState("process", WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<WorkflowInstanceIntent> workflowInstanceLifecycle =
        envRule.events().onlyStatesOf("process").collect(Collectors.toList());

    LifecycleAssert.assertThat(workflowInstanceLifecycle).compliesWithCompleteLifecycle();

    final Record<WorkflowInstanceRecord> rejection =
        envRule.events().onlyWorkflowInstanceRecords().onlyRejections().findFirst().get();

    Assertions.assertThat(rejection)
        .hasIntent(WorkflowInstanceIntent.CANCEL)
        .hasRejectionReason(
            "Expected to cancel a workflow instance with key '"
                + createdEvent.getKey()
                + "', but no such workflow was found");
  }

  @Test
  public void shouldCancelActivityInStateReady() {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);

    final Record<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));
    waitUntil(
        () ->
            envRule
                .events()
                .onlyWorkflowInstanceRecords()
                .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .filter(
                    w -> w.getValue().getElementIdBuffer().equals(BufferUtil.wrapString("start")))
                .exists());

    // when
    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());

    // then
    streamProcessorRule.awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<Record<WorkflowInstanceRecord>> records =
        envRule.events().onlyWorkflowInstanceRecords().collect(Collectors.toList());

    final List<WorkflowInstanceIntent> workflowInstanceLifecycle =
        envRule.events().onlyStatesOf("process").collect(Collectors.toList());

    final List<WorkflowInstanceIntent> taskLifecycle =
        envRule.events().onlyStatesOf("task").collect(Collectors.toList());

    LifecycleAssert.assertThat(workflowInstanceLifecycle)
        .compliesWithCompleteLifecycle()
        .endsWith(WorkflowInstanceIntent.ELEMENT_TERMINATED);
    LifecycleAssert.assertThat(taskLifecycle).compliesWithCompleteLifecycle();

    WorkflowInstanceAssert.assertThat(records)
        .doesNotEvaluateFlowAfterTerminatingElement("process");
  }

  @Test
  public void shouldCancelAndCompleteJobConcurrentlyIbProcess() {
    // given
    streamProcessorRule.deploy(SUB_PROCESS_WORKFLOW);
    final Record<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));
    waitUntil(() -> envRule.events().onlyJobRecords().withIntent(JobIntent.CREATE).exists());

    // when
    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    streamProcessorRule.completeFirstJob();

    // then
    streamProcessorRule.awaitElementInState("process", WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<WorkflowInstanceIntent> workflowInstanceLifecycle =
        envRule.events().onlyStatesOf("process").collect(Collectors.toList());
    final List<WorkflowInstanceIntent> subProcessLifecycle =
        envRule.events().onlyStatesOf("subProcess").collect(Collectors.toList());
    final List<WorkflowInstanceIntent> taskLifecycle =
        envRule.events().onlyStatesOf("task").collect(Collectors.toList());

    LifecycleAssert.assertThat(workflowInstanceLifecycle)
        .compliesWithCompleteLifecycle()
        .endsWith(WorkflowInstanceIntent.ELEMENT_TERMINATED);

    LifecycleAssert.assertThat(subProcessLifecycle).compliesWithCompleteLifecycle();

    LifecycleAssert.assertThat(taskLifecycle).compliesWithCompleteLifecycle();
  }

  @Test
  public void shouldRetryToOpenMessageSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);
    streamProcessorRule.createWorkflowInstance(
        r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    final Record<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    envRule
        .getClock()
        .addTime(
            MessageObserver.SUBSCRIPTION_CHECK_INTERVAL.plus(MessageObserver.SUBSCRIPTION_TIMEOUT));

    // then
    verify(streamProcessorRule.getMockSubscriptionCommandSender(), timeout(5_000).times(2))
        .openMessageSubscription(
            START_PARTITION_ID,
            catchEvent.getValue().getWorkflowInstanceKey(),
            catchEvent.getKey(),
            catchEvent.getValue().getBpmnProcessIdBuffer(),
            wrapString("order canceled"),
            wrapString("order-123"),
            true);
  }

  @Test
  public void shouldRejectDuplicatedOpenWorkflowInstanceSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);
    streamProcessorRule.createWorkflowInstance(
        r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    final Record<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);

    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    // then
    final Record<WorkflowInstanceSubscriptionRecord> rejection =
        streamProcessorRule.awaitAndGetFirstSubscriptionRejection();

    assertThat(rejection.getIntent()).isEqualTo(WorkflowInstanceSubscriptionIntent.OPEN);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectDuplicatedCorrelateWorkflowInstanceSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    streamProcessorRule.createWorkflowInstance(
        r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    final Record<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);
    waitUntil(
        () ->
            envRule
                .events()
                .onlyWorkflowInstanceSubscriptionRecords()
                .withIntent(WorkflowInstanceSubscriptionIntent.OPENED)
                .exists());

    // when
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CORRELATE, subscription);
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CORRELATE, subscription);

    // then
    final Record<WorkflowInstanceSubscriptionRecord> rejection =
        streamProcessorRule.awaitAndGetFirstSubscriptionRejection();

    assertThat(rejection.getIntent()).isEqualTo(WorkflowInstanceSubscriptionIntent.CORRELATE);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);

    final ArgumentCaptor<DirectBuffer> captor = ArgumentCaptor.forClass(DirectBuffer.class);

    verify(streamProcessorRule.getMockSubscriptionCommandSender(), timeout(5_000))
        .correlateMessageSubscription(
            eq(subscription.getSubscriptionPartitionId()),
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            eq(subscription.getBpmnProcessIdBuffer()),
            captor.capture());
    BufferUtil.equals(captor.getValue(), subscription.getMessageNameBuffer());

    verify(streamProcessorRule.getMockSubscriptionCommandSender(), timeout(5_000))
        .rejectCorrelateMessageSubscription(
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getBpmnProcessIdBuffer()),
            eq(subscription.getMessageKey()),
            captor.capture(),
            any());

    BufferUtil.equals(captor.getValue(), subscription.getMessageNameBuffer());
  }

  @Test
  public void shouldRejectCorrelateWorkflowInstanceSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    final Record<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(
            r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    final Record<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    waitUntil(
        () ->
            envRule
                .events()
                .onlyWorkflowInstanceSubscriptionRecords()
                .withIntent(WorkflowInstanceSubscriptionIntent.OPENED)
                .exists());

    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, catchEvent.getValue());
    streamProcessorRule.awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    // when
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CORRELATE, subscription);

    // then
    final Record<WorkflowInstanceSubscriptionRecord> rejection =
        streamProcessorRule.awaitAndGetFirstSubscriptionRejection();

    assertThat(rejection.getIntent()).isEqualTo(WorkflowInstanceSubscriptionIntent.CORRELATE);
    // since we mock the message partition, we never get the acknowledged CLOSE command, so our
    // subscription remains in closing state
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRetryToCloseMessageSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    final Record<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(
            r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    final Record<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);

    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, catchEvent.getValue());

    streamProcessorRule.awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    // when
    envRule
        .getClock()
        .addTime(
            MessageObserver.SUBSCRIPTION_CHECK_INTERVAL.plus(MessageObserver.SUBSCRIPTION_TIMEOUT));

    // then
    verify(streamProcessorRule.getMockSubscriptionCommandSender(), timeout(5_000).times(2))
        .closeMessageSubscription(
            subscription.getSubscriptionPartitionId(),
            subscription.getWorkflowInstanceKey(),
            subscription.getElementInstanceKey(),
            subscription.getMessageNameBuffer());
  }

  @Test
  public void shouldRejectDuplicatedCloseWorkflowInstanceSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);
    streamProcessorRule.createWorkflowInstance(
        r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    final Record<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    waitUntil(
        () ->
            envRule
                .events()
                .onlyWorkflowInstanceSubscriptionRecords()
                .withIntent(WorkflowInstanceSubscriptionIntent.OPENED)
                .exists());

    // when
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CLOSE, subscription);
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CLOSE, subscription);

    // then
    final Record<WorkflowInstanceSubscriptionRecord> rejection =
        streamProcessorRule.awaitAndGetFirstSubscriptionRejection();

    assertThat(rejection.getIntent()).isEqualTo(WorkflowInstanceSubscriptionIntent.CLOSE);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldNotTriggerBoundaryEventIfTaskIsCompleted() {
    // given
    streamProcessorRule.deploy(TIMER_BOUNDARY_EVENT_WORKFLOW);
    streamProcessorRule.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    final Record<TimerRecord> timerRecord =
        streamProcessorRule.awaitTimerInState("timer1", TimerIntent.CREATED);
    final Record<JobRecord> jobRecord =
        streamProcessorRule.awaitJobInState("task1", JobIntent.CREATED);

    envRule.writeCommand(jobRecord.getKey(), JobIntent.COMPLETE, jobRecord.getValue());
    envRule.writeCommand(timerRecord.getKey(), TimerIntent.TRIGGER, timerRecord.getValue());
    streamProcessorRule.awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // then
    assertThat(envRule.events().onlyTimerRecords().collect(Collectors.toList()))
        .extracting(r -> tuple(r.getRecordType(), r.getIntent()))
        .containsSubsequence(
            tuple(RecordType.COMMAND, TimerIntent.CREATE),
            tuple(RecordType.EVENT, TimerIntent.CREATED),
            tuple(RecordType.COMMAND, TimerIntent.TRIGGER),
            tuple(RecordType.COMMAND_REJECTION, TimerIntent.TRIGGER));
    // ensures timer1 node never exists as far as execution goes
    assertThat(
            envRule
                .events()
                .onlyWorkflowInstanceRecords()
                .onlyEvents()
                .collect(Collectors.toList()))
        .noneMatch(r -> r.getValue().getElementIdBuffer().equals(wrapString("timer1")));
  }

  @Test
  public void shouldIgnoreSecondConsecutiveBoundaryEventTrigger() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .boundaryEvent("timer1")
            .timerWithDuration("PT1S")
            .endEvent("timer1End")
            .moveToActivity("task")
            .boundaryEvent("timer2")
            .timerWithDuration("PT2S")
            .endEvent("timer2End")
            .done();

    streamProcessorRule.deploy(workflow);
    streamProcessorRule.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    final Record<TimerRecord> timer1Record =
        streamProcessorRule.awaitTimerInState("timer1", TimerIntent.CREATED);
    final Record<TimerRecord> timer2Record =
        streamProcessorRule.awaitTimerInState("timer2", TimerIntent.CREATED);

    envRule.writeCommand(timer1Record.getKey(), TimerIntent.TRIGGER, timer1Record.getValue());
    envRule.writeCommand(timer2Record.getKey(), TimerIntent.TRIGGER, timer2Record.getValue());
    streamProcessorRule.awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // then
    assertThat(
            envRule
                .events()
                .onlyWorkflowInstanceRecords()
                .skipUntil(r -> r.getValue().getElementIdBuffer().equals(wrapString("task")))
                .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETING)
                .map(Record::getValue)
                .map(WorkflowInstanceRecord::getElementIdBuffer)
                .map(BufferUtil::bufferAsString))
        .containsExactly("timer1", "timer1End", "process");
  }

  private WorkflowInstanceSubscriptionRecord subscriptionRecordForEvent(
      final Record<WorkflowInstanceRecord> catchEvent) {
    return new WorkflowInstanceSubscriptionRecord()
        .setSubscriptionPartitionId(START_PARTITION_ID)
        .setWorkflowInstanceKey(catchEvent.getValue().getWorkflowInstanceKey())
        .setElementInstanceKey(catchEvent.getKey())
        .setMessageKey(3L)
        .setMessageName(wrapString("order canceled"));
  }
}
