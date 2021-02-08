/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing;

import io.zeebe.engine.processing.bpmn.BpmnStreamProcessor;
import io.zeebe.engine.processing.common.CatchEventBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.message.CloseWorkflowInstanceSubscription;
import io.zeebe.engine.processing.message.CorrelateWorkflowInstanceSubscription;
import io.zeebe.engine.processing.message.OpenWorkflowInstanceSubscriptionProcessor;
import io.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.timer.CancelTimerProcessor;
import io.zeebe.engine.processing.timer.CreateTimerProcessor;
import io.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.zeebe.engine.processing.timer.TriggerTimerProcessor;
import io.zeebe.engine.processing.variable.UpdateVariableDocumentProcessor;
import io.zeebe.engine.processing.variable.UpdateVariableStreamWriter;
import io.zeebe.engine.processing.workflowinstance.CreateWorkflowInstanceProcessor;
import io.zeebe.engine.processing.workflowinstance.CreateWorkflowInstanceWithResultProcessor;
import io.zeebe.engine.processing.workflowinstance.WorkflowInstanceCommandProcessor;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.engine.state.mutable.MutableWorkflowInstanceSubscriptionState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceSubscriptionIntent;
import java.util.Arrays;
import java.util.List;

public final class WorkflowEventProcessors {

  private static final List<WorkflowInstanceIntent> WORKFLOW_INSTANCE_COMMANDS =
      Arrays.asList(WorkflowInstanceIntent.CANCEL);

  private static boolean isWorkflowInstanceEvent(final WorkflowInstanceIntent intent) {
    return !WORKFLOW_INSTANCE_COMMANDS.contains(intent);
  }

  public static TypedRecordProcessor<WorkflowInstanceRecord> addWorkflowProcessors(
      final ZeebeState zeebeState,
      final ExpressionProcessor expressionProcessor,
      final TypedRecordProcessors typedRecordProcessors,
      final SubscriptionCommandSender subscriptionCommandSender,
      final CatchEventBehavior catchEventBehavior,
      final DueDateTimerChecker timerChecker,
      final StateWriter stateWriter) {
    final MutableWorkflowInstanceSubscriptionState subscriptionState =
        zeebeState.getWorkflowInstanceSubscriptionState();

    typedRecordProcessors.withListener(new UpdateVariableStreamWriter());

    addWorkflowInstanceCommandProcessor(
        typedRecordProcessors, zeebeState.getElementInstanceState());

    final var bpmnStreamProcessor =
        new BpmnStreamProcessor(expressionProcessor, catchEventBehavior, zeebeState);
    addBpmnStepProcessor(typedRecordProcessors, bpmnStreamProcessor);

    addMessageStreamProcessors(
        typedRecordProcessors, subscriptionState, subscriptionCommandSender, zeebeState);
    addTimerStreamProcessors(
        typedRecordProcessors, timerChecker, zeebeState, catchEventBehavior, expressionProcessor);
    addVariableDocumentStreamProcessors(typedRecordProcessors, zeebeState);
    addWorkflowInstanceCreationStreamProcessors(typedRecordProcessors, zeebeState, stateWriter);

    return bpmnStreamProcessor;
  }

  private static void addWorkflowInstanceCommandProcessor(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableElementInstanceState elementInstanceState) {

    final WorkflowInstanceCommandProcessor commandProcessor =
        new WorkflowInstanceCommandProcessor(elementInstanceState);

    WORKFLOW_INSTANCE_COMMANDS.forEach(
        intent ->
            typedRecordProcessors.onCommand(ValueType.WORKFLOW_INSTANCE, intent, commandProcessor));
  }

  private static void addBpmnStepProcessor(
      final TypedRecordProcessors typedRecordProcessors,
      final BpmnStreamProcessor bpmnStepProcessor) {

    Arrays.stream(WorkflowInstanceIntent.values())
        .filter(WorkflowEventProcessors::isWorkflowInstanceEvent)
        .forEach(
            intent ->
                typedRecordProcessors.onEvent(
                    ValueType.WORKFLOW_INSTANCE, intent, bpmnStepProcessor));
  }

  private static void addMessageStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final MutableWorkflowInstanceSubscriptionState subscriptionState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final ZeebeState zeebeState) {
    typedRecordProcessors
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.OPEN,
            new OpenWorkflowInstanceSubscriptionProcessor(subscriptionState))
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.CORRELATE,
            new CorrelateWorkflowInstanceSubscription(
                subscriptionState, subscriptionCommandSender, zeebeState))
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.CLOSE,
            new CloseWorkflowInstanceSubscription(subscriptionState));
  }

  private static void addTimerStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final DueDateTimerChecker timerChecker,
      final ZeebeState zeebeState,
      final CatchEventBehavior catchEventOutput,
      final ExpressionProcessor expressionProcessor) {

    typedRecordProcessors
        .onCommand(
            ValueType.TIMER, TimerIntent.CREATE, new CreateTimerProcessor(zeebeState, timerChecker))
        .onCommand(
            ValueType.TIMER,
            TimerIntent.TRIGGER,
            new TriggerTimerProcessor(zeebeState, catchEventOutput, expressionProcessor))
        .onCommand(
            ValueType.TIMER,
            TimerIntent.CANCEL,
            new CancelTimerProcessor(zeebeState.getTimerState()))
        .withListener(timerChecker);
  }

  private static void addVariableDocumentStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors, final ZeebeState zeebeState) {
    final ElementInstanceState elementInstanceState = zeebeState.getElementInstanceState();
    final MutableVariableState variablesState = zeebeState.getVariableState();

    typedRecordProcessors.onCommand(
        ValueType.VARIABLE_DOCUMENT,
        VariableDocumentIntent.UPDATE,
        new UpdateVariableDocumentProcessor(elementInstanceState, variablesState));
  }

  private static void addWorkflowInstanceCreationStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final ZeebeState zeebeState,
      final StateWriter stateWriter) {
    final MutableElementInstanceState elementInstanceState = zeebeState.getElementInstanceState();
    final MutableVariableState variablesState = zeebeState.getVariableState();
    final KeyGenerator keyGenerator = zeebeState.getKeyGenerator();

    final CreateWorkflowInstanceProcessor createProcessor =
        new CreateWorkflowInstanceProcessor(
            zeebeState.getWorkflowState(),
            elementInstanceState,
            variablesState,
            keyGenerator,
            stateWriter);
    typedRecordProcessors.onCommand(
        ValueType.WORKFLOW_INSTANCE_CREATION,
        WorkflowInstanceCreationIntent.CREATE,
        createProcessor);

    typedRecordProcessors.onCommand(
        ValueType.WORKFLOW_INSTANCE_CREATION,
        WorkflowInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT,
        new CreateWorkflowInstanceWithResultProcessor(createProcessor, elementInstanceState));
  }
}
