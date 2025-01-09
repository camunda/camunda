/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing;

import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.bpmn.BpmnStreamProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
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
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
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
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final ProcessEngineMetrics processEngineMetrics) {
    final MutableProcessMessageSubscriptionState subscriptionState =
        processingState.getProcessMessageSubscriptionState();
    final var keyGenerator = processingState.getKeyGenerator();

    addProcessInstanceCommandProcessor(writers, typedRecordProcessors, processingState);

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
        transientProcessMessageSubscriptionState);
    addTimerStreamProcessors(
        typedRecordProcessors, timerChecker, processingState, bpmnBehaviors, writers);
    addVariableDocumentStreamProcessors(
        typedRecordProcessors,
        bpmnBehaviors,
        processingState.getElementInstanceState(),
        keyGenerator,
        writers);
    addProcessInstanceCreationStreamProcessors(
        typedRecordProcessors, processingState, writers, bpmnBehaviors, processEngineMetrics);
    addProcessInstanceModificationStreamProcessors(
        typedRecordProcessors, processingState, writers, bpmnBehaviors);
    addProcessInstanceMigrationStreamProcessors(
        typedRecordProcessors, processingState, writers, bpmnBehaviors);
    addProcessInstanceBatchStreamProcessors(typedRecordProcessors, processingState, writers);

    return bpmnStreamProcessor;
  }

  private static void addProcessInstanceCommandProcessor(
      final Writers writers,
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState processingState) {
    typedRecordProcessors.onCommand(
        ValueType.PROCESS_INSTANCE,
        ProcessInstanceIntent.CANCEL,
        new ProcessInstanceCancelProcessor(processingState, writers));
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
                scheduledTaskState.get().getPendingProcessMessageSubscriptionState()));
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
      final ElementInstanceState elementInstanceState,
      final KeyGenerator keyGenerator,
      final Writers writers) {
    typedRecordProcessors.onCommand(
        ValueType.VARIABLE_DOCUMENT,
        VariableDocumentIntent.UPDATE,
        new VariableDocumentUpdateProcessor(
            elementInstanceState, keyGenerator, bpmnBehaviors.variableBehavior(), writers));
  }

  private static void addProcessInstanceCreationStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableProcessingState processingState,
      final Writers writers,
      final BpmnBehaviors bpmnBehaviors,
      final ProcessEngineMetrics metrics) {
    final MutableElementInstanceState elementInstanceState =
        processingState.getElementInstanceState();
    final KeyGenerator keyGenerator = processingState.getKeyGenerator();

    final ProcessInstanceCreationCreateProcessor createProcessor =
        new ProcessInstanceCreationCreateProcessor(
            processingState.getProcessState(), keyGenerator, writers, bpmnBehaviors, metrics);
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
      final BpmnBehaviors bpmnBehaviors) {
    final ProcessInstanceModificationModifyProcessor modificationProcessor =
        new ProcessInstanceModificationModifyProcessor(
            writers,
            processingState.getElementInstanceState(),
            processingState.getProcessState(),
            bpmnBehaviors);
    typedRecordProcessors.onCommand(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        ProcessInstanceModificationIntent.MODIFY,
        modificationProcessor);
  }

  private static void addProcessInstanceMigrationStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ProcessingState processingState,
      final Writers writers,
      final BpmnBehaviors bpmnBehaviors) {
    typedRecordProcessors.onCommand(
        ValueType.PROCESS_INSTANCE_MIGRATION,
        ProcessInstanceMigrationIntent.MIGRATE,
        new ProcessInstanceMigrationMigrateProcessor(writers, processingState, bpmnBehaviors));
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
}
