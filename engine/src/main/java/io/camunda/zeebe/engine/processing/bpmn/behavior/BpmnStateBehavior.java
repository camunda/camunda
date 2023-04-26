/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class BpmnStateBehavior {

  private final ElementInstanceState elementInstanceState;
  private final VariableState variablesState;
  private final JobState jobState;
  private final ProcessState processState;
  private final VariableBehavior variableBehavior;

  public BpmnStateBehavior(
      final ProcessingState processingState, final VariableBehavior variableBehavior) {
    this.variableBehavior = variableBehavior;

    processState = processingState.getProcessState();
    elementInstanceState = processingState.getElementInstanceState();
    variablesState = processingState.getVariableState();
    jobState = processingState.getJobState();
  }

  public ElementInstance getElementInstance(final BpmnElementContext context) {
    return getElementInstance(context.getElementInstanceKey());
  }

  public ElementInstance getElementInstance(final long elementInstanceKey) {
    return elementInstanceState.getInstance(elementInstanceKey);
  }

  public JobState getJobState() {
    return jobState;
  }

  // used by canceling, since we don't care about active sequence flows
  public boolean canBeTerminated(final BpmnElementContext context) {
    final ElementInstance flowScopeInstance = getFlowScopeInstance(context);

    if (flowScopeInstance == null) {
      return false;
    }

    final long activePaths = flowScopeInstance.getNumberOfActiveElementInstances();
    if (activePaths < 0) {
      throw new BpmnProcessingException(
          context,
          String.format(
              "Expected number of active paths to be positive but got %d for instance %s",
              activePaths, flowScopeInstance));
    }

    return activePaths == 0;
  }

  public boolean canBeCompleted(final BpmnElementContext context) {
    final ElementInstance flowScopeInstance = getFlowScopeInstance(context);

    if (flowScopeInstance == null) {
      return false;
    }

    final long activePaths =
        flowScopeInstance.getNumberOfActiveElementInstances()
            + flowScopeInstance.getActiveSequenceFlows();
    if (activePaths < 0) {
      throw new BpmnProcessingException(
          context,
          String.format(
              "Expected number of active paths to be positive but got %d for instance %s",
              activePaths, flowScopeInstance));
    }

    return activePaths == 0;
  }

  public ElementInstance getFlowScopeInstance(final BpmnElementContext context) {
    return elementInstanceState.getInstance(context.getFlowScopeKey());
  }

  public BpmnElementContext getFlowScopeContext(final BpmnElementContext context) {
    final var flowScope = getFlowScopeInstance(context);
    return context.copy(flowScope.getKey(), flowScope.getValue(), flowScope.getState());
  }

  public BpmnElementContext getParentElementInstanceContext(final BpmnElementContext context) {
    final var parentElementInstance =
        elementInstanceState.getInstance(context.getParentElementInstanceKey());
    return context.copy(
        parentElementInstance.getKey(),
        parentElementInstance.getValue(),
        parentElementInstance.getState());
  }

  public Optional<DeployedProcess> getProcess(final long processDefinitionKey) {
    return Optional.ofNullable(processState.getProcessByKey(processDefinitionKey));
  }

  public Optional<DeployedProcess> getLatestProcessVersion(final DirectBuffer processId) {
    final var process = processState.getLatestProcessVersionByProcessId(processId);
    return Optional.ofNullable(process);
  }

  public Optional<ElementInstance> getCalledChildInstance(final BpmnElementContext context) {
    final var elementInstance = getElementInstance(context);
    final var calledChildInstanceKey = elementInstance.getCalledChildInstanceKey();
    return Optional.ofNullable(elementInstanceState.getInstance(calledChildInstanceKey));
  }

  public DirectBuffer getLocalVariable(
      final BpmnElementContext context, final DirectBuffer variableName) {
    return variablesState.getVariableLocal(context.getElementInstanceKey(), variableName);
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
    variableBehavior.setLocalVariable(
        context.getElementInstanceKey(),
        context.getProcessDefinitionKey(),
        context.getProcessInstanceKey(),
        context.getBpmnProcessId(),
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

    variableBehavior.mergeDocument(
        targetScope,
        context.getProcessDefinitionKey(),
        context.getProcessInstanceKey(),
        context.getBpmnProcessId(),
        variablesAsDocument);
  }

  public void copyVariablesToProcessInstance(
      final long sourceScopeKey,
      final long targetProcessInstanceKey,
      final DeployedProcess targetProcess) {
    final var variables = variablesState.getVariablesAsDocument(sourceScopeKey);
    variableBehavior.mergeDocument(
        targetProcessInstanceKey,
        targetProcess.getKey(),
        targetProcessInstanceKey,
        targetProcess.getBpmnProcessId(),
        variables);
  }

  public boolean isInterrupted(final BpmnElementContext flowScopeContext) {
    final var flowScopeInstance =
        elementInstanceState.getInstance(flowScopeContext.getElementInstanceKey());
    return flowScopeInstance.getNumberOfActiveElementInstances() == 0
        && flowScopeInstance.isInterrupted()
        && flowScopeInstance.isActive();
  }

  public boolean isInterruptedByTerminateEndEvent(
      final BpmnElementContext flowScopeContext, final ElementInstance flowScopeInstance) {
    final var process = getProcess(flowScopeContext.getProcessDefinitionKey());
    if (process.isEmpty() || !isInterrupted(flowScopeContext)) {
      return false;
    }

    final var interruptingElement =
        process.get().getProcess().getElementById(flowScopeInstance.getInterruptingElementId());
    if (interruptingElement.getElementType().equals(BpmnElementType.END_EVENT)) {
      return ((ExecutableEndEvent) interruptingElement).isTerminateEndEvent();
    }

    return false;
  }

  public int getNumberOfTakenSequenceFlows(
      final long flowScopeKey, final DirectBuffer gatewayElementId) {
    return elementInstanceState.getNumberOfTakenSequenceFlows(flowScopeKey, gatewayElementId);
  }
}
