/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow;

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

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.workflow.message.MessageObserver;
import io.zeebe.engine.util.StreamProcessorControl;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;

public class WorkflowInstanceStreamProcessorTest {

  @Rule public Timeout timeoutRule = new Timeout(2, TimeUnit.MINUTES);

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance SERVICE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .sequenceFlowId("flow1")
          .serviceTask("task", b -> b.zeebeTaskType("taskType"))
          .sequenceFlowId("flow2")
          .endEvent("end")
          .done();

  private static final BpmnModelInstance SUB_PROCESS_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .subProcess("subProcess")
          .embeddedSubProcess()
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("taskType"))
          .endEvent()
          .subProcessDone()
          .endEvent()
          .done();

  private static final BpmnModelInstance MESSAGE_CATCH_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent(
              "catch-event",
              c -> c.message(m -> m.name("order canceled").zeebeCorrelationKey("orderId")))
          .done();

  private static final BpmnModelInstance TIMER_BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task1", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer1")
          .cancelActivity(true)
          .timerWithDuration("PT1S")
          .endEvent("timer1End")
          .moveToActivity("task1")
          .endEvent("end")
          .done();

  public StreamProcessorRule envRule = new StreamProcessorRule();
  public WorkflowInstanceStreamProcessorRule streamProcessorRule =
      new WorkflowInstanceStreamProcessorRule(envRule);

  @Rule public RuleChain chain = RuleChain.outerRule(envRule).around(streamProcessorRule);

  private StreamProcessorControl streamProcessor;

  @Before
  public void setUp() {

    streamProcessor = streamProcessorRule.getStreamProcessor();
  }

  @Test
  public void shouldRejectCancellationInDirectSuccession() {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);

    streamProcessor.blockAfterJobEvent(r -> r.getMetadata().getIntent() == JobIntent.CREATE);

    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));
    waitUntil(() -> streamProcessor.isBlocked());

    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());

    // when
    streamProcessor.unblock();

    // then
    streamProcessorRule.awaitElementInState("process", WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<WorkflowInstanceIntent> workflowInstanceLifecycle =
        envRule.events().onlyStatesOf("process").collect(Collectors.toList());

    LifecycleAssert.assertThat(workflowInstanceLifecycle).compliesWithCompleteLifecycle();

    final TypedRecord<WorkflowInstanceRecord> rejection =
        envRule.events().onlyWorkflowInstanceRecords().onlyRejections().findFirst().get();

    Assertions.assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(WorkflowInstanceIntent.CANCEL);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo(
            "Expected to cancel a workflow instance with key '"
                + createdEvent.getKey()
                + "', but no such workflow was found");
  }

  @Test
  public void shouldCancelActivityInStateReady() {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);

    streamProcessor.blockAfterWorkflowInstanceRecord(
        isForElement(
            "start",
            WorkflowInstanceIntent
                .ELEMENT_COMPLETED)); // blocks before handling sequence flow taken

    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));
    waitUntil(() -> streamProcessor.isBlocked());

    // when
    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    streamProcessor.unblock();

    // then
    streamProcessorRule.awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<TypedRecord<WorkflowInstanceRecord>> records =
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
  public void shouldCancelScopeBeforeTakingSequenceFlow() {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);

    // stop when ELEMENT_COMPLETED is written
    streamProcessor.blockAfterWorkflowInstanceRecord(
        r ->
            r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETING
                && r.getValue().getBpmnElementType() == BpmnElementType.SERVICE_TASK);

    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    streamProcessorRule.completeFirstJob();

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    streamProcessor.unblock();

    // then
    streamProcessorRule.awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<TypedRecord<WorkflowInstanceRecord>> records =
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
  public void shouldCancelActivityInStateCompleting() {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);

    // stop when ELEMENT_COMPLETING is written
    streamProcessor.blockAfterJobEvent(r -> r.getMetadata().getIntent() == JobIntent.COMPLETED);

    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    streamProcessorRule.completeFirstJob();

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    streamProcessor.unblock();

    // then
    streamProcessorRule.awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<TypedRecord<WorkflowInstanceRecord>> records =
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
  public void shouldCancelAndCompleteJobConcurrentlyInSubProcess() {
    // given
    streamProcessorRule.deploy(SUB_PROCESS_WORKFLOW);

    streamProcessor.blockAfterJobEvent(r -> r.getMetadata().getIntent() == JobIntent.CREATE);

    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));
    waitUntil(() -> streamProcessor.isBlocked());

    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    streamProcessorRule.completeFirstJob();

    // when
    streamProcessor.unblock();

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

    streamProcessor.blockAfterWorkflowInstanceRecord(
        isForElement("catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    streamProcessorRule.createWorkflowInstance(
        r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    waitUntil(() -> streamProcessor.isBlocked());

    final TypedRecord<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    envRule
        .getClock()
        .addTime(
            MessageObserver.SUBSCRIPTION_CHECK_INTERVAL.plus(MessageObserver.SUBSCRIPTION_TIMEOUT));

    streamProcessor.unblock();

    // then
    verify(streamProcessorRule.getMockSubscriptionCommandSender(), timeout(5_000).times(2))
        .openMessageSubscription(
            START_PARTITION_ID,
            catchEvent.getValue().getWorkflowInstanceKey(),
            catchEvent.getKey(),
            wrapString("order canceled"),
            wrapString("order-123"),
            true);
  }

  @Test
  public void shouldRejectDuplicatedOpenWorkflowInstanceSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    streamProcessor.blockAfterWorkflowInstanceRecord(
        isForElement("catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    streamProcessorRule.createWorkflowInstance(
        r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    waitUntil(() -> streamProcessor.isBlocked());

    final TypedRecord<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);

    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    streamProcessor.unblock();

    // then
    final TypedRecord<WorkflowInstanceSubscriptionRecord> rejection =
        streamProcessorRule.awaitAndGetFirstSubscriptionRejection();

    Assertions.assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(WorkflowInstanceSubscriptionIntent.OPEN);
    Assertions.assertThat(rejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectDuplicatedCorrelateWorkflowInstanceSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    streamProcessorRule.createWorkflowInstance(
        r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    final TypedRecord<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);

    streamProcessor.blockAfterWorkflowInstanceSubscriptionEvent(
        e -> e.getMetadata().getIntent() == WorkflowInstanceSubscriptionIntent.OPENED);

    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CORRELATE, subscription);
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CORRELATE, subscription);

    streamProcessor.unblock();

    // then
    final TypedRecord<WorkflowInstanceSubscriptionRecord> rejection =
        streamProcessorRule.awaitAndGetFirstSubscriptionRejection();

    Assertions.assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(WorkflowInstanceSubscriptionIntent.CORRELATE);
    Assertions.assertThat(rejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.NOT_FOUND);

    final ArgumentCaptor<DirectBuffer> captor = ArgumentCaptor.forClass(DirectBuffer.class);

    verify(streamProcessorRule.getMockSubscriptionCommandSender(), timeout(5_000))
        .correlateMessageSubscription(
            eq(subscription.getSubscriptionPartitionId()),
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            captor.capture());
    BufferUtil.equals(captor.getValue(), subscription.getMessageName());

    verify(streamProcessorRule.getMockSubscriptionCommandSender(), timeout(5_000))
        .rejectCorrelateMessageSubscription(
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getElementInstanceKey()),
            eq(subscription.getMessageKey()),
            captor.capture(),
            any());

    BufferUtil.equals(captor.getValue(), subscription.getMessageName());
  }

  @Test
  public void shouldRejectCorrelateWorkflowInstanceSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(
            r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    final TypedRecord<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);

    streamProcessor.blockAfterWorkflowInstanceSubscriptionEvent(
        e -> e.getMetadata().getIntent() == WorkflowInstanceSubscriptionIntent.OPENED);

    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    waitUntil(() -> streamProcessor.isBlocked());

    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, catchEvent.getValue());

    streamProcessor.unblock();

    streamProcessorRule.awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    // when
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CORRELATE, subscription);

    // then
    final TypedRecord<WorkflowInstanceSubscriptionRecord> rejection =
        streamProcessorRule.awaitAndGetFirstSubscriptionRejection();

    Assertions.assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(WorkflowInstanceSubscriptionIntent.CORRELATE);
    // since we mock the message partition, we never get the acknowledged CLOSE command, so our
    // subscription remains in closing state
    Assertions.assertThat(rejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRetryToCloseMessageSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createAndReceiveWorkflowInstance(
            r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    final TypedRecord<WorkflowInstanceRecord> catchEvent =
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

    streamProcessor.unblock();

    // then
    verify(streamProcessorRule.getMockSubscriptionCommandSender(), timeout(5_000).times(2))
        .closeMessageSubscription(
            subscription.getSubscriptionPartitionId(),
            subscription.getWorkflowInstanceKey(),
            subscription.getElementInstanceKey(),
            subscription.getMessageName());
  }

  @Test
  public void shouldRejectDuplicatedCloseWorkflowInstanceSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);
    streamProcessorRule.createWorkflowInstance(
        r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("orderId", "order-123")));

    final TypedRecord<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);

    streamProcessor.blockAfterWorkflowInstanceSubscriptionEvent(
        e -> e.getMetadata().getIntent() == WorkflowInstanceSubscriptionIntent.OPENED);

    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CLOSE, subscription);
    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CLOSE, subscription);

    streamProcessor.unblock();

    // then
    final TypedRecord<WorkflowInstanceSubscriptionRecord> rejection =
        streamProcessorRule.awaitAndGetFirstSubscriptionRejection();

    Assertions.assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(WorkflowInstanceSubscriptionIntent.CLOSE);
    Assertions.assertThat(rejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldNotTriggerBoundaryEventIfTaskIsCompleted() {
    // given
    streamProcessorRule.deploy(TIMER_BOUNDARY_EVENT_WORKFLOW);
    streamProcessor.blockAfterJobEvent(r -> r.getMetadata().getIntent() == JobIntent.CREATED);
    streamProcessorRule.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    final TypedRecord<TimerRecord> timerRecord =
        streamProcessorRule.awaitTimerInState("timer1", TimerIntent.CREATED);
    final TypedRecord<JobRecord> jobRecord =
        streamProcessorRule.awaitJobInState("task1", JobIntent.CREATED);

    envRule.writeEvent(jobRecord.getKey(), JobIntent.COMPLETED, jobRecord.getValue());
    envRule.writeCommand(timerRecord.getKey(), TimerIntent.TRIGGER, timerRecord.getValue());
    streamProcessor.unblock();
    streamProcessorRule.awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // then

    Assertions.assertThat(envRule.events().onlyTimerRecords().collect(Collectors.toList()))
        .extracting(TypedRecord::getMetadata)
        .extracting(m -> tuple(m.getRecordType(), m.getIntent()))
        .containsExactly(
            tuple(RecordType.COMMAND, TimerIntent.CREATE),
            tuple(RecordType.EVENT, TimerIntent.CREATED),
            tuple(RecordType.COMMAND, TimerIntent.TRIGGER),
            tuple(RecordType.COMMAND_REJECTION, TimerIntent.TRIGGER));
    // ensures timer1 node never exists as far as execution goes
    Assertions.assertThat(
            envRule
                .events()
                .onlyWorkflowInstanceRecords()
                .onlyEvents()
                .collect(Collectors.toList()))
        .noneMatch(r -> r.getValue().getElementId().equals(wrapString("timer1")));
  }

  @Test
  public void shouldIgnoreSecondConsecutiveBoundaryEventTrigger() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .boundaryEvent("timer1")
            .timerWithDuration("PT1S")
            .endEvent("timer1End")
            .moveToActivity("task")
            .boundaryEvent("timer2")
            .timerWithDuration("PT2S")
            .endEvent("timer2End")
            .done();

    streamProcessorRule.deploy(workflow);
    streamProcessor.blockAfterWorkflowInstanceRecord(
        r ->
            r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED
                && r.getValue().getElementId().equals(wrapString("task")));
    streamProcessorRule.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    final TypedRecord<TimerRecord> timer1Record =
        streamProcessorRule.awaitTimerInState("timer1", TimerIntent.CREATED);
    final TypedRecord<TimerRecord> timer2Record =
        streamProcessorRule.awaitTimerInState("timer2", TimerIntent.CREATED);

    envRule.writeCommand(timer1Record.getKey(), TimerIntent.TRIGGER, timer1Record.getValue());
    envRule.writeCommand(timer2Record.getKey(), TimerIntent.TRIGGER, timer2Record.getValue());
    streamProcessor.unblock();
    streamProcessorRule.awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // then
    Assertions.assertThat(
            envRule
                .events()
                .onlyWorkflowInstanceRecords()
                .skipUntil(r -> r.getValue().getElementId().equals(wrapString("task")))
                .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETING)
                .map(TypedRecord::getValue)
                .map(WorkflowInstanceRecord::getElementId)
                .map(BufferUtil::bufferAsString))
        .containsExactly("timer1", "timer1End", "process");
  }

  private Predicate<TypedRecord<WorkflowInstanceRecord>> isForElement(final String elementId) {
    return r -> BufferUtil.wrapString(elementId).equals(r.getValue().getElementId());
  }

  private Predicate<TypedRecord<WorkflowInstanceRecord>> isForElement(
      final String elementId, final WorkflowInstanceIntent intent) {
    return isForElement(elementId).and(t -> t.getMetadata().getIntent() == intent);
  }

  private WorkflowInstanceSubscriptionRecord subscriptionRecordForEvent(
      final TypedRecord<WorkflowInstanceRecord> catchEvent) {
    return new WorkflowInstanceSubscriptionRecord()
        .setSubscriptionPartitionId(START_PARTITION_ID)
        .setWorkflowInstanceKey(catchEvent.getValue().getWorkflowInstanceKey())
        .setElementInstanceKey(catchEvent.getKey())
        .setMessageKey(3L)
        .setMessageName(wrapString("order canceled"));
  }
}
