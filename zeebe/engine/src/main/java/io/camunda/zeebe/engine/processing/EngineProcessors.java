/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.zeebe.engine.processing.clock.ClockProcessors;
import io.camunda.zeebe.engine.processing.common.DecisionBehavior;
import io.camunda.zeebe.engine.processing.deployment.DeploymentCreateProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributeProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCommandSender;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCompleteProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentRedistributor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionAcknowledgeProcessor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionContinueProcessor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionFinishProcessor;
import io.camunda.zeebe.engine.processing.distribution.CommandRedistributor;
import io.camunda.zeebe.engine.processing.dmn.DecisionEvaluationEvaluteProcessor;
import io.camunda.zeebe.engine.processing.identity.AuthorizationProcessors;
import io.camunda.zeebe.engine.processing.incident.IncidentEventProcessors;
import io.camunda.zeebe.engine.processing.job.JobEventProcessors;
import io.camunda.zeebe.engine.processing.message.MessageEventProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.resource.ResourceDeletionDeleteProcessor;
import io.camunda.zeebe.engine.processing.resource.ResourceFetchProcessor;
import io.camunda.zeebe.engine.processing.signal.SignalBroadcastProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContext;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.camunda.zeebe.engine.processing.user.UserProcessors;
import io.camunda.zeebe.engine.processing.usertask.UserTaskEventProcessors;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.FeatureFlags;
import java.time.InstantSource;
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
    final var keyGenerator = processingState.getKeyGenerator();
    final var routingInfo =
        RoutingInfo.dynamic(
            processingState.getRoutingState(), RoutingInfo.forStaticPartitions(partitionsCount));
    final var scheduledTaskStateFactory =
        typedRecordProcessorContext.getScheduledTaskStateFactory();
    final var writers = typedRecordProcessorContext.getWriters();
    final var typedRecordProcessors = TypedRecordProcessors.processors(keyGenerator, writers);

    typedRecordProcessors.withListener(processingState);

    final var clock = typedRecordProcessorContext.getClock();
    final int partitionId = typedRecordProcessorContext.getPartitionId();
    final var config = typedRecordProcessorContext.getConfig();

    final DueDateTimerChecker timerChecker =
        new DueDateTimerChecker(
            scheduledTaskStateFactory.get().getTimerState(), featureFlags, clock);

    final var jobMetrics = new JobMetrics(partitionId);
    final var processEngineMetrics =
        new ProcessEngineMetrics(typedRecordProcessorContext.getMeterRegistry());

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
            routingInfo,
            timerChecker,
            jobStreamer,
            jobMetrics,
            decisionBehavior,
            clock,
            transientProcessMessageSubscriptionState);

    final var commandDistributionBehavior =
        new CommandDistributionBehavior(
            processingState.getDistributionState(),
            writers,
            typedRecordProcessorContext.getPartitionId(),
            routingInfo,
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
        keyGenerator,
        featureFlags,
        commandDistributionBehavior,
        config,
        clock);
    addMessageProcessors(
        bpmnBehaviors,
        subscriptionCommandSender,
        processingState,
        scheduledTaskStateFactory,
        typedRecordProcessors,
        writers,
        config,
        featureFlags,
        commandDistributionBehavior,
        clock);

    final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor =
        addProcessProcessors(
            processingState,
            scheduledTaskStateFactory,
            bpmnBehaviors,
            typedRecordProcessors,
            subscriptionCommandSender,
            writers,
            timerChecker,
            commandDistributionBehavior,
            partitionId,
            routingInfo,
            clock,
            transientProcessMessageSubscriptionState,
            processEngineMetrics);

    addDecisionProcessors(typedRecordProcessors, decisionBehavior, writers, processingState);

    JobEventProcessors.addJobProcessors(
        typedRecordProcessors,
        processingState,
        scheduledTaskStateFactory,
        bpmnBehaviors,
        writers,
        jobMetrics,
        config,
        clock);

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
        commandDistributionBehavior,
        typedRecordProcessors,
        writers,
        processingState,
        scheduledTaskStateFactory,
        interPartitionCommandSender);

    UserTaskEventProcessors.addUserTaskProcessors(
        typedRecordProcessors, processingState, bpmnBehaviors, writers);

    UserProcessors.addUserProcessors(
        keyGenerator,
        typedRecordProcessors,
        processingState,
        writers,
        commandDistributionBehavior,
        config);

    ClockProcessors.addClockProcessors(
        typedRecordProcessors, writers, keyGenerator, clock, commandDistributionBehavior);

    AuthorizationProcessors.addAuthorizationProcessors(
        keyGenerator, typedRecordProcessors, processingState, writers, commandDistributionBehavior);

    addResourceFetchProcessors(typedRecordProcessors, writers, processingState);

    return typedRecordProcessors;
  }

  private static BpmnBehaviorsImpl createBehaviors(
      final MutableProcessingState processingState,
      final Writers writers,
      final SubscriptionCommandSender subscriptionCommandSender,
      final RoutingInfo routingInfo,
      final DueDateTimerChecker timerChecker,
      final JobStreamer jobStreamer,
      final JobMetrics jobMetrics,
      final DecisionBehavior decisionBehavior,
      final InstantSource clock,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState) {
    return new BpmnBehaviorsImpl(
        processingState,
        writers,
        jobMetrics,
        decisionBehavior,
        subscriptionCommandSender,
        routingInfo,
        timerChecker,
        jobStreamer,
        clock,
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
      final CommandDistributionBehavior commandDistributionBehavior,
      final int partitionId,
      final RoutingInfo routingInfo,
      final InstantSource clock,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final ProcessEngineMetrics processEngineMetrics) {
    return BpmnProcessors.addBpmnStreamProcessor(
        processingState,
        scheduledTaskState,
        bpmnBehaviors,
        typedRecordProcessors,
        subscriptionCommandSender,
        timerChecker,
        writers,
        commandDistributionBehavior,
        partitionId,
        routingInfo,
        clock,
        transientProcessMessageSubscriptionState,
        processEngineMetrics);
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
      final EngineConfiguration config,
      final InstantSource clock) {

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
            config,
            clock);
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
      final FeatureFlags featureFlags,
      final CommandDistributionBehavior commandDistributionBehavior,
      final InstantSource clock) {
    MessageEventProcessors.addMessageProcessors(
        bpmnBehaviors,
        typedRecordProcessors,
        processingState,
        scheduledTaskStateFactory,
        subscriptionCommandSender,
        writers,
        config,
        featureFlags,
        commandDistributionBehavior,
        clock);
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

  private static void addResourceFetchProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final ProcessingState processingState) {
    final var resourceFetchProcessor = new ResourceFetchProcessor(writers, processingState);
    typedRecordProcessors.onCommand(
        ValueType.RESOURCE, ResourceIntent.FETCH, resourceFetchProcessor);
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
      final CommandDistributionBehavior commandDistributionBehavior,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final ProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final InterPartitionCommandSender interPartitionCommandSender) {

    // periodically retries command distribution
    typedRecordProcessors.withListener(
        new CommandRedistributor(
            scheduledTaskStateFactory.get().getDistributionState(), interPartitionCommandSender));

    final var distributionState = processingState.getDistributionState();
    typedRecordProcessors.onCommand(
        ValueType.COMMAND_DISTRIBUTION,
        CommandDistributionIntent.ACKNOWLEDGE,
        new CommandDistributionAcknowledgeProcessor(
            commandDistributionBehavior, distributionState, writers));
    typedRecordProcessors.onCommand(
        ValueType.COMMAND_DISTRIBUTION,
        CommandDistributionIntent.FINISH,
        new CommandDistributionFinishProcessor(writers, commandDistributionBehavior));
    typedRecordProcessors.onCommand(
        ValueType.COMMAND_DISTRIBUTION,
        CommandDistributionIntent.CONTINUE,
        new CommandDistributionContinueProcessor(distributionState, writers));
  }
}
