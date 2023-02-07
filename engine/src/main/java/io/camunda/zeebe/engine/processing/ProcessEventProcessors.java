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
import io.camunda.zeebe.engine.processing.processinstance.CreateProcessInstanceProcessor;
import io.camunda.zeebe.engine.processing.processinstance.CreateProcessInstanceWithResultProcessor;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCommandProcessor;
import io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceModificationProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.timer.CancelTimerProcessor;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.camunda.zeebe.engine.processing.timer.TriggerTimerProcessor;
import io.camunda.zeebe.engine.processing.variable.UpdateVariableDocumentProcessor;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import java.util.Arrays;

public final class ProcessEventProcessors {

  public static TypedRecordProcessor<ProcessInstanceRecord> addProcessProcessors(
      final MutableZeebeState zeebeState,
      final BpmnBehaviors bpmnBehaviors,
      final TypedRecordProcessors typedRecordProcessors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final DueDateTimerChecker timerChecker,
      final Writers writers) {
    final MutableProcessMessageSubscriptionState subscriptionState =
        zeebeState.getProcessMessageSubscriptionState();
    final var keyGenerator = zeebeState.getKeyGenerator();

    final var processEngineMetrics = new ProcessEngineMetrics(zeebeState.getPartitionId());

    addProcessInstanceCommandProcessor(
        writers, typedRecordProcessors, zeebeState.getElementInstanceState());

    final var bpmnStreamProcessor =
        new BpmnStreamProcessor(bpmnBehaviors, zeebeState, writers, processEngineMetrics);
    addBpmnStepProcessor(typedRecordProcessors, bpmnStreamProcessor);

    addMessageStreamProcessors(
        typedRecordProcessors,
        subscriptionState,
        subscriptionCommandSender,
        bpmnBehaviors,
        zeebeState,
        writers);
    addTimerStreamProcessors(
        typedRecordProcessors, timerChecker, zeebeState, bpmnBehaviors, writers);
    addVariableDocumentStreamProcessors(
        typedRecordProcessors,
        bpmnBehaviors,
        zeebeState.getElementInstanceState(),
        keyGenerator,
        writers);
    addProcessInstanceCreationStreamProcessors(
        typedRecordProcessors, zeebeState, writers, bpmnBehaviors, processEngineMetrics);
    addProcessInstanceModificationStreamProcessors(
        typedRecordProcessors, zeebeState, writers, bpmnBehaviors);

    return bpmnStreamProcessor;
  }

  private static void addProcessInstanceCommandProcessor(
      final Writers writers,
      final TypedRecordProcessors typedRecordProcessors,
      final MutableElementInstanceState elementInstanceState) {

    final ProcessInstanceCommandProcessor commandProcessor =
        new ProcessInstanceCommandProcessor(writers, elementInstanceState);

    Arrays.stream(ProcessInstanceIntent.values())
        .filter(ProcessInstanceIntent::isProcessInstanceCommand)
        .forEach(
            intent ->
                typedRecordProcessors.onCommand(
                    ValueType.PROCESS_INSTANCE, intent, commandProcessor));
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
      final MutableZeebeState zeebeState,
      final Writers writers) {
    typedRecordProcessors
        .onCommand(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            ProcessMessageSubscriptionIntent.CREATE,
            new ProcessMessageSubscriptionCreateProcessor(
                zeebeState.getProcessMessageSubscriptionState(), writers))
        .onCommand(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            ProcessMessageSubscriptionIntent.CORRELATE,
            new ProcessMessageSubscriptionCorrelateProcessor(
                subscriptionState, subscriptionCommandSender, zeebeState, bpmnBehaviors, writers))
        .onCommand(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            ProcessMessageSubscriptionIntent.DELETE,
            new ProcessMessageSubscriptionDeleteProcessor(subscriptionState, writers))
        .withListener(
            new PendingProcessMessageSubscriptionChecker(
                subscriptionCommandSender, zeebeState.getPendingProcessMessageSubscriptionState()));
  }

  private static void addTimerStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final DueDateTimerChecker timerChecker,
      final MutableZeebeState zeebeState,
      final BpmnBehaviors bpmnBehaviors,
      final Writers writers) {
    typedRecordProcessors
        .onCommand(
            ValueType.TIMER,
            TimerIntent.TRIGGER,
            new TriggerTimerProcessor(zeebeState, bpmnBehaviors, writers))
        .onCommand(
            ValueType.TIMER,
            TimerIntent.CANCEL,
            new CancelTimerProcessor(
                zeebeState.getTimerState(), writers.state(), writers.rejection()))
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
        new UpdateVariableDocumentProcessor(
            elementInstanceState, keyGenerator, bpmnBehaviors.variableBehavior(), writers));
  }

  private static void addProcessInstanceCreationStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableZeebeState zeebeState,
      final Writers writers,
      final BpmnBehaviors bpmnBehaviors,
      final ProcessEngineMetrics metrics) {
    final MutableElementInstanceState elementInstanceState = zeebeState.getElementInstanceState();
    final KeyGenerator keyGenerator = zeebeState.getKeyGenerator();

    final CreateProcessInstanceProcessor createProcessor =
        new CreateProcessInstanceProcessor(
            zeebeState.getProcessState(), keyGenerator, writers, bpmnBehaviors, metrics);
    typedRecordProcessors.onCommand(
        ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATE, createProcessor);

    typedRecordProcessors.onCommand(
        ValueType.PROCESS_INSTANCE_CREATION,
        ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT,
        new CreateProcessInstanceWithResultProcessor(createProcessor, elementInstanceState));
  }

  private static void addProcessInstanceModificationStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ZeebeState zeebeState,
      final Writers writers,
      final BpmnBehaviors bpmnBehaviors) {
    final ProcessInstanceModificationProcessor modificationProcessor =
        new ProcessInstanceModificationProcessor(
            writers,
            zeebeState.getElementInstanceState(),
            zeebeState.getProcessState(),
            bpmnBehaviors);
    typedRecordProcessors.onCommand(
        ValueType.PROCESS_INSTANCE_MODIFICATION,
        ProcessInstanceModificationIntent.MODIFY,
        modificationProcessor);
  }
}
