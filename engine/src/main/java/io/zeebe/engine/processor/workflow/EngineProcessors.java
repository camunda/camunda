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

import io.zeebe.engine.processor.ProcessingContext;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.processor.workflow.deployment.DeploymentCreatedProcessor;
import io.zeebe.engine.processor.workflow.deployment.DeploymentEventProcessors;
import io.zeebe.engine.processor.workflow.deployment.distribute.DeploymentDistributeProcessor;
import io.zeebe.engine.processor.workflow.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processor.workflow.incident.IncidentEventProcessors;
import io.zeebe.engine.processor.workflow.job.JobEventProcessors;
import io.zeebe.engine.processor.workflow.message.MessageEventProcessors;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processor.workflow.timer.DueDateTimerChecker;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;

public class EngineProcessors {

  public static TypedRecordProcessors createEngineProcessors(
      ProcessingContext processingContext,
      int partitionsCount,
      SubscriptionCommandSender subscriptionCommandSender,
      DeploymentDistributor deploymentDistributor) {

    final TypedRecordProcessors typedRecordProcessors = TypedRecordProcessors.processors();
    final LogStream stream = processingContext.getLogStream();
    final int partitionId = stream.getPartitionId();
    final ZeebeState zeebeState = processingContext.getZeebeState();

    addDistributeDeploymentProcessors(
        zeebeState, stream, typedRecordProcessors, deploymentDistributor);

    final CatchEventBehavior catchEventBehavior =
        new CatchEventBehavior(zeebeState, subscriptionCommandSender, partitionsCount);

    addDeploymentRelatedProcessorAndServices(
        catchEventBehavior, partitionId, zeebeState, typedRecordProcessors);
    addMessageProcessors(subscriptionCommandSender, zeebeState, typedRecordProcessors);

    final BpmnStepProcessor stepProcessor =
        addWorkflowProcessors(
            zeebeState, typedRecordProcessors, subscriptionCommandSender, catchEventBehavior);
    addIncidentProcessors(zeebeState, stepProcessor, typedRecordProcessors);
    addJobProcessors(zeebeState, typedRecordProcessors);

    return typedRecordProcessors;
  }

  private static void addDistributeDeploymentProcessors(
      ZeebeState zeebeState,
      LogStream stream,
      TypedRecordProcessors typedRecordProcessors,
      DeploymentDistributor deploymentDistributor) {

    final DeploymentDistributeProcessor deploymentDistributeProcessor =
        new DeploymentDistributeProcessor(
            zeebeState.getDeploymentState(),
            new LogStreamWriterImpl(stream),
            deploymentDistributor);

    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, deploymentDistributeProcessor);
  }

  private static BpmnStepProcessor addWorkflowProcessors(
      ZeebeState zeebeState,
      TypedRecordProcessors typedRecordProcessors,
      SubscriptionCommandSender subscriptionCommandSender,
      CatchEventBehavior catchEventBehavior) {
    final DueDateTimerChecker timerChecker = new DueDateTimerChecker(zeebeState.getWorkflowState());
    return WorkflowEventProcessors.addWorkflowProcessors(
        zeebeState,
        typedRecordProcessors,
        subscriptionCommandSender,
        catchEventBehavior,
        timerChecker);
  }

  private static void addDeploymentRelatedProcessorAndServices(
      CatchEventBehavior catchEventBehavior,
      int partitionId,
      ZeebeState zeebeState,
      TypedRecordProcessors typedRecordProcessors) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final boolean isDeploymentPartition = partitionId == Protocol.DEPLOYMENT_PARTITION;
    if (isDeploymentPartition) {
      DeploymentEventProcessors.addTransformingDeploymentProcessor(
          typedRecordProcessors, zeebeState, catchEventBehavior);
    } else {
      DeploymentEventProcessors.addDeploymentCreateProcessor(typedRecordProcessors, workflowState);
    }

    typedRecordProcessors.onEvent(
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATED,
        new DeploymentCreatedProcessor(workflowState, isDeploymentPartition));
  }

  private static void addIncidentProcessors(
      ZeebeState zeebeState,
      BpmnStepProcessor stepProcessor,
      TypedRecordProcessors typedRecordProcessors) {
    IncidentEventProcessors.addProcessors(typedRecordProcessors, zeebeState, stepProcessor);
  }

  private static void addJobProcessors(
      ZeebeState zeebeState, TypedRecordProcessors typedRecordProcessors) {
    JobEventProcessors.addJobProcessors(typedRecordProcessors, zeebeState);
  }

  private static void addMessageProcessors(
      SubscriptionCommandSender subscriptionCommandSender,
      ZeebeState zeebeState,
      TypedRecordProcessors typedRecordProcessors) {
    MessageEventProcessors.addMessageProcessors(
        typedRecordProcessors, zeebeState, subscriptionCommandSender);
  }
}
