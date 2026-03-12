/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing;

import static io.camunda.zeebe.protocol.record.intent.DeploymentIntent.CREATE;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.zeebe.dmn.DecisionEngineFactory;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.el.impl.ExpressionLanguageMetricsImpl;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.metrics.DistributionMetrics;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.BatchOperationSetupProcessors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviorsImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.clock.ClockProcessors;
import io.camunda.zeebe.engine.processing.clustervariable.ClusterVariableProcessors;
import io.camunda.zeebe.engine.processing.common.DecisionBehavior;
import io.camunda.zeebe.engine.processing.conditional.ConditionalEvaluationEvaluateProcessor;
import io.camunda.zeebe.engine.processing.deployment.DeploymentCreateProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributeProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCommandSender;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCompleteProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentRedistributionScheduler;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionAcknowledgeProcessor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionContinueProcessor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionFinishProcessor;
import io.camunda.zeebe.engine.processing.distribution.CommandRedistributionScheduler;
import io.camunda.zeebe.engine.processing.dmn.DecisionEvaluationEvaluateProcessor;
import io.camunda.zeebe.engine.processing.expression.ExpressionProcessors;
import io.camunda.zeebe.engine.processing.globallistener.GlobalListenersProcessors;
import io.camunda.zeebe.engine.processing.historydeletion.HistoryDeletionProcessors;
import io.camunda.zeebe.engine.processing.identity.AuthorizationProcessors;
import io.camunda.zeebe.engine.processing.identity.GroupProcessors;
import io.camunda.zeebe.engine.processing.identity.IdentitySetupProcessors;
import io.camunda.zeebe.engine.processing.identity.MappingRuleProcessors;
import io.camunda.zeebe.engine.processing.identity.RoleProcessors;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.incident.IncidentEventProcessors;
import io.camunda.zeebe.engine.processing.job.JobEventProcessors;
import io.camunda.zeebe.engine.processing.message.MessageEventProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.metrics.job.JobMetricsProcessors;
import io.camunda.zeebe.engine.processing.metrics.usage.UsageMetricsProcessors;
import io.camunda.zeebe.engine.processing.resource.ResourceDeletionDeleteProcessor;
import io.camunda.zeebe.engine.processing.resource.ResourceFetchProcessor;
import io.camunda.zeebe.engine.processing.scaling.ScalingProcessors;
import io.camunda.zeebe.engine.processing.signal.SignalBroadcastProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContext;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.tenant.TenantProcessors;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerCheckScheduler;
import io.camunda.zeebe.engine.processing.user.UserProcessors;
import io.camunda.zeebe.engine.processing.usertask.UserTaskProcessor;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.ConditionalEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
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
      final JobStreamer jobStreamer,
      final SearchClientsProxy searchClientsProxy,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {

    final var processingState = typedRecordProcessorContext.getProcessingState();
    final var keyGenerator = processingState.getKeyGenerator();
    final var routingInfo =
        RoutingInfo.dynamic(
            processingState.getRoutingState(), RoutingInfo.forStaticPartitions(partitionsCount));
    final var scheduledTaskStateFactory =
        typedRecordProcessorContext.getScheduledTaskStateFactory();
    final var writers = typedRecordProcessorContext.getWriters();
    final var typedRecordProcessors = TypedRecordProcessors.processors();

    typedRecordProcessors.withListener(processingState);

    final var clock = typedRecordProcessorContext.getClock();
    final int partitionId = typedRecordProcessorContext.getPartitionId();
    final var config = typedRecordProcessorContext.getConfig();
    final var securityConfig = typedRecordProcessorContext.getSecurityConfig();

    final DueDateTimerCheckScheduler timerChecker =
        new DueDateTimerCheckScheduler(
            scheduledTaskStateFactory.get().getTimerState(), featureFlags, clock);

    final var jobMetrics = new JobProcessingMetrics(typedRecordProcessorContext.getMeterRegistry());
    final var processEngineMetrics =
        new ProcessEngineMetrics(typedRecordProcessorContext.getMeterRegistry());
    final var distributionMetrics =
        new DistributionMetrics(typedRecordProcessorContext.getMeterRegistry());
    final var batchOperationMetrics =
        new BatchOperationMetrics(typedRecordProcessorContext.getMeterRegistry(), partitionId);
    final ExpressionLanguageMetricsImpl expressionLanguageMetrics =
        new ExpressionLanguageMetricsImpl(typedRecordProcessorContext.getMeterRegistry());

    subscriptionCommandSender.setWriters(writers);

    final var decisionBehavior =
        new DecisionBehavior(
            DecisionEngineFactory.createDecisionEngine(), processingState, processEngineMetrics);
    final var authCheckBehavior =
        new AuthorizationCheckBehavior(processingState, securityConfig, config);
    final var asyncRequestBehavior =
        new AsyncRequestBehavior(processingState.getKeyGenerator(), writers.state());
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
            authCheckBehavior,
            transientProcessMessageSubscriptionState,
            expressionLanguageMetrics,
            config);

    final var commandDistributionBehavior =
        new CommandDistributionBehavior(
            processingState.getDistributionState(),
            writers,
            typedRecordProcessorContext.getPartitionId(),
            routingInfo,
            interPartitionCommandSender,
            distributionMetrics);

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
        clock,
        authCheckBehavior,
        routingInfo,
        expressionLanguageMetrics);
    addMessageProcessors(
        typedRecordProcessorContext.getPartitionId(),
        bpmnBehaviors,
        subscriptionCommandSender,
        processingState,
        scheduledTaskStateFactory,
        typedRecordProcessors,
        writers,
        config,
        featureFlags,
        commandDistributionBehavior,
        clock,
        authCheckBehavior,
        routingInfo);

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
            config,
            asyncRequestBehavior,
            authCheckBehavior,
            transientProcessMessageSubscriptionState,
            processEngineMetrics);

    addDecisionProcessors(
        typedRecordProcessors, decisionBehavior, writers, processingState, authCheckBehavior);

    JobEventProcessors.addJobProcessors(
        typedRecordProcessors,
        processingState,
        scheduledTaskStateFactory,
        bpmnBehaviors,
        writers,
        jobMetrics,
        config,
        clock,
        authCheckBehavior);

    final var userTaskProcessor =
        createUserTaskProcessor(
            processingState, bpmnBehaviors, writers, asyncRequestBehavior, authCheckBehavior);
    addUserTaskProcessors(typedRecordProcessors, userTaskProcessor);

    addIncidentProcessors(
        processingState,
        bpmnStreamProcessor,
        userTaskProcessor,
        typedRecordProcessors,
        writers,
        bpmnBehaviors.jobActivationBehavior(),
        authCheckBehavior);
    addResourceDeletionProcessors(
        typedRecordProcessors,
        writers,
        processingState,
        commandDistributionBehavior,
        bpmnBehaviors,
        authCheckBehavior);
    addSignalBroadcastProcessors(
        typedRecordProcessors,
        bpmnBehaviors,
        writers,
        processingState,
        commandDistributionBehavior,
        authCheckBehavior);
    addConditionalEvaluationProcessors(
        typedRecordProcessors, bpmnBehaviors, writers, processingState, authCheckBehavior);
    addCommandDistributionProcessors(
        commandDistributionBehavior,
        scheduledTaskStateFactory,
        typedRecordProcessors,
        writers,
        processingState,
        partitionsCount,
        config);

    UserProcessors.addUserProcessors(
        keyGenerator,
        typedRecordProcessors,
        processingState,
        writers,
        commandDistributionBehavior,
        authCheckBehavior);

    ClockProcessors.addClockProcessors(
        typedRecordProcessors,
        writers,
        keyGenerator,
        clock,
        commandDistributionBehavior,
        authCheckBehavior);

    AuthorizationProcessors.addAuthorizationProcessors(
        keyGenerator,
        typedRecordProcessors,
        processingState,
        writers,
        commandDistributionBehavior,
        authCheckBehavior);

    RoleProcessors.addRoleProcessors(
        typedRecordProcessors,
        processingState,
        authCheckBehavior,
        keyGenerator,
        writers,
        commandDistributionBehavior);

    GroupProcessors.addGroupProcessors(
        typedRecordProcessors,
        processingState,
        authCheckBehavior,
        keyGenerator,
        writers,
        commandDistributionBehavior);

    ScalingProcessors.addScalingProcessors(
        commandDistributionBehavior,
        bpmnBehaviors,
        typedRecordProcessors,
        writers,
        keyGenerator,
        processingState);

    TenantProcessors.addTenantProcessors(
        typedRecordProcessors,
        processingState,
        authCheckBehavior,
        keyGenerator,
        writers,
        commandDistributionBehavior);

    MappingRuleProcessors.addMappingRuleProcessors(
        typedRecordProcessors,
        processingState,
        authCheckBehavior,
        keyGenerator,
        writers,
        commandDistributionBehavior);

    IdentitySetupProcessors.addIdentitySetupProcessors(
        keyGenerator, typedRecordProcessors, writers, securityConfig, config);

    addResourceFetchProcessors(typedRecordProcessors, writers, processingState, authCheckBehavior);

    BatchOperationSetupProcessors.addBatchOperationProcessors(
        keyGenerator,
        typedRecordProcessors,
        writers,
        commandDistributionBehavior,
        authCheckBehavior,
        scheduledTaskStateFactory,
        searchClientsProxy,
        processingState,
        config,
        partitionId,
        routingInfo,
        batchOperationMetrics,
        brokerRequestAuthorizationConverter);

    ClusterVariableProcessors.addClusterVariableProcessors(
        keyGenerator,
        typedRecordProcessors,
        processingState.getClusterVariableState(),
        writers,
        commandDistributionBehavior,
        authCheckBehavior,
        config);

    UsageMetricsProcessors.addUsageMetricsProcessors(
        typedRecordProcessors, config, clock, processingState, writers, keyGenerator);

    HistoryDeletionProcessors.addHistoryDeletionProcessors(
        typedRecordProcessors, writers, processingState, authCheckBehavior);
    GlobalListenersProcessors.addGlobalListenersProcessors(
        keyGenerator,
        typedRecordProcessors,
        writers,
        commandDistributionBehavior,
        config,
        processingState,
        authCheckBehavior);

    ExpressionProcessors.addProcessors(
        keyGenerator,
        typedRecordProcessors,
        writers,
        bpmnBehaviors.expressionBehavior(),
        bpmnBehaviors.expressionLanguage(),
        authCheckBehavior);

    JobMetricsProcessors.addJobMetricsProcessors(
        typedRecordProcessors,
        config,
        processingState.getJobMetricsState(),
        writers,
        keyGenerator,
        clock);

    return typedRecordProcessors;
  }

  private static TypedRecordProcessor<UserTaskRecord> createUserTaskProcessor(
      final MutableProcessingState processingState,
      final BpmnBehaviorsImpl bpmnBehaviors,
      final Writers writers,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    return new UserTaskProcessor(
        processingState,
        processingState.getUserTaskState(),
        processingState.getKeyGenerator(),
        bpmnBehaviors,
        writers,
        asyncRequestBehavior,
        authCheckBehavior);
  }

  private static BpmnBehaviorsImpl createBehaviors(
      final MutableProcessingState processingState,
      final Writers writers,
      final SubscriptionCommandSender subscriptionCommandSender,
      final RoutingInfo routingInfo,
      final DueDateTimerCheckScheduler timerChecker,
      final JobStreamer jobStreamer,
      final JobProcessingMetrics jobMetrics,
      final DecisionBehavior decisionBehavior,
      final InstantSource clock,
      final AuthorizationCheckBehavior authCheckBehavior,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final ExpressionLanguageMetrics expressionLanguageMetrics,
      final EngineConfiguration config) {
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
        authCheckBehavior,
        transientProcessMessageSubscriptionState,
        expressionLanguageMetrics,
        config);
  }

  private static TypedRecordProcessor<ProcessInstanceRecord> addProcessProcessors(
      final MutableProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskState,
      final BpmnBehaviorsImpl bpmnBehaviors,
      final TypedRecordProcessors typedRecordProcessors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Writers writers,
      final DueDateTimerCheckScheduler timerChecker,
      final CommandDistributionBehavior commandDistributionBehavior,
      final int partitionId,
      final RoutingInfo routingInfo,
      final InstantSource clock,
      final EngineConfiguration config,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior,
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
        config,
        asyncRequestBehavior,
        authCheckBehavior,
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
      final InstantSource clock,
      final AuthorizationCheckBehavior authCheckBehavior,
      final RoutingInfo routingInfo,
      final ExpressionLanguageMetrics expressionLanguageMetrics) {

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
            clock,
            authCheckBehavior,
            expressionLanguageMetrics);

    typedRecordProcessors.onCommand(ValueType.DEPLOYMENT, CREATE, processor);

    // periodically retries deployment distribution
    final var deploymentRedistributor =
        new DeploymentRedistributionScheduler(
            deploymentDistributionCommandSender,
            scheduledTaskStateSupplier.get().getDeploymentState(),
            routingInfo);
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
      final TypedRecordProcessor<UserTaskRecord> userTaskProcessor,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    IncidentEventProcessors.addProcessors(
        typedRecordProcessors,
        processingState,
        bpmnStreamProcessor,
        userTaskProcessor,
        writers,
        jobActivationBehavior,
        authCheckBehavior);
  }

  private static void addMessageProcessors(
      final int partitionId,
      final BpmnBehaviorsImpl bpmnBehaviors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final MutableProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final EngineConfiguration config,
      final FeatureFlags featureFlags,
      final CommandDistributionBehavior commandDistributionBehavior,
      final InstantSource clock,
      final AuthorizationCheckBehavior authCheckBehavior,
      final RoutingInfo routingInfo) {
    MessageEventProcessors.addMessageProcessors(
        partitionId,
        bpmnBehaviors,
        typedRecordProcessors,
        processingState,
        scheduledTaskStateFactory,
        subscriptionCommandSender,
        writers,
        config,
        featureFlags,
        commandDistributionBehavior,
        clock,
        authCheckBehavior,
        routingInfo);
  }

  private static void addDecisionProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final DecisionBehavior decisionBehavior,
      final Writers writers,
      final MutableProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior) {

    final DecisionEvaluationEvaluateProcessor decisionEvaluationEvaluateProcessor =
        new DecisionEvaluationEvaluateProcessor(
            decisionBehavior, processingState.getKeyGenerator(), writers, authCheckBehavior);
    typedRecordProcessors.onCommand(
        ValueType.DECISION_EVALUATION,
        DecisionEvaluationIntent.EVALUATE,
        decisionEvaluationEvaluateProcessor);
  }

  private static void addResourceDeletionProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final MutableProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final BpmnBehaviors bpmnBehaviors,
      final AuthorizationCheckBehavior authCheckBehavior) {
    final var resourceDeletionProcessor =
        new ResourceDeletionDeleteProcessor(
            writers,
            processingState.getKeyGenerator(),
            processingState,
            commandDistributionBehavior,
            bpmnBehaviors,
            authCheckBehavior);
    typedRecordProcessors.onCommand(
        ValueType.RESOURCE_DELETION, ResourceDeletionIntent.DELETE, resourceDeletionProcessor);
  }

  private static void addResourceFetchProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior) {
    final var resourceFetchProcessor =
        new ResourceFetchProcessor(writers, processingState, authCheckBehavior);
    typedRecordProcessors.onCommand(
        ValueType.RESOURCE, ResourceIntent.FETCH, resourceFetchProcessor);
  }

  private static void addSignalBroadcastProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final BpmnBehaviorsImpl bpmnBehaviors,
      final Writers writers,
      final MutableProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    final var signalBroadcastProcessor =
        new SignalBroadcastProcessor(
            writers,
            processingState.getKeyGenerator(),
            processingState,
            bpmnBehaviors.stateBehavior(),
            bpmnBehaviors.eventTriggerBehavior(),
            commandDistributionBehavior,
            authCheckBehavior);
    typedRecordProcessors.onCommand(
        ValueType.SIGNAL, SignalIntent.BROADCAST, signalBroadcastProcessor);
  }

  private static void addConditionalEvaluationProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers,
      final MutableProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior) {
    final var conditionalEvaluationProcessor =
        new ConditionalEvaluationEvaluateProcessor(
            writers,
            processingState.getKeyGenerator(),
            processingState,
            bpmnBehaviors.stateBehavior(),
            bpmnBehaviors.eventTriggerBehavior(),
            authCheckBehavior,
            bpmnBehaviors.expressionProcessor());
    typedRecordProcessors.onCommand(
        ValueType.CONDITIONAL_EVALUATION,
        ConditionalEvaluationIntent.EVALUATE,
        conditionalEvaluationProcessor);
  }

  private static void addUserTaskProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final TypedRecordProcessor<UserTaskRecord> userTaskProcessor) {

    UserTaskIntent.commands()
        .forEach(
            intent ->
                typedRecordProcessors.onCommand(ValueType.USER_TASK, intent, userTaskProcessor));
  }

  private static void addCommandDistributionProcessors(
      final CommandDistributionBehavior commandDistributionBehavior,
      final Supplier<ScheduledTaskState> scheduledTaskStateSupplier,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final ProcessingState processingState,
      final int staticPartitionsCount,
      final EngineConfiguration config) {

    {
      final var scheduledTaskState = scheduledTaskStateSupplier.get();
      // periodically retries command distribution
      // Note that the CommandRedistributionScheduler runs in a separate actor, so it must not use
      // the same
      // state as the StreamProcessors as it runs in another RocksDB transaction as well.
      // It can only use the state from scheduledTaskStateSupplier.
      typedRecordProcessors.withListener(
          new CommandRedistributionScheduler(
              commandDistributionBehavior.withScheduledState(
                  scheduledTaskState.getDistributionState()),
              RoutingInfo.dynamic(
                  scheduledTaskState.getRoutingState(),
                  RoutingInfo.forStaticPartitions(staticPartitionsCount)),
              config));
    }

    final var distributionState = processingState.getDistributionState();
    typedRecordProcessors.onCommand(
        ValueType.COMMAND_DISTRIBUTION,
        CommandDistributionIntent.ACKNOWLEDGE,
        new CommandDistributionAcknowledgeProcessor(
            commandDistributionBehavior, distributionState, writers));
    typedRecordProcessors.onCommand(
        ValueType.COMMAND_DISTRIBUTION,
        CommandDistributionIntent.FINISH,
        new CommandDistributionFinishProcessor(commandDistributionBehavior));
    typedRecordProcessors.onCommand(
        ValueType.COMMAND_DISTRIBUTION,
        CommandDistributionIntent.CONTINUE,
        new CommandDistributionContinueProcessor(distributionState, writers));

    typedRecordProcessors.withListener(commandDistributionBehavior);
  }
}
