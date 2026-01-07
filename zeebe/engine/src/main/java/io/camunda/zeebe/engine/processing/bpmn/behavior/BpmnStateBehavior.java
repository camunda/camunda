/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder.ElementTreePathProperties;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.MultiInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.RuntimeInstructionValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public final class BpmnStateBehavior {

  private final ElementInstanceState elementInstanceState;
  private final VariableState variablesState;
  private final JobState jobState;
  private final ProcessState processState;
  private final VariableBehavior variableBehavior;
  private final MultiInstanceState multiInstanceState;

  public BpmnStateBehavior(
      final ProcessingState processingState, final VariableBehavior variableBehavior) {
    this.variableBehavior = variableBehavior;

    processState = processingState.getProcessState();
    elementInstanceState = processingState.getElementInstanceState();
    variablesState = processingState.getVariableState();
    jobState = processingState.getJobState();
    multiInstanceState = processingState.getMultiInstanceState();
  }

  public ElementInstance getElementInstance(final BpmnElementContext context) {
    return getElementInstance(context.getElementInstanceKey());
  }

  public ElementInstance getElementInstance(final long elementInstanceKey) {
    return elementInstanceState.getInstance(elementInstanceKey);
  }

  public ElementTreePathProperties getElementTreePath(
      final long elementInstanceKey,
      final long flowScopeKey,
      final ProcessInstanceRecordValue processInstanceRecordValue) {
    return new ElementTreePathBuilder()
        .withElementInstanceProvider(elementInstanceState::getInstance)
        .withCallActivityIndexProvider(processState::getFlowElement)
        .withElementInstanceKey(elementInstanceKey)
        .withFlowScopeKey(flowScopeKey)
        .withRecordValue(processInstanceRecordValue)
        .build();
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

  public List<BpmnElementContext> getChildInstanceContexts(final BpmnElementContext context) {
    return elementInstanceState.getChildren(context.getElementInstanceKey()).stream()
        .map(
            childInstance ->
                context.copy(
                    childInstance.getKey(), childInstance.getValue(), childInstance.getState()))
        .collect(Collectors.toList());
  }

  public Set<DirectBuffer> getTakenSequenceFlowIds(final BpmnElementContext context) {
    return elementInstanceState.getTakenSequenceFlows(
        context.getFlowScopeKey(), context.getElementId());
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

  public Optional<DeployedProcess> getProcess(
      final long processDefinitionKey, final String tenantId) {
    return Optional.ofNullable(
        processState.getProcessByKeyAndTenant(processDefinitionKey, tenantId));
  }

  public Optional<DeployedProcess> getLatestProcessVersion(
      final DirectBuffer processId, final String tenantId) {
    final var process = processState.getLatestProcessVersionByProcessId(processId, tenantId);
    return Optional.ofNullable(process);
  }

  public Optional<DeployedProcess> getProcessByProcessIdAndDeploymentKey(
      final DirectBuffer processId, final long deploymentKey, final String tenantId) {
    final var process =
        processState.getProcessByProcessIdAndDeploymentKey(processId, deploymentKey, tenantId);
    return Optional.ofNullable(process);
  }

  public Optional<DeployedProcess> getProcessByProcessIdAndVersionTag(
      final DirectBuffer processId, final String versionTag, final String tenantId) {
    final var process =
        processState.getProcessByProcessIdAndVersionTag(processId, versionTag, tenantId);
    return Optional.ofNullable(process);
  }

  public Either<Failure, Long> getDeploymentKey(
      final long processDefinitionKey, final String tenantId) {
    return getProcess(processDefinitionKey, tenantId)
        .map(DeployedProcess::getDeploymentKey)
        .<Either<Failure, Long>>map(Either::right)
        .orElseGet(
            () ->
                // should actually never happen if deployed process was persisted correctly, but
                // just in case
                Either.left(
                    new Failure(
                        String.format(
                            "Expected to find deployment key for process definition key %s and tenant %s, but not found.",
                            processDefinitionKey, tenantId))));
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
        context.getRootProcessInstanceKey(),
        context.getBpmnProcessId(),
        context.getTenantId(),
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
        context.getRootProcessInstanceKey(),
        context.getBpmnProcessId(),
        context.getTenantId(),
        variablesAsDocument);
  }

  public void copyAllVariablesToProcessInstance(
      final long sourceScopeKey,
      final long targetProcessInstanceKey,
      final long targetRootProcessInstanceKey,
      final DeployedProcess targetProcess) {
    final var variables = variablesState.getVariablesAsDocument(sourceScopeKey);
    copyVariablesToProcessInstance(
        targetProcessInstanceKey, targetRootProcessInstanceKey, targetProcess, variables);
  }

  public void copyLocalVariablesToProcessInstance(
      final long sourceScopeKey,
      final long targetProcessInstanceKey,
      final long targetRootProcessInstanceKey,
      final DeployedProcess targetProcess) {
    final var variables = variablesState.getVariablesLocalAsDocument(sourceScopeKey);
    copyVariablesToProcessInstance(
        targetProcessInstanceKey, targetRootProcessInstanceKey, targetProcess, variables);
  }

  /**
   * Gets the runtime instructions for a specific element ID within a process instance.
   *
   * @param processInstanceKey the key of the process instance
   * @param elementId the ID of the element that has completed or terminated
   * @return a list of runtime instructions for the given element ID, potentially empty
   */
  public List<RuntimeInstructionValue> getRuntimeInstructionsForElementId(
      final long processInstanceKey, final String elementId) {
    return elementInstanceState.getRuntimeInstructionsForElementId(processInstanceKey, elementId);
  }

  private void copyVariablesToProcessInstance(
      final long targetProcessInstanceKey,
      final long targetRootProcessInstanceKey,
      final DeployedProcess targetProcess,
      final DirectBuffer variables) {
    variableBehavior.mergeDocument(
        targetProcessInstanceKey,
        targetProcess.getKey(),
        targetProcessInstanceKey,
        targetRootProcessInstanceKey,
        targetProcess.getBpmnProcessId(),
        targetProcess.getTenantId(),
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
    final var process =
        getProcess(flowScopeContext.getProcessDefinitionKey(), flowScopeContext.getTenantId());
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

  public Optional<List<DirectBuffer>> getInputCollection(final long multiInstanceKey) {
    return multiInstanceState.getInputCollection(multiInstanceKey);
  }
}
