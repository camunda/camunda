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
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviorsImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.common.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.common.DecisionBehavior;
import io.camunda.zeebe.engine.processing.deployment.DeploymentCreateProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributeProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCommandSender;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCompleteProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentRedistributor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionAcknowledgeProcessor;
import io.camunda.zeebe.engine.processing.distribution.CommandRedistributor;
import io.camunda.zeebe.engine.processing.dmn.DecisionEvaluationEvaluteProcessor;
import io.camunda.zeebe.engine.processing.incident.IncidentEventProcessors;
import io.camunda.zeebe.engine.processing.job.JobEventProcessors;
import io.camunda.zeebe.engine.processing.message.MessageEventProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.resource.ResourceDeletionDeleteProcessor;
import io.camunda.zeebe.engine.processing.signal.SignalBroadcastProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContext;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.camunda.zeebe.engine.processing.usertask.UserTaskEventProcessors;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
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
import java.util.function.Supplier;

public final class EngineProcessors {

  private EngineProcessors() {}

  public static TypedRecordProcessors createEngineProcessors(
      final TypedRecordProcessorContext typedRecordProcessorContext,
      final int partitionsCount,
      final SubscriptionCommandSender subscriptionCommandSender,
      final InterPartitionCommandSender interPartitionCommandSender,
      final FeatureFlags featureFlags,
      final JobStreamer jobStreamer) {

    final var processingState = typedRecordProcessorContext.getProcessingState();
    final var scheduledTaskStateFactory =
        typedRecordProcessorContext.getScheduledTaskStateFactory();
    final var writers = typedRecordProcessorContext.getWriters();
    final TypedRecordProcessors typedRecordProcessors =
        TypedRecordProcessors.processors(processingState.getKeyGenerator(), writers);

    typedRecordProcessors.withListener(processingState);

    final int partitionId = typedRecordProcessorContext.getPartitionId();
    final var config = typedRecordProcessorContext.getConfig();

    final DueDateTimerChecker timerChecker =
        new DueDateTimerChecker(scheduledTaskStateFactory.get().getTimerState(), featureFlags);

    final var jobMetrics = new JobMetrics(partitionId);
    final var processEngineMetrics = new ProcessEngineMetrics(processingState.getPartitionId());

    subscriptionCommandSender.setWriters(writers);

    final var decisionBehavior =
        new DecisionBehavior(
            DecisionEngineFactory.createDecisionEngine(), processingState, processEngineMetrics);
    final var transientProcessMessageSubscriptionState =
        typedRecordProcessorContext.getTransientProcessMessageSubscriptionState();
    final BpmnBehaviorsImpl bpmnBehaviors =
        createBehaviors(
            processingState,
            writers,
            subscriptionCommandSender,
            partitionsCount,
            timerChecker,
            jobStreamer,
            jobMetrics,
            decisionBehavior,
            transientProcessMessageSubscriptionState);

    final var commandDistributionBehavior =
        new CommandDistributionBehavior(
            writers,
            typedRecordProcessorContext.getPartitionId(),
            partitionsCount,
            interPartitionCommandSender);

    final var deploymentDistributionCommandSender =
        new DeploymentDistributionCommandSender(
            typedRecordProcessorContext.getPartitionId(), interPartitionCommandSender);
    addDeploymentRelatedProcessorAndServices(
        bpmnBehaviors,
        processingState,
        scheduledTaskStateFactory,
        typedRecordProcessors,
        writers,
        deploymentDistributionCommandSender,
        processingState.getKeyGenerator(),
        featureFlags,
        commandDistributionBehavior,
        config);
    addMessageProcessors(
        bpmnBehaviors,
        subscriptionCommandSender,
        processingState,
        scheduledTaskStateFactory,
        typedRecordProcessors,
        writers,
        config,
        featureFlags);

    final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor =
        addProcessProcessors(
            processingState,
            scheduledTaskStateFactory,
            bpmnBehaviors,
            typedRecordProcessors,
            subscriptionCommandSender,
            writers,
            timerChecker,
            transientProcessMessageSubscriptionState,
            config);

    addDecisionProcessors(typedRecordProcessors, decisionBehavior, writers, processingState);

    JobEventProcessors.addJobProcessors(
        typedRecordProcessors,
        processingState,
        scheduledTaskStateFactory,
        bpmnBehaviors,
        writers,
        jobMetrics,
        config);

    addIncidentProcessors(
        processingState,
        bpmnStreamProcessor,
        typedRecordProcessors,
        writers,
        bpmnBehaviors.jobActivationBehavior());
    addResourceDeletionProcessors(
        typedRecordProcessors,
        writers,
        processingState,
        commandDistributionBehavior,
        bpmnBehaviors);
    addSignalBroadcastProcessors(
        typedRecordProcessors,
        bpmnBehaviors,
        writers,
        processingState,
        commandDistributionBehavior);
    addCommandDistributionProcessors(
        typedRecordProcessors,
        writers,
        processingState,
        scheduledTaskStateFactory,
        interPartitionCommandSender);

    UserTaskEventProcessors.addUserTaskProcessors(
        typedRecordProcessors, processingState, bpmnBehaviors, writers);

    return typedRecordProcessors;
  }

  private static BpmnBehaviorsImpl createBehaviors(
      final MutableProcessingState processingState,
      final Writers writers,
      final SubscriptionCommandSender subscriptionCommandSender,
      final int partitionsCount,
      final DueDateTimerChecker timerChecker,
      final JobStreamer jobStreamer,
      final JobMetrics jobMetrics,
      final DecisionBehavior decisionBehavior,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState) {
    return new BpmnBehaviorsImpl(
        processingState,
        writers,
        jobMetrics,
        decisionBehavior,
        subscriptionCommandSender,
        partitionsCount,
        timerChecker,
        jobStreamer,
        transientProcessMessageSubscriptionState);
  }

  private static TypedRecordProcessor<ProcessInstanceRecord> addProcessProcessors(
      final MutableProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskState,
      final BpmnBehaviorsImpl bpmnBehaviors,
      final TypedRecordProcessors typedRecordProcessors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Writers writers,
      final DueDateTimerChecker timerChecker,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final EngineConfiguration config) {
    return BpmnProcessors.addBpmnStreamProcessor(
        processingState,
        scheduledTaskState,
        bpmnBehaviors,
        typedRecordProcessors,
        subscriptionCommandSender,
        timerChecker,
        writers,
        transientProcessMessageSubscriptionState,
        config);
  }

  private static void addDeploymentRelatedProcessorAndServices(
      final BpmnBehaviorsImpl bpmnBehaviors,
      final ProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskStateSupplier,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final DeploymentDistributionCommandSender deploymentDistributionCommandSender,
      final KeyGenerator keyGenerator,
      final FeatureFlags featureFlags,
      final CommandDistributionBehavior distributionBehavior,
      final EngineConfiguration config) {

    // on deployment partition CREATE Command is received and processed
    // it will cause a distribution to other partitions
    final var processor =
        new DeploymentCreateProcessor(
            processingState,
            bpmnBehaviors,
            writers,
            keyGenerator,
            featureFlags,
            distributionBehavior,
            config);
    typedRecordProcessors.onCommand(ValueType.DEPLOYMENT, CREATE, processor);

    // periodically retries deployment distribution
    final var deploymentRedistributor =
        new DeploymentRedistributor(
            deploymentDistributionCommandSender,
            scheduledTaskStateSupplier.get().getDeploymentState());
    typedRecordProcessors.withListener(deploymentRedistributor);

    // on other partitions DISTRIBUTE command is received and processed
    final DeploymentDistributeProcessor deploymentDistributeProcessor =
        new DeploymentDistributeProcessor(
            processingState, deploymentDistributionCommandSender, writers, keyGenerator);
    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, deploymentDistributeProcessor);

    // completes the deployment distribution
    final var completeDeploymentDistributionProcessor =
        new DeploymentDistributionCompleteProcessor(processingState.getDeploymentState(), writers);
    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT_DISTRIBUTION,
        DeploymentDistributionIntent.COMPLETE,
        completeDeploymentDistributionProcessor);
  }

  private static void addIncidentProcessors(
      final ProcessingState processingState,
      final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior) {
    IncidentEventProcessors.addProcessors(
        typedRecordProcessors,
        processingState,
        bpmnStreamProcessor,
        writers,
        jobActivationBehavior);
  }

  private static void addMessageProcessors(
      final BpmnBehaviorsImpl bpmnBehaviors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final MutableProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final EngineConfiguration config,
      final FeatureFlags featureFlags) {
    MessageEventProcessors.addMessageProcessors(
        bpmnBehaviors,
        typedRecordProcessors,
        processingState,
        scheduledTaskStateFactory,
        subscriptionCommandSender,
        writers,
        config,
        featureFlags);
  }

  private static void addDecisionProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final DecisionBehavior decisionBehavior,
      final Writers writers,
      final MutableProcessingState processingState) {

    final DecisionEvaluationEvaluteProcessor decisionEvaluationEvaluteProcessor =
        new DecisionEvaluationEvaluteProcessor(
            decisionBehavior, processingState.getKeyGenerator(), writers);
    typedRecordProcessors.onCommand(
        ValueType.DECISION_EVALUATION,
        DecisionEvaluationIntent.EVALUATE,
        decisionEvaluationEvaluteProcessor);
  }

  private static void addResourceDeletionProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final MutableProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final BpmnBehaviors bpmnBehaviors) {
    final var resourceDeletionProcessor =
        new ResourceDeletionDeleteProcessor(
            writers,
            processingState.getKeyGenerator(),
            processingState,
            commandDistributionBehavior,
            bpmnBehaviors);
    typedRecordProcessors.onCommand(
        ValueType.RESOURCE_DELETION, ResourceDeletionIntent.DELETE, resourceDeletionProcessor);
  }

  private static void addSignalBroadcastProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final BpmnBehaviorsImpl bpmnBehaviors,
      final Writers writers,
      final MutableProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior) {
    final var signalBroadcastProcessor =
        new SignalBroadcastProcessor(
            writers,
            processingState.getKeyGenerator(),
            processingState,
            bpmnBehaviors.stateBehavior(),
            bpmnBehaviors.eventTriggerBehavior(),
            commandDistributionBehavior);
    typedRecordProcessors.onCommand(
        ValueType.SIGNAL, SignalIntent.BROADCAST, signalBroadcastProcessor);
  }

  private static void addCommandDistributionProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final ProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final InterPartitionCommandSender interPartitionCommandSender) {

    // periodically retries command distribution
    typedRecordProcessors.withListener(
        new CommandRedistributor(
            scheduledTaskStateFactory.get().getDistributionState(), interPartitionCommandSender));

    final var commandDistributionAcknowledgeProcessor =
        new CommandDistributionAcknowledgeProcessor(
            processingState.getDistributionState(), writers);
    typedRecordProcessors.onCommand(
        ValueType.COMMAND_DISTRIBUTION,
        CommandDistributionIntent.ACKNOWLEDGE,
        commandDistributionAcknowledgeProcessor);
  }
}
