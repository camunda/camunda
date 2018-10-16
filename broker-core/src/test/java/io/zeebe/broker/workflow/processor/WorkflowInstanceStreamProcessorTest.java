/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.processor;

import static io.zeebe.broker.subscription.message.processor.MessageStreamProcessor.SUBSCRIPTION_CHECK_INTERVAL;
import static io.zeebe.broker.subscription.message.processor.MessageStreamProcessor.SUBSCRIPTION_TIMEOUT;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowInstanceStreamProcessorTest {

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
              c -> c.message(m -> m.name("order canceled").zeebeCorrelationKey("$.orderId")))
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
        streamProcessorRule.createWorkflowInstance(PROCESS_ID);
    waitUntil(() -> streamProcessor.isBlocked());

    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    final long secondCommandPosition =
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

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(WorkflowInstanceIntent.CANCEL);
    assertThat(rejection.getSourcePosition()).isEqualTo(secondCommandPosition);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("Workflow instance is not running");
  }

  @Test
  public void shouldCancelActivityInStateReady() throws InterruptedException {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);

    streamProcessor.blockAfterWorkflowInstanceRecord(
        isForElement("start")); // blocks before handling sequence flow taken

    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createWorkflowInstance(PROCESS_ID);
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
        r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETING);

    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createWorkflowInstance(PROCESS_ID);

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
        streamProcessorRule.createWorkflowInstance(PROCESS_ID);

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
        streamProcessorRule.createWorkflowInstance(PROCESS_ID);
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

    streamProcessorRule.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

    waitUntil(() -> streamProcessor.isBlocked());

    final TypedRecord<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    envRule.getClock().addTime(SUBSCRIPTION_CHECK_INTERVAL.plus(SUBSCRIPTION_TIMEOUT));

    streamProcessor.unblock();

    // then
    verify(streamProcessorRule.getMockSubscriptionCommandSender(), timeout(5_000).times(2))
        .openMessageSubscription(
            catchEvent.getValue().getWorkflowInstanceKey(),
            catchEvent.getKey(),
            wrapString("order canceled"),
            wrapString("order-123"));
  }

  @Test
  public void shouldRejectDuplicatedOpenWorkflowInstanceSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    streamProcessor.blockAfterWorkflowInstanceRecord(
        isForElement("catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    streamProcessorRule.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

    waitUntil(() -> streamProcessor.isBlocked());

    final TypedRecord<WorkflowInstanceRecord> catchEvent =
        streamProcessorRule.awaitElementInState(
            "catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);

    envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    final long secondCommandPosition =
        envRule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    streamProcessor.unblock();

    // then
    waitUntil(
        () ->
            envRule
                .events()
                .onlyWorkflowInstanceSubscriptionRecords()
                .onlyRejections()
                .findFirst()
                .isPresent());

    final TypedRecord<WorkflowInstanceSubscriptionRecord> rejection =
        envRule
            .events()
            .onlyWorkflowInstanceSubscriptionRecords()
            .onlyRejections()
            .findFirst()
            .get();

    assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(WorkflowInstanceSubscriptionIntent.OPEN);
    assertThat(rejection.getSourcePosition()).isEqualTo(secondCommandPosition);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("subscription is already open");
  }

  @Test
  public void shouldRejectDuplicatedCorrelateWorkflowInstanceSubscription() {
    // given
    streamProcessorRule.deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    streamProcessorRule.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

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
    final long secondCommandPosition =
        envRule.writeCommand(WorkflowInstanceSubscriptionIntent.CORRELATE, subscription);

    streamProcessor.unblock();

    // then
    waitUntil(
        () ->
            envRule
                .events()
                .onlyWorkflowInstanceSubscriptionRecords()
                .onlyRejections()
                .findFirst()
                .isPresent());

    final TypedRecord<WorkflowInstanceSubscriptionRecord> rejection =
        envRule
            .events()
            .onlyWorkflowInstanceSubscriptionRecords()
            .onlyRejections()
            .findFirst()
            .get();

    assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(WorkflowInstanceSubscriptionIntent.CORRELATE);
    assertThat(rejection.getSourcePosition()).isEqualTo(secondCommandPosition);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("subscription is already correlated");

    verify(streamProcessorRule.getMockSubscriptionCommandSender(), timeout(5_000).times(2))
        .correlateMessageSubscription(
            eq(subscription.getSubscriptionPartitionId()),
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getActivityInstanceKey()),
            any());
  }

  /** https://github.com/zeebe-io/zeebe/issues/1411 */
  @Test
  public void shouldUpdateStateOnCommands() {
    // given
    streamProcessorRule.deploy(SERVICE_TASK_WORKFLOW);
    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        streamProcessorRule.createWorkflowInstance(PROCESS_ID);

    streamProcessorRule.awaitElementInState("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    streamProcessor.blockAfterWorkflowInstanceRecord(
        r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.UPDATE_PAYLOAD);

    final WorkflowInstanceRecord payloadUpdate = createdEvent.getValue();
    payloadUpdate.setPayload(MsgPackUtil.asMsgPack("key", "val"));

    envRule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.UPDATE_PAYLOAD, payloadUpdate);
    waitUntil(() -> streamProcessor.isBlocked());

    // when
    streamProcessor.restart();
    streamProcessorRule.completeFirstJob();

    // then
    final TypedRecord<WorkflowInstanceRecord> completedEvent =
        streamProcessorRule.awaitElementInState(
            PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    MsgPackUtil.assertEquality(completedEvent.getValue().getPayload(), "{'key': 'val'}");
  }

  private Predicate<TypedRecord<WorkflowInstanceRecord>> isForElement(final String elementId) {
    return r -> BufferUtil.wrapString(elementId).equals(r.getValue().getActivityId());
  }

  private Predicate<TypedRecord<WorkflowInstanceRecord>> isForElement(
      final String elementId, final WorkflowInstanceIntent intent) {
    return isForElement(elementId).and(t -> t.getMetadata().getIntent() == intent);
  }

  private WorkflowInstanceSubscriptionRecord subscriptionRecordForEvent(
      final TypedRecord<WorkflowInstanceRecord> catchEvent) {
    return new WorkflowInstanceSubscriptionRecord()
        .setSubscriptionPartitionId(1)
        .setWorkflowInstanceKey(catchEvent.getValue().getWorkflowInstanceKey())
        .setActivityInstanceKey(catchEvent.getKey())
        .setMessageName(wrapString("order canceled"));
  }
}
