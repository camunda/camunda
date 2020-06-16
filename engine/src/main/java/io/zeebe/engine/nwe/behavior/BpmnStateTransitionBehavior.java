/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.nwe.behavior;

import io.zeebe.engine.metrics.WorkflowEngineMetrics;
import io.zeebe.engine.nwe.BpmnElementContainerProcessor;
import io.zeebe.engine.nwe.BpmnElementContext;
import io.zeebe.engine.nwe.BpmnProcessingException;
import io.zeebe.engine.nwe.WorkflowInstanceStateTransitionGuard;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.WorkflowInstanceLifecycle;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCallActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.function.Function;

public final class BpmnStateTransitionBehavior {

  private static final String NO_WORKFLOW_FOUND_MESSAGE =
      "Expected to find a deployed workflow for process id '%s', but none found.";
  private final TypedStreamWriter streamWriter;
  private final KeyGenerator keyGenerator;
  private final BpmnStateBehavior stateBehavior;
  private final Function<BpmnElementType, BpmnElementContainerProcessor<ExecutableFlowElement>>
      processorLookUp;

  private final WorkflowInstanceStateTransitionGuard stateTransitionGuard;
  private final WorkflowEngineMetrics metrics;
  private final WorkflowInstanceRecord childInstanceRecord = new WorkflowInstanceRecord();

  public BpmnStateTransitionBehavior(
      final TypedStreamWriter streamWriter,
      final KeyGenerator keyGenerator,
      final BpmnStateBehavior stateBehavior,
      final WorkflowEngineMetrics metrics,
      final WorkflowInstanceStateTransitionGuard stateTransitionGuard,
      final Function<BpmnElementType, BpmnElementContainerProcessor<ExecutableFlowElement>>
          processorLookUp) {
    this.streamWriter = streamWriter;
    this.keyGenerator = keyGenerator;
    this.stateBehavior = stateBehavior;
    this.metrics = metrics;
    this.stateTransitionGuard = stateTransitionGuard;
    this.processorLookUp = processorLookUp;
  }

  public void transitionToActivated(final BpmnElementContext context) {

    transitionTo(context, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    stateTransitionGuard.registerStateTransition(context, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    metrics.elementInstanceActivated(context.getBpmnElementType());
  }

  public void transitionToCompleting(final BpmnElementContext context) {

    transitionTo(context, WorkflowInstanceIntent.ELEMENT_COMPLETING);

    stateTransitionGuard.registerStateTransition(
        context, WorkflowInstanceIntent.ELEMENT_COMPLETING);
  }

  public void transitionToCompleted(final BpmnElementContext context) {

    transitionTo(context, WorkflowInstanceIntent.ELEMENT_COMPLETED);

    stateTransitionGuard.registerStateTransition(context, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    metrics.elementInstanceCompleted(context.getBpmnElementType());
  }

  public void transitionToTerminating(final BpmnElementContext context) {

    transitionTo(context, WorkflowInstanceIntent.ELEMENT_TERMINATING);

    stateTransitionGuard.registerStateTransition(
        context, WorkflowInstanceIntent.ELEMENT_TERMINATING);
  }

  public void transitionToTerminated(final BpmnElementContext context) {

    transitionTo(context, WorkflowInstanceIntent.ELEMENT_TERMINATED);

    stateTransitionGuard.registerStateTransition(
        context, WorkflowInstanceIntent.ELEMENT_TERMINATED);
    metrics.elementInstanceTerminated(context.getBpmnElementType());
  }

  private void transitionTo(
      final BpmnElementContext context, final WorkflowInstanceIntent transition) {

    verifyTransition(context, transition);

    streamWriter.appendFollowUpEvent(
        context.getElementInstanceKey(), transition, context.getRecordValue());
  }

  private void verifyTransition(
      final BpmnElementContext context, final WorkflowInstanceIntent transition) {

    if (!WorkflowInstanceLifecycle.canTransition(context.getIntent(), transition)) {
      throw new BpmnProcessingException(
          context,
          String.format(
              "Expected to take transition to '%s' but element instance is in state '%s'.",
              transition, context.getIntent()));
    }
  }

  public void takeSequenceFlow(
      final BpmnElementContext context, final ExecutableSequenceFlow sequenceFlow) {
    verifyTransition(context, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);

    final var record =
        context
            .getRecordValue()
            .setElementId(sequenceFlow.getId())
            .setBpmnElementType(sequenceFlow.getElementType());

    streamWriter.appendNewEvent(
        keyGenerator.nextKey(), WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, record);

    stateBehavior.spawnToken(context);
  }

  public ElementInstance activateChildInstance(
      final BpmnElementContext context, final ExecutableFlowElement childElement) {

    final var childInstanceRecord =
        context
            .getRecordValue()
            .setFlowScopeKey(context.getElementInstanceKey())
            .setElementId(childElement.getId())
            .setBpmnElementType(childElement.getElementType());

    final var childInstanceKey = keyGenerator.nextKey();

    streamWriter.appendNewEvent(
        childInstanceKey, WorkflowInstanceIntent.ELEMENT_ACTIVATING, childInstanceRecord);

    stateBehavior.updateElementInstance(context, ElementInstance::spawnToken);

    return stateBehavior.createChildElementInstance(context, childInstanceKey, childInstanceRecord);
  }

  public void activateElementInstanceInFlowScope(
      final BpmnElementContext context, final ExecutableFlowElement element) {

    final var elementInstanceRecord =
        context
            .getRecordValue()
            .setFlowScopeKey(context.getFlowScopeKey())
            .setElementId(element.getId())
            .setBpmnElementType(element.getElementType());

    final var elementInstanceKey = keyGenerator.nextKey();

    streamWriter.appendNewEvent(
        elementInstanceKey, WorkflowInstanceIntent.ELEMENT_ACTIVATING, elementInstanceRecord);

    stateBehavior.createElementInstanceInFlowScope(
        context, elementInstanceKey, elementInstanceRecord);
  }

  public void terminateChildInstances(final BpmnElementContext context) {

    final var childInstances = stateBehavior.getChildInstances(context);

    for (final BpmnElementContext childInstanceContext : childInstances) {

      if (WorkflowInstanceLifecycle.canTerminate(childInstanceContext.getIntent())) {
        transitionToTerminating(childInstanceContext);

      } else if (childInstanceContext.getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETED) {
        // clean up the state because the completed event will not be processed
        stateBehavior.removeElementInstance(childInstanceContext);
      }
    }

    final var elementInstance = stateBehavior.getElementInstance(context);
    final var activeChildInstances = elementInstance.getNumberOfActiveElementInstances();

    if (activeChildInstances == 0) {
      // terminate element instance if all child instances are terminated
      transitionToTerminated(context);

    } else {
      // don't yet transition to terminated but wait for child instances to be terminated

      // clean up the state because some events of child instances will not be processed (e.g.
      // element completed, sequence flow taken)
      final int pendingTokens = elementInstance.getNumberOfActiveTokens() - activeChildInstances;
      for (int t = 0; t < pendingTokens; t++) {
        elementInstance.consumeToken();
      }
      stateBehavior.updateElementInstance(elementInstance);
    }
  }

  public <T extends ExecutableFlowNode> void takeOutgoingSequenceFlows(
      final T element, final BpmnElementContext context) {

    final var outgoingSequenceFlows = element.getOutgoing();
    if (outgoingSequenceFlows.isEmpty()) {
      // behaves like an implicit end event
      onElementCompleted(element, context);

    } else {
      outgoingSequenceFlows.forEach(sequenceFlow -> takeSequenceFlow(context, sequenceFlow));
    }
  }

  public void onElementCompleted(
      final ExecutableFlowElement element, final BpmnElementContext childContext) {
    final ExecutableFlowElement containerScope;
    final BpmnElementContext containerContext;
    final var flowScope = element.getFlowScope();
    if (flowScope != null) {
      containerScope = flowScope;
      containerContext = stateBehavior.getFlowScopeContext(childContext);
    } else {
      // no flowscope, assume this is called from a parent workflow
      containerContext = stateBehavior.getParentElementInstanceContext(childContext);
      containerScope = getParentWorkflowScope(containerContext, childContext);
    }
    final var containerProcessor = processorLookUp.apply(containerScope.getElementType());
    containerProcessor.onChildCompleted(containerScope, containerContext, childContext);
  }

  public void onElementTerminated(
      final ExecutableFlowElement element, final BpmnElementContext childContext) {
    final ExecutableFlowElement containerScope;
    final BpmnElementContext containerContext;
    final var flowScope = element.getFlowScope();
    if (flowScope != null) {
      containerContext = stateBehavior.getFlowScopeContext(childContext);
      containerScope = flowScope;
    } else {
      // no flowscope, assume this is called from a parent workflow
      containerContext = stateBehavior.getParentElementInstanceContext(childContext);
      containerScope = getParentWorkflowScope(containerContext, childContext);
    }
    final var containerProcessor = processorLookUp.apply(containerContext.getBpmnElementType());
    containerProcessor.onChildTerminated(containerScope, containerContext, childContext);
  }

  private ExecutableCallActivity getParentWorkflowScope(
      final BpmnElementContext callActivityContext, final BpmnElementContext childContext) {
    final var workflowKey = callActivityContext.getWorkflowKey();
    final var elementId = callActivityContext.getElementId();

    return stateBehavior
        .getWorkflow(workflowKey)
        .map(DeployedWorkflow::getWorkflow)
        .map(
            workflow ->
                workflow.getElementById(
                    elementId, BpmnElementType.CALL_ACTIVITY, ExecutableCallActivity.class))
        .orElseThrow(
            () ->
                new BpmnProcessingException(
                    childContext, String.format(NO_WORKFLOW_FOUND_MESSAGE, workflowKey)));
  }

  public long createChildProcessInstance(
      final DeployedWorkflow workflow, final BpmnElementContext context) {

    final var workflowInstanceKey = keyGenerator.nextKey();

    childInstanceRecord.reset();
    childInstanceRecord
        .setBpmnProcessId(workflow.getBpmnProcessId())
        .setVersion(workflow.getVersion())
        .setWorkflowKey(workflow.getKey())
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setParentWorkflowInstanceKey(context.getWorkflowInstanceKey())
        .setParentElementInstanceKey(context.getElementInstanceKey())
        .setElementId(workflow.getWorkflow().getId())
        .setBpmnElementType(workflow.getWorkflow().getElementType());

    streamWriter.appendFollowUpEvent(
        workflowInstanceKey, WorkflowInstanceIntent.ELEMENT_ACTIVATING, childInstanceRecord);

    stateBehavior.createElementInstance(workflowInstanceKey, childInstanceRecord);

    return workflowInstanceKey;
  }

  public void terminateChildProcessInstance(final BpmnElementContext context) {
    stateBehavior
        .getCalledChildInstance(context)
        .filter(ElementInstance::canTerminate)
        .map(instance -> context.copy(instance.getKey(), instance.getValue(), instance.getState()))
        .ifPresentOrElse(
            childInstanceContext -> transitionToTerminating(childInstanceContext) /* TERMINATING */,
            () -> transitionToTerminated(context) /* TERMINATED */);
  }
}
