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

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.engine.processor.TypedEventImpl;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.workflow.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processor.workflow.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.util.CopiedTypedEvent;
import io.zeebe.engine.util.RecordStream;
import io.zeebe.engine.util.Records;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.engine.util.TypedRecordStream;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class EngineRule extends ExternalResource {

  private final StreamProcessorRule environmentRule =
      new StreamProcessorRule(Protocol.DEPLOYMENT_PARTITION);

  private SubscriptionCommandSender mockSubscriptionCommandSender;
  private DeploymentDistributor mockDeploymentDistributor;

  @Override
  public Statement apply(Statement base, Description description) {
    final Statement statement = super.apply(base, description);
    return environmentRule.apply(statement, description);
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

    mockDeploymentDistributor = mock(DeploymentDistributor.class);

    when(mockDeploymentDistributor.pushDeployment(anyLong(), anyLong(), any()))
        .thenReturn(CompletableActorFuture.completed(null));

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    final UnsafeBuffer deploymentBuffer = new UnsafeBuffer(new byte[deploymentRecord.getLength()]);
    deploymentRecord.write(deploymentBuffer, 0);

    final PendingDeploymentDistribution deploymentDistribution =
        mock(PendingDeploymentDistribution.class);
    when(deploymentDistribution.getDeployment()).thenReturn(deploymentBuffer);
    when(mockDeploymentDistributor.removePendingDeployment(anyLong()))
        .thenReturn(deploymentDistribution);

    environmentRule.startTypedStreamProcessor(
        (processingContext) ->
            EngineProcessors.createEngineProcessors(
                processingContext, 1, mockSubscriptionCommandSender, mockDeploymentDistributor));
  }

  public TypedRecord<DeploymentRecord> deploy(final BpmnModelInstance modelInstance) {
    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString("process.bpmn"))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)))
        .setResourceType(ResourceType.BPMN_XML);

    environmentRule.writeCommand(DeploymentIntent.CREATE, deploymentRecord);

    return awaitRecord(ValueType.DEPLOYMENT, DeploymentIntent.CREATED, DeploymentRecord.class);
  }

  public TypedRecord<WorkflowInstanceCreationRecord> createWorkflowInstance(
      Function<WorkflowInstanceCreationRecord, WorkflowInstanceCreationRecord> transformer) {
    final long position =
        environmentRule.writeCommand(
            WorkflowInstanceCreationIntent.CREATE,
            transformer.apply(new WorkflowInstanceCreationRecord()));

    return awaitRecord(
        ValueType.WORKFLOW_INSTANCE_CREATION,
        WorkflowInstanceCreationIntent.CREATED,
        (e) -> ((TypedEventImpl) e).getSourceEventPosition() == position,
        WorkflowInstanceCreationRecord.class);
  }

  public RecordStream events() {
    return environmentRule.events();
  }

  public void completeJobOfType(String type) {
    final TypedRecord<JobRecord> createdEvent = awaitJobInState(type, JobIntent.CREATED);

    environmentRule.writeCommand(
        createdEvent.getKey(), JobIntent.COMPLETE, createdEvent.getValue());
    awaitJobInState(type, JobIntent.COMPLETED);
  }

  private TypedRecord<JobRecord> awaitJobInState(final String type, final JobIntent state) {
    return awaitRecord(ValueType.JOB, state, withJobType(wrapString(type)), JobRecord.class);
  }

  public TypedRecord<WorkflowInstanceRecord> awaitWorkflowInstanceRecord(
      String elementId, Intent intent) {
    return awaitRecord(
        ValueType.WORKFLOW_INSTANCE,
        intent,
        withElementId(wrapString(elementId)),
        WorkflowInstanceRecord.class);
  }

  public List<TypedRecord<WorkflowInstanceRecord>> collectWorkflowInstanceRecords(
      WorkflowInstanceIntent intent, BpmnElementType elementType, int amount) {
    return collectWorkflowInstanceRecords(intent, withBpmElementType(elementType), amount);
  }

  public List<TypedRecord<WorkflowInstanceRecord>> collectWorkflowInstanceRecords(
      Intent intent, Predicate<TypedRecord<WorkflowInstanceRecord>> matcher, int amount) {
    return collectRecords(
        ValueType.WORKFLOW_INSTANCE, intent, matcher, WorkflowInstanceRecord.class, amount);
  }

  public List<TypedRecord<WorkflowInstanceRecord>> collectWorkflowInstanceRecordsUntilCompletion() {
    return collectRecords(
        ValueType.WORKFLOW_INSTANCE,
        r ->
            r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETED
                && r.getKey() == r.getValue().getWorkflowInstanceKey(),
        WorkflowInstanceRecord.class);
  }

  public List<TypedRecord<WorkflowInstanceRecord>> collectWorkflowInstanceRecordsUntil(
      Predicate<TypedRecord<WorkflowInstanceRecord>> abortCondition) {
    return collectRecords(
        ValueType.WORKFLOW_INSTANCE, abortCondition, WorkflowInstanceRecord.class);
  }

  public List<TypedRecord<WorkflowInstanceRecord>> collectWorkflowInstanceRecords(
      Intent intent, Predicate<TypedRecord<WorkflowInstanceRecord>> abortCondition) {
    return collectRecords(
        ValueType.WORKFLOW_INSTANCE, intent, abortCondition, WorkflowInstanceRecord.class);
  }

  private <T extends UnpackedObject> List<TypedRecord<T>> collectRecords(
      ValueType valueType, Predicate<TypedRecord<T>> abortCondition, Class<T> valueClass) {
    return TestUtil.doRepeatedly(
            () ->
                new TypedRecordStream<T>(
                        environmentRule
                            .events()
                            .filter(r -> Records.isRecordOfType(r, valueType))
                            .map(e -> CopiedTypedEvent.toTypedEvent(e, valueClass)))
                    .limit(abortCondition)
                    .collect(Collectors.toList()))
        .until(typedRecords -> typedRecords.stream().anyMatch(abortCondition));
  }

  private <T extends UnpackedObject> List<TypedRecord<T>> collectRecords(
      ValueType valueType,
      Intent intent,
      Predicate<TypedRecord<T>> abortCondition,
      Class<T> valueClass) {
    return TestUtil.doRepeatedly(
            () ->
                new TypedRecordStream<T>(
                        environmentRule
                            .events()
                            .filter(r -> Records.isEvent(r, valueType, intent))
                            .map(e -> CopiedTypedEvent.toTypedEvent(e, valueClass)))
                    .limit(abortCondition)
                    .collect(Collectors.toList()))
        .until(typedRecords -> typedRecords.stream().anyMatch(abortCondition));
  }

  private <T extends UnpackedObject> List<TypedRecord<T>> collectRecords(
      ValueType valueType,
      Intent intent,
      Predicate<TypedRecord<T>> matcher,
      Class<T> valueClass,
      int amount) {
    return TestUtil.doRepeatedly(
            () ->
                environmentRule
                    .events()
                    .filter(r -> Records.isEvent(r, valueType, intent))
                    .map(e -> CopiedTypedEvent.toTypedEvent(e, valueClass))
                    .filter(matcher)
                    .limit(amount)
                    .collect(Collectors.toList()))
        .until(typedRecords -> typedRecords.size() >= amount);
  }

  private <T extends UnpackedObject> TypedRecord<T> awaitRecord(
      ValueType valueType, Intent intent, Class<T> recordClass) {
    return awaitRecord(valueType, intent, (r) -> true, recordClass);
  }

  private <T extends UnpackedObject> TypedRecord<T> awaitRecord(
      ValueType valueType, Intent intent, Predicate<TypedRecord<T>> matcher, Class<T> valueClass) {
    return TestUtil.doRepeatedly(
            () ->
                environmentRule
                    .events()
                    .filter(r -> Records.isEvent(r, valueType, intent))
                    .map(e -> CopiedTypedEvent.toTypedEvent(e, valueClass))
                    .filter(matcher)
                    .findFirst())
        .until(Optional::isPresent)
        .orElse(null);
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////// PREDICATES /////////////////////////////////////////////
  /////////////////////////////////////////////////////////////////////////////////////////////////

  private Predicate<TypedRecord<JobRecord>> withJobType(DirectBuffer type) {
    return r -> r.getValue().getType().equals(type);
  }

  private static Predicate<TypedRecord<WorkflowInstanceRecord>> withBpmElementType(
      BpmnElementType bpmElementType) {
    return r -> r.getValue().getBpmnElementType() == bpmElementType;
  }

  private static Predicate<TypedRecord<WorkflowInstanceRecord>> withElementId(
      DirectBuffer elementId) {
    return r -> r.getValue().getElementId().equals(elementId);
  }
}
