/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.processor.workflow.instance.CreateWorkflowInstanceProcessor;
import io.zeebe.engine.processor.workflow.message.CloseWorkflowInstanceSubscription;
import io.zeebe.engine.processor.workflow.message.CorrelateWorkflowInstanceSubscription;
import io.zeebe.engine.processor.workflow.message.OpenWorkflowInstanceSubscriptionProcessor;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processor.workflow.timer.CancelTimerProcessor;
import io.zeebe.engine.processor.workflow.timer.CreateTimerProcessor;
import io.zeebe.engine.processor.workflow.timer.DueDateTimerChecker;
import io.zeebe.engine.processor.workflow.timer.TriggerTimerProcessor;
import io.zeebe.engine.processor.workflow.variable.UpdateVariableDocumentProcessor;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.engine.state.instance.WorkflowEngineState;
import io.zeebe.engine.state.message.WorkflowInstanceSubscriptionState;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceSubscriptionIntent;
import java.util.Arrays;
import java.util.List;

public class WorkflowEventProcessors {

  private static final List<WorkflowInstanceIntent> WORKFLOW_INSTANCE_COMMANDS =
      Arrays.asList(WorkflowInstanceIntent.CANCEL);

  private static boolean isWorkflowInstanceEvent(WorkflowInstanceIntent intent) {
    return !WORKFLOW_INSTANCE_COMMANDS.contains(intent);
  }

  public static BpmnStepProcessor addWorkflowProcessors(
      ZeebeState zeebeState,
      TypedRecordProcessors typedRecordProcessors,
      SubscriptionCommandSender subscriptionCommandSender,
      CatchEventBehavior catchEventBehavior,
      DueDateTimerChecker timerChecker) {
    final WorkflowInstanceSubscriptionState subscriptionState =
        zeebeState.getWorkflowInstanceSubscriptionState();

    final WorkflowEngineState workflowEngineState =
        new WorkflowEngineState(zeebeState.getWorkflowState());
    typedRecordProcessors.withListener(workflowEngineState);

    addWorkflowInstanceCommandProcessor(typedRecordProcessors, workflowEngineState, zeebeState);

    final BpmnStepProcessor bpmnStepProcessor =
        new BpmnStepProcessor(workflowEngineState, zeebeState, catchEventBehavior);
    addBpmnStepProcessor(typedRecordProcessors, bpmnStepProcessor);

    addMessageStreamProcessors(
        typedRecordProcessors, subscriptionState, subscriptionCommandSender, zeebeState);
    addTimerStreamProcessors(typedRecordProcessors, timerChecker, zeebeState, catchEventBehavior);
    addVariableDocumentStreamProcessors(typedRecordProcessors, zeebeState);
    addWorkflowInstanceCreationStreamProcessors(typedRecordProcessors, zeebeState);

    return bpmnStepProcessor;
  }

  private static void addWorkflowInstanceCommandProcessor(
      final TypedRecordProcessors typedRecordProcessors,
      WorkflowEngineState workflowEngineState,
      final ZeebeState zeebeState) {

    final WorkflowInstanceCommandProcessor commandProcessor =
        new WorkflowInstanceCommandProcessor(workflowEngineState, zeebeState.getKeyGenerator());

    WORKFLOW_INSTANCE_COMMANDS.forEach(
        intent ->
            typedRecordProcessors.onCommand(ValueType.WORKFLOW_INSTANCE, intent, commandProcessor));
  }

  private static void addBpmnStepProcessor(
      final TypedRecordProcessors typedRecordProcessors, BpmnStepProcessor bpmnStepProcessor) {

    Arrays.stream(WorkflowInstanceIntent.values())
        .filter(WorkflowEventProcessors::isWorkflowInstanceEvent)
        .forEach(
            intent ->
                typedRecordProcessors.onEvent(
                    ValueType.WORKFLOW_INSTANCE, intent, bpmnStepProcessor));
  }

  private static void addMessageStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      WorkflowInstanceSubscriptionState subscriptionState,
      SubscriptionCommandSender subscriptionCommandSender,
      ZeebeState zeebeState) {
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
      DueDateTimerChecker timerChecker,
      ZeebeState zeebeState,
      CatchEventBehavior catchEventOutput) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();

    typedRecordProcessors
        .onCommand(
            ValueType.TIMER, TimerIntent.CREATE, new CreateTimerProcessor(zeebeState, timerChecker))
        .onCommand(
            ValueType.TIMER,
            TimerIntent.TRIGGER,
            new TriggerTimerProcessor(zeebeState, catchEventOutput))
        .onCommand(ValueType.TIMER, TimerIntent.CANCEL, new CancelTimerProcessor(workflowState))
        .withListener(timerChecker);
  }

  private static void addVariableDocumentStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors, ZeebeState zeebeState) {
    final ElementInstanceState elementInstanceState =
        zeebeState.getWorkflowState().getElementInstanceState();
    final VariablesState variablesState = elementInstanceState.getVariablesState();

    typedRecordProcessors.onCommand(
        ValueType.VARIABLE_DOCUMENT,
        VariableDocumentIntent.UPDATE,
        new UpdateVariableDocumentProcessor(elementInstanceState, variablesState));
  }

  private static void addWorkflowInstanceCreationStreamProcessors(
      final TypedRecordProcessors typedRecordProcessors, ZeebeState zeebeState) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final ElementInstanceState elementInstanceState = workflowState.getElementInstanceState();
    final VariablesState variablesState = elementInstanceState.getVariablesState();
    final KeyGenerator keyGenerator = zeebeState.getKeyGenerator();

    typedRecordProcessors.onCommand(
        ValueType.WORKFLOW_INSTANCE_CREATION,
        WorkflowInstanceCreationIntent.CREATE,
        new CreateWorkflowInstanceProcessor(
            workflowState, elementInstanceState, variablesState, keyGenerator));
  }
}
