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
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.system.workflow.repository.api.management.FetchWorkflowResponse;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.map.WorkflowCache;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WorkflowInstanceStreamProcessorTest {

  private static final String PROCESS_ID = "process";
  private static final DirectBuffer PROCESS_ID_BUFFER = BufferUtil.wrapString(PROCESS_ID);

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

  @Rule public StreamProcessorRule rule = new StreamProcessorRule();

  @Mock private SubscriptionCommandSender mockSubscriptionCommandSender;
  @Mock private TopologyManager mockTopologyManager;

  private StreamProcessorControl streamProcessor;
  private WorkflowCache workflowCache;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    workflowCache = new WorkflowCache(null, mock(TopologyManager.class));

    when(mockSubscriptionCommandSender.hasPartitionIds()).thenReturn(true);
    when(mockSubscriptionCommandSender.openMessageSubscription(anyLong(), anyLong(), any(), any()))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.correlateMessageSubscription(
            anyInt(), anyLong(), anyLong(), any()))
        .thenReturn(true);

    streamProcessor =
        rule.runStreamProcessor(
            env -> {
              final WorkflowInstanceStreamProcessor streamProcessor =
                  new WorkflowInstanceStreamProcessor(
                      workflowCache, mockSubscriptionCommandSender, mockTopologyManager);

              return streamProcessor.createStreamProcessor(env);
            });
  }

  @Test
  public void shouldRejectCancellationInDirectSuccession() {
    // given
    deploy(SERVICE_TASK_WORKFLOW);

    streamProcessor.blockAfterJobEvent(r -> r.getMetadata().getIntent() == JobIntent.CREATE);

    final TypedRecord<WorkflowInstanceRecord> createdEvent = createWorkflowInstance();
    waitUntil(() -> streamProcessor.isBlocked());

    rule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    final long secondCommandPosition =
        rule.writeCommand(
            createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());

    // when
    streamProcessor.unblock();

    // then
    awaitElementInState("process", WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<WorkflowInstanceIntent> workflowInstanceLifecycle =
        rule.events().onlyStatesOf("process").collect(Collectors.toList());

    LifecycleAssert.assertThat(workflowInstanceLifecycle).compliesWithCompleteLifecycle();

    final TypedRecord<WorkflowInstanceRecord> rejection =
        rule.events().onlyWorkflowInstanceRecords().onlyRejections().findFirst().get();

    assertThat(rejection.getMetadata().getIntent()).isEqualTo(WorkflowInstanceIntent.CANCEL);
    assertThat(rejection.getSourcePosition()).isEqualTo(secondCommandPosition);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("Workflow instance is not running");
  }

  @Test
  public void shouldCancelActivityInStateReady() throws InterruptedException {
    // given
    deploy(SERVICE_TASK_WORKFLOW);

    streamProcessor.blockAfterWorkflowInstanceRecord(
        isForElement("start")); // blocks before handling sequence flow taken

    final TypedRecord<WorkflowInstanceRecord> createdEvent = createWorkflowInstance();
    waitUntil(() -> streamProcessor.isBlocked());

    // when
    rule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    streamProcessor.unblock();

    // then
    awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<TypedRecord<WorkflowInstanceRecord>> records =
        rule.events().onlyWorkflowInstanceRecords().collect(Collectors.toList());

    final List<WorkflowInstanceIntent> workflowInstanceLifecycle =
        rule.events().onlyStatesOf("process").collect(Collectors.toList());

    final List<WorkflowInstanceIntent> taskLifecycle =
        rule.events().onlyStatesOf("task").collect(Collectors.toList());

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
    deploy(SERVICE_TASK_WORKFLOW);

    // stop when ELEMENT_COMPLETED is written
    streamProcessor.blockAfterWorkflowInstanceRecord(
        r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETING);

    final TypedRecord<WorkflowInstanceRecord> createdEvent = createWorkflowInstance();

    completeJob();

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    rule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    streamProcessor.unblock();

    // then
    awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<TypedRecord<WorkflowInstanceRecord>> records =
        rule.events().onlyWorkflowInstanceRecords().collect(Collectors.toList());

    final List<WorkflowInstanceIntent> workflowInstanceLifecycle =
        rule.events().onlyStatesOf("process").collect(Collectors.toList());

    final List<WorkflowInstanceIntent> taskLifecycle =
        rule.events().onlyStatesOf("task").collect(Collectors.toList());

    LifecycleAssert.assertThat(workflowInstanceLifecycle)
        .compliesWithCompleteLifecycle()
        .endsWith(WorkflowInstanceIntent.ELEMENT_TERMINATED);
    LifecycleAssert.assertThat(taskLifecycle).compliesWithCompleteLifecycle();

    WorkflowInstanceAssert.assertThat(records)
        .doesNotEvaluateFlowAfterTerminatingElement("process");
  }

  private void completeJob() {
    final TypedRecord<JobRecord> createCommand = awaitAndGetFirstRecordInState(JobIntent.CREATE);

    final long jobKey = rule.writeEvent(JobIntent.CREATED, createCommand.getValue());
    rule.writeEvent(jobKey, JobIntent.COMPLETED, createCommand.getValue());
  }

  @Test
  public void shouldCancelActivityInStateCompleting() {
    // given
    deploy(SERVICE_TASK_WORKFLOW);

    // stop when ELEMENT_COMPLETING is written
    streamProcessor.blockAfterJobEvent(r -> r.getMetadata().getIntent() == JobIntent.COMPLETED);

    final TypedRecord<WorkflowInstanceRecord> createdEvent = createWorkflowInstance();

    completeJob();

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    rule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    streamProcessor.unblock();

    // then
    awaitElementInState(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<TypedRecord<WorkflowInstanceRecord>> records =
        rule.events().onlyWorkflowInstanceRecords().collect(Collectors.toList());

    final List<WorkflowInstanceIntent> workflowInstanceLifecycle =
        rule.events().onlyStatesOf("process").collect(Collectors.toList());

    final List<WorkflowInstanceIntent> taskLifecycle =
        rule.events().onlyStatesOf("task").collect(Collectors.toList());

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
    deploy(SUB_PROCESS_WORKFLOW);

    streamProcessor.blockAfterJobEvent(r -> r.getMetadata().getIntent() == JobIntent.CREATE);

    final TypedRecord<WorkflowInstanceRecord> createdEvent = createWorkflowInstance();
    waitUntil(() -> streamProcessor.isBlocked());

    rule.writeCommand(
        createdEvent.getKey(), WorkflowInstanceIntent.CANCEL, createdEvent.getValue());
    completeJob();

    // when
    streamProcessor.unblock();

    // then
    awaitElementInState("process", WorkflowInstanceIntent.ELEMENT_TERMINATED);

    final List<WorkflowInstanceIntent> workflowInstanceLifecycle =
        rule.events().onlyStatesOf("process").collect(Collectors.toList());
    final List<WorkflowInstanceIntent> subProcessLifecycle =
        rule.events().onlyStatesOf("subProcess").collect(Collectors.toList());
    final List<WorkflowInstanceIntent> taskLifecycle =
        rule.events().onlyStatesOf("task").collect(Collectors.toList());

    LifecycleAssert.assertThat(workflowInstanceLifecycle)
        .compliesWithCompleteLifecycle()
        .endsWith(WorkflowInstanceIntent.ELEMENT_TERMINATED);

    LifecycleAssert.assertThat(subProcessLifecycle).compliesWithCompleteLifecycle();

    LifecycleAssert.assertThat(taskLifecycle).compliesWithCompleteLifecycle();
  }

  @Test
  public void shouldRetryToOpenMessageSubscription() {
    // given
    deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    streamProcessor.blockAfterWorkflowInstanceRecord(
        isForElement("catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    createWorkflowInstance(asMsgPack("orderId", "order-123"));

    waitUntil(() -> streamProcessor.isBlocked());

    final TypedRecord<WorkflowInstanceRecord> catchEvent =
        awaitElementInState("catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    rule.getClock().addTime(SUBSCRIPTION_CHECK_INTERVAL.plus(SUBSCRIPTION_TIMEOUT));

    streamProcessor.unblock();

    // then
    verify(mockSubscriptionCommandSender, timeout(5_000).times(2))
        .openMessageSubscription(
            catchEvent.getValue().getWorkflowInstanceKey(),
            catchEvent.getKey(),
            wrapString("order canceled"),
            wrapString("order-123"));
  }

  @Test
  public void shouldRejectDuplicatedOpenWorkflowInstanceSubscription() {
    // given
    deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    streamProcessor.blockAfterWorkflowInstanceRecord(
        isForElement("catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    createWorkflowInstance(asMsgPack("orderId", "order-123"));

    waitUntil(() -> streamProcessor.isBlocked());

    final TypedRecord<WorkflowInstanceRecord> catchEvent =
        awaitElementInState("catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);

    rule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    final long secondCommandPosition =
        rule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    streamProcessor.unblock();

    // then
    waitUntil(
        () ->
            rule.events()
                .onlyWorkflowInstanceSubscriptionRecords()
                .onlyRejections()
                .findFirst()
                .isPresent());

    final TypedRecord<WorkflowInstanceSubscriptionRecord> rejection =
        rule.events().onlyWorkflowInstanceSubscriptionRecords().onlyRejections().findFirst().get();

    assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(WorkflowInstanceSubscriptionIntent.OPEN);
    assertThat(rejection.getSourcePosition()).isEqualTo(secondCommandPosition);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("subscription is already open");
  }

  @Test
  public void shouldRejectDuplicatedCorrelateWorkflowInstanceSubscription() {
    // given
    deploy(MESSAGE_CATCH_EVENT_WORKFLOW);

    createWorkflowInstance(asMsgPack("orderId", "order-123"));

    final TypedRecord<WorkflowInstanceRecord> catchEvent =
        awaitElementInState("catch-event", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final WorkflowInstanceSubscriptionRecord subscription = subscriptionRecordForEvent(catchEvent);

    streamProcessor.blockAfterWorkflowInstanceSubscriptionEvent(
        e -> e.getMetadata().getIntent() == WorkflowInstanceSubscriptionIntent.OPENED);

    rule.writeCommand(WorkflowInstanceSubscriptionIntent.OPEN, subscription);

    waitUntil(() -> streamProcessor.isBlocked());

    // when
    rule.writeCommand(WorkflowInstanceSubscriptionIntent.CORRELATE, subscription);
    final long secondCommandPosition =
        rule.writeCommand(WorkflowInstanceSubscriptionIntent.CORRELATE, subscription);

    streamProcessor.unblock();

    // then
    waitUntil(
        () ->
            rule.events()
                .onlyWorkflowInstanceSubscriptionRecords()
                .onlyRejections()
                .findFirst()
                .isPresent());

    final TypedRecord<WorkflowInstanceSubscriptionRecord> rejection =
        rule.events().onlyWorkflowInstanceSubscriptionRecords().onlyRejections().findFirst().get();

    assertThat(rejection.getMetadata().getIntent())
        .isEqualTo(WorkflowInstanceSubscriptionIntent.CORRELATE);
    assertThat(rejection.getSourcePosition()).isEqualTo(secondCommandPosition);
    assertThat(BufferUtil.bufferAsString(rejection.getMetadata().getRejectionReason()))
        .isEqualTo("subscription is already correlated");

    verify(mockSubscriptionCommandSender, timeout(5_000).times(2))
        .correlateMessageSubscription(
            eq(subscription.getSubscriptionPartitionId()),
            eq(subscription.getWorkflowInstanceKey()),
            eq(subscription.getActivityInstanceKey()),
            any());
  }

  private TypedRecord<WorkflowInstanceRecord> createWorkflowInstance() {
    return createWorkflowInstance(wrapString(""));
  }

  private TypedRecord<WorkflowInstanceRecord> createWorkflowInstance(DirectBuffer payload) {
    rule.writeCommand(WorkflowInstanceIntent.CREATE, workflowInstanceRecord(payload));
    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        awaitAndGetFirstRecordInState(WorkflowInstanceIntent.CREATED);
    return createdEvent;
  }

  private Predicate<TypedRecord<WorkflowInstanceRecord>> isForElement(String elementId) {
    return r -> BufferUtil.wrapString(elementId).equals(r.getValue().getActivityId());
  }

  private Predicate<TypedRecord<WorkflowInstanceRecord>> isForElement(
      String elementId, WorkflowInstanceIntent intent) {
    return isForElement(elementId).and(t -> t.getMetadata().getIntent() == intent);
  }

  private static WorkflowInstanceRecord workflowInstanceRecord(DirectBuffer payload) {
    final WorkflowInstanceRecord record = new WorkflowInstanceRecord();

    record.setBpmnProcessId(PROCESS_ID_BUFFER);
    record.setPayload(payload);

    return record;
  }

  private WorkflowInstanceSubscriptionRecord subscriptionRecordForEvent(
      final TypedRecord<WorkflowInstanceRecord> catchEvent) {
    return new WorkflowInstanceSubscriptionRecord()
        .setSubscriptionPartitionId(1)
        .setWorkflowInstanceKey(catchEvent.getValue().getWorkflowInstanceKey())
        .setActivityInstanceKey(catchEvent.getKey())
        .setMessageName(wrapString("order canceled"));
  }

  // TODO: this is not nice, but should go away once we get rid of workflow fetching
  private void deploy(BpmnModelInstance modelInstance) {
    final FetchWorkflowResponse response = new FetchWorkflowResponse();
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, modelInstance);

    final DirectBuffer xmlBuffer = new UnsafeBuffer(outStream.toByteArray());
    response
        .bpmnXml(xmlBuffer)
        .deploymentKey(1)
        .workflowKey(1)
        .bpmnProcessId(PROCESS_ID_BUFFER)
        .version(1);

    final UnsafeBuffer responseBuffer = new UnsafeBuffer(new byte[response.getLength()]);
    response.write(responseBuffer, 0);

    workflowCache.addWorkflow(responseBuffer);
  }

  private void awaitFirstRecordInState(Intent state) {
    waitUntil(() -> rule.events().withIntent(state).findFirst().isPresent());
  }

  private TypedRecord<WorkflowInstanceRecord> awaitElementInState(
      String elementId, WorkflowInstanceIntent intent) {
    final DirectBuffer elementIdAsBuffer = BufferUtil.wrapString(elementId);

    return doRepeatedly(
            () ->
                rule.events()
                    .onlyWorkflowInstanceRecords()
                    .withIntent(intent)
                    .filter(r -> elementIdAsBuffer.equals(r.getValue().getActivityId()))
                    .findFirst())
        .until(o -> o.isPresent())
        .get();
  }

  private TypedRecord<WorkflowInstanceRecord> awaitAndGetFirstRecordInState(
      WorkflowInstanceIntent state) {
    awaitFirstRecordInState(state);
    return rule.events().onlyWorkflowInstanceRecords().withIntent(state).findFirst().get();
  }

  private TypedRecord<JobRecord> awaitAndGetFirstRecordInState(JobIntent state) {
    awaitFirstRecordInState(state);
    return rule.events().onlyJobRecords().withIntent(state).findFirst().get();
  }
}
