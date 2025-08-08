/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.adhocsubprocess.AdHocSubProcessInstructionActivateProcessor;
import io.camunda.zeebe.engine.processing.adhocsubprocess.AdHocSubProcessInstructionCompleteProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnStreamProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.message.PendingProcessMessageSubscriptionChecker;
import io.camunda.zeebe.engine.processing.message.ProcessMessageSubscriptionCorrelateProcessor;
import io.camunda.zeebe.engine.processing.message.ProcessMessageSubscriptionCreateProcessor;
import io.camunda.zeebe.engine.processing.message.ProcessMessageSubscriptionDeleteProcessor;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceBatchActivateProcessor;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceBatchTerminateProcessor;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCancelProcessor;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCreationCreateProcessor;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCreationCreateWithResultProcessor;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceMigrationMigrateProcessor;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceModificationModifyProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.camunda.zeebe.engine.processing.timer.TimerCancelProcessor;
import io.camunda.zeebe.engine.processing.timer.TimerTriggerProcessor;
import io.camunda.zeebe.engine.processing.variable.VariableDocumentUpdateProcessor;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.function.Supplier;

public final class BpmnProcessors {

  public static TypedRecordProcessor<ProcessInstanceRecord> addBpmnStreamProcessor(
      final MutableProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskState,
      final BpmnBehaviors bpmnBehaviors,
      final TypedRecordProcessors typedRecordProcessors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final DueDateTimerChecker timerChecker,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final int partitionId,
      final RoutingInfo routingInfo,
      final InstantSource clock,
      final EngineConfiguration config,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final ProcessEngineMetrics processEngineMetrics) {
    final MutableProcessMessageSubscriptionState subscriptionState =
        processingState.getProcessMessageSubscriptionState();
    final var keyGenerator = processingState.getKeyGenerator();

    addProcessInstanceCommandProcessor(
        writers, typedRecordProcessors, processingState, asyncRequestBehavior, authCheckBehavior);

    final var bpmnStreamProcessor =
        new BpmnStreamProcessor(
            bpmnBehaviors, processingState, writers, processEngineMetrics, config);
    addBpmnStepProcessor(typedRecordProcessors, bpmnStreamProcessor);

    addMessageStreamProcessors(
        typedRecordProcessors,
        subscriptionState,
        subscriptionCommandSender,
        bpmnBehaviors,
        processingState,
        scheduledTaskState,
        writers,
        clock,
        transientProcessMessageSubscriptionState);
    addTimerStreamProcessors(
        typedRecordProcessors, timerChecker, processingState, bpmnBehaviors, writers);
    addVariableDocumentStreamProcessors(
        typedRecordProcessors,
        bpmnBehaviors,
        processingState,
        keyGenerator,
        writers,
        processingState.getUserTaskState(),
        asyncRequestBehavior,
        authCheckBehavior);
    addProcessInstanceCreationStreamProcessors(
        typedRecordProcessors,
        processingState,
        writers,
        bpmnBehaviors,
        processEngineMetrics,
        config,
        authCheckBehavior);
    addProcessInstanceModificationStreamProcessors(
        typedRecordProcessors, processingState, writers, bpmnBehaviors, authCheckBehavior);
    addProcessInstanceMigrationStreamProcessors(
        typedRecordProcessors,
        processingState,
        writers,
        bpmnBehaviors,
        commandDistributionBehavior,
        partitionId,
        routingInfo,
        authCheckBehavior,
        keyGenerator);
    addProcessInstanceBatchStreamProcessors(typedRecordProcessors, processingState, writers);
    addAdHocSubProcessActivityStreamProcessors(
        typedRecordProcessors, processingState, writers, authCheckBehavior, bpmnBehaviors);

    return bpmnStreamProcessor;
  }

  private static void addProcessInstanceCommandProcessor(
      final Writers writers,
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState processingState,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    typedRecordProcessors.onCommand(
        ValueType.PROCESS_INSTANCE,
        ProcessInstanceIntent.CANCEL,
        new ProcessInstanceCancelProcessor(
            processingState, writers, asyncRequestBehavior, authCheckBehavior));
  }

  private static void addBpmnStepProcessor(
      final TypedRecordProcessors typedRecordProcessors,
      final BpmnStreamProcessor bpmnStepProcessor) {

    Arrays.stream(ProcessInstanceIntent.values())
        .filter(ProcessInstanceIntent::isBpmnElementCommand)
        .forEach(
            intent ->
                typedRecordProcessors.onCommand(
                    ValueType.PROCESS_INSTANCE, intent, bpmnStepProcessor));
  }

  private static void addMessageStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessMessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final BpmnBehaviors bpmnBehaviors,
      final MutableProcessingState processingState,
      final Supplier<ScheduledTaskState> scheduledTaskState,
      final Writers writers,
      final InstantSource clock,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState) {
    typedRecordProcessors
        .onCommand(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            ProcessMessageSubscriptionIntent.CREATE,
            new ProcessMessageSubscriptionCreateProcessor(
                processingState.getProcessMessageSubscriptionState(),
                writers,
                transientProcessMessageSubscriptionState))
        .onCommand(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            ProcessMessageSubscriptionIntent.CORRELATE,
            new ProcessMessageSubscriptionCorrelateProcessor(
                subscriptionState,
                subscriptionCommandSender,
                processingState,
                bpmnBehaviors,
                writers,
                transientProcessMessageSubscriptionState))
        .onCommand(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            ProcessMessageSubscriptionIntent.DELETE,
            new ProcessMessageSubscriptionDeleteProcessor(
                subscriptionState, writers, transientProcessMessageSubscriptionState))
        .withListener(
            new PendingProcessMessageSubscriptionChecker(
                subscriptionCommandSender,
                scheduledTaskState.get().getPendingProcessMessageSubscriptionState(),
                clock));
  }

  private static void addTimerStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final DueDateTimerChecker timerChecker,
      final MutableProcessingState processingState,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers) {
    typedRecordProcessors
        .onCommand(
            ValueType.TIMER,
            TimerIntent.TRIGGER,
            new TimerTriggerProcessor(processingState, bpmnBehaviors, writers))
        .onCommand(
            ValueType.TIMER,
            TimerIntent.CANCEL,
            new TimerCancelProcessor(
                processingState.getTimerState(), writers.state(), writers.rejection()))
        .withListener(timerChecker);
  }

  private static void addVariableDocumentStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final BpmnBehaviors bpmnBehaviors,
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final MutableUserTaskState userTaskState,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    typedRecordProcessors.onCommand(
        ValueType.VARIABLE_DOCUMENT,
        VariableDocumentIntent.UPDATE,
        new VariableDocumentUpdateProcessor(
            processingState,
            keyGenerator,
            bpmnBehaviors,
            writers,
            userTaskState,
            asyncRequestBehavior,
            authCheckBehavior));
  }

  private static void addProcessInstanceCreationStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessingState processingState,
      final Writers writers,
      final BpmnBehaviors bpmnBehaviors,
      final ProcessEngineMetrics metrics,
      final EngineConfiguration config,
      final AuthorizationCheckBehavior authCheckBehavior) {
    final MutableElementInstanceState elementInstanceState =
        processingState.getElementInstanceState();
    final KeyGenerator keyGenerator = processingState.getKeyGenerator();

    final ProcessInstanceCreationCreateProcessor createProcessor =
        new ProcessInstanceCreationCreateProcessor(
            processingState.getProcessState(),
            keyGenerator,
            writers,
            bpmnBehaviors,
            metrics,
            authCheckBehavior);
    typedRecordProcessors.onCommand(
        ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATE, createProcessor);

    typedRecordProcessors.onCommand(
        ValueType.PROCESS_INSTANCE_CREATION,
        ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT,
        new ProcessInstanceCreationCreateWithResultProcessor(
            createProcessor, elementInstanceState));
  }

  private static void addProcessInstanceModificationStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState processingState,
      final Writers writers,
      final BpmnBehaviors bpmnBehaviors,
      final AuthorizationCheckBehavior authCheckBehavior) {
    final ProcessInstanceModificationModifyProcessor modificationProcessor =
        new ProcessInstanceModificationModifyProcessor(
            writers,
            processingState.getElementInstanceState(),
            processingState.getProcessState(),
            bpmnBehaviors,
            authCheckBehavior);
    typedRecordProcessors.onCommand(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        ProcessInstanceModificationIntent.MODIFY,
        modificationProcessor);
  }

  private static void addProcessInstanceMigrationStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState processingState,
      final Writers writers,
      final BpmnBehaviors bpmnBehaviors,
      final CommandDistributionBehavior commandDistributionBehavior,
      final int partitionId,
      final RoutingInfo routingInfo,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator) {
    typedRecordProcessors.onCommand(
        ValueType.PROCESS_INSTANCE_MIGRATION,
        ProcessInstanceMigrationIntent.MIGRATE,
        new ProcessInstanceMigrationMigrateProcessor(
            writers,
            processingState,
            bpmnBehaviors,
            commandDistributionBehavior,
            partitionId,
            routingInfo,
            authCheckBehavior,
            keyGenerator));
  }

  private static void addProcessInstanceBatchStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessingState processingState,
      final Writers writers) {
    typedRecordProcessors
        .onCommand(
            ValueType.PROCESS_INSTANCE_BATCH,
            ProcessInstanceBatchIntent.TERMINATE,
            new ProcessInstanceBatchTerminateProcessor(
                writers,
                processingState.getKeyGenerator(),
                processingState.getElementInstanceState()))
        .onCommand(
            ValueType.PROCESS_INSTANCE_BATCH,
            ProcessInstanceBatchIntent.ACTIVATE,
            new ProcessInstanceBatchActivateProcessor(
                writers,
                processingState.getKeyGenerator(),
                processingState.getElementInstanceState(),
                processingState.getProcessState()));
  }

  private static void addAdHocSubProcessActivityStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessingState processingState,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior,
      final BpmnBehaviors bpmnBehaviors) {
    typedRecordProcessors.onCommand(
        ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION,
        AdHocSubProcessInstructionIntent.ACTIVATE,
        new AdHocSubProcessInstructionActivateProcessor(
            writers, processingState, authCheckBehavior, bpmnBehaviors));
    typedRecordProcessors.onCommand(
        ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION,
        AdHocSubProcessInstructionIntent.COMPLETE,
        new AdHocSubProcessInstructionCompleteProcessor(writers, processingState, bpmnBehaviors));
  }
}
