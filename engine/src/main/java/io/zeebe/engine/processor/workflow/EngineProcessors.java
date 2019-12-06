/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import java.util.function.Consumer;

public class EngineProcessors {

  public static TypedRecordProcessors createEngineProcessors(
      ProcessingContext processingContext,
      int partitionsCount,
      SubscriptionCommandSender subscriptionCommandSender,
      DeploymentDistributor deploymentDistributor,
      DeploymentResponder deploymentResponder,
      Consumer<String> onJobsAvailableCallback) {

    final ZeebeState zeebeState = processingContext.getZeebeState();
    final TypedRecordProcessors typedRecordProcessors = TypedRecordProcessors.processors();
    final LogStream stream = processingContext.getLogStream();
    final int partitionId = stream.getPartitionId();
    final int maxFragmentSize = processingContext.getMaxFragmentSize();

    addDistributeDeploymentProcessors(zeebeState, typedRecordProcessors, deploymentDistributor);

    final CatchEventBehavior catchEventBehavior =
        new CatchEventBehavior(zeebeState, subscriptionCommandSender, partitionsCount);

    addDeploymentRelatedProcessorAndServices(
        catchEventBehavior, partitionId, zeebeState, typedRecordProcessors, deploymentResponder);
    addMessageProcessors(subscriptionCommandSender, zeebeState, typedRecordProcessors);

    final BpmnStepProcessor stepProcessor =
        addWorkflowProcessors(
            zeebeState, typedRecordProcessors, subscriptionCommandSender, catchEventBehavior);
    addIncidentProcessors(zeebeState, stepProcessor, typedRecordProcessors);
    addJobProcessors(zeebeState, typedRecordProcessors, onJobsAvailableCallback, maxFragmentSize);

    return typedRecordProcessors;
  }

  private static void addDistributeDeploymentProcessors(
      ZeebeState zeebeState,
      TypedRecordProcessors typedRecordProcessors,
      DeploymentDistributor deploymentDistributor) {

    final DeploymentDistributeProcessor deploymentDistributeProcessor =
        new DeploymentDistributeProcessor(zeebeState.getDeploymentState(), deploymentDistributor);

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
      TypedRecordProcessors typedRecordProcessors,
      DeploymentResponder deploymentResponder) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final boolean isDeploymentPartition = partitionId == Protocol.DEPLOYMENT_PARTITION;
    if (isDeploymentPartition) {
      DeploymentEventProcessors.addTransformingDeploymentProcessor(
          typedRecordProcessors, zeebeState, catchEventBehavior);
    } else {
      DeploymentEventProcessors.addDeploymentCreateProcessor(
          typedRecordProcessors, workflowState, deploymentResponder, partitionId);
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
      ZeebeState zeebeState,
      TypedRecordProcessors typedRecordProcessors,
      Consumer<String> onJobsAvailableCallback,
      int maxFragmentSize) {
    JobEventProcessors.addJobProcessors(
        typedRecordProcessors, zeebeState, onJobsAvailableCallback, maxFragmentSize);
  }

  private static void addMessageProcessors(
      SubscriptionCommandSender subscriptionCommandSender,
      ZeebeState zeebeState,
      TypedRecordProcessors typedRecordProcessors) {
    MessageEventProcessors.addMessageProcessors(
        typedRecordProcessors, zeebeState, subscriptionCommandSender);
  }
}
