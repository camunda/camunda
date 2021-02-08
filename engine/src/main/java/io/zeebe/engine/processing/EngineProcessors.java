/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing;

import io.zeebe.el.ExpressionLanguageFactory;
import io.zeebe.engine.processing.common.CatchEventBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.deployment.DeploymentCreatedProcessor;
import io.zeebe.engine.processing.deployment.DeploymentEventProcessors;
import io.zeebe.engine.processing.deployment.DeploymentResponder;
import io.zeebe.engine.processing.deployment.distribute.DeploymentDistributeProcessor;
import io.zeebe.engine.processing.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processing.incident.IncidentEventProcessors;
import io.zeebe.engine.processing.job.JobErrorThrownProcessor;
import io.zeebe.engine.processing.job.JobEventProcessors;
import io.zeebe.engine.processing.message.MessageEventProcessors;
import io.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processing.streamprocessor.ProcessingContext;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.mutable.MutableWorkflowState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
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
    final var stateWriter = processingContext.getStateWriter();
    final var commandWriter = processingContext.getCommandWriter();
    final var rejectionWriter = processingContext.getRejectionWriter();
    final TypedRecordProcessors typedRecordProcessors =
        TypedRecordProcessors.processors(
            zeebeState.getKeyGenerator(), stateWriter, commandWriter, rejectionWriter);

    final LogStream stream = processingContext.getLogStream();
    final int partitionId = stream.getPartitionId();
    final int maxFragmentSize = processingContext.getMaxFragmentSize();

    addDistributeDeploymentProcessors(
        actor, zeebeState, typedRecordProcessors, deploymentDistributor, stateWriter);

    final var variablesState = zeebeState.getVariableState();
    final var expressionProcessor =
        new ExpressionProcessor(
            ExpressionLanguageFactory.createExpressionLanguage(), variablesState::getVariable);

    final CatchEventBehavior catchEventBehavior =
        new CatchEventBehavior(
            zeebeState, expressionProcessor, subscriptionCommandSender, partitionsCount);

    addDeploymentRelatedProcessorAndServices(
        catchEventBehavior,
        partitionId,
        zeebeState,
        typedRecordProcessors,
        deploymentResponder,
        expressionProcessor,
        stateWriter);
    addMessageProcessors(subscriptionCommandSender, zeebeState, typedRecordProcessors, stateWriter);

    final TypedRecordProcessor<WorkflowInstanceRecord> bpmnStreamProcessor =
        addWorkflowProcessors(
            zeebeState,
            expressionProcessor,
            typedRecordProcessors,
            subscriptionCommandSender,
            catchEventBehavior,
            stateWriter);

    final JobErrorThrownProcessor jobErrorThrownProcessor =
        addJobProcessors(
            zeebeState,
            typedRecordProcessors,
            onJobsAvailableCallback,
            maxFragmentSize,
            stateWriter);

    addIncidentProcessors(
        zeebeState,
        bpmnStreamProcessor,
        typedRecordProcessors,
        jobErrorThrownProcessor,
        stateWriter);

    return typedRecordProcessors;
  }

  private static void addDistributeDeploymentProcessors(
      final ActorControl actor,
      final ZeebeState zeebeState,
      final TypedRecordProcessors typedRecordProcessors,
      final DeploymentDistributor deploymentDistributor,
      final StateWriter stateWriter) {

    final DeploymentDistributeProcessor deploymentDistributeProcessor =
        new DeploymentDistributeProcessor(
            actor, zeebeState.getDeploymentState(), deploymentDistributor);

    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, deploymentDistributeProcessor);
  }

  private static TypedRecordProcessor<WorkflowInstanceRecord> addWorkflowProcessors(
      final ZeebeState zeebeState,
      final ExpressionProcessor expressionProcessor,
      final TypedRecordProcessors typedRecordProcessors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final CatchEventBehavior catchEventBehavior,
      final StateWriter stateWriter) {
    final DueDateTimerChecker timerChecker = new DueDateTimerChecker(zeebeState.getTimerState());
    return WorkflowEventProcessors.addWorkflowProcessors(
        zeebeState,
        expressionProcessor,
        typedRecordProcessors,
        subscriptionCommandSender,
        catchEventBehavior,
        timerChecker,
        stateWriter);
  }

  private static void addDeploymentRelatedProcessorAndServices(
      final CatchEventBehavior catchEventBehavior,
      final int partitionId,
      final ZeebeState zeebeState,
      final TypedRecordProcessors typedRecordProcessors,
      final DeploymentResponder deploymentResponder,
      final ExpressionProcessor expressionProcessor,
      final StateWriter stateWriter) {
    final MutableWorkflowState workflowState = zeebeState.getWorkflowState();
    final boolean isDeploymentPartition = partitionId == Protocol.DEPLOYMENT_PARTITION;
    if (isDeploymentPartition) {
      DeploymentEventProcessors.addTransformingDeploymentProcessor(
          typedRecordProcessors, zeebeState, catchEventBehavior, expressionProcessor);
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
      final TypedRecordProcessor<WorkflowInstanceRecord> bpmnStreamProcessor,
      final TypedRecordProcessors typedRecordProcessors,
      final JobErrorThrownProcessor jobErrorThrownProcessor,
      final StateWriter stateWriter) {
    IncidentEventProcessors.addProcessors(
        typedRecordProcessors, zeebeState, bpmnStreamProcessor, jobErrorThrownProcessor);
  }

  private static JobErrorThrownProcessor addJobProcessors(
      final ZeebeState zeebeState,
      final TypedRecordProcessors typedRecordProcessors,
      final Consumer<String> onJobsAvailableCallback,
      final int maxFragmentSize,
      final StateWriter stateWriter) {
    return JobEventProcessors.addJobProcessors(
        typedRecordProcessors, zeebeState, onJobsAvailableCallback, maxFragmentSize);
  }

  private static void addMessageProcessors(
      final SubscriptionCommandSender subscriptionCommandSender,
      final ZeebeState zeebeState,
      final TypedRecordProcessors typedRecordProcessors,
      final StateWriter stateWriter) {
    MessageEventProcessors.addMessageProcessors(
        typedRecordProcessors, zeebeState, subscriptionCommandSender);
  }
}
