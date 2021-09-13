/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing;

import static io.camunda.zeebe.protocol.record.intent.DeploymentIntent.CREATE;

import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventPublicationBehavior;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.DeploymentCreateProcessor;
import io.camunda.zeebe.engine.processing.deployment.DeploymentResponder;
import io.camunda.zeebe.engine.processing.deployment.distribute.CompleteDeploymentDistributionProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributeProcessor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributor;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentRedistributor;
import io.camunda.zeebe.engine.processing.incident.IncidentEventProcessors;
import io.camunda.zeebe.engine.processing.job.JobEventProcessors;
import io.camunda.zeebe.engine.processing.message.MessageEventProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.ProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.migration.DbMigrationController;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.util.sched.ActorControl;
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
    final MutableZeebeState zeebeState = processingContext.getZeebeState();
    final var writers = processingContext.getWriters();
    final TypedRecordProcessors typedRecordProcessors =
        TypedRecordProcessors.processors(zeebeState.getKeyGenerator(), writers);

    // register listener that handles migrations immediately, so it is the first to be called
    typedRecordProcessors.withListener(new DbMigrationController());

    typedRecordProcessors.withListener(processingContext.getZeebeState());

    final LogStream stream = processingContext.getLogStream();
    final int partitionId = stream.getPartitionId();
    final int maxFragmentSize = processingContext.getMaxFragmentSize();

    final var variablesState = zeebeState.getVariableState();
    final var expressionProcessor =
        new ExpressionProcessor(
            ExpressionLanguageFactory.createExpressionLanguage(), variablesState::getVariable);

    final DueDateTimerChecker timerChecker = new DueDateTimerChecker(zeebeState.getTimerState());
    final CatchEventBehavior catchEventBehavior =
        new CatchEventBehavior(
            zeebeState,
            expressionProcessor,
            subscriptionCommandSender,
            writers.state(),
            timerChecker,
            partitionsCount);

    final var eventTriggerBehavior =
        new EventTriggerBehavior(
            zeebeState.getKeyGenerator(), catchEventBehavior, writers, zeebeState);

    final var eventPublicationBehavior =
        new BpmnEventPublicationBehavior(
            zeebeState, zeebeState.getKeyGenerator(), eventTriggerBehavior, writers);

    addDeploymentRelatedProcessorAndServices(
        catchEventBehavior,
        partitionId,
        zeebeState,
        typedRecordProcessors,
        deploymentResponder,
        expressionProcessor,
        writers,
        partitionsCount,
        actor,
        deploymentDistributor,
        zeebeState.getKeyGenerator());
    addMessageProcessors(
        eventTriggerBehavior,
        subscriptionCommandSender,
        zeebeState,
        typedRecordProcessors,
        writers);

    final var jobMetrics = new JobMetrics(partitionId);

    final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor =
        addProcessProcessors(
            zeebeState,
            expressionProcessor,
            typedRecordProcessors,
            subscriptionCommandSender,
            catchEventBehavior,
            eventTriggerBehavior,
            writers,
            timerChecker,
            jobMetrics);

    JobEventProcessors.addJobProcessors(
        typedRecordProcessors,
        zeebeState,
        onJobsAvailableCallback,
        eventPublicationBehavior,
        maxFragmentSize,
        writers,
        jobMetrics);

    addIncidentProcessors(
        zeebeState,
        bpmnStreamProcessor,
        typedRecordProcessors,
        writers,
        zeebeState.getKeyGenerator());

    return typedRecordProcessors;
  }

  private static TypedRecordProcessor<ProcessInstanceRecord> addProcessProcessors(
      final MutableZeebeState zeebeState,
      final ExpressionProcessor expressionProcessor,
      final TypedRecordProcessors typedRecordProcessors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final CatchEventBehavior catchEventBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final Writers writers,
      final DueDateTimerChecker timerChecker,
      final JobMetrics jobMetrics) {
    return ProcessEventProcessors.addProcessProcessors(
        zeebeState,
        expressionProcessor,
        typedRecordProcessors,
        subscriptionCommandSender,
        catchEventBehavior,
        timerChecker,
        eventTriggerBehavior,
        writers,
        jobMetrics);
  }

  private static void addDeploymentRelatedProcessorAndServices(
      final CatchEventBehavior catchEventBehavior,
      final int partitionId,
      final ZeebeState zeebeState,
      final TypedRecordProcessors typedRecordProcessors,
      final DeploymentResponder deploymentResponder,
      final ExpressionProcessor expressionProcessor,
      final Writers writers,
      final int partitionsCount,
      final ActorControl actor,
      final DeploymentDistributor deploymentDistributor,
      final KeyGenerator keyGenerator) {

    // on deployment partition CREATE Command is received and processed
    // it will cause a distribution to other partitions
    final var processor =
        new DeploymentCreateProcessor(
            zeebeState,
            catchEventBehavior,
            expressionProcessor,
            partitionsCount,
            writers,
            actor,
            deploymentDistributor,
            keyGenerator);
    typedRecordProcessors.onCommand(ValueType.DEPLOYMENT, CREATE, processor);

    // redistributes deployments after recovery
    final var deploymentRedistributor =
        new DeploymentRedistributor(
            partitionsCount, deploymentDistributor, zeebeState.getDeploymentState());
    typedRecordProcessors.withListener(deploymentRedistributor);

    // on other partitions DISTRIBUTE command is received and processed
    final DeploymentDistributeProcessor deploymentDistributeProcessor =
        new DeploymentDistributeProcessor(
            zeebeState.getProcessState(),
            zeebeState.getMessageStartEventSubscriptionState(),
            deploymentResponder,
            partitionId,
            writers,
            keyGenerator);
    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, deploymentDistributeProcessor);

    // completes the deployment distribution
    final var completeDeploymentDistributionProcessor =
        new CompleteDeploymentDistributionProcessor(zeebeState.getDeploymentState(), writers);
    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT_DISTRIBUTION,
        DeploymentDistributionIntent.COMPLETE,
        completeDeploymentDistributionProcessor);
  }

  private static void addIncidentProcessors(
      final ZeebeState zeebeState,
      final TypedRecordProcessor<ProcessInstanceRecord> bpmnStreamProcessor,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final KeyGenerator keyGenerator) {
    IncidentEventProcessors.addProcessors(
        typedRecordProcessors, zeebeState, bpmnStreamProcessor, writers, keyGenerator);
  }

  private static void addMessageProcessors(
      final EventTriggerBehavior eventTriggerBehavior,
      final SubscriptionCommandSender subscriptionCommandSender,
      final MutableZeebeState zeebeState,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers) {
    MessageEventProcessors.addMessageProcessors(
        eventTriggerBehavior,
        typedRecordProcessors,
        zeebeState,
        subscriptionCommandSender,
        writers);
  }
}
