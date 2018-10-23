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

import static io.zeebe.protocol.intent.DeploymentIntent.CREATE;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.workflow.processor.deployment.DeploymentCreateProcessor;
import io.zeebe.broker.workflow.processor.deployment.TransformingDeploymentCreateProcessor;
import io.zeebe.broker.workflow.processor.job.JobCompletedEventProcessor;
import io.zeebe.broker.workflow.processor.job.JobCreatedProcessor;
import io.zeebe.broker.workflow.processor.message.CorrelateWorkflowInstanceSubscription;
import io.zeebe.broker.workflow.processor.message.OpenWorkflowInstanceSubscriptionProcessor;
import io.zeebe.broker.workflow.state.WorkflowEngineState;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import java.util.function.Consumer;

public class WorkflowInstanceStreamProcessor implements StreamProcessorLifecycleAware {

  private TypedStreamReader streamReader;

  private final TopologyManager topologyManager;
  private final WorkflowState workflowState;
  private final SubscriptionCommandSender subscriptionCommandSender;
  private final Consumer<StreamProcessorContext> onRecoveredCallback;
  private final Runnable onClosedCallback;

  public WorkflowInstanceStreamProcessor(
      final WorkflowState workflowState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final TopologyManager topologyManager) {
    this((ctx) -> {}, () -> {}, workflowState, subscriptionCommandSender, topologyManager);
  }

  public WorkflowInstanceStreamProcessor(
      final Consumer<StreamProcessorContext> onRecoveredCallback,
      final Runnable onClosedCallback,
      final WorkflowState workflowState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final TopologyManager topologyManager) {
    this.onRecoveredCallback = onRecoveredCallback;
    this.onClosedCallback = onClosedCallback;
    this.workflowState = workflowState;
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.topologyManager = topologyManager;
  }

  public TypedStreamProcessor createStreamProcessor(final TypedStreamEnvironment environment) {
    final int partitionId = environment.getStream().getPartitionId();
    final KeyGenerator keyGenerator =
        KeyGenerator.createWorkflowInstanceKeyGenerator(partitionId, workflowState);
    final WorkflowEngineState engineState = new WorkflowEngineState(workflowState);

    final TypedEventStreamProcessorBuilder streamProcessorBuilder =
        environment.newStreamProcessor().keyGenerator(keyGenerator).withListener(engineState);

    addWorkflowInstanceCommandProcessor(streamProcessorBuilder, engineState, keyGenerator);
    addBpmnStepProcessor(streamProcessorBuilder, engineState);
    addJobStreamProcessors(streamProcessorBuilder);
    addMessageStreamProcessors(streamProcessorBuilder);
    addDeploymentStreamProcessors(streamProcessorBuilder, partitionId);

    return streamProcessorBuilder.withStateController(workflowState).withListener(this).build();
  }

  public StateSnapshotController createSnapshotController(final StateStorage storage) {
    return new StateSnapshotController(workflowState, storage);
  }

  private void addDeploymentStreamProcessors(
      final TypedEventStreamProcessorBuilder streamProcessorBuilder, final int partitionId) {

    final TypedRecordProcessor<?> processor =
        Protocol.DEPLOYMENT_PARTITION == partitionId
            ? new TransformingDeploymentCreateProcessor(this.workflowState)
            : new DeploymentCreateProcessor(this.workflowState);

    streamProcessorBuilder.onCommand(ValueType.DEPLOYMENT, CREATE, processor);
  }

  private void addWorkflowInstanceCommandProcessor(
      final TypedEventStreamProcessorBuilder builder,
      WorkflowEngineState workflowEngineState,
      KeyGenerator keyGenerator) {

    final WorkflowInstanceCommandProcessor commandProcessor =
        new WorkflowInstanceCommandProcessor(keyGenerator, workflowEngineState);

    builder
        .onCommand(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE, commandProcessor)
        .onCommand(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL, commandProcessor)
        .onCommand(
            ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.UPDATE_PAYLOAD, commandProcessor);
  }

  private void addBpmnStepProcessor(
      TypedEventStreamProcessorBuilder streamProcessorBuilder,
      WorkflowEngineState workflowEngineState) {
    final BpmnStepProcessor bpmnStepProcessor =
        new BpmnStepProcessor(workflowEngineState, subscriptionCommandSender);

    streamProcessorBuilder
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_READY, bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.START_EVENT_OCCURRED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.END_EVENT_OCCURRED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.GATEWAY_ACTIVATED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_TERMINATING,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_TERMINATED,
            bpmnStepProcessor);
  }

  private void addMessageStreamProcessors(
      final TypedEventStreamProcessorBuilder streamProcessorBuilder) {
    streamProcessorBuilder
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.OPEN,
            new OpenWorkflowInstanceSubscriptionProcessor(workflowState))
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.CORRELATE,
            new CorrelateWorkflowInstanceSubscription(
                topologyManager, workflowState, subscriptionCommandSender));
  }

  private void addJobStreamProcessors(
      final TypedEventStreamProcessorBuilder streamProcessorBuilder) {
    streamProcessorBuilder
        .onEvent(ValueType.JOB, JobIntent.CREATED, new JobCreatedProcessor(workflowState))
        .onEvent(ValueType.JOB, JobIntent.COMPLETED, new JobCompletedEventProcessor(workflowState));
  }

  @Override
  public void onOpen(final TypedStreamProcessor streamProcessor) {
    this.streamReader = streamProcessor.getEnvironment().buildStreamReader();
  }

  @Override
  public void onRecovered(final TypedStreamProcessor streamProcessor) {
    onRecoveredCallback.accept(streamProcessor.getStreamProcessorContext());
  }

  @Override
  public void onClose() {
    onClosedCallback.run();
    streamReader.close();
  }
}
