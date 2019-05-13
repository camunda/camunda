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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedStreamProcessor;
import io.zeebe.engine.processor.workflow.job.JobEventProcessors;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processor.workflow.timer.DueDateTimerChecker;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.util.CopiedTypedEvent;
import io.zeebe.engine.util.Records;
import io.zeebe.engine.util.StreamProcessorControl;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.engine.util.TypedRecordStream;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

@SuppressWarnings("unchecked")
public class WorkflowInstanceStreamProcessorRule extends ExternalResource
    implements StreamProcessorLifecycleAware {

  public static final int VERSION = 1;
  public static final int WORKFLOW_KEY = 123;
  public static final int DEPLOYMENT_KEY = 1;
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private final StreamProcessorRule environmentRule;

  private SubscriptionCommandSender mockSubscriptionCommandSender;

  private StreamProcessorControl streamProcessor;
  private WorkflowState workflowState;
  private ZeebeState zeebeState;
  private ActorControl actor;

  public WorkflowInstanceStreamProcessorRule(StreamProcessorRule streamProcessorRule) {
    this.environmentRule = streamProcessorRule;
  }

  public SubscriptionCommandSender getMockSubscriptionCommandSender() {
    return mockSubscriptionCommandSender;
  }

  @Override
  protected void before() {
    mockSubscriptionCommandSender = mock(SubscriptionCommandSender.class);

    when(mockSubscriptionCommandSender.openMessageSubscription(
            anyInt(), anyLong(), anyLong(), any(), any(), anyBoolean()))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.correlateMessageSubscription(
            anyInt(), anyLong(), anyLong(), any()))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.closeMessageSubscription(
            anyInt(), anyLong(), anyLong(), any(DirectBuffer.class)))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.rejectCorrelateMessageSubscription(
            anyLong(), anyLong(), anyLong(), any(), any()))
        .thenReturn(true);

    streamProcessor =
        environmentRule.runTypedStreamProcessor(
            (typedEventStreamProcessorBuilder, zeebeDb, dbContext) -> {
              zeebeState = new ZeebeState(zeebeDb, dbContext);
              workflowState = zeebeState.getWorkflowState();
              WorkflowEventProcessors.addWorkflowProcessors(
                  typedEventStreamProcessorBuilder,
                  zeebeState,
                  mockSubscriptionCommandSender,
                  new CatchEventBehavior(zeebeState, mockSubscriptionCommandSender, 1),
                  new DueDateTimerChecker(workflowState));

              JobEventProcessors.addJobProcessors(typedEventStreamProcessorBuilder, zeebeState);
              typedEventStreamProcessorBuilder.withListener(this);
              return typedEventStreamProcessorBuilder.build();
            });
  }

  public StreamProcessorControl getStreamProcessor() {
    return streamProcessor;
  }

  public void deploy(final BpmnModelInstance modelInstance, int deploymentKey, int version) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, modelInstance);
    final DirectBuffer xmlBuffer = new UnsafeBuffer(outStream.toByteArray());

    final DeploymentRecord record = new DeploymentRecord();
    final DirectBuffer resourceName = wrapString("resourceName");

    final Process process = modelInstance.getModelElementsByType(Process.class).iterator().next();

    record
        .resources()
        .add()
        .setResource(xmlBuffer)
        .setResourceName(resourceName)
        .setResourceType(ResourceType.BPMN_XML);

    record
        .workflows()
        .add()
        .setKey(WORKFLOW_KEY)
        .setResourceName(resourceName)
        .setBpmnProcessId(BufferUtil.wrapString(process.getId()))
        .setVersion(version);

    actor.call(() -> workflowState.putDeployment(deploymentKey, record)).join();
  }

  public void deploy(final BpmnModelInstance modelInstance) {
    deploy(modelInstance, DEPLOYMENT_KEY, VERSION);
  }

  public TypedRecord<WorkflowInstanceRecord> createAndReceiveWorkflowInstance(
      Function<WorkflowInstanceCreationRecord, WorkflowInstanceCreationRecord> transformer) {
    final TypedRecord<WorkflowInstanceCreationRecord> createdRecord =
        createWorkflowInstance(transformer);

    return awaitAndGetFirstWorkflowInstanceRecord(
        r ->
            r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATING
                && r.getKey() == createdRecord.getValue().getInstanceKey());
  }

  public TypedRecord<WorkflowInstanceCreationRecord> createWorkflowInstance(
      Function<WorkflowInstanceCreationRecord, WorkflowInstanceCreationRecord> transformer) {
    final long position =
        environmentRule.writeCommand(
            WorkflowInstanceCreationIntent.CREATE,
            transformer.apply(new WorkflowInstanceCreationRecord()));

    return awaitAndGetFirstRecord(
        ValueType.WORKFLOW_INSTANCE_CREATION,
        (e, r) ->
            e.getSourceEventPosition() == position
                && r.getMetadata().getIntent() == WorkflowInstanceCreationIntent.CREATED,
        new WorkflowInstanceCreationRecord());
  }

  public void completeFirstJob() {
    final TypedRecord<JobRecord> createCommand = awaitAndGetFirstRecordInState(JobIntent.CREATE);

    final long jobKey = environmentRule.writeEvent(JobIntent.CREATED, createCommand.getValue());
    environmentRule.writeEvent(jobKey, JobIntent.COMPLETED, createCommand.getValue());
  }

  public TypedRecord<WorkflowInstanceRecord> awaitAndGetFirstWorkflowInstanceRecord(
      Predicate<TypedRecord<WorkflowInstanceRecord>> matcher) {
    return awaitAndGetFirstRecord(
        ValueType.WORKFLOW_INSTANCE, matcher, WorkflowInstanceRecord.class);
  }

  public <T extends UnpackedObject> TypedRecord<T> awaitAndGetFirstRecord(
      ValueType valueType, Predicate<TypedRecord<T>> matcher, Class<T> valueClass) {
    return TestUtil.doRepeatedly(
            () ->
                environmentRule
                    .events()
                    .filter(r -> Records.isRecordOfType(r, valueType))
                    .map(e -> CopiedTypedEvent.toTypedEvent(e, valueClass))
                    .filter(matcher)
                    .findFirst())
        .until(Optional::isPresent)
        .orElse(null);
  }

  public <T extends UnpackedObject> TypedRecord<T> awaitAndGetFirstRecord(
      ValueType valueType, BiFunction<CopiedTypedEvent, TypedRecord<T>, Boolean> matcher, T value) {
    return (TypedRecord)
        TestUtil.doRepeatedly(
                () ->
                    environmentRule
                        .events()
                        .filter(r -> Records.isRecordOfType(r, valueType))
                        .map(e -> new CopiedTypedEvent(e, value))
                        .filter(e -> matcher.apply(e, e))
                        .findFirst())
            .until(Optional::isPresent)
            .orElse(null);
  }

  private TypedRecord<WorkflowInstanceRecord> awaitAndGetFirstRecordInState(
      final WorkflowInstanceIntent state) {
    awaitFirstRecordInState(state);
    return environmentRule
        .events()
        .onlyWorkflowInstanceRecords()
        .withIntent(state)
        .findFirst()
        .get();
  }

  private TypedRecord<JobRecord> awaitAndGetFirstRecordInState(final JobIntent state) {
    awaitFirstRecordInState(state);
    return environmentRule.events().onlyJobRecords().withIntent(state).findFirst().get();
  }

  private void awaitFirstRecordInState(final Intent state) {
    waitUntil(() -> environmentRule.events().withIntent(state).findFirst().isPresent());
  }

  public TypedRecord<WorkflowInstanceSubscriptionRecord> awaitAndGetFirstSubscriptionRejection() {
    waitUntil(
        () ->
            environmentRule
                .events()
                .onlyWorkflowInstanceSubscriptionRecords()
                .onlyRejections()
                .findFirst()
                .isPresent());

    return environmentRule
        .events()
        .onlyWorkflowInstanceSubscriptionRecords()
        .onlyRejections()
        .findFirst()
        .get();
  }

  public TypedRecord<WorkflowInstanceRecord> awaitElementInState(
      final String elementId, final WorkflowInstanceIntent intent) {
    final DirectBuffer elementIdAsBuffer = BufferUtil.wrapString(elementId);

    return TestUtil.doRepeatedly(
            () ->
                environmentRule
                    .events()
                    .onlyWorkflowInstanceRecords()
                    .withIntent(intent)
                    .filter(r -> elementIdAsBuffer.equals(r.getValue().getElementId()))
                    .findFirst())
        .until(o -> o.isPresent())
        .get();
  }

  public TypedRecord<TimerRecord> awaitTimerInState(final String timerId, final TimerIntent state) {
    final DirectBuffer handlerNodeId = wrapString(timerId);
    final Supplier<TypedRecordStream<TimerRecord>> lookupStream =
        () ->
            environmentRule
                .events()
                .onlyTimerRecords()
                .filter(r -> r.getValue().getHandlerNodeId().equals(handlerNodeId))
                .withIntent(state);

    waitUntil(() -> lookupStream.get().findFirst().isPresent());
    return lookupStream.get().findFirst().get();
  }

  public TypedRecord<JobRecord> awaitJobInState(final String activityId, final JobIntent state) {
    final DirectBuffer activityIdBuffer = wrapString(activityId);
    final Supplier<TypedRecordStream<JobRecord>> lookupStream =
        () ->
            environmentRule
                .events()
                .onlyJobRecords()
                .filter(r -> r.getValue().getHeaders().getElementId().equals(activityIdBuffer))
                .withIntent(state);

    waitUntil(() -> lookupStream.get().findFirst().isPresent());
    return lookupStream.get().findFirst().get();
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    actor = streamProcessor.getActor();
  }

  @Override
  public void onRecovered(TypedStreamProcessor streamProcessor) {
    // recovered
  }

  @Override
  public void onClose() {
    actor = null;
  }
}
