/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing;

import static io.camunda.zeebe.protocol.record.intent.DeploymentIntent.CREATE;

import io.camunda.zeebe.dmn.DecisionEngineFactory;
import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviorsImpl;
import io.camunda.zeebe.engine.processing.common.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.common.DecisionBehavior;
import io.camunda.zeebe.engine.processing.deployment.DeploymentCreateProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.CompleteDeploymentDistributionProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributeProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCommandSender;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentRedistributor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionAcknowledgeProcessor;
import io.camunda.zeebe.engine.processing.dmn.EvaluateDecisionProcessor;
import io.camunda.zeebe.engine.processing.incident.IncidentEventProcessors;
import io.camunda.zeebe.engine.processing.job.JobEventProcessors;
import io.camunda.zeebe.engine.processing.message.MessageEventProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.resource.ResourceDeletionProcessor;
import io.camunda.zeebe.engine.processing.signal.SignalBroadcastProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContext;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.camunda.zeebe.engine.state.ScheduledTaskDbState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.migration.DbMigrationController;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.FeatureFlags;

public final class EngineProcessors {

  private EngineProcessors() {}

  public static TypedRecordProcessors createEngineProcessors(
      final TypedRecordProcessorContext typedRecordProcessorContext,
      final int partitionsCount,
      final SubscriptionCommandSender subscriptionCommandSender,
      final InterPartitionCommandSender interPartitionCommandSender,
      final FeatureFlags featureFlags) {

    final var processingState = typedRecordProcessorContext.getProcessingState();
    final var writers = typedRecordProcessorContext.getWriters();
    final TypedRecordProcessors typedRecordProcessors =
        TypedRecordProcessors.processors(processingState.getKeyGenerator(), writers);

    // register listener that handles migrations immediately, so it is the first to be called
    typedRecordProcessors.withListener(new DbMigrationController(processingState));

    typedRecordProcessors.withListener(processingState);

    final int partitionId = typedRecordProcessorContext.getPartitionId();

    final DueDateTimerChecker timerChecker =
        new DueDateTimerChecker(processingState.getTimerState(), featureFlags);

    final var jobMetrics = new JobMetrics(partitionId);
    final var processEngineMetrics = new ProcessEngineMetrics(processingState.getPartitionId());

    subscriptionCommandSender.setWriters(writers);

    final var decisionBehavior =
        new DecisionBehavior(
            DecisionEngineFactory.createDecisionEngine(), processingState, processEngineMetrics);
    final BpmnBehaviorsImpl bpmnBehaviors =
        createBehaviors(
            processingState,
            writers,
            subscriptionCommandSender,
            partitionsCount,
            timerChecker,
            jobMetrics,
            decisionBehavior);

    final DeploymentDistributionCommandSender deploymentDistributionCommandSender =
        new DeploymentDistributionCommandSender(
            typedRecordProcessorContext.getPartitionId(), interPartitionCommandSender);
    // TODO unused for now, will be used with the implementation of
    // https://github.com/camunda/zeebe/issues/11661
    final var commandDistributionBehavior =
        new CommandDistributionBehavior(
            writers,
            typedRecordProcessorContext.getPartitionId(),
            partitionsCount,
            interPartitionCommandSender,
            processingState.getKeyGenerator());

    addDeploymentRelatedProcessorAndServices(
        bpmnBehaviors,
        processingState,
        typedRecordProcessors,
        writers,
        partitionsCount,
        deploymentDistributionCommandSender,
        processingState.getKeyGenerator());
    addMessageProcessors(
        bpmnBehaviors,
        subscriptionCommandSender,
        processingState,
        typedRecordProcessorContext.getScheduledTaskDbState(),
        typedRecordProcessors,
        writers);

    final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor =
        addProcessProcessors(
            processingState,
            bpmnBehaviors,
            typedRecordProcessors,
            subscriptionCommandSender,
            writers,
            timerChecker);

    addDecisionProcessors(typedRecordProcessors, decisionBehavior, writers, processingState);

    JobEventProcessors.addJobProcessors(
        typedRecordProcessors, processingState, bpmnBehaviors, writers, jobMetrics);

    addIncidentProcessors(processingState, bpmnStreamProcessor, typedRecordProcessors, writers);
    addResourceDeletionProcessors(typedRecordProcessors, writers, processingState);
    addSignalBroadcastProcessors(typedRecordProcessors, bpmnBehaviors, writers, processingState);
    addCommandDistributionProcessors(typedRecordProcessors, writers, processingState);

    return typedRecordProcessors;
  }

  private static BpmnBehaviorsImpl createBehaviors(
      final MutableProcessingState processingState,
      final Writers writers,
      final SubscriptionCommandSender subscriptionCommandSender,
      final int partitionsCount,
      final DueDateTimerChecker timerChecker,
      final JobMetrics jobMetrics,
      final DecisionBehavior decisionBehavior) {
    return new BpmnBehaviorsImpl(
        processingState,
        writers,
        jobMetrics,
        decisionBehavior,
        subscriptionCommandSender,
        partitionsCount,
        timerChecker);
  }

  private static TypedRecordProcessor<ProcessInstanceRecord> addProcessProcessors(
      final MutableProcessingState processingState,
      final BpmnBehaviorsImpl bpmnBehaviors,
      final TypedRecordProcessors typedRecordProcessors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Writers writers,
      final DueDateTimerChecker timerChecker) {
    return ProcessEventProcessors.addProcessProcessors(
        processingState,
        bpmnBehaviors,
        typedRecordProcessors,
        subscriptionCommandSender,
        timerChecker,
        writers);
  }

  private static void addDeploymentRelatedProcessorAndServices(
      final BpmnBehaviorsImpl bpmnBehaviors,
      final ProcessingState processingState,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final int partitionsCount,
      final DeploymentDistributionCommandSender deploymentDistributionCommandSender,
      final KeyGenerator keyGenerator) {

    // on deployment partition CREATE Command is received and processed
    // it will cause a distribution to other partitions
    final var processor =
        new DeploymentCreateProcessor(
            processingState,
            bpmnBehaviors,
            partitionsCount,
            writers,
            deploymentDistributionCommandSender,
            keyGenerator);
    typedRecordProcessors.onCommand(ValueType.DEPLOYMENT, CREATE, processor);

    // periodically retries deployment distribution
    final var deploymentRedistributor =
        new DeploymentRedistributor(
            deploymentDistributionCommandSender, processingState.getDeploymentState());
    typedRecordProcessors.withListener(deploymentRedistributor);

    // on other partitions DISTRIBUTE command is received and processed
    final DeploymentDistributeProcessor deploymentDistributeProcessor =
        new DeploymentDistributeProcessor(
            processingState, deploymentDistributionCommandSender, writers, keyGenerator);
    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, deploymentDistributeProcessor);

    // completes the deployment distribution
    final var completeDeploymentDistributionProcessor =
        new CompleteDeploymentDistributionProcessor(processingState.getDeploymentState(), writers);
    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT_DISTRIBUTION,
        DeploymentDistributionIntent.COMPLETE,
        completeDeploymentDistributionProcessor);
  }

  private static void addIncidentProcessors(
      final ProcessingState processingState,
      final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers) {
    IncidentEventProcessors.addProcessors(
        typedRecordProcessors, processingState, bpmnStreamProcessor, writers);
  }

  private static void addMessageProcessors(
      final BpmnBehaviorsImpl bpmnBehaviors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final MutableProcessingState processingState,
      final ScheduledTaskDbState scheduledTaskDbState,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers) {
    MessageEventProcessors.addMessageProcessors(
        bpmnBehaviors,
        typedRecordProcessors,
        processingState,
        scheduledTaskDbState,
        subscriptionCommandSender,
        writers);
  }

  private static void addDecisionProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final DecisionBehavior decisionBehavior,
      final Writers writers,
      final MutableProcessingState processingState) {

    final EvaluateDecisionProcessor evaluateDecisionProcessor =
        new EvaluateDecisionProcessor(decisionBehavior, processingState.getKeyGenerator(), writers);
    typedRecordProcessors.onCommand(
        ValueType.DECISION_EVALUATION,
        DecisionEvaluationIntent.EVALUATE,
        evaluateDecisionProcessor);
  }

  private static void addResourceDeletionProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final MutableProcessingState processingState) {
    final var resourceDeletionProcessor =
        new ResourceDeletionProcessor(
            writers, processingState.getKeyGenerator(), processingState.getDecisionState());
    typedRecordProcessors.onCommand(
        ValueType.RESOURCE_DELETION, ResourceDeletionIntent.DELETE, resourceDeletionProcessor);
  }

  private static void addSignalBroadcastProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final BpmnBehaviorsImpl bpmnBehaviors,
      final Writers writers,
      final MutableProcessingState processingState) {
    final var signalBroadcastProcessor =
        new SignalBroadcastProcessor(
            writers,
            processingState.getKeyGenerator(),
            processingState.getEventScopeInstanceState(),
            processingState.getProcessState(),
            bpmnBehaviors.stateBehavior(),
            bpmnBehaviors.eventTriggerBehavior(),
            processingState.getSignalSubscriptionState());
    typedRecordProcessors.onCommand(
        ValueType.SIGNAL, SignalIntent.BROADCAST, signalBroadcastProcessor);
  }

  private static void addCommandDistributionProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final ProcessingState processingState) {
    final var commandDistributionAcknowledgeProcessor =
        new CommandDistributionAcknowledgeProcessor(
            processingState.getDistributionState(), writers);
    typedRecordProcessors.onCommand(
        ValueType.COMMAND_DISTRIBUTION,
        CommandDistributionIntent.ACKNOWLEDGE,
        commandDistributionAcknowledgeProcessor);
  }
}
