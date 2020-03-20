/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import io.zeebe.el.ExpressionLanguageFactory;
import io.zeebe.engine.processor.ProcessingContext;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.processor.workflow.deployment.DeploymentCreatedProcessor;
import io.zeebe.engine.processor.workflow.deployment.DeploymentEventProcessors;
import io.zeebe.engine.processor.workflow.deployment.distribute.DeploymentDistributeProcessor;
import io.zeebe.engine.processor.workflow.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processor.workflow.incident.IncidentEventProcessors;
import io.zeebe.engine.processor.workflow.job.JobErrorThrownProcessor;
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
import io.zeebe.util.sched.ActorControl;
import java.util.function.Consumer;

public final class EngineProcessors {

  public static TypedRecordProcessors createEngineProcessors(
      final ProcessingContext processingContext,
      final int partitionsCount,
      final SubscriptionCommandSender subscriptionCommandSender,
      final DeploymentDistributor deploymentDistributor,
      final DeploymentResponder deploymentResponder,
      final Consumer<String> onJobsAvailableCallback) {

    final var actor = processingContext.getActor();
    final ZeebeState zeebeState = processingContext.getZeebeState();
    final TypedRecordProcessors typedRecordProcessors =
        TypedRecordProcessors.processors(zeebeState.getKeyGenerator());
    final LogStream stream = processingContext.getLogStream();
    final int partitionId = stream.getPartitionId();
    final int maxFragmentSize = processingContext.getMaxFragmentSize();

    addDistributeDeploymentProcessors(
        actor, zeebeState, typedRecordProcessors, deploymentDistributor);

    final var expressionProcessor =
        new ExpressionProcessor(
            ExpressionLanguageFactory.createExpressionLanguage(),
            zeebeState.getWorkflowState().getElementInstanceState().getVariablesState());

    final CatchEventBehavior catchEventBehavior =
        new CatchEventBehavior(
            zeebeState, expressionProcessor, subscriptionCommandSender, partitionsCount);

    addDeploymentRelatedProcessorAndServices(
        catchEventBehavior, partitionId, zeebeState, typedRecordProcessors, deploymentResponder);
    addMessageProcessors(subscriptionCommandSender, zeebeState, typedRecordProcessors);

    final BpmnStepProcessor stepProcessor =
        addWorkflowProcessors(
            zeebeState,
            expressionProcessor,
            typedRecordProcessors,
            subscriptionCommandSender,
            catchEventBehavior);

    final JobErrorThrownProcessor jobErrorThrownProcessor =
        addJobProcessors(
            zeebeState, typedRecordProcessors, onJobsAvailableCallback, maxFragmentSize);

    addIncidentProcessors(
        zeebeState, stepProcessor, typedRecordProcessors, jobErrorThrownProcessor);

    return typedRecordProcessors;
  }

  private static void addDistributeDeploymentProcessors(
      final ActorControl actor,
      final ZeebeState zeebeState,
      final TypedRecordProcessors typedRecordProcessors,
      final DeploymentDistributor deploymentDistributor) {

    final DeploymentDistributeProcessor deploymentDistributeProcessor =
        new DeploymentDistributeProcessor(
            actor, zeebeState.getDeploymentState(), deploymentDistributor);

    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, deploymentDistributeProcessor);
  }

  private static BpmnStepProcessor addWorkflowProcessors(
      final ZeebeState zeebeState,
      final ExpressionProcessor expressionProcessor,
      final TypedRecordProcessors typedRecordProcessors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final CatchEventBehavior catchEventBehavior) {
    final DueDateTimerChecker timerChecker = new DueDateTimerChecker(zeebeState.getWorkflowState());
    return WorkflowEventProcessors.addWorkflowProcessors(
        zeebeState,
        expressionProcessor,
        typedRecordProcessors,
        subscriptionCommandSender,
        catchEventBehavior,
        timerChecker);
  }

  private static void addDeploymentRelatedProcessorAndServices(
      final CatchEventBehavior catchEventBehavior,
      final int partitionId,
      final ZeebeState zeebeState,
      final TypedRecordProcessors typedRecordProcessors,
      final DeploymentResponder deploymentResponder) {
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
      final ZeebeState zeebeState,
      final BpmnStepProcessor stepProcessor,
      final TypedRecordProcessors typedRecordProcessors,
      final JobErrorThrownProcessor jobErrorThrownProcessor) {
    IncidentEventProcessors.addProcessors(
        typedRecordProcessors, zeebeState, stepProcessor, jobErrorThrownProcessor);
  }

  private static JobErrorThrownProcessor addJobProcessors(
      final ZeebeState zeebeState,
      final TypedRecordProcessors typedRecordProcessors,
      final Consumer<String> onJobsAvailableCallback,
      final int maxFragmentSize) {
    return JobEventProcessors.addJobProcessors(
        typedRecordProcessors, zeebeState, onJobsAvailableCallback, maxFragmentSize);
  }

  private static void addMessageProcessors(
      final SubscriptionCommandSender subscriptionCommandSender,
      final ZeebeState zeebeState,
      final TypedRecordProcessors typedRecordProcessors) {
    MessageEventProcessors.addMessageProcessors(
        typedRecordProcessors, zeebeState, subscriptionCommandSender);
  }
}
