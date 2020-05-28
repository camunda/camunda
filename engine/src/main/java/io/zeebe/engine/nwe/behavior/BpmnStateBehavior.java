/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.nwe.behavior;

import io.zeebe.engine.nwe.BpmnElementContext;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.EventScopeInstanceState;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public final class BpmnStateBehavior {

  private final ElementInstanceState elementInstanceState;
  private final EventScopeInstanceState eventScopeInstanceState;
  private final VariablesState variablesState;
  private final JobState jobState;

  public BpmnStateBehavior(final ZeebeState zeebeState) {
    final var workflowState = zeebeState.getWorkflowState();
    elementInstanceState = workflowState.getElementInstanceState();
    eventScopeInstanceState = workflowState.getEventScopeInstanceState();
    variablesState = elementInstanceState.getVariablesState();
    jobState = zeebeState.getJobState();
  }

  public ElementInstance getElementInstance(final BpmnElementContext context) {
    return elementInstanceState.getInstance(context.getElementInstanceKey());
  }

  public void updateElementInstance(final ElementInstance elementInstance) {
    elementInstanceState.updateInstance(elementInstance);
  }

  public void updateElementInstance(
      final BpmnElementContext context, final Consumer<ElementInstance> modifier) {
    final var elementInstance = getElementInstance(context);
    modifier.accept(elementInstance);
    updateElementInstance(elementInstance);
  }

  public void updateFlowScopeInstance(
      final BpmnElementContext context, final Consumer<ElementInstance> modifier) {
    final var elementInstance = getFlowScopeInstance(context);
    modifier.accept(elementInstance);
    updateElementInstance(elementInstance);
  }

  public JobState getJobState() {
    return jobState;
  }

  public boolean isLastActiveExecutionPathInScope(final BpmnElementContext context) {
    final ElementInstance flowScopeInstance = getFlowScopeInstance(context);

    if (flowScopeInstance == null) {
      return false;
    }

    final int activePaths = flowScopeInstance.getNumberOfActiveTokens();
    if (activePaths < 0) {
      throw new IllegalStateException(
          String.format(
              "Expected number of active paths to be positive but got %d for instance %s",
              activePaths, flowScopeInstance));
    }

    return activePaths == 1;
  }

  public void consumeToken(final BpmnElementContext context) {
    final ElementInstance flowScopeInstance = getFlowScopeInstance(context);
    if (flowScopeInstance != null) {
      elementInstanceState.consumeToken(flowScopeInstance.getKey());
    }
  }

  public void spawnToken(final BpmnElementContext context) {
    final ElementInstance flowScopeInstance = getFlowScopeInstance(context);
    if (flowScopeInstance != null) {
      elementInstanceState.spawnToken(flowScopeInstance.getKey());
    }
  }

  // replaces BpmnStepContext.getFlowScopeInstance()
  public ElementInstance getFlowScopeInstance(final BpmnElementContext context) {
    return elementInstanceState.getInstance(context.getFlowScopeKey());
  }

  public void removeElementInstance(final BpmnElementContext context) {
    eventScopeInstanceState.deleteInstance(context.getElementInstanceKey());
    elementInstanceState.removeInstance(context.getElementInstanceKey());
  }

  public List<BpmnElementContext> getChildInstances(final BpmnElementContext context) {
    return elementInstanceState.getChildren(context.getElementInstanceKey()).stream()
        .map(
            childInstance ->
                context.copy(
                    childInstance.getKey(), childInstance.getValue(), childInstance.getState()))
        .collect(Collectors.toList());
  }

  // todo(@korthout): remove this method
  //  we should restrict what can be done to the state
  public VariablesState getVariablesState() {
    return variablesState;
  }

  public ElementInstance createChildElementInstance(
      final BpmnElementContext context,
      final long childInstanceKey,
      final WorkflowInstanceRecord childRecord) {
    final var parentElementInstance = getElementInstance(context);
    return elementInstanceState.newInstance(
        parentElementInstance,
        childInstanceKey,
        childRecord,
        WorkflowInstanceIntent.ELEMENT_ACTIVATING);
  }

  public void createElementInstanceInFlowScope(
      final BpmnElementContext context,
      final long elementInstanceKey,
      final WorkflowInstanceRecord record) {
    final ElementInstance flowScopeInstance = getFlowScopeInstance(context);
    elementInstanceState.newInstance(
        flowScopeInstance, elementInstanceKey, record, WorkflowInstanceIntent.ELEMENT_ACTIVATING);
  }

  public void setLocalVariable(
      final BpmnElementContext context,
      final DirectBuffer variableName,
      final DirectBuffer variableValue) {
    setLocalVariable(context, variableName, variableValue, 0, variableValue.capacity());
  }

  public void setLocalVariable(
      final BpmnElementContext context,
      final DirectBuffer variableName,
      final DirectBuffer variableValue,
      final int valueOffset,
      final int valueLength) {
    variablesState.setVariableLocal(
        context.getElementInstanceKey(),
        context.getWorkflowKey(),
        variableName,
        variableValue,
        valueOffset,
        valueLength);
  }

  public void propagateVariable(final BpmnElementContext context, final DirectBuffer variableName) {

    final var sourceScope = context.getElementInstanceKey();
    final var targetScope = context.getFlowScopeKey();

    final var variablesAsDocument =
        variablesState.getVariablesAsDocument(sourceScope, List.of(variableName));

    variablesState.setVariablesFromDocument(
        targetScope, context.getWorkflowKey(), variablesAsDocument);
  }

  public BpmnElementContext getFlowScopeContext(final BpmnElementContext context) {
    final var flowScope = getFlowScopeInstance(context);
    return context.copy(flowScope.getKey(), flowScope.getValue(), flowScope.getState());
  }
}
